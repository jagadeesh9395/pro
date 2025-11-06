package com.tal.pro.controller;

import com.tal.pro.model.Candidate;
import com.tal.pro.model.Job;
import com.tal.pro.repository.CandidateRepository;
import com.tal.pro.service.JobService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.util.List;

@Controller
@RequestMapping("/candidate")
public class CandidateController {

    @Autowired
    private CandidateRepository candidateRepository;
    
    @Autowired
    private JobService jobService;

    @GetMapping("/dashboard")
    public String dashboard(Model model, Principal principal,
                          @RequestParam(defaultValue = "0") int page,
                          @RequestParam(defaultValue = "10") int size,
                          @RequestParam(required = false) String query,
                          @RequestParam(required = false) String location) {
        try {
            if (principal == null) {
                return "redirect:/auth/login?error=not_authenticated";
            }
            
            String username = principal.getName();
            Candidate candidate = candidateRepository.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("Candidate not found"));
            
            // Get paginated and filtered jobs
            Pageable pageable = PageRequest.of(page, size);
            Page<Job> jobsPage;
            
            if ((query != null && !query.isEmpty()) || (location != null && !location.isEmpty())) {
                // Use search with filters
                jobsPage = jobService.searchJobsWithFilters(
                    query != null ? query : "", 
                    location != null ? location : "", 
                    null, // jobType is null for now, can be added later
                    pageable
                );
            } else {
                // Get all active jobs if no search criteria
                jobsPage = jobService.getAllActiveJobs(pageable);
            }
            
            model.addAttribute("candidate", candidate);
            model.addAttribute("currentUser", candidate);
            model.addAttribute("username", username);
            model.addAttribute("fullName", candidate.getFullName());
            model.addAttribute("email", candidate.getEmail());
            model.addAttribute("isCandidate", true);
            
            // Add jobs to the model
            model.addAttribute("jobs", jobsPage.getContent());
            model.addAttribute("currentPage", jobsPage.getNumber());
            model.addAttribute("totalItems", jobsPage.getTotalElements());
            model.addAttribute("totalPages", jobsPage.getTotalPages());
            model.addAttribute("pageSize", size);
            
            // Add search parameters to model for form population and pagination
            if (query != null) model.addAttribute("queryParam", query);
            if (location != null) model.addAttribute("locationParam", location);
            
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
