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

import java.io.*;
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
        log.info("Command received: {}", command);
        switch (command.getCommandType()) {
            case AUTH_REQUEST:
                userAuthProcess(ctx, user);
                break;
            case CONTENT_REQUEST:
                ContentActionType type = (ContentActionType) command.getParameter(ParameterType.CONTENT_ACTION);
                String current = (String) command.getParameter(ParameterType.CURRENT);
                String path = getPathToCurrent(current, user);
                if (!Files.exists(Paths.get(path))) {
                    sendErrorMessage(ctx, "NOT FOUND");
                    return;
                }
                switch (type) {
                    case OPEN:
                        ctx.writeAndFlush(new Command(CommandType.CONTENT_RESPONSE).setAll(getUserFiles(current, user)));
                        break;
                    case DOWNLOAD:
                        sendFileProcess(ctx, path);
                        break;
                    case DELETE:
                        deleteProcess(ctx, path);
                        log.info(current.substring(0, current.lastIndexOf("/")));
                        ctx.writeAndFlush(new Command(CommandType.CONTENT_RESPONSE).setAll(getUserFiles(current.substring(0, current.lastIndexOf("/")), user)));
                        break;
                    case RENAME:
                        renameProcess(ctx, path, (String) command.getParameter(ParameterType.NEW_NAME));
                        ctx.writeAndFlush(new Command(CommandType.CONTENT_RESPONSE).setAll(getUserFiles(current.substring(0, current.lastIndexOf("/")), user)));
                        break;
                }
                break;
            case FILE_UPLOAD:
                uploadFileProcess(ctx, command);
                break;
        }
    }

    private void renameProcess(ChannelHandlerContext ctx, String path, String newName) {
        try {
            File src = new File(path);
            File dst = new File(src.getParent() + File.separator + newName);
            if (dst.exists()) {
                ctx.writeAndFlush(new Command(CommandType.ERROR).setParameter(ParameterType.MESSAGE, newName + " already exists"));
                return;
            }
            if (src.isFile()) {
                Files.move(src.toPath(), dst.toPath());
            } else if (src.isDirectory()) {
                moveDirectory(src, dst);
                deleteFile(src);
            }
            ctx.writeAndFlush(new Command(CommandType.RENAME_OK));
        } catch (IOException e) {
            log.error("Rename file exception: {}", e.getMessage(), e);
        }
    }

    private void moveDirectory(File src, File dst) throws IOException {
        Files.createDirectory(dst.toPath());
        File[] files = src.listFiles();
        for (File f : files) {
            if (f.isFile()) {
                Files.move(f.toPath(), new File(dst + File.separator + f.getName()).toPath());
            } else if (f.isDirectory()) {
                moveDirectory(f, new File(dst + File.separator + f.getName()));
            }
        }
    }

    private void deleteProcess(ChannelHandlerContext ctx, String path) {
        File file = new File(path);
        try {
            deleteFile(file);
            ctx.writeAndFlush(new Command(CommandType.DELETE_OK));
        } catch (IOException e) {
            log.error("Delete file exception: {}", e.getMessage(), e);
        }
    }

    private void deleteFile(File file) throws IOException {
        File[] content = file.listFiles();
        if (content != null) {
            for (File f : content) {
                deleteFile(f);
            }
        }
        Files.delete(file.toPath());
    }

    private void sendFileProcess(ChannelHandlerContext ctx, String path) {
        File file = new File(path);
        try (FileInputStream is = new FileInputStream(file)) {
            byte[] buffer = new byte[is.available()];
            int size = is.read(buffer);
            String md5 = getFileChecksum(file);

            ctx.writeAndFlush(new Command(CommandType.FILE_DOWNLOAD)
                    .setParameter(ParameterType.FILE_DTO,
                            FileDTO.builder()
                                    .name(file.getName())
                                    .content(buffer)
                                    .size((long) size)
                                    .md5(md5)
                                    .build())
            );
        } catch (Exception e) {
            log.error("Send file exception: {}", e.getMessage(), e);
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
        String fullFilePath = getPathToCurrent(dto.getPath(), dto.getOwner()) + dto.getName();
        if (Files.exists(Paths.get(fullFilePath))) {
            sendErrorMessage(ctx, "File already exists");
            return;
        }
        File file = new File(fullFilePath);
        try (FileOutputStream os = new FileOutputStream(file)) {
            os.write(dto.getContent());
        } catch (IOException e) {
            log.error("File write exception: {}", e.getMessage(), e);
        }
        if (file.length() != dto.getSize() || !dto.getMd5().equals(getFileChecksum(file))) {
            sendErrorMessage(ctx, "File is corrupted");
            try {
                Files.deleteIfExists(Paths.get(fullFilePath));
            } catch (IOException e) {
                log.error("Corrupted file delete exception: {}", e.getMessage(), e);
            }
            return;
        }
        ctx.writeAndFlush(new Command(CommandType.FILE_UPLOAD_OK));
        ctx.writeAndFlush(new Command(CommandType.CONTENT_RESPONSE).setAll(getUserFiles(dto.getPath(), dto.getOwner())));
    }

    private Map<ParameterType, Object> getUserFiles(String current, User user) {
        Map<ParameterType, Object> parameters = new HashMap<>();
        List<String> directories = new ArrayList<>();
        List<String> files = new ArrayList<>();
        try {
            Files.walkFileTree(Paths.get(getPathToCurrent(current, user)), Collections.singleton(FileVisitOption.FOLLOW_LINKS), 1, new SimpleFileVisitor<Path>() {
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
            log.error("walkFileTree exception: {}", e.getMessage(), e);
        }
        List<String> currentPath = new ArrayList<>();
        currentPath.add("root");
        if (!current.equals("root")) {
            Arrays.stream(current.split("/")).filter(str -> !str.isEmpty()).forEach(currentPath::add);
        }
        parameters.put(ParameterType.DIRECTORIES, directories);
        parameters.put(ParameterType.FILES, files);
        parameters.put(ParameterType.CURRENT, currentPath);
        return parameters;
    }

    private String getPathToCurrent(String currentDir, User user) {
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
