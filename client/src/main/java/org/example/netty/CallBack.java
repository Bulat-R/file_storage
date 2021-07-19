package org.example.netty;

import org.example.model.command.Command;

import java.net.ConnectException;

public interface CallBack {

    void call(Command command) throws Exception;
}
