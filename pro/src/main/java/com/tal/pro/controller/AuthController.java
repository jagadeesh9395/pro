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
import com.tal.pro.repository.CandidateRepository;
import com.tal.pro.repository.RecruiterRepository;
import com.tal.pro.repository.RoleRepository;
import com.tal.pro.repository.UserRepository;
import com.tal.pro.security.services.UserDetailsImpl;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    @Autowired
    AuthenticationManager authenticationManager;

    @Autowired
    UserRepository userRepository;

    @Autowired
    RoleRepository roleRepository;

    @Autowired
    CandidateRepository candidateRepository;

    @Autowired
    RecruiterRepository recruiterRepository;

    @Autowired
    PasswordEncoder encoder;

    

    @PostMapping("/signin")
    public ResponseEntity<?> authenticateUser(@Valid @RequestBody LoginRequest loginRequest) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.getUsername(), loginRequest.getPassword()));

            SecurityContextHolder.getContext().setAuthentication(authentication);
            
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
        } catch (Exception e) {
            return ResponseEntity.status(401).body(new MessageResponse("Error: Invalid username or password"));
        }
    }

    @PostMapping("/signup")
    public ResponseEntity<?> registerUser(@Valid @RequestBody SignupRequest signUpRequest) {
        if (userRepository.existsByUsername(signUpRequest.getUsername())) {
            return ResponseEntity
                .badRequest()
                .body(new MessageResponse("Error: Username is already taken!"));
        }

        if (userRepository.existsByEmail(signUpRequest.getEmail())) {
            return ResponseEntity
                .badRequest()
                .body(new MessageResponse("Error: Email is already in use!"));
        }

        // Create new user based on role
        User user;
        String role = signUpRequest.getRole().toLowerCase();
        
        // Common user fields
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
        
        // Save the user first to get the generated ID
        User savedUser = userRepository.save(user);
        
        // If this is a candidate or recruiter, save to the appropriate collection
        if (user instanceof Candidate) {
            Candidate candidate = (Candidate) user;
            candidate.setId(savedUser.getId()); // Ensure the ID is set
            candidateRepository.save(candidate);
        } else if (user instanceof Recruiter) {
            Recruiter recruiter = (Recruiter) user;
            recruiter.setId(savedUser.getId()); // Ensure the ID is set
            recruiterRepository.save(recruiter);
        }

        return ResponseEntity.ok(new MessageResponse("User registered successfully!"));
    }

    @GetMapping("/user")
    public ResponseEntity<?> getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || 
            authentication.getPrincipal().equals("anonymousUser")) {
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
}
