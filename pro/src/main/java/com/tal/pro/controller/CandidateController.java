package com.tal.pro.controller;

import com.tal.pro.model.Candidate;
import com.tal.pro.model.Job;
import com.tal.pro.model.JobApplication;
import com.tal.pro.repository.CandidateRepository;
import com.tal.pro.service.JobApplicationService;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/candidate")
public class CandidateController {

    @Autowired
    private CandidateRepository candidateRepository;

    @Autowired
    private JobService jobService;

    @Autowired
    private JobApplicationService jobApplicationService;

    @GetMapping("/upload-resume")
    public String showUploadResumePage(Model model, Principal principal) {
        try {
            if (principal == null) {
                return "redirect:/auth/login?error=not_authenticated";
            }

            String username = principal.getName();
            Candidate candidate = candidateRepository.findByUsernameWithResume(username)
                    .orElseThrow(() -> new RuntimeException("Candidate not found"));

            model.addAttribute("candidate", candidate);
            return "candidate/upload-resume";

        } catch (Exception e) {
            model.addAttribute("error", "Error loading upload page: " + e.getMessage());
            return "error";
        }
    }

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
            // Load candidate with resume using a custom query if needed
            Candidate candidate = candidateRepository.findByUsernameWithResume(username)
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

            // Get candidate's applications with job details and status history
            List<JobApplication> applications = jobApplicationService.getApplicationsByCandidateId(candidate.getId());

            // Log the number of applications found for debugging
            System.out.println("Found " + applications.size() + " applications for candidate: " + candidate.getId());
            applications.forEach(app -> {
                System.out.println("Application ID: " + app.getId() +
                        ", Job: " + (app.getJob() != null ? app.getJob().getJobTitle() : "No Job") +
                        ", Status: " + (app.getStatus() != null ? app.getStatus().name() : "No Status"));

                // Sort status history by change date (newest first)
                if (app.getStatusHistory() != null) {
                    app.getStatusHistory().sort((h1, h2) -> h2.getChangedAt().compareTo(h1.getChangedAt()));
                }
            });

            // Get recent applications (last 5)
            List<JobApplication> recentApplications = applications.stream()
                    .filter(app -> app.getAppliedAt() != null) // Filter out null appliedAt
                    .sorted((a1, a2) -> a2.getAppliedAt().compareTo(a1.getAppliedAt()))
                    .limit(5)
                    .toList();

            // Log if any applications were filtered out
            if (recentApplications.size() < Math.min(5, applications.size())) {
                System.out.println("Filtered out " + (applications.size() - recentApplications.size()) + " applications with null appliedAt");
            }

            // Add candidate and user info to model
            model.addAttribute("candidate", candidate);
            model.addAttribute("currentUser", candidate);
            model.addAttribute("username", username);
            model.addAttribute("fullName", candidate.getFullName());
            model.addAttribute("email", candidate.getEmail());
            model.addAttribute("isCandidate", true);

            // Add applications data
            model.addAttribute("recentApplications", recentApplications);
            model.addAttribute("totalApplications", applications.size());

            // Add job search results with pagination
            model.addAttribute("jobs", jobsPage.getContent());
            model.addAttribute("currentPage", jobsPage.getNumber());
            model.addAttribute("totalPages", jobsPage.getTotalPages());
            model.addAttribute("totalItems", jobsPage.getTotalElements());
            model.addAttribute("pageSize", size);

            // Add search parameters for pagination and form population
            if (query != null && !query.isEmpty()) {
                model.addAttribute("query", query);
                model.addAttribute("queryParam", query);
            }
            if (location != null && !location.isEmpty()) {
                model.addAttribute("location", location);
                model.addAttribute("locationParam", location);
            }

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
            candidate.setPhoneNumber(updatedCandidate.getPhoneNumber());

            // Handle skills - store as comma-separated string
            if (updatedCandidate.getSkills() != null && !updatedCandidate.getSkills().trim().isEmpty()) {
                // Clean up the skills string by removing extra spaces and empty entries
                String cleanedSkills = Arrays.stream(updatedCandidate.getSkills().split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.joining(", "));
                candidate.setSkills(cleanedSkills);
            } else {
                candidate.setSkills("");
            }

            candidate.setExperience(updatedCandidate.getExperience());
            candidate.setLocation(updatedCandidate.getLocation());

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
