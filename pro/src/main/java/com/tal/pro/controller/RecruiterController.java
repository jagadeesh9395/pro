package com.tal.pro.controller;

import com.tal.pro.model.Recruiter;
import com.tal.pro.repository.RecruiterRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.security.Principal;

@Controller
@RequestMapping("/recruiter")
public class RecruiterController {

    @Autowired
    private RecruiterRepository recruiterRepository;

    @GetMapping("/dashboard")
    public String dashboard(Model model, Principal principal) {
        try {
            if (principal == null) {
                return "redirect:/auth/login?error=not_authenticated";
            }
            
            String username = principal.getName();
            Recruiter recruiter = recruiterRepository.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("Recruiter not found"));
            
            // Add all necessary attributes to the model
            model.addAttribute("recruiter", recruiter);
            model.addAttribute("currentUser", recruiter);
            model.addAttribute("username", username);
            model.addAttribute("fullName", recruiter.getFullName());
            model.addAttribute("email", recruiter.getEmail());
            model.addAttribute("companyName", recruiter.getCompany());
            
            // Add roles to the model
            model.addAttribute("isRecruiter", true);
            
            return "recruiter/dashboard";
        } catch (Exception e) {
            e.printStackTrace();
            return "redirect:/auth/login?error=access_denied";
        }
    }

    @GetMapping("/profile")
    public String profile(Model model, Principal principal) {
        try {
            if (principal == null) {
                return "redirect:/auth/login?error=not_authenticated";
            }
            
            String username = principal.getName();
            Recruiter recruiter = recruiterRepository.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("Recruiter not found"));
            
            // Add all necessary attributes to the model
            model.addAttribute("recruiter", recruiter);
            model.addAttribute("currentUser", recruiter);
            model.addAttribute("username", username);
            model.addAttribute("fullName", recruiter.getFullName());
            model.addAttribute("email", recruiter.getEmail());
            model.addAttribute("companyName", recruiter.getCompany());
            model.addAttribute("companyDescription", recruiter.getCompanyDescription());
            model.addAttribute("website", recruiter.getWebsite());
            model.addAttribute("isRecruiter", true);
            
            return "recruiter/profile";
        } catch (Exception e) {
            e.printStackTrace();
            return "redirect:/auth/login?error=access_denied";
        }
    }

    @GetMapping("/jobs")
    public String jobs() {
        return "recruiter/jobs";
    }

    @GetMapping("/candidates")
    public String candidates() {
        return "recruiter/candidates";
    }
}
