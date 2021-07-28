package org.example.service;

import lombok.extern.slf4j.Slf4j;
import org.example.model.user.User;
import org.example.netty.Config;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class InMemoryUserService implements UserService {

    private final Map<String, User> users = new HashMap<>();

    {
        users.put("12345@email.com", User.builder().email("12345@email.com").password("12345").build());
        users.put("67890@email.com", User.builder().email("64890@email.com").password("67890").build());
        users.put("xxxxx@yandex.ru", User.builder().email("xxxxx@yandex.ru").password("xxxxx").build());

    }

    @Override
    public boolean isAuthorized(User user) {
        if (!users.containsKey(user.getEmail())) {
            return false;
        }
        User userFromMap = users.get(user.getEmail());
        return userFromMap.getPassword().equals(user.getPassword());
    }

    @Override
    public String getRootPath(User user) throws Exception {
        File file = new File(Config.storagePath + File.separator + user.getEmail());
        try {
            if (Files.notExists(file.toPath())) {
                Files.createDirectory(file.toPath());
                log.info("Folder for user {} created at {}", user.getEmail(), file.getAbsolutePath());
            }
        } catch (IOException e) {
            log.error("User folder create exception: {}", e.getMessage());
            throw e;
        }
        return file.getAbsolutePath();
    }
}
