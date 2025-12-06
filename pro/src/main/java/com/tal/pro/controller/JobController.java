package com.tal.pro.controller;

import com.tal.pro.dto.JobDto;
import com.tal.pro.model.Candidate;
import com.tal.pro.model.Job;
import com.tal.pro.model.Recruiter;
import com.tal.pro.repository.RecruiterRepository;
import com.tal.pro.service.JobService;
import com.tal.pro.service.JobApplicationService;
import com.tal.pro.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.springframework.security.access.AccessDeniedException;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/recruiter/jobs")
public class JobController {

    private final JobService jobService;
    private final JobApplicationService jobApplicationService;
    private final RecruiterRepository recruiterRepository;
    private static final Logger log = org.slf4j.LoggerFactory.getLogger(JobController.class);

    @Autowired
    public JobController(JobService jobService,
            JobApplicationService jobApplicationService,
            RecruiterRepository recruiterRepository) {
        this.jobService = jobService;
        this.jobApplicationService = jobApplicationService;
        this.recruiterRepository = recruiterRepository;
    }

    @GetMapping("/{jobId}/applications")
    public String viewJobApplications(
            @PathVariable String jobId,
            @AuthenticationPrincipal Recruiter recruiter,
            Model model) {
        // Verify the job exists and belongs to the recruiter
        Job job = jobService.getJobById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found with id: " + jobId));

        if (!job.getPostedBy().getId().equals(recruiter.getId())) {
            throw new AccessDeniedException("You don't have permission to view these applications");
        }

        model.addAttribute("job", job);
        model.addAttribute("applications", jobApplicationService.getApplicationsByJobId(jobId));
        return "recruiter/job-applications";
    }

    @GetMapping("/new")
    public String showJobForm(Model model) {
        model.addAttribute("job", new JobDto());
        model.addAttribute("jobTypes", Job.JobType.values());
        model.addAttribute("isEditMode", false);
        return "recruiter/post-job";
    }

    @PostMapping("/post")
    @ResponseBody
    public ResponseEntity<?> postJob(
            @Valid @RequestBody JobDto jobDto,
            BindingResult result,
            @AuthenticationPrincipal UserDetails principal) {

        if (result.hasErrors()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Validation error",
                    "errors", result.getFieldErrors().stream()
                            .collect(Collectors.toMap(
                                    FieldError::getField,
                                    FieldError::getDefaultMessage,
                                    (existing, replacement) -> existing + ", " + replacement))));
        }

        try {
            // Re-fetch recruiter to ensure we have a valid entity with ID
            // The Principal object from session might be incomplete or detached
            String username = principal.getUsername();
            Recruiter recruiter = recruiterRepository.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("Recruiter not found"));

            Job createdJob = jobService.postNewJob(jobDto, recruiter);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Job posted successfully!",
                    "redirectUrl", "/recruiter/jobs"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "success", false,
                            "message", "Error posting job: " + e.getMessage()));
        }
    }

    @GetMapping("/success/{jobId}")
    public String showSuccessPage(@PathVariable String jobId, Model model) {
        Job job = jobService.getJobById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found with id: " + jobId));
        model.addAttribute("job", job);
        return "recruiter/job-success";
    }

    @GetMapping("/{id}/edit")
    public String editJobForm(@PathVariable String id, Model model,
            @AuthenticationPrincipal Object principal,
            RedirectAttributes redirectAttributes) {

        // Check if user is authenticated
        if (principal == null) {
            redirectAttributes.addFlashAttribute("error", "You must be logged in to edit a job");
            return "redirect:/login";
        }

        // Get the recruiter from the principal
        Recruiter recruiter;
        if (principal instanceof Recruiter) {
            recruiter = (Recruiter) principal;
        } else if (principal instanceof UserDetails) {
            // If using UserDetails, try to load the recruiter
            String username = ((UserDetails) principal).getUsername();
            recruiter = recruiterRepository.findByUsername(username).orElse(null);
        } else {
            recruiter = null;
        }

        if (recruiter == null) {
            redirectAttributes.addFlashAttribute("error", "Only recruiters can edit jobs");
            return "redirect:/access-denied";
        }

        // Create final copy of redirectAttributes for use in lambda
        final RedirectAttributes finalRedirectAttributes = redirectAttributes;

        return jobService.getJobById(id)
                .map(job -> {
                    // If job doesn't have a postedBy, assign it to the current recruiter
                    if (job.getPostedBy() == null) {
                        job.setPostedBy(recruiter);
                        jobService.saveJob(job);
                    }
                    // Check if the current user is the owner of the job
                    else if (job.getPostedBy() != null && !job.getPostedBy().getId().equals(recruiter.getId())) {
                        finalRedirectAttributes.addFlashAttribute("error",
                                "You don't have permission to edit this job");
                        return "redirect:/access-denied";
                    }

                    model.addAttribute("job", JobDto.fromJob(job));
                    model.addAttribute("jobTypes", Job.JobType.values());
                    model.addAttribute("isEditMode", true);
                    return "recruiter/post-job";
                })
                .orElseGet(() -> {
                    finalRedirectAttributes.addFlashAttribute("error", "Job not found");
                    return "redirect:/recruiter/dashboard";
                });
    }

    @PutMapping("/{id}")
    @ResponseBody
    public ResponseEntity<?> updateJob(
            @PathVariable String id,
            @Valid @RequestBody JobDto jobDto,
            BindingResult result,
            @AuthenticationPrincipal Object principal) {

        System.out.println("Received update request for job: " + id);
        System.out.println("Principal class: " + (principal != null ? principal.getClass().getName() : "null"));

        if (principal == null) {
            System.out.println("No authentication found. User not logged in.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of(
                            "success", false,
                            "message", "You must be logged in to perform this action"));
        }

        // Get the authenticated user's email from UserDetails
        String userEmail;
        if (principal instanceof UserDetails) {
            userEmail = ((UserDetails) principal).getUsername();
            System.out.println("Authenticated user email: " + userEmail);
        } else {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of(
                            "success", false,
                            "message", "Invalid user session"));
        }

        // Find the recruiter by email
        Optional<Recruiter> recruiterOpt = recruiterRepository.findByUsername(userEmail);
        if (recruiterOpt.isEmpty()) {
            System.out.println("No recruiter found with email: " + userEmail);
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of(
                            "success", false,
                            "message", "You must be logged in as a recruiter to update a job"));
        }
        Recruiter recruiter = recruiterOpt.get();

        System.out.println("Authenticated as recruiter: " + recruiter.getEmail());

        if (result.hasErrors()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Validation error",
                    "errors", result.getFieldErrors().stream()
                            .collect(Collectors.toMap(
                                    FieldError::getField,
                                    FieldError::getDefaultMessage,
                                    (existing, replacement) -> existing + ", " + replacement))));
        }

        try {
            // First, check if the job exists and the recruiter has permission
            Job existingJob = jobService.getJobById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Job not found with id: " + id));

            // If the job doesn't have a postedBy, assign it to the current recruiter
            if (existingJob.getPostedBy() == null) {
                existingJob.setPostedBy(recruiter);
                jobService.saveJob(existingJob);
            }
            // Check if the current user is the owner of the job
            else if (!existingJob.getPostedBy().getId().equals(recruiter.getId())) {
                throw new SecurityException("You are not authorized to update this job");
            }

            // Proceed with the update
            Job updatedJob = jobService.updateJob(id, jobDto, recruiter);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Job updated successfully!",
                    "redirectUrl", "/recruiter/jobs"));

        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "success", false,
                    "message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "success", false,
                    "message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "message", "Error updating job: " + e.getMessage()));
        }
    }

    @GetMapping("/view/{id}")
    public String viewJob(
            @PathVariable String id,
            Model model,
            @AuthenticationPrincipal Object principal,
            RedirectAttributes redirectAttributes) {

        // Basic validation for MongoDB ObjectId format (24 hex chars)
        if (id == null || id.trim().isEmpty() || id.length() != 24 || !id.matches("^[a-fA-F0-9]+$")) {
            redirectAttributes.addFlashAttribute("error", "Invalid job ID format");
            return "redirect:/recruiter/dashboard";
        }

        try {
            Job job = jobService.getJobById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Job not found with id: " + id));

            model.addAttribute("job", job);

            // Check if the current user is a candidate
            if (principal != null && principal instanceof Candidate) {
                model.addAttribute("isCandidate", true);
                model.addAttribute("hasApplied", jobApplicationService.hasApplied((Candidate) principal, job));
            } else {
                model.addAttribute("isCandidate", false);
            }

            return "recruiter/job-details";
        } catch (ResourceNotFoundException e) {
            redirectAttributes.addFlashAttribute("error", "Job not found");
            return "redirect:/recruiter/dashboard";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error loading job: " + e.getMessage());
            return "redirect:/recruiter/dashboard";
        }
    }

    @PostMapping("/{id}/delete")
    public String deleteJob(
            @PathVariable String id,
            @AuthenticationPrincipal Object principal,
            RedirectAttributes redirectAttributes) {

        if (principal == null) {
            return "redirect:/auth/login?error=not_authenticated";
        }

        try {
            // Get the recruiter from the principal
            Recruiter recruiter;
            if (principal instanceof Recruiter) {
                recruiter = (Recruiter) principal;
            } else if (principal instanceof UserDetails) {
                String username = ((UserDetails) principal).getUsername();
                recruiter = recruiterRepository.findByUsername(username)
                        .orElseThrow(() -> new RuntimeException("Recruiter not found"));
            } else {
                throw new AccessDeniedException("Invalid user session");
            }

            jobService.deleteJob(id, recruiter);
            redirectAttributes.addFlashAttribute("success", "Job deleted successfully");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error deleting job: " + e.getMessage());
        }

        return "redirect:/recruiter/jobs";
    }

    @PostMapping("/{id}/toggle-status")
    public String toggleJobStatus(
            @PathVariable String id,
            @AuthenticationPrincipal Object principal,
            RedirectAttributes redirectAttributes) {

        if (principal == null) {
            return "redirect:/auth/login?error=not_authenticated";
        }

        try {
            // Get the recruiter from the principal
            Recruiter recruiter;
            if (principal instanceof Recruiter) {
                recruiter = (Recruiter) principal;
            } else if (principal instanceof UserDetails) {
                String username = ((UserDetails) principal).getUsername();
                recruiter = recruiterRepository.findByUsername(username)
                        .orElseThrow(() -> new RuntimeException("Recruiter not found"));
            } else {
                throw new AccessDeniedException("Invalid user session");
            }

            Job job = jobService.getJobById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Job not found"));

            // Verify ownership
            if (!job.getPostedBy().getId().equals(recruiter.getId())) {
                throw new AccessDeniedException("You are not authorized to modify this job");
            }

            // Toggle status
            job.setActive(!job.isActive());
            jobService.saveJob(job);

            String status = job.isActive() ? "activated" : "deactivated";
            redirectAttributes.addFlashAttribute("success", "Job " + status + " successfully");

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error updating job status: " + e.getMessage());
        }

        return "redirect:/recruiter/jobs";
    }
}
