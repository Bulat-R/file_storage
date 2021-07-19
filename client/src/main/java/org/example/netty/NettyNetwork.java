package org.example.netty;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.serialization.ClassResolvers;
import io.netty.handler.codec.serialization.ObjectDecoder;
import io.netty.handler.codec.serialization.ObjectEncoder;
import lombok.extern.slf4j.Slf4j;
import org.example.model.command.Command;

import java.net.ConnectException;

@Slf4j
public class NettyNetwork {

    private final CallBack callBack;
    private SocketChannel channel;
    EventLoopGroup worker;

    public NettyNetwork(CallBack callBack, String host, int port) throws Exception {
        log.info("NettyNetwork constructor: host {}, port {}", host, port);
        this.callBack = callBack;
        Thread thread = new Thread(() -> {
            worker = new NioEventLoopGroup();
            try {
                Bootstrap bootstrap = new Bootstrap();
                bootstrap.group(worker)
                        .channel(NioSocketChannel.class)
                        .handler(new ChannelInitializer<SocketChannel>() {
                            @Override
                            protected void initChannel(SocketChannel c) throws Exception {
                                channel = c;
                                c.pipeline().addLast(
                                        new ObjectEncoder(),
                                        new ObjectDecoder(ClassResolvers.cacheDisabled(null)),
                                        new ClientCommandHandler(callBack)
                                );
                            }
                        });
                ChannelFuture future = bootstrap.connect(host, port).sync();
                future.channel().closeFuture().sync();
            } catch (Exception e) {
                log.error(e.getMessage());
            } finally {
                worker.shutdownGracefully();
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    public void writeMessage(Command command) throws ConnectException {
        log.info("Try to send command: {}", command);
        if (isConnected()) {
            channel.writeAndFlush(command);
        } else {
            throw new ConnectException();
        }
    }

    public boolean isConnected() {
        return channel != null && channel.isActive();
    }

    public void close() {
        worker.shutdownGracefully();
    }
}