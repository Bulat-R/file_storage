package org.example.service;

import org.example.model.user.User;

import java.io.IOException;

public interface UserService {

    boolean isAuthorized(User user);

    String getRootPath(User user) throws Exception;
}
