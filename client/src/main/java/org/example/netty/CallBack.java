package org.example.netty;

import org.example.model.command.Command;

public interface CallBack {

    void call(Command command) throws Exception;
}
