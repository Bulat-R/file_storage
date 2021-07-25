package org.example.netty;

import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Properties;

@Slf4j
public class Config {
    public static final String storagePath;
    public static final int port;

    static {
        Properties properties = new Properties();
        try (InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream("config.properties")) {
            properties.load(is);
        } catch (Exception e) {
            e.printStackTrace();
        }
        storagePath = properties.getProperty("storagePath");
        port = Integer.parseInt(properties.getProperty("port"));

        try {
            File file = new File(storagePath);
            if (Files.notExists(file.toPath())) {
                Files.createDirectory(file.toPath());
                log.info("Main storage was created at {}", file.getAbsolutePath());
            }
            log.info("Main storage is located at {}", file.getAbsolutePath());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
