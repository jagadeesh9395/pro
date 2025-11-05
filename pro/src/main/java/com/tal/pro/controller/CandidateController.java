package com.tal.pro.controller;

import com.tal.pro.model.Candidate;
import com.tal.pro.repository.CandidateRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;

@Controller
@RequestMapping("/candidate")
public class CandidateController {

    @Autowired
    private CandidateRepository candidateRepository;

    @GetMapping("/dashboard")
    public String dashboard(Model model, Principal principal) {
        try {
            if (principal == null) {
                return "redirect:/auth/login?error=not_authenticated";
            }
            
            String username = principal.getName();
            Candidate candidate = candidateRepository.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("Candidate not found"));
            
            model.addAttribute("candidate", candidate);
            model.addAttribute("currentUser", candidate);
            model.addAttribute("username", username);
            model.addAttribute("fullName", candidate.getFullName());
            model.addAttribute("email", candidate.getEmail());
            model.addAttribute("isCandidate", true);
            
            return "candidate/dashboard";
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
            Candidate candidate = candidateRepository.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("Candidate not found"));
            
            model.addAttribute("candidate", candidate);
            model.addAttribute("currentUser", candidate);
            model.addAttribute("username", username);
            model.addAttribute("fullName", candidate.getFullName());
            model.addAttribute("email", candidate.getEmail());
            model.addAttribute("skills", candidate.getSkills());
            model.addAttribute("experience", candidate.getExperience());
            model.addAttribute("isCandidate", true);
            
            return "candidate/profile";
        } catch (Exception e) {
            e.printStackTrace();
            return "redirect:/auth/login?error=access_denied";
        }
    }
    
    @PostMapping("/profile/update")
    public String updateProfile(@ModelAttribute("candidate") Candidate updatedCandidate, 
                              Principal principal,
                              RedirectAttributes redirectAttributes) {
        try {
            String username = principal.getName();
            Candidate candidate = candidateRepository.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("Candidate not found"));
            
            // Update candidate details
            candidate.setFullName(updatedCandidate.getFullName());
            candidate.setEmail(updatedCandidate.getEmail());
            candidate.setSkills(updatedCandidate.getSkills());
            candidate.setExperience(updatedCandidate.getExperience());
            
            candidateRepository.save(candidate);
            
            redirectAttributes.addFlashAttribute("success", "Profile updated successfully!");
            return "redirect:/candidate/profile";
        } catch (Exception e) {
            e.printStackTrace();
            redirectAttributes.addAttribute("error", "Failed to update profile");
            return "redirect:/candidate/profile";
        }
    }
    
    @PostMapping("/profile/change-password")
    public String changePassword(@RequestParam("currentPassword") String currentPassword,
                               @RequestParam("newPassword") String newPassword,
                               @RequestParam("confirmPassword") String confirmPassword,
                               Principal principal,
                               RedirectAttributes redirectAttributes) {
        try {
            // TODO: Implement password change logic
            // 1. Verify current password
            // 2. Check if new passwords match
            // 3. Update password
            
            redirectAttributes.addFlashAttribute("success", "Password updated successfully!");
            return "redirect:/candidate/profile";
        } catch (Exception e) {
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("error", "Failed to update password: " + e.getMessage());
            return "redirect:/candidate/profile#change-password";
        }
    }
}
