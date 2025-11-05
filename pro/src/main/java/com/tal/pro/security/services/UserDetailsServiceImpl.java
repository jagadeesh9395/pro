package com.tal.pro.security.services;

import com.tal.pro.model.User;
import com.tal.pro.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {
    @Autowired
    private UserRepository userRepository;

    @Override
    @Transactional
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        System.out.println("Attempting to load user by username: " + username);
        try {
            User user = userRepository.findByUsername(username)
                    .orElseThrow(() -> {
                        System.out.println("User not found with username: " + username);
                        return new UsernameNotFoundException("User Not Found with username: " + username);
                    });

            if (user == null) {
                System.out.println("User object is null for username: " + username);
                throw new UsernameNotFoundException("User not found with username: " + username);
            }

            System.out.println("User found: " + user.getUsername() + " with roles: " + 
                user.getRoles().stream()
                    .map(role -> role.getName().name())
                    .collect(java.util.stream.Collectors.joining(", ")));

            return UserDetailsImpl.build(user);
        } catch (Exception e) {
            System.err.println("Error loading user " + username + ": " + e.getMessage());
            e.printStackTrace();
            throw new UsernameNotFoundException("User not found with username: " + username, e);
        }
    }
}
