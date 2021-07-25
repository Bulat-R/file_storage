package org.example.controller;

import org.example.model.user.User;

public class Config {
    private static User user;
    private static String host;
    private static int port;
    private static final char[] forbidden = new char[]{'/', '\\', '*', '?', ':', '|', '>', '<', '\"', '+', '%', '!', '\'', '@', '~'};

    private Config() {
    }

    public static User getUser() {
        return user;
    }

    public static void setUser(User user) {
        if (user.getEmail() == null || user.getEmail().isEmpty()) {
            throw new IllegalArgumentException("email can't be empty");
        }
        if (user.getPassword() == null || user.getPassword().isEmpty()) {
            throw new IllegalArgumentException("password can't be empty");
        }
        Config.user = user;
    }

    public static String getHost() {
        return host;
    }

    public static void setHost(String host) {
        if (host == null || host.isEmpty()) {
            throw new IllegalArgumentException("host can't be empty");
        }
        Config.host = host;
    }

    public static int getPort() {
        return port;
    }

    public static void setPort(String port) {
        if (port == null || port.isEmpty()) {
            throw new IllegalArgumentException("port can't be empty");
        }
        try {
            Config.port = Integer.parseInt(port);
            if (Config.port < 0 || Config.port > 65535) {
                throw new IllegalArgumentException("port is not valid");
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("port is not valid");
        }
    }

    public static char[] getForbidden() {
        return forbidden;
    }
}
