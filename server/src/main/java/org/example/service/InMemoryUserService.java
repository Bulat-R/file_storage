package org.example.service;

import org.example.model.user.User;

import java.util.HashMap;
import java.util.Map;

public class InMemoryUserService implements UserService {

    private Map<String, User> users = new HashMap<>();

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
    public String getRootPath(User user) {
        return System.getProperty("user.home");
    }
}
