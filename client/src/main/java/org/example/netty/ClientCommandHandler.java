package org.example.netty;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;
import org.example.model.command.Command;

@Slf4j
public class ClientCommandHandler extends SimpleChannelInboundHandler<Command> {

    private final CallBack callBack;

    public ClientCommandHandler(CallBack callBack) {
        this.callBack = callBack;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Command command) throws Exception {
        callBack.call(command);
    }
}