package com.tal.pro.controller;

import com.tal.pro.dto.ApplicationDetailsDto;
import com.tal.pro.dto.JobApplicationDto;
import com.tal.pro.exception.ResourceNotFoundException;
import com.tal.pro.model.*;
import com.tal.pro.model.JobApplication.ApplicationStatus;
import com.tal.pro.repository.CandidateRepository;
import com.tal.pro.repository.RecruiterRepository;
import com.tal.pro.security.services.UserDetailsImpl;
import com.tal.pro.service.CandidateService;
import com.tal.pro.service.JobApplicationService;
import com.tal.pro.service.JobService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
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
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/jobs")
@Slf4j
public class JobApplicationController {

    private final JobService jobService;
    private final JobApplicationService jobApplicationService;
    private final CandidateService candidateService;
    private final RecruiterRepository recruiterRepository;
    private final CandidateRepository candidateRepository;

    @Value("${file.upload-dir:uploads}")
    private String uploadDir;

    @Autowired
    public JobApplicationController(CandidateRepository candidateRepository, RecruiterRepository recruiterRepository, 
                                  CandidateService candidateService, JobApplicationService jobApplicationService, 
                                  JobService jobService) {
        this.candidateRepository = candidateRepository;
        this.recruiterRepository = recruiterRepository;
        this.candidateService = candidateService;
        this.jobApplicationService = jobApplicationService;
        this.jobService = jobService;
    }



    @PostMapping("/applications/{id}/status")
    public String updateApplicationStatus(
            @PathVariable("id") String applicationId,
            @RequestParam("status") ApplicationStatus status,
            @RequestParam(value = "notes", required = false) String notes,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {

        try {
            String updatedBy = authentication.getName();
            JobApplication application = jobApplicationService.updateApplicationStatus(applicationId, status, notes, updatedBy);
            redirectAttributes.addFlashAttribute("success", "Application status updated successfully!");
            return "redirect:/recruiter/applications/" + application.getId();
        } catch (ResourceNotFoundException e) {
            redirectAttributes.addFlashAttribute("error", "Application not found");
            return "redirect:/recruiter/dashboard";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error updating application status: " + e.getMessage());
            return "redirect:/recruiter/dashboard";
        }
    }

    @PostMapping("/applications/{id}/notes")
    public String addApplicationNote(
            @PathVariable String id,
            @RequestParam String notes,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {

        try {
            String updatedBy = authentication.getName();
            JobApplication application = jobApplicationService.addNoteToApplication(id, notes, updatedBy);
            redirectAttributes.addFlashAttribute("success", "Note added successfully!");
            return "redirect:/recruiter/applications/" + application.getId();
        } catch (ResourceNotFoundException e) {
            redirectAttributes.addFlashAttribute("error", "Application not found");
            return "redirect:/recruiter/dashboard";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error adding note: " + e.getMessage());
            return "redirect:/recruiter/dashboard";
        }
    }

    @ModelAttribute("statusList")
    public JobApplication.ApplicationStatus[] getApplicationStatuses() {
        return JobApplication.ApplicationStatus.values();
    }

    @GetMapping({"/view/{id}", "/jobs/view/{id}"})
    public String viewJob(@PathVariable String id, Model model, @AuthenticationPrincipal Object principal) {
        try {
            Job job = jobService.getJobById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Job not found with id: " + id));

            boolean isAuthenticated = false;
            boolean isCandidate = false;
            boolean isRecruiter = false;
            final boolean[] hasApplied = {false};
            String dashboardType = "guest";

            if (principal instanceof UserDetails userDetails) {
                isAuthenticated = true;
                Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
                
                if (authentication != null && authentication.isAuthenticated()) {
                    Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
                    boolean hasRecruiterRole = authorities.stream()
                        .anyMatch(auth -> "ROLE_RECRUITER".equals(auth.getAuthority()));
                    boolean hasCandidateRole = authorities.stream()
                        .anyMatch(auth -> "ROLE_CANDIDATE".equals(auth.getAuthority()));

                    if (hasRecruiterRole) {
                        isRecruiter = true;
                        dashboardType = "recruiter";
                    } 
                    
                    if (hasCandidateRole) {
                        isCandidate = true;
                        dashboardType = "candidate";
                        candidateRepository.findByUsername(userDetails.getUsername())
                            .ifPresent(candidate -> hasApplied[0] = jobApplicationService.hasApplied(candidate, job));
                    }
                }
            }

            // Get application status and notes if user has applied
            String applicationStatus = "";
            String applicationNotes = "";
            
            if (hasApplied[0] && isCandidate) {
                Optional<JobApplication> applicationOpt = jobApplicationService.findByCandidateAndJob(
                    candidateRepository.findByUsername(((UserDetails) principal).getUsername()).get(),
                    job
                );
                
                if (applicationOpt.isPresent()) {
                    JobApplication application = applicationOpt.get();
                    applicationStatus = application.getStatus() != null ? application.getStatus().name() : "";
                    applicationNotes = application.getNotes() != null ? application.getNotes() : "";
                }
            }
            
            model.addAllAttributes(Map.of(
                "isAuthenticated", isAuthenticated,
                "isCandidate", isCandidate,
                "isRecruiter", isRecruiter,
                "hasApplied", hasApplied[0],
                "dashboardType", dashboardType,
                "job", job,
                "applicationStatus", applicationStatus,
                "applicationNotes", applicationNotes
            ));

            return "candidate/job-details";
        } catch (ResourceNotFoundException e) {
            return "redirect:/jobs?error=not_found";
        } catch (Exception e) {
            log.error("Error viewing job: {}", id, e);
            return "redirect:/jobs?error=server_error";
        }
    }

    @GetMapping("/{jobId}")
    public String viewJobDetails(
            @PathVariable String jobId,
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            Model model) {
        
        // Initialize candidate as null
        Candidate candidate = null;
        
        // If user is authenticated and has candidate role, get the candidate
        if (userDetails != null && userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_CANDIDATE"))) {
            candidate = candidateRepository.findByUsername(userDetails.getUsername())
                .orElse(null);
        }
        // Get the job or return 404 if not found
        Optional<Job> jobOpt = jobService.getJobById(jobId);
        if (jobOpt.isEmpty()) {
            return "redirect:/jobs?error=not_found";
        }
        
        // Check if the current user has applied for this job and get application status
        boolean hasApplied = false;
        String applicationStatus = null;
        
        if (candidate != null && candidate.getId() != null) {
            hasApplied = jobApplicationService.hasCandidateApplied(jobId, candidate.getId());
            if (hasApplied) {
                // Get the application status if the candidate has applied
                applicationStatus = jobApplicationService.findByJobIdAndCandidateId(jobId, candidate.getId())
                    .map(app -> app.getStatus().name())
                    .orElse(null);
            }
        }
        
        model.addAttribute("job", jobOpt.get());
        model.addAttribute("hasApplied", hasApplied);
        model.addAttribute("applicationStatus", applicationStatus);
        model.addAttribute("isAuthenticated", candidate != null);
        
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

            // Create and populate DTO
            ApplicationDetailsDto appDetails = ApplicationDetailsDto.fromJobApplication(application);
            
            // Add attributes to the model
            model.addAttribute("appDetails", appDetails);
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

