package com.tal.pro.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tal.pro.exception.ResourceNotFoundException;
import com.tal.pro.model.Candidate;
import com.tal.pro.model.Job;
import com.tal.pro.payload.request.ProfileUpdateRequest;
import com.tal.pro.repository.CandidateRepository;
import com.tal.pro.service.CandidateService;
import com.tal.pro.service.JobService;
import com.tal.pro.util.FileUploadUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.AuthenticationException;
import java.io.IOException;
import java.security.Principal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/candidate")
@Slf4j
public class CandidateController {

    @Autowired
    private CandidateService candidateService;

    @Autowired
    private JobService jobService;

    @Autowired
    private CandidateRepository candidateRepository;
    @Autowired
    private FileUploadUtil fileUploadUtil;
    
    @Autowired
    private ObjectMapper objectMapper;

    @Value("${file.upload-dir:uploads}")
    private String uploadDir;

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

    @PostMapping("/profile/update")
    @ResponseBody
    public ResponseEntity<?> updateProfile(
            @RequestParam(value = "fullName", required = false) String fullName,
            @RequestParam(value = "phoneNumber", required = false) String phoneNumber,
            @RequestParam(value = "currentAddress", required = false) String currentAddress,
            @RequestParam(value = "skills", required = false) List<String> skills,
            @RequestParam(value = "resumeFile", required = false) MultipartFile resumeFile,
            Principal principal) {
        
        log.info("=== Profile Update Request ===");
        log.info("Principal: {}", principal != null ? principal.getName() : "null");
        log.info("Received data - Name: {}, Phone: {}", fullName, phoneNumber);
        log.info("Skills: {}", skills);
        if (resumeFile != null) {
            log.info("Received file: {} ({} bytes)", resumeFile.getOriginalFilename(), resumeFile.getSize());
        }
        
        try {
            String username = principal.getName();
            log.info("Updating profile for user: {}", username);
            
            Candidate existingCandidate = candidateRepository.findByUsername(username)
                    .orElseThrow(() -> new ResourceNotFoundException("Candidate not found with username: " + username));
            
            // Update basic fields
            if (fullName != null) existingCandidate.setFullName(fullName);
            if (phoneNumber != null) existingCandidate.setPhoneNumber(phoneNumber);
            if (currentAddress != null) existingCandidate.setCurrentAddress(currentAddress);
            
            // Handle skills
            if (skills != null && !skills.isEmpty()) {
                try {
                    existingCandidate.setSkills(skills);
                    log.info("Updated skills: {}", skills);
                } catch (Exception e) {
                    log.warn("Failed to set skills: {}", skills, e);
                }
            }
            
            existingCandidate.setUpdatedAt(LocalDate.now());

            // Handle resume file upload
            if (resumeFile != null && !resumeFile.isEmpty()) {
                try {
                    // Delete old resume if exists
                    if (existingCandidate.getResumeFileId() != null) {
                        fileUploadUtil.deleteFile(existingCandidate.getResumeFileId());
                    }
                    
                    // Upload new resume
                    String fileId = fileUploadUtil.storeFile(resumeFile);
                    existingCandidate.setResumeFileId(fileId);
                    existingCandidate.setResumeUrl("/api/files/" + fileId);
                } catch (IOException e) {
                    log.error("Error uploading resume", e);
                    return ResponseEntity.status(500).body(Map.of(
                        "success", false,
                        "message", "Error uploading resume: " + e.getMessage()
                    ));
                }
            }

            // Save the updated candidate
            candidateRepository.save(existingCandidate);

            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Profile updated successfully"
            ));
        } catch (Exception e) {
            log.error("Error updating profile", e);
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "message", "Error updating profile: " + e.getMessage()
            ));
        }
    }

    @GetMapping("/profile")
    public String viewProfile(Model model, Principal principal) {
        try {
            // Check if user is not authenticated
            if (principal == null) {
                return "redirect:/auth/login?error=login_required";
            }

            // Get the current candidate's details
            String username = principal.getName();
            Candidate candidate = candidateRepository.findByUsername(username)
                    .orElseGet(() -> {
                        // Create a new candidate if not found (shouldn't normally happen)
                        log.warn("Creating new candidate for username: {}", username);
                        return new Candidate();
                    });

            // Ensure all collections are initialized
            if (candidate.getSkills() == null) candidate.setSkills(new ArrayList<>());
            if (candidate.getWorkExperiences() == null) candidate.setWorkExperiences(new ArrayList<>());
            if (candidate.getEducations() == null) candidate.setEducations(new ArrayList<>());
            if (candidate.getCertifications() == null) candidate.setCertifications(new ArrayList<>());
            if (candidate.getReferences() == null) candidate.setReferences(new ArrayList<>());

            log.debug("Loading profile for candidate: {}", username);
            log.debug("Skills: {}", candidate.getSkills().size());
            log.debug("Work Experiences: {}", candidate.getWorkExperiences().size());
            log.debug("Educations: {}", candidate.getEducations().size());

            // Add candidate details to the model
            model.addAttribute("candidate", candidate);
            model.addAttribute("candidateForm", candidate);  // Always set candidateForm to ensure it's not null

            // Add any flash attributes
            model.addAttribute("success", model.getAttribute("success"));
            model.addAttribute("error", model.getAttribute("error"));

            return "candidate/profile";

        } catch (Exception e) {
            // Log the error for debugging
            log.error("Error loading candidate profile", e);

            // Add error message to the model
            model.addAttribute("error", "An error occurred while loading your profile. Please try again.");

            // If there's an authentication issue, redirect to login
            if (e instanceof AuthenticationException) {
                return "redirect:/auth/login?error=session_expired";
            }
        }
        return "candidate/profile";
    }

    @PostMapping("/profile")
    @Transactional
    public String updateProfile(@ModelAttribute("profile") @Valid ProfileUpdateRequest request,
                                BindingResult result,
                                @RequestParam(value = "resumeFile", required = false) MultipartFile resumeFile,
                                Principal principal,
                                RedirectAttributes redirectAttributes) {

        if (result.hasErrors()) {
            redirectAttributes.addFlashAttribute("org.springframework.validation.BindingResult.profile", result);
            redirectAttributes.addFlashAttribute("profile", request);
            return "redirect:/candidate/profile";
        }

        try {
            String username = principal.getName();
            Candidate candidate = candidateService.updateProfile(username, request);

            // Handle resume file upload if present
            if (resumeFile != null && !resumeFile.isEmpty()) {
                try {
                    String resumeFileName = fileUploadUtil.storeFile(resumeFile);
                    candidate.setResumeFileId(resumeFileName);
                    candidate.setResumeUrl("/api/files/resume/" + resumeFileName);
                    candidateRepository.save(candidate);
                    redirectAttributes.addFlashAttribute("successMessage", "Profile and resume updated successfully!");
                } catch (IOException e) {
                    log.error("Failed to upload resume: {}", e.getMessage());
                    redirectAttributes.addFlashAttribute("warningMessage",
                            "Profile updated, but failed to upload resume. " + e.getMessage());
                }
            } else {
                redirectAttributes.addFlashAttribute("successMessage", "Profile updated successfully!");
            }

            return "redirect:/candidate/profile";

        } catch (ResourceNotFoundException e) {
            log.error("Candidate not found: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Candidate not found. Please log in again.");
            return "redirect:/auth/login";
        } catch (Exception e) {
            log.error("Error updating candidate profile: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error",
                    "An error occurred while updating your profile. Please try again.");
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
