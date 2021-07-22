package org.example.netty;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.example.model.dto.FileDTO;
import org.example.model.command.Command;
import org.example.model.command.CommandType;
import org.example.model.command.ParameterType;
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
    protected void channelRead0(ChannelHandlerContext ctx, Command command) throws Exception {
        log.info("Command received: {}", command);
        switch (command.getCommandType()) {
            case AUTH_REQUEST:
                userAuthProcess(ctx, command);
                break;
            case CONTENT_REQUEST:
                ctx.writeAndFlush(new Command(CommandType.CONTENT_RESPONSE,
                        getUserFiles((String) command.getParameters().get(ParameterType.CURRENT_DIR),
                                (User) command.getParameters().get(ParameterType.USER))));
                break;
            case FILE_UPLOAD:
                uploadFileProcess(ctx, command);
                break;
        }
    }

    private void userAuthProcess(ChannelHandlerContext ctx, Command command) {
        if (userService.isAuthorized((User) command.getParameters().get(ParameterType.USER))) {
            ctx.writeAndFlush(new Command(CommandType.AUTH_OK, null));
            ctx.writeAndFlush(new Command(CommandType.CONTENT_RESPONSE, getUserFiles("root",
                    (User) command.getParameters().get(ParameterType.USER))));
        } else {
            ctx.writeAndFlush(new Command(CommandType.AUTH_NO, null));
        }
    }

    private void uploadFileProcess(ChannelHandlerContext ctx, Command command) {
        FileDTO dto = (FileDTO) command.getParameters().get(ParameterType.FILE_DTO);
        String fullFilePath = getPathToUserDir(dto.getPath(), dto.getOwner()) + dto.getName();

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

        ctx.writeAndFlush(new Command(CommandType.FILE_UPLOAD_OK, null));
        ctx.writeAndFlush(new Command(CommandType.CONTENT_RESPONSE, getUserFiles(dto.getPath(), dto.getOwner())));
    }

    private Map<ParameterType, Object> getUserFiles(String currentDir, User user) {

        Map<ParameterType, Object> map = new HashMap<>();

        List<String> directories = new ArrayList<>();
        List<String> files = new ArrayList<>();

        try {
            Files.walkFileTree(Paths.get(getPathToUserDir(currentDir, user)), Collections.singleton(FileVisitOption.FOLLOW_LINKS), 1, new SimpleFileVisitor<Path>() {
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

        map.put(ParameterType.DIRECTORIES, directories);
        map.put(ParameterType.FILES_LIST, files);
        map.put(ParameterType.CURRENT_DIR, current);

        return map;
    }

    private String getPathToUserDir(String currentDir, User user) {
        String separator = File.separator;
        StringBuilder sb = new StringBuilder(userService.getRootPath(user));
        if (!currentDir.equals("root") && !currentDir.equals("/")) {
            String[] dirs = currentDir.split("/");
            for (int i = 0; i < dirs.length; i++) {
                sb.append(dirs[i]).append(separator);
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
        Map<ParameterType, Object> parameters = new HashMap<>();
        parameters.put(ParameterType.MESSAGE, message);
        ctx.writeAndFlush(new Command(CommandType.ERROR, parameters));
    }
}
