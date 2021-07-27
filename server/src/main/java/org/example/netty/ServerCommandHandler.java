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
        try {
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
                case CREATE_DIR:
                    createDir(ctx, command);
                    break;
            }
        } catch (Exception e) {
            log.error("Channel read exception: {}", e.getMessage(), e);
            sendErrorMessage(ctx, "Unknown server error");
        }
    }

    private void createDir(ChannelHandlerContext ctx, Command command) throws IOException {
        String current = (String) command.getParameter(ParameterType.CURRENT);
        String path = getPathToCurrent(current, (User) command.getParameter(ParameterType.USER));
        File dir = new File(path + File.separator + command.getParameter(ParameterType.DIR_NAME));
        if (Files.exists(dir.toPath()) && Files.isDirectory(dir.toPath())) {
            sendErrorMessage(ctx, dir.getName() + " already exists");
            return;
        }
        Files.createDirectory(dir.toPath());
        ctx.writeAndFlush(new Command(CommandType.CREATE_OK));
        ctx.writeAndFlush(new Command(CommandType.CONTENT_RESPONSE).setAll(getUserFiles(current, (User) command.getParameter(ParameterType.USER))));
    }

    private void renameProcess(ChannelHandlerContext ctx, String path, String newName) throws IOException {
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

    private void deleteProcess(ChannelHandlerContext ctx, String path) throws IOException {
        File file = new File(path);
        deleteFile(file);
        ctx.writeAndFlush(new Command(CommandType.DELETE_OK));
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

    private void sendFileProcess(ChannelHandlerContext ctx, String path) throws IOException {
        File file = new File(path);
        FileInputStream is = new FileInputStream(file);
        byte[] buffer = new byte[is.available()];
        int size = is.read(buffer);
        String md5 = getFileChecksum(file);
        ctx.writeAndFlush(new Command(CommandType.FILE_DOWNLOAD)
                .setParameter(ParameterType.FILE_DTO,
                        FileDTO.builder()
                                .name(file.getName())
                                .content(buffer)
                                .fullSize((long) size)
                                .md5(md5)
                                .build())
        );
    }

    private void userAuthProcess(ChannelHandlerContext ctx, User user) throws IOException {
        if (userService.isAuthorized(user)) {
            ctx.writeAndFlush(new Command(CommandType.AUTH_OK));
            ctx.writeAndFlush(new Command(CommandType.CONTENT_RESPONSE).setAll(getUserFiles("root", user)));
        } else {
            ctx.writeAndFlush(new Command(CommandType.AUTH_NO));
        }
    }

    private void uploadFileProcess(ChannelHandlerContext ctx, Command command) {
        try {
            FileDTO dto = (FileDTO) command.getParameter(ParameterType.FILE_DTO);
            String fullFilePath = getPathToCurrent(dto.getPath(), dto.getOwner()) + dto.getName();
            File file = new File(fullFilePath);
            if (!DigestUtils.md5Hex(dto.getContent()).equals(dto.getMd5())) {
                ctx.writeAndFlush(new Command(CommandType.UPLOAD_ERROR).setParameter(ParameterType.MESSAGE, "File is corrupted"));
                Files.deleteIfExists(Paths.get(fullFilePath));
                return;
            }
            if (Files.exists(file.toPath()) && dto.isStart()) {
                ctx.writeAndFlush(new Command(CommandType.UPLOAD_ERROR).setParameter(ParameterType.MESSAGE, file.getName() + " already exists"));
                return;
            }
            FileOutputStream os = new FileOutputStream(file, true);
            if (!dto.isEnd()) {
                os.write(dto.getContent());
                ctx.writeAndFlush(new Command(CommandType.NEXT_PART));
            } else {
                os.write(dto.getContent());
                if (file.length() != dto.getFullSize()) {
                    ctx.writeAndFlush(new Command(CommandType.UPLOAD_ERROR).setParameter(ParameterType.MESSAGE, "File is corrupted"));
                    Files.deleteIfExists(Paths.get(fullFilePath));
                } else {
                    ctx.writeAndFlush(new Command(CommandType.FILE_UPLOAD_OK));
                    ctx.writeAndFlush(new Command(CommandType.CONTENT_RESPONSE).setAll(getUserFiles(dto.getPath(), dto.getOwner())));
                }
            }
        } catch (IOException e) {
            log.error("Upload file error: {}", e.getMessage(), e);
            ctx.writeAndFlush(new Command(CommandType.UPLOAD_ERROR));
        }
    }

    private Map<ParameterType, Object> getUserFiles(String current, User user) throws IOException {
        Map<ParameterType, Object> parameters = new HashMap<>();
        List<String> directories = new ArrayList<>();
        List<String> files = new ArrayList<>();
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

    private String getPathToCurrent(String current, User user) throws IOException {
        StringBuilder sb = new StringBuilder(userService.getRootPath(user));
        sb.append(File.separator);
        if (!current.equals("root") && !current.equals("/")) {
            String[] dirs = current.split("/");
            for (String dir : dirs) {
                sb.append(dir).append(File.separator);
            }
        }
        return sb.toString();
    }

    private String getFileChecksum(File file) throws IOException {
        String md5;
        InputStream is = Files.newInputStream(Paths.get(file.toURI()));
        md5 = DigestUtils.md5Hex(is);
        return md5;
    }

    private void sendErrorMessage(ChannelHandlerContext ctx, String message) {
        ctx.writeAndFlush(new Command(CommandType.ERROR).setParameter(ParameterType.MESSAGE, message));
    }
}
