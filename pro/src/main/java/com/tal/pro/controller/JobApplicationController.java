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
import java.util.List;
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
            Model model) {

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

            // Check if resume exists (either new upload or existing)
            if ((resumeFile == null || resumeFile.isEmpty()) && 
                (applicationDto.getResumePath() == null || applicationDto.getResumePath().isEmpty())) {
                result.rejectValue("resumeFile", "file.required", "Please upload your resume");
                return "candidate/apply-job";
            }

            String resumeFilename = applicationDto.getResumePath(); // Use existing resume by default
            
            // Handle new resume upload
            if (resumeFile != null && !resumeFile.isEmpty()) {
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

                // Generate a unique filename
                String originalFilename = resumeFile.getOriginalFilename();
                String fileExtension = "";
                if (originalFilename != null && originalFilename.contains(".")) {
                    fileExtension = originalFilename.substring(originalFilename.lastIndexOf("."));
                } else {
                    fileExtension = ".pdf"; // Default extension if none provided
                }
                resumeFilename = UUID.randomUUID().toString() + fileExtension;

                try {
                    // Save the file
                    Path filePath = uploadPath.resolve(resumeFilename);
                    Files.copy(resumeFile.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
                    
                    // Update candidate's resume path
                    candidate.setResumeUrl(resumeFilename);
                    candidateService.saveCandidate(candidate);
                } catch (IOException e) {
                    throw new RuntimeException("Failed to save resume file: " + e.getMessage(), e);
                }
            }

            // Create and save application
            JobApplication application = new JobApplication();
            application.setFullName(applicationDto.getFullName() != null ? applicationDto.getFullName() : "");
            application.setEmail(applicationDto.getEmail() != null ? applicationDto.getEmail() : "");
            application.setPhone(applicationDto.getPhone() != null ? applicationDto.getPhone() : "");
            application.setCurrentCompany(applicationDto.getCurrentCompany() != null ? applicationDto.getCurrentCompany() : "");
            application.setResumePath(resumeFilename);
            application.setCoverLetter(applicationDto.getCoverLetter() != null ? applicationDto.getCoverLetter() : "");
            application.setNoticePeriod(applicationDto.getNoticePeriod() != null ? applicationDto.getNoticePeriod() : 0);
            application.setExpectedSalary(applicationDto.getExpectedSalary() != null ? applicationDto.getExpectedSalary() : 0.0);
            application.setAdditionalInfo(applicationDto.getAdditionalInfo() != null ? applicationDto.getAdditionalInfo() : "");
            application.setStatus(JobApplication.ApplicationStatus.APPLIED);
            application.setAppliedAt(LocalDateTime.now());
            application.setUpdatedAt(LocalDateTime.now());
            application.setUpdatedBy(candidate.getId());
            application.setCandidate(candidate);
            application.setJob(job);

            // Save the application
            JobApplication savedApplication = jobApplicationService.submitApplication(jobId, candidate, application);
            if (savedApplication == null || savedApplication.getId() == null) {
                throw new RuntimeException("Failed to save application");
            }

            // Add success message and redirect to application details
            redirectAttributes.addFlashAttribute("success", "Your application has been submitted successfully!");
            return "redirect:/jobs/applications/" + savedApplication.getId();

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

    @GetMapping("/applications/{applicationId}")
    public String viewApplicationDetails(
            @PathVariable String applicationId,
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            Model model) {
        
        try {
            // Get the application
            JobApplication application = jobApplicationService.getApplicationById(applicationId)
                    .orElseThrow(() -> new ResourceNotFoundException("Application not found with id: " + applicationId));
            
            // Verify the current user is the owner of the application
            if (!application.getCandidate().getId().equals(userDetails.getId())) {
                return "redirect:/candidate/dashboard?error=unauthorized";
            }
            
            // Get the job details
            Job job = jobService.getJobById(application.getJob().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Job not found"));
            
            // Get application status history
            List<ApplicationStatusHistory> statusHistory = 
                    jobApplicationService.getApplicationStatusHistory(applicationId);
            
            // Add attributes to the model
            model.addAttribute("application", application);
            model.addAttribute("job", job);
            model.addAttribute("statusHistory", statusHistory);
            model.addAttribute("currentPath", "/candidate/applications/" + applicationId);
            
            return "candidate/application-details";
            
        } catch (ResourceNotFoundException e) {
            return "redirect:/candidate/dashboard?error=not_found";
        } catch (Exception e) {
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

