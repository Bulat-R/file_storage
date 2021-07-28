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
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class ServerCommandHandler extends SimpleChannelInboundHandler<Command> {

    private final UserService userService;
    private final Semaphore semaphore;
    private final AtomicBoolean hasError;
    private User user;

    public ServerCommandHandler(UserService userService) {
        this.userService = userService;
        semaphore = new Semaphore(1);
        hasError = new AtomicBoolean(false);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Command command) {
        try {
            log.info("Command received: {}", command);
            if (command.getCommandType() == CommandType.AUTH_REQUEST) {
                userAuthProcess(ctx, command);
                return;
            }
            if (user != null && command.getParameter(ParameterType.USER).equals(user)) {
                switch (command.getCommandType()) {
                    case CONTENT_REQUEST:
                        ContentActionType type = (ContentActionType) command.getParameter(ParameterType.CONTENT_ACTION);
                        String current = (String) command.getParameter(ParameterType.CURRENT);
                        String path = getPathToCurrent(current);
                        if (!Files.exists(Paths.get(path))) {
                            sendErrorMessage(ctx, current + " not found");
                            return;
                        }
                        switch (type) {
                            case OPEN:
                                ctx.writeAndFlush(new Command(CommandType.CONTENT_RESPONSE).setAll(getUserFiles(current)));
                                break;
                            case DOWNLOAD:
                                downloadFileProcess(ctx, path);
                                break;
                            case DELETE:
                                deleteProcess(ctx, path);
                                ctx.writeAndFlush(new Command(CommandType.CONTENT_RESPONSE).setAll(getUserFiles(current.substring(0, current.lastIndexOf("/")))));
                                break;
                            case RENAME:
                                renameProcess(ctx, path, (String) command.getParameter(ParameterType.NEW_NAME));
                                ctx.writeAndFlush(new Command(CommandType.CONTENT_RESPONSE).setAll(getUserFiles(current.substring(0, current.lastIndexOf("/")))));
                                break;
                        }
                        break;
                    case FILE_UPLOAD:
                        uploadFileProcess(ctx, command);
                        break;
                    case CREATE_DIR:
                        createDir(ctx, command);
                        break;
                    case DOWNLOAD_ERROR:
                        hasError.set(true);
                        semaphore.release();
                        break;
                    case NEXT_PART:
                        semaphore.release();
                        break;
                }
            }
        } catch (Exception e) {
            log.error("Channel read exception: {}", e.getMessage(), e);
            sendErrorMessage(ctx, "Unknown server error");
        }
    }

    private void createDir(ChannelHandlerContext ctx, Command command) throws Exception {
        String current = (String) command.getParameter(ParameterType.CURRENT);
        String path = getPathToCurrent(current);
        File dir = new File(path + File.separator + command.getParameter(ParameterType.DIR_NAME));
        if (Files.exists(dir.toPath()) && Files.isDirectory(dir.toPath())) {
            sendErrorMessage(ctx, dir.getName() + " already exists");
            return;
        }
        Files.createDirectory(dir.toPath());
        ctx.writeAndFlush(new Command(CommandType.CONTENT_RESPONSE).setAll(getUserFiles(current)));
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
    }

    private void moveDirectory(File src, File dst) throws IOException {
        Files.createDirectory(dst.toPath());
        for (File f : src.listFiles()) {
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

    private void downloadFileProcess(ChannelHandlerContext ctx, String path) {
        hasError.set(false);
        File file = new File(path);
        if (Files.isDirectory(file.toPath())) {
            sendErrorMessage(ctx, "Not file");
            return;
        }
        downloadThreadStart(ctx, file);
    }

    private void downloadThreadStart(ChannelHandlerContext ctx, File file) {
        semaphore.release(semaphore.drainPermits());
        Thread th = new Thread(() -> {
            long size = file.length();
            FileDTO fileDTO = FileDTO.builder()
                    .name(file.getName())
                    .fullSize(size)
                    .build();
            long readBytes = 0;
            log.info("Download started file: {}, size {}", file.getAbsolutePath(), size);
            long startTime = System.currentTimeMillis();
            try (FileInputStream is = new FileInputStream(file)) {
                while (!fileDTO.isEnd()) {
                    semaphore.acquire();
                    if (hasError.get()) {
                        log.info("Download file thread was interrupted");
                        break;
                    }
                    fileDTO.setStart(readBytes == 0);
                    long l = (size - readBytes) > 30_000_000 ? 30_000_000 : size - readBytes;
                    byte[] buffer = new byte[(int) l];
                    readBytes += is.read(buffer, 0, (int) l);
                    String md5 = DigestUtils.md5Hex(buffer);
                    fileDTO.setMd5(md5);
                    fileDTO.setContent(buffer);
                    fileDTO.setPart(fileDTO.getPart() + 1);
                    fileDTO.setEnd(readBytes == size);
                    ctx.writeAndFlush(new Command(CommandType.FILE_DOWNLOAD).setParameter(ParameterType.FILE_DTO, fileDTO));
                }
                log.info("Download finished at {} ms", System.currentTimeMillis() - startTime);
            } catch (Exception e) {
                log.error("Download file exception: {}", e.getMessage(), e);
                ctx.writeAndFlush(new Command(CommandType.DOWNLOAD_ERROR));
            }
        });
        th.setDaemon(true);
        th.start();
    }

    private void userAuthProcess(ChannelHandlerContext ctx, Command command) throws Exception {
        User user = (User) command.getParameter(ParameterType.USER);
        if (userService.isAuthorized(user)) {
            this.user = user;
            ctx.writeAndFlush(new Command(CommandType.AUTH_OK));
            ctx.writeAndFlush(new Command(CommandType.CONTENT_RESPONSE).setAll(getUserFiles("root")));
        } else {
            ctx.writeAndFlush(new Command(CommandType.AUTH_NO));
        }
    }

    private void uploadFileProcess(ChannelHandlerContext ctx, Command command) {
        try {
            FileDTO dto = (FileDTO) command.getParameter(ParameterType.FILE_DTO);
            String fullFilePath = getPathToCurrent(dto.getPath()) + dto.getName();
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
            try (FileOutputStream os = new FileOutputStream(file, true)) {
                if (!dto.isEnd()) {
                    os.write(dto.getContent());
                    ctx.writeAndFlush(new Command(CommandType.NEXT_PART));
                } else {
                    os.write(dto.getContent());
                    if (file.length() != dto.getFullSize()) {
                        ctx.writeAndFlush(new Command(CommandType.UPLOAD_ERROR).setParameter(ParameterType.MESSAGE, "File is corrupted"));
                        Files.deleteIfExists(Paths.get(fullFilePath));
                    } else {
                        ctx.writeAndFlush(new Command(CommandType.CONTENT_RESPONSE).setAll(getUserFiles(dto.getPath())));
                    }
                }
            }
        } catch (Exception e) {
            log.error("Upload file error: {}", e.getMessage(), e);
            ctx.writeAndFlush(new Command(CommandType.UPLOAD_ERROR).setParameter(ParameterType.MESSAGE, "Unknown error"));
        }
    }

    private Map<ParameterType, Object> getUserFiles(String current) throws Exception {
        Map<ParameterType, Object> parameters = new HashMap<>();
        List<String> directories = new ArrayList<>();
        List<String> files = new ArrayList<>();
        Files.walkFileTree(Paths.get(getPathToCurrent(current)), Collections.singleton(FileVisitOption.FOLLOW_LINKS), 1, new SimpleFileVisitor<Path>() {
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

    private String getPathToCurrent(String current) throws Exception {
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

    private void sendErrorMessage(ChannelHandlerContext ctx, String message) {
        ctx.writeAndFlush(new Command(CommandType.ERROR).setParameter(ParameterType.MESSAGE, message));
    }
}
