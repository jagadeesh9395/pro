package com.tal.pro.controller;

import com.tal.pro.model.ERole;
import com.tal.pro.model.Role;
import com.tal.pro.model.User;
import com.tal.pro.model.Candidate;
import com.tal.pro.model.Recruiter;
import com.tal.pro.payload.request.LoginRequest;
import com.tal.pro.payload.request.SignupRequest;
import com.tal.pro.payload.response.MessageResponse;
import com.tal.pro.payload.response.UserInfoResponse;
import com.tal.pro.repository.RoleRepository;
import com.tal.pro.repository.UserRepository;
import com.tal.pro.security.services.UserDetailsImpl;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    
    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder encoder;

    @GetMapping("/user")
    public ResponseEntity<?> getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || authentication.getPrincipal().equals("anonymousUser")) {
            return ResponseEntity.status(401).body(new MessageResponse("Not authenticated"));
        }
        
        if (authentication.getPrincipal() instanceof UserDetailsImpl) {
            UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
            List<String> roles = userDetails.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(new UserInfoResponse(
                    userDetails.getId(),
                    userDetails.getUsername(),
                    userDetails.getEmail(),
                    userDetails.getFullName(),
                    roles
            ));
        } else if (authentication.getPrincipal() instanceof String) {
            String username = (String) authentication.getPrincipal();
            User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
                
            List<String> roles = user.getRoles().stream()
                    .map(role -> role.getName().name())
                    .collect(Collectors.toList());
                    
            return ResponseEntity.ok(new UserInfoResponse(
                    user.getId(),
                    user.getUsername(),
                    user.getEmail(),
                    user.getFullName(),
                    roles
            ));
        }
        
        return ResponseEntity.status(401).body(new MessageResponse("Authentication failed"));
    }
    
    @GetMapping("/current-username")
    public ResponseEntity<?> getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || authentication.getPrincipal().equals("anonymousUser")) {
            return ResponseEntity.status(401).body(new MessageResponse("Not authenticated"));
        }
        
        String username = authentication.getName();
        return ResponseEntity.ok(Collections.singletonMap("username", username));
    }

    @PostMapping("/signup")
    public ResponseEntity<?> registerUser(@Valid @RequestBody SignupRequest signUpRequest) {
        // Check if username is already taken
        if (userRepository.existsByUsername(signUpRequest.getUsername())) {
            return ResponseEntity
                    .badRequest()
                    .body(new MessageResponse("Error: Username is already taken!"));
        }

        // Check if email is already in use
        if (userRepository.existsByEmail(signUpRequest.getEmail())) {
            return ResponseEntity
                    .badRequest()
                    .body(new MessageResponse("Error: Email is already in use!"));
        }

        // Validate role
        if (signUpRequest.getRole() == null) {
            return ResponseEntity
                    .badRequest()
                    .body(new MessageResponse("Error: Role is required!"));
        }

        // Create new user's account based on role
        User user;
        String role = signUpRequest.getRole().toLowerCase();
        
        // Set common user fields
        String encodedPassword = encoder.encode(signUpRequest.getPassword());
        
        // Create appropriate user type
        switch (role) {
            case "candidate" -> {
                Candidate candidate = new Candidate(
                    signUpRequest.getUsername(),
                    signUpRequest.getEmail(),
                    encodedPassword,
                    signUpRequest.getFirstName(),
                    signUpRequest.getLastName()
                );
                candidate.setPhoneNumber(signUpRequest.getPhoneNumber());
                candidate.setResumeUrl(signUpRequest.getResumeUrl());
                candidate.setSkills(signUpRequest.getSkills());
                candidate.setExperience(signUpRequest.getExperience());
                user = candidate;
            }
            case "recruiter" -> {
                Recruiter recruiter = new Recruiter(
                    signUpRequest.getUsername(),
                    signUpRequest.getEmail(),
                    encodedPassword,
                    signUpRequest.getFirstName(),
                    signUpRequest.getLastName()
                );
                recruiter.setPhoneNumber(signUpRequest.getPhoneNumber());
                recruiter.setCompany(signUpRequest.getCompany());
                recruiter.setPosition(signUpRequest.getPosition());
                user = recruiter;
            }
            default -> {
                return ResponseEntity
                        .badRequest()
                        .body(new MessageResponse("Error: Invalid role specified!"));
            }
        }

        // Set user roles
        Set<Role> roles = new HashSet<>();
        ERole roleName = role.equalsIgnoreCase("candidate") ? ERole.ROLE_CANDIDATE : 
                          role.equalsIgnoreCase("recruiter") ? ERole.ROLE_RECRUITER : 
                          null;
                          
        if (roleName == null) {
            return ResponseEntity
                .badRequest()
                .body(new MessageResponse("Error: Invalid role specified!"));
        }
        
        // Find or create the role
        Role userRole = roleRepository.findByName(roleName)
            .orElseGet(() -> {
                Role newRole = new Role(roleName);
                return roleRepository.save(newRole);
            });
            
        roles.add(userRole);
        user.setRoles(roles);

        // Save the user to database
        userRepository.save(user);

        return ResponseEntity.ok(new MessageResponse("User registered successfully!"));
    }
}
