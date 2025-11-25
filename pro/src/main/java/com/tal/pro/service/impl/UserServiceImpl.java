package com.tal.pro.service.impl;

import com.tal.pro.model.User;
import com.tal.pro.repository.UserRepository;
import com.tal.pro.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    @Autowired
    public UserServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public String getUsernameById(String userId) {
        if (userId == null) {
            return null;
        }
        Optional<User> user = userRepository.findById(userId);
        return user.map(User::getUsername).orElse(null);
    }
}
