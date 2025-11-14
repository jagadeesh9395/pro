package com.tal.pro.controller;

import com.tal.pro.dto.JobApplicationDto;
import com.tal.pro.exception.ResourceNotFoundException;
import com.tal.pro.model.ApplicationStatusHistory;
import com.tal.pro.model.Candidate;
import com.tal.pro.model.Job;
import com.tal.pro.model.JobApplication;
import com.tal.pro.service.CandidateService;
import com.tal.pro.service.JobApplicationService;
import com.tal.pro.service.JobService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import com.tal.pro.security.services.UserDetailsImpl;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Controller
@RequestMapping("/jobs")
public class JobApplicationController {

    private final JobService jobService;
    private final JobApplicationService jobApplicationService;
    private final CandidateService candidateService;

    @Value("${file.upload-dir:uploads}")
    private String uploadDir;

    @Autowired
    public JobApplicationController(JobService jobService, 
                                  JobApplicationService jobApplicationService,
                                  CandidateService candidateService) {
        this.jobService = jobService;
        this.jobApplicationService = jobApplicationService;
        this.candidateService = candidateService;
    }

    @GetMapping("/view/{id}")
    public String viewJob(@PathVariable("id") String id, Model model, @AuthenticationPrincipal Object principal) {
        System.out.println("Fetching job with ID: " + id);
        try {
            Optional<Job> jobOpt = jobService.getJobById(id);
            System.out.println("Job found: " + jobOpt.isPresent());
            
            if (!jobOpt.isPresent()) {
                System.out.println("Job not found with ID: " + id);
                return "redirect:/jobs?error=not_found";
            }
            
            Job job = jobOpt.get();
            System.out.println("Job details - Title: " + job.getJobTitle() + ", Company: " + job.getCompanyName());
            
            model.addAttribute("job", job);
            
            // Since this is a candidate-specific page, if user is authenticated, they are a candidate
            boolean isCandidate = (principal != null);
            System.out.println("User is authenticated (candidate): " + isCandidate);
            model.addAttribute("isCandidate", isCandidate);
            
            if (isCandidate) {
                try {
                    boolean hasApplied = jobApplicationService.hasApplied((Candidate) principal, job);
                    System.out.println("User has applied: " + hasApplied);
                    model.addAttribute("hasApplied", hasApplied);
                } catch (Exception e) {
                    System.err.println("Error checking application status: " + e.getMessage());
                    model.addAttribute("hasApplied", false);
                }
            } else {
                model.addAttribute("hasApplied", false);
            }
            
            // Debug: Print all model attributes
            System.out.println("Model attributes:");
            model.asMap().forEach((key, value) -> {
                System.out.println(key + " = " + value);
            });
            
            return "candidate/job-details";
        } catch (Exception e) {
            System.err.println("Error in viewJob: " + e.getMessage());
            e.printStackTrace();
            return "redirect:/jobs?error=server_error";
        }
    }
    
    @GetMapping("/{jobId}")
    public String viewJobDetails(
            @PathVariable String jobId,
            @AuthenticationPrincipal Candidate candidate,
            Model model) {
        model.addAttribute("job", jobService.getJobById(jobId));
        model.addAttribute("hasApplied", jobApplicationService.hasCandidateApplied(jobId, candidate.getId()));
        return "candidate/job-details";
    }

    @GetMapping("/{jobId}/apply")
    public String showApplicationForm(
            @PathVariable String jobId,
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            HttpServletRequest request,
            Model model) {
        
        // Debug logging
        System.out.println("=== DEBUG: showApplicationForm called ===");
        System.out.println("Job ID: " + jobId);
        System.out.println("Authenticated User: " + (userDetails != null ? userDetails.getUsername() : "null"));
        
        // Check if user is authenticated
        if (userDetails == null) {
            System.out.println("DEBUG: No authenticated user, redirecting to login");
            return "redirect:/auth/login?redirect=/jobs/" + jobId + "/apply";
        }
        
        // Get the candidate details
        Candidate candidate = candidateService.getOrCreateCandidate(userDetails);
        
        System.out.println("DEBUG: Candidate found: " + candidate.getEmail());

        return jobService.getJobById(jobId)
                .map(job -> {
                    // Check if already applied
                    if (jobApplicationService.hasCandidateApplied(jobId, candidate.getId())) {
                        return "redirect:/jobs/" + jobId + "?error=already_applied";
                    }
                    
                    model.addAttribute("job", job);

                    // Create and populate DTO with candidate data
                    JobApplicationDto applicationDto = new JobApplicationDto();
                    applicationDto.setFullName(candidate.getFullName());
                    applicationDto.setEmail(candidate.getEmail());
                    applicationDto.setPhone(candidate.getPhoneNumber());
                    applicationDto.setCurrentCompany(candidate.getCurrentCompany());
                    
                    // Set resume path if available
                    if (candidate.getResumeUrl() != null && !candidate.getResumeUrl().isEmpty()) {
                        applicationDto.setResumePath(candidate.getResumeUrl());
                    }

                    // Log for debugging
                    System.out.println("Pre-filled application data for " + candidate.getEmail() + ": " + applicationDto);
                    
                    // Add to model
                    model.addAttribute("applicationDto", applicationDto);
                    model.addAttribute("currentPath", request.getRequestURI());
                    
                    return "candidate/apply-job";
                })
                .orElse("redirect:/jobs?error=job_not_found");
    }

    @PostMapping("/{jobId}/apply")
    public String submitApplication(
            @PathVariable String jobId,
            @ModelAttribute("applicationDto") @Valid JobApplicationDto applicationDto,
            BindingResult result,
            @RequestParam(value = "resumeFile", required = false) MultipartFile resumeFile,
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            RedirectAttributes redirectAttributes,
            Model model,
            HttpServletRequest request) {
        
        String requestInfo = String.format("Request: %s %s", request.getMethod(), request.getRequestURI());
        System.out.println("\n=== DEBUG: Submit application called ===");
        System.out.println("Request: " + requestInfo);
        System.out.println("Job ID: " + jobId);
        System.out.println("User: " + (userDetails != null ? userDetails.getUsername() : "null"));
        System.out.println("New resume file present: " + (resumeFile != null && !resumeFile.isEmpty()));
        System.out.println("Using existing resume: " + ("true".equalsIgnoreCase(request.getParameter("useExistingResume"))));
        System.out.println("Application DTO: " + applicationDto);
        
        // Log form data for debugging
        if (result.hasErrors()) {
            System.err.println("\n=== FORM VALIDATION ERRORS ===");
            result.getAllErrors().forEach(error -> 
                System.err.println(" - " + error.getDefaultMessage())
            );
            model.addAttribute("org.springframework.validation.BindingResult.applicationDto", result);
            model.addAttribute("error", "Please correct the following errors:");
            
            // Re-add the job to the model for form re-rendering
            Job job = jobService.getJobById(jobId)
                    .orElseThrow(() -> new ResourceNotFoundException("Job not found with id: " + jobId));
            model.addAttribute("job", job);
            
            return "candidate/apply-job";
        }
        
        try {
            // Add job to model for form re-rendering in case of errors
            Job job = jobService.getJobById(jobId)
                    .orElseThrow(() -> new ResourceNotFoundException("Job not found with id: " + jobId));
            model.addAttribute("job", job);
            
            // Get the candidate
            Candidate candidate = candidateService.getOrCreateCandidate(userDetails);
            if (candidate == null) {
                throw new RuntimeException("Candidate not found or could not be created");
            }
            
            // Validate required fields
            if (result.hasErrors()) {
                return "candidate/apply-job";
            }

            // Check if using existing resume - handle different possible parameter types
            Object useExistingResumeObj = request.getParameter("useExistingResume");
            boolean useExistingResume = false;
            
            if (useExistingResumeObj != null) {
                if (useExistingResumeObj instanceof Boolean) {
                    useExistingResume = (Boolean) useExistingResumeObj;
                } else if (useExistingResumeObj instanceof String) {
                    useExistingResume = Boolean.parseBoolean((String) useExistingResumeObj);
                } else if (useExistingResumeObj instanceof Number) {
                    useExistingResume = ((Number) useExistingResumeObj).intValue() != 0;
                }
            }
            
            // Debug log
            System.out.println("useExistingResume parameter type: " + (useExistingResumeObj != null ? useExistingResumeObj.getClass().getName() : "null"));
            System.out.println("useExistingResume parameter value: " + useExistingResumeObj);
            System.out.println("Parsed useExistingResume: " + useExistingResume);
            System.out.println("Resume file: " + (resumeFile != null ? "provided" : "not provided"));
            
            // If not using existing resume and no file is provided, show error
            if (!useExistingResume && (resumeFile == null || resumeFile.isEmpty())) {
                result.rejectValue("resumeFile", "file.required", "Please upload your resume or select to use your existing resume");
                model.addAttribute("job", job);
                return "candidate/apply-job";
            }

            // Initialize resume filename
            String resumeFilename = null;
            
            if (useExistingResume) {
                // Use existing resume path from the candidate's profile
                resumeFilename = candidate.getResumeUrl();
                System.out.println("Using existing resume: " + resumeFilename);
                
                if (resumeFilename == null || resumeFilename.isEmpty()) {
                    result.rejectValue("resumeFile", "file.missing", "No existing resume found. Please upload a new resume.");
                    model.addAttribute("job", job);
                    return "candidate/apply-job";
                }
            } 
            // Handle new resume upload if not using existing resume
            else if (resumeFile != null && !resumeFile.isEmpty()) {
                // Validate file size (5MB max)
                if (resumeFile.getSize() > 5 * 1024 * 1024) {
                    result.rejectValue("resumeFile", "file.size", "File size must be less than 5MB");
                    return "candidate/apply-job";
                }
                
                // Create upload directory if it doesn't exist
                Path uploadPath = Paths.get(uploadDir);
                if (!Files.exists(uploadPath)) {
                    Files.createDirectories(uploadPath);
                }

                // Generate a unique filename for the new resume
                String originalFilename = resumeFile.getOriginalFilename();
                String fileExtension = "";
                if (originalFilename != null && originalFilename.contains(".")) {
                    fileExtension = originalFilename.substring(originalFilename.lastIndexOf("."));
                } else {
                    fileExtension = ".pdf"; // Default extension if none provided
                }
                resumeFilename = UUID.randomUUID().toString() + fileExtension;
                
                // Save the file
                Path filePath = uploadPath.resolve(resumeFilename);
                Files.copy(resumeFile.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
                
                // Update candidate's resume path
                candidate.setResumeUrl(resumeFilename);
                candidate = candidateService.saveCandidate(candidate);
                System.out.println("Saved new resume for candidate: " + candidate.getResumeUrl());
            }
            
            // Update candidate's profile with new information from the application
            boolean candidateUpdated = false;
            
            // 1. Update phone number if provided and different
            if (applicationDto.getPhone() != null && !applicationDto.getPhone().trim().isEmpty() && 
                !applicationDto.getPhone().trim().equals(candidate.getPhoneNumber())) {
                System.out.println("Updating candidate's phone number from '" + candidate.getPhoneNumber() + "' to '" + applicationDto.getPhone().trim() + "'");
                try {
                    candidate.setPhoneNumber(applicationDto.getPhone().trim());
                    candidateUpdated = true;
                } catch (Exception e) {
                    System.err.println("Error updating candidate's phone number: " + e.getMessage());
                    e.printStackTrace();
                }
            }
            
            // 2. Update current company if provided and different
            if (applicationDto.getCurrentCompany() != null && !applicationDto.getCurrentCompany().trim().isEmpty() && 
                !applicationDto.getCurrentCompany().trim().equals(candidate.getCurrentCompany())) {
                System.out.println("Updating candidate's current company from '" + candidate.getCurrentCompany() + "' to '" + applicationDto.getCurrentCompany().trim() + "'");
                try {
                    candidate.setCurrentCompany(applicationDto.getCurrentCompany().trim());
                    candidateUpdated = true;
                } catch (Exception e) {
                    System.err.println("Error updating candidate's current company: " + e.getMessage());
                    e.printStackTrace();
                }
            }
            
            // 3. Update resume URL if a new resume was uploaded
            if (resumeFilename != null && !resumeFilename.equals(candidate.getResumeUrl())) {
                System.out.println("Updating candidate's resume URL to: " + resumeFilename);
                try {
                    candidate.setResumeUrl(resumeFilename);
                    candidateUpdated = true;
                } catch (Exception e) {
                    System.err.println("Error updating candidate's resume URL: " + e.getMessage());
                    e.printStackTrace();
                }
            }
            
            // Save candidate if any updates were made
            if (candidateUpdated) {
                try {
                    candidate = candidateService.saveCandidate(candidate);
                    System.out.println("Successfully updated candidate profile");
                } catch (Exception e) {
                    System.err.println("Error saving candidate profile: " + e.getMessage());
                    e.printStackTrace();
                }
            } else {
                System.out.println("No candidate profile updates needed");
            }
            
            // Create the application with the resume path
            JobApplication application = new JobApplication();
            
            // Set personal information - use DTO values if provided, otherwise fall back to candidate profile
            application.setFullName(applicationDto.getFullName() != null && !applicationDto.getFullName().trim().isEmpty() ? 
                applicationDto.getFullName().trim() : candidate.getFullName());
                
            application.setEmail(applicationDto.getEmail() != null && !applicationDto.getEmail().trim().isEmpty() ? 
                applicationDto.getEmail().trim() : candidate.getEmail());
                
            application.setPhone(applicationDto.getPhone() != null && !applicationDto.getPhone().trim().isEmpty() ? 
                applicationDto.getPhone().trim() : candidate.getPhoneNumber());
                
            application.setCurrentCompany(applicationDto.getCurrentCompany() != null && !applicationDto.getCurrentCompany().trim().isEmpty() ? 
                applicationDto.getCurrentCompany().trim() : candidate.getCurrentCompany());
            
            // Set application-specific fields
            application.setResumePath(resumeFilename != null ? resumeFilename : candidate.getResumeUrl());
            application.setCoverLetter(applicationDto.getCoverLetter() != null ? applicationDto.getCoverLetter() : "");
            application.setNoticePeriod(applicationDto.getNoticePeriod() != null ? applicationDto.getNoticePeriod() : 0);
            application.setExpectedSalary(applicationDto.getExpectedSalary() != null ? applicationDto.getExpectedSalary() : 0.0);
            application.setAdditionalInfo(applicationDto.getAdditionalInfo() != null ? applicationDto.getAdditionalInfo().trim() : "");
            
            // Set application metadata
            application.setStatus(JobApplication.ApplicationStatus.APPLIED);
            application.setAppliedAt(LocalDateTime.now());
            application.setUpdatedAt(LocalDateTime.now());
            application.setUpdatedBy(candidate.getId());
            application.setCandidate(candidate);
            application.setJob(job);
            
            // Log the final application data for debugging
            System.out.println("Final application data - Name: " + application.getFullName() + 
                             ", Email: " + application.getEmail() + 
                             ", Phone: " + application.getPhone() + 
                             ", Company: " + application.getCurrentCompany());

            // Save the application
            try {
                JobApplication savedApplication = jobApplicationService.submitApplication(jobId, candidate, application);
                
                if (savedApplication == null || savedApplication.getId() == null) {
                    throw new RuntimeException("Failed to save application - no ID returned");
                }
                
                // Log successful submission
                System.out.println("Application submitted successfully with ID: " + savedApplication.getId());
                
                // Add success message and redirect to application details
                redirectAttributes.addFlashAttribute("success", "Your application has been submitted successfully!");
                return "redirect:/jobs/applications/" + savedApplication.getId();
                
            } catch (Exception e) {
                System.err.println("Error in submitApplication: " + e.getMessage());
                e.printStackTrace();
                redirectAttributes.addFlashAttribute("error", "Error submitting application: " + e.getMessage());
                return "redirect:/jobs/" + jobId + "/apply";
            }

        } catch (ResourceNotFoundException e) {
            redirectAttributes.addFlashAttribute("error", "Job not found: " + e.getMessage());
            return "redirect:/jobs";
        } catch (Exception e) {
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("error", "An error occurred while submitting your application: " + 
                (e.getMessage() != null ? e.getMessage() : "Please try again later."));
            return "redirect:/jobs/" + jobId + "/apply";
        }
    }

    // Debug endpoint to check application data
    @ResponseBody
    @GetMapping("/api/debug/application/{applicationId}")
    public Map<String, Object> debugApplication(@PathVariable String applicationId) {
        JobApplication application = jobApplicationService.getApplicationById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found"));
                
        Map<String, Object> result = new HashMap<>();
        result.put("id", application.getId());
        result.put("noticePeriod", application.getNoticePeriod());
        result.put("expectedSalary", application.getExpectedSalary());
        result.put("coverLetter", application.getCoverLetter() != null ? "[present]" : null);
        result.put("appliedAt", application.getAppliedAt());
        result.put("status", application.getStatus() != null ? application.getStatus().name() : null);
        
        return result;
    }
    
    
    @GetMapping("/applications/{applicationId}")
    public String viewApplicationDetails(
            @PathVariable String applicationId,
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            Model model,
            RedirectAttributes redirectAttributes) {
        
        System.out.println("Viewing application details for ID: " + applicationId);
        
        try {
            // Get the application
            JobApplication application = jobApplicationService.getApplicationById(applicationId)
                    .orElseThrow(() -> {
                        System.err.println("Application not found: " + applicationId);
                        return new ResourceNotFoundException("Application not found with id: " + applicationId);
                    });
            
            System.out.println("Found application: " + application);
            
            // Verify the current user is the owner of the application
            if (application.getCandidate() == null || application.getCandidate().getId() == null) {
                System.err.println("Application has no candidate or candidate ID is null");
                return "redirect:/candidate/dashboard?error=invalid_application";
            }
            
            if (!application.getCandidate().getId().equals(userDetails.getId())) {
                System.err.println("Unauthorized access attempt for application: " + applicationId + " by user: " + userDetails.getId());
                return "redirect:/candidate/dashboard?error=unauthorized";
            }
            
            // Get the job details
            if (application.getJob() == null || application.getJob().getId() == null) {
                System.err.println("Application has no job or job ID is null");
                return "redirect:/candidate/dashboard?error=invalid_job";
            }
            
            String jobId = application.getJob().getId();
            System.out.println("Looking up job with ID: " + jobId);
            
            Job job = jobService.getJobById(jobId)
                    .orElseThrow(() -> {
                        System.err.println("Job not found for application: " + applicationId + ", job ID: " + jobId);
                        return new ResourceNotFoundException("Job not found for this application");
                    });
            
            System.out.println("Found job: " + job);
            
            // Get application status history
            List<ApplicationStatusHistory> statusHistory = 
                    jobApplicationService.getApplicationStatusHistory(applicationId);
            
            // Get the candidate details
            Candidate candidate = candidateService.getCandidateById(application.getCandidate().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Candidate not found with id: " + application.getCandidate().getId()));
            
            // Ensure application has all required fields, fallback to candidate data if needed
            if ((application.getFullName() == null || application.getFullName().isEmpty()) && candidate.getFullName() != null) {
                application.setFullName(candidate.getFullName());
            }
            if ((application.getEmail() == null || application.getEmail().isEmpty()) && candidate.getEmail() != null) {
                application.setEmail(candidate.getEmail());
            }
            if ((application.getPhone() == null || application.getPhone().isEmpty()) && candidate.getPhoneNumber() != null) {
                application.setPhone(candidate.getPhoneNumber());
            }
            if ((application.getCurrentCompany() == null || application.getCurrentCompany().isEmpty()) && candidate.getCurrentCompany() != null) {
                application.setCurrentCompany(candidate.getCurrentCompany());
            }
            if ((application.getResumePath() == null || application.getResumePath().isEmpty()) && candidate.getResumeUrl() != null) {
                application.setResumePath(candidate.getResumeUrl());
            }
            
            // Add attributes to the model
            model.addAttribute("application", application);
            model.addAttribute("job", job);
            model.addAttribute("candidate", candidate);
            model.addAttribute("statusHistory", statusHistory != null ? statusHistory : new ArrayList<ApplicationStatusHistory>());
            model.addAttribute("currentPath", "/candidate/applications/" + applicationId);
            
            return "candidate/application-details";
        } catch (ResourceNotFoundException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/candidate/dashboard?error=not_found";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "An error occurred while loading the application details.");
            return "redirect:/candidate/dashboard?error=server_error";
        }
    }

    @GetMapping("/applications/{applicationId}/resume")
    @ResponseBody
    public ResponseEntity<Resource> downloadResume(@PathVariable String applicationId) {
        try {
            JobApplication application = jobApplicationService.getApplicationById(applicationId)
                    .orElseThrow(() -> new IllegalArgumentException("Application not found"));

            Path filePath = Paths.get(uploadDir).resolve(application.getResumePath()).normalize();
            Resource resource = new UrlResource(filePath.toUri());

            if (resource.exists() || resource.isReadable()) {
                String contentType = "application/octet-stream";
                try {
                    contentType = Files.probeContentType(filePath);
                } catch (IOException ex) {
                    // Use default content type
                }

                return ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(contentType))
                        .header(HttpHeaders.CONTENT_DISPOSITION,
                                "attachment; filename=\"" + resource.getFilename() + "\"")
                        .body(resource);
            } else {
                throw new RuntimeException("Could not read the file!");
            }
        } catch (MalformedURLException e) {
            throw new RuntimeException("Error: " + e.getMessage());
        } catch (IOException e) {
            throw new RuntimeException("Error: " + e.getMessage());
        }
    }
}

