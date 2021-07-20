package org.example.netty;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.example.model.command.Command;
import org.example.model.command.CommandType;
import org.example.model.parameter.ParameterType;
import org.example.model.user.User;
import org.example.service.UserService;

import java.io.File;
import java.io.IOException;
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
                if (userService.isAuthorized((User) command.getParameters().get(ParameterType.USER))) {
                    ctx.writeAndFlush(new Command(CommandType.AUTH_OK, null));
                    ctx.writeAndFlush(new Command(CommandType.CONTENT_RESPONSE, getUserFiles("root")));
                } else {
                    ctx.writeAndFlush(new Command(CommandType.AUTH_NO, null));
                }
                break;
            case CONTENT_REQUEST:
                ctx.writeAndFlush(new Command(CommandType.CONTENT_RESPONSE,
                        getUserFiles((String) command.getParameters().get(ParameterType.CURRENT_DIR))));
                break;
        }
    }

    @SneakyThrows
    private Map<ParameterType, List<String>> getUserFiles(String currentDir) {

        Map<ParameterType, List<String>> map = new HashMap<>();

        List<String> directories = new ArrayList<>();
        List<String> files = new ArrayList<>();

        Files.walkFileTree(Paths.get(getFullPathToDir(currentDir)), Collections.singleton(FileVisitOption.FOLLOW_LINKS), 1, new SimpleFileVisitor<Path>() {
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

        List<String> current = new ArrayList<>();
        current.add("root");
        if (!currentDir.equals("root")) {
            Arrays.stream(currentDir.split("/")).filter(str -> !str.isEmpty()).forEach(current::add);
        }

        map.put(ParameterType.DIRECTORIES, directories);
        map.put(ParameterType.FILES, files);
        map.put(ParameterType.CURRENT_DIR, current);

        return map;
    }

    private String getFullPathToDir(String currentDir) {
        String separator = File.separator;
        StringBuilder sb = new StringBuilder(userService.getRootPath());
        if (!currentDir.equals("root")) {
            String[] dirs = currentDir.split("/");
            for (int i = 0; i < dirs.length; i++) {
                sb.append(dirs[i]).append(separator);
            }
        }
        return sb.toString();
    }
}
