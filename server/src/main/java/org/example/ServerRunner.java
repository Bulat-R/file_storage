package org.example;

import org.example.netty.Config;
import org.example.netty.Server;

import java.io.IOException;

public class ServerRunner {

    public static void main(String[] args) throws IOException {
        new Server(Config.port);
    }
}
