package org.example.nio;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.Iterator;
import java.util.Set;

public class Server {

    private static int cnt = 1;
    private ServerSocketChannel sc;
    private Selector selector;
    private String name = "user";

    public Server() throws IOException {
        sc = ServerSocketChannel.open();
        selector = Selector.open();
        sc.bind(new InetSocketAddress(8189));
        sc.configureBlocking(false);
        sc.register(selector, SelectionKey.OP_ACCEPT);

        while (sc.isOpen()) {
            selector.select();
            Set<SelectionKey> selectedKeys = selector.selectedKeys();
            Iterator<SelectionKey> iterator = selectedKeys.iterator();
            while (iterator.hasNext()) {
                SelectionKey key = iterator.next();
                if (key.isAcceptable()) {
                    handleAccept(key);
                }
                if (key.isReadable()) {
                    handleRead(key);
                }
                iterator.remove();
            }
        }
    }

    private void handleRead(SelectionKey key) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(256);
        SocketChannel channel = (SocketChannel) key.channel();
        String name = (String) key.attachment();
        int read;
        StringBuilder sb = new StringBuilder();
        while (true) {
            read = channel.read(buffer);
            buffer.flip();
            if (read == -1) {
                channel.close();
                break;
            }
            if (read > 0) {
                while (buffer.hasRemaining()) {
                    sb.append((char) buffer.get());
                }
                buffer.clear();
            } else {
                break;
            }
        }
        System.out.println("received: " + sb);
        String response = MessageParser(sb.toString());
        for (SelectionKey selectionKey : selector.keys()) {
            if (selectionKey.isValid() && selectionKey.channel() instanceof SocketChannel) {
                SocketChannel ch = (SocketChannel) selectionKey.channel();
                ch.write(ByteBuffer.wrap(response.getBytes(StandardCharsets.UTF_8)));
            }
        }
    }

    private void handleAccept(SelectionKey key) throws IOException {
        SocketChannel channel = sc.accept();
        channel.configureBlocking(false);
        channel.register(selector, SelectionKey.OP_READ, name + cnt);
        cnt++;
    }

    private String MessageParser(String msg) {
        String[] str = msg.split(" ");
        switch (str[0].trim()) {
            case "ls":
                return getFilesList();
            case "cat":
                if (str.length > 1) {
                    return readFile(Paths.get(str[1].trim()));
                }
                return "filename is empty\n";
            default:
                return msg;
        }
    }

    private String getFilesList() {
        StringBuilder builder = new StringBuilder();
        try {
            Files.walkFileTree(Paths.get("").toAbsolutePath(), Collections.singleton(FileVisitOption.FOLLOW_LINKS), 1, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    builder.append(file.getFileName().toString()).append("\n");
                    return super.visitFile(file, attrs);
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
        return builder.toString();
    }

    private String readFile(Path file) {
        StringBuilder builder = new StringBuilder();
        if (Files.notExists(file) || Files.isDirectory(file)) {
            return String.format("file %s not found\n", file);
        }
        try {
            Files.lines(file).forEach(s -> builder.append(s).append("\n"));
            return builder.toString();
        } catch (IOException e) {
            e.printStackTrace();
            return e.getMessage();
        }
    }

}