package org.example.netty;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;
import org.example.model.Message;

@Slf4j
public class MessageHandler extends SimpleChannelInboundHandler<Message> {

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Message message) throws Exception {
        log.debug("received: {}", message);
        ctx.writeAndFlush(message);
    }
}