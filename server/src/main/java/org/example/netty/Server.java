package org.example.netty;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.serialization.ClassResolvers;
import io.netty.handler.codec.serialization.ObjectDecoder;
import io.netty.handler.codec.serialization.ObjectEncoder;
import lombok.extern.slf4j.Slf4j;
import org.example.service.InMemoryUserService;

@Slf4j
public class Server {

    public Server(int port) {
        EventLoopGroup auth = new NioEventLoopGroup(1);
        EventLoopGroup worker = new NioEventLoopGroup();
        try {
            ServerBootstrap serverBootstrap = new ServerBootstrap();
            serverBootstrap.group(auth, worker)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel channel) throws Exception {
                            channel.pipeline().addLast(
                                    new ObjectEncoder(),
                                    new ObjectDecoder(Config.maxObjectSize, ClassResolvers.weakCachingConcurrentResolver(null)),
                                    new ServerCommandHandler(new InMemoryUserService())
                            );
                        }
                    });
            ChannelFuture channelFuture = serverBootstrap.bind(port).sync();
            log.info("=================== Server started (port {}) ===================", port);
            channelFuture.channel().closeFuture().sync();
        } catch (Exception e) {
            log.error("Server start exception: {}", e.getMessage(), e);
        } finally {
            auth.shutdownGracefully();
            worker.shutdownGracefully();
        }
    }
}