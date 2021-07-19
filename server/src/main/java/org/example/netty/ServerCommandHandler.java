package org.example.netty;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.example.model.command.Command;
import org.example.model.command.CommandType;
import org.example.model.parameter.ParameterType;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

@Slf4j
public class ServerCommandHandler extends SimpleChannelInboundHandler<Command> {

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Command command) throws Exception {
        log.info("Incoming command: {}", command);
        switch (command.getCommandType()) {
            case AUTH_REQUEST:
                ctx.writeAndFlush(new Command(CommandType.AUTH_OK, null));
                break;
            case CONTENT_REQUEST:
                ctx.writeAndFlush(new Command(CommandType.CONTENT_RESPONSE,
                        getUserPaths((String) command.getParameters().get(ParameterType.CURRENT_DIR))));
                break;
        }
    }

    @SneakyThrows
    private Map<ParameterType, List<String>> getUserPaths(String currentDir) {
        Map<ParameterType, List<String>> map = new HashMap<>();

        List<String> directories = new ArrayList<>();
        List<String> files = new ArrayList<>();

        Files.walkFileTree(Paths.get(""), Collections.singleton(FileVisitOption.FOLLOW_LINKS), 1, new SimpleFileVisitor<Path>(){
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

        map.put(ParameterType.DIRECTORIES, directories);
        map.put(ParameterType.FILES, files);
        map.put(ParameterType.CURRENT_DIR, Collections.singletonList(currentDir));

        return map;
    }
}
