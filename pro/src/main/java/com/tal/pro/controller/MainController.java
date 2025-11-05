package com.tal.pro.controller;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class MainController {

    @GetMapping("/")
    public String home() {
        return "home";
    }

    @GetMapping({"/login", "/auth/login"})
    public String login() {
        return "auth/login";
    }

    @GetMapping({"/register", "/auth/register"})
    public String register() {
        return "auth/register";
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isCandidate = auth.getAuthorities().stream()
                .anyMatch(r -> r.getAuthority().equals("ROLE_CANDIDATE"));
        boolean isRecruiter = auth.getAuthorities().stream()
                .anyMatch(r -> r.getAuthority().equals("ROLE_RECRUITER"));

        if (isCandidate) {
            return "redirect:/candidate/dashboard";
        } else if (isRecruiter) {
            return "redirect:/recruiter/dashboard";
        }
        
        return "redirect:/login";
    }
}
