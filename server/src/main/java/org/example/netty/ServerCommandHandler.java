package org.example.netty;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.example.model.command.Command;
import org.example.model.command.CommandType;
import org.example.model.command.ContentActionType;
import org.example.model.command.ParameterType;
import org.example.model.dto.FileDTO;
import org.example.model.user.User;
import org.example.service.UserService;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

@Slf4j
public class ServerCommandHandler extends SimpleChannelInboundHandler<Command> {

    private final UserService userService;

    public ServerCommandHandler(UserService userService) {
        this.userService = userService;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Command command) {
        User user = (User) command.getParameter(ParameterType.USER);
        log.info("User: {}, command received: {}", user, command);
        switch (command.getCommandType()) {
            case AUTH_REQUEST:
                userAuthProcess(ctx, user);
                break;
            case CONTENT_REQUEST:
                String currentDir = (String) command.getParameter(ParameterType.CURRENT_DIR);
                String path = getPathToUserFile(currentDir, user);
                if (!Files.exists(Paths.get(path))) {
                    sendErrorMessage(ctx, "NOT FOUND");
                    return;
                }
                ContentActionType type = (ContentActionType) command.getParameter(ParameterType.CONTENT_ACTION);
                switch (type) {
                    case OPEN:
                        ctx.writeAndFlush(new Command(CommandType.CONTENT_RESPONSE).setAll(getUserFiles(currentDir, user)));
                        break;
                    case RENAME:

                    case DELETE:
                    case DOWNLOAD:
                        sendErrorMessage(ctx, "METHOD NOT YET REALISED");
                        break;
                }
                break;
            case FILE_UPLOAD:
                uploadFileProcess(ctx, command);
                break;
        }
    }

    private void userAuthProcess(ChannelHandlerContext ctx, User user) {
        if (userService.isAuthorized(user)) {
            ctx.writeAndFlush(new Command(CommandType.AUTH_OK));
            ctx.writeAndFlush(new Command(CommandType.CONTENT_RESPONSE).setAll(getUserFiles("root", user)));
        } else {
            ctx.writeAndFlush(new Command(CommandType.AUTH_NO));
        }
    }

    private void uploadFileProcess(ChannelHandlerContext ctx, Command command) {
        FileDTO dto = (FileDTO) command.getParameter(ParameterType.FILE_DTO);
        String fullFilePath = getPathToUserFile(dto.getPath(), dto.getOwner()) + dto.getName();

        if (Files.exists(Paths.get(fullFilePath))) {
            sendErrorMessage(ctx, "File already exists");
            return;
        }

        File file = new File(fullFilePath);

        try (FileOutputStream os = new FileOutputStream(file)) {
            os.write(dto.getContent());
        } catch (IOException e) {
            log.error("File write exception: {}", e.getMessage());
        }

        if (file.length() != dto.getSize() || !dto.getMd5().equals(getFileChecksum(file))) {
            sendErrorMessage(ctx, "File is corrupted");
            try {
                Files.deleteIfExists(Paths.get(fullFilePath));
            } catch (IOException e) {
                log.error("Corrupted file delete exception: {}", e.getMessage());
            }
            return;
        }

        ctx.writeAndFlush(new Command(CommandType.FILE_UPLOAD_OK));
        ctx.writeAndFlush(new Command(CommandType.CONTENT_RESPONSE).setAll(getUserFiles(dto.getPath(), dto.getOwner())));
    }

    private Map<ParameterType, Object> getUserFiles(String currentDir, User user) {

        Map<ParameterType, Object> parameters = new HashMap<>();

        List<String> directories = new ArrayList<>();
        List<String> files = new ArrayList<>();

        try {
            Files.walkFileTree(Paths.get(getPathToUserFile(currentDir, user)), Collections.singleton(FileVisitOption.FOLLOW_LINKS), 1, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (file.toFile().isDirectory()) {
                        directories.add(file.getFileName().toString());
                    } else if (file.toFile().isFile()) {
                        files.add(file.getFileName().toString());
                    }
                    return super.visitFile(file, attrs);
                }
            });
        } catch (IOException e) {
            log.error("walkFileTree exception: {}", e.getMessage());
        }

        List<String> current = new ArrayList<>();
        current.add("root");
        if (!currentDir.equals("root")) {
            Arrays.stream(currentDir.split("/")).filter(str -> !str.isEmpty()).forEach(current::add);
        }

        parameters.put(ParameterType.DIRECTORIES, directories);
        parameters.put(ParameterType.FILES, files);
        parameters.put(ParameterType.CURRENT_DIR, current);

        return parameters;
    }

    private String getPathToUserFile(String currentDir, User user) {
        String separator = File.separator;
        StringBuilder sb = new StringBuilder(userService.getRootPath(user));
        if (!currentDir.equals("root") && !currentDir.equals("/")) {
            String[] dirs = currentDir.split("/");
            for (String dir : dirs) {
                sb.append(dir).append(separator);
            }
        }
        return sb.toString();
    }

    private String getFileChecksum(File file) {
        String md5 = "";
        try (InputStream is = Files.newInputStream(Paths.get(file.toURI()))) {
            md5 = DigestUtils.md5Hex(is);
        } catch (Exception e) {
            log.error("File checkSum exception: {}", e.getMessage());
        }
        return md5;
    }

    private void sendErrorMessage(ChannelHandlerContext ctx, String message) {
        ctx.writeAndFlush(new Command(CommandType.ERROR).setParameter(ParameterType.MESSAGE, message));
    }
}
