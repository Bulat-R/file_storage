package org.example.service;

import org.example.model.user.User;

public interface UserService {

    boolean isAuthorized(User user);

    String getRootPath(User user);
}
