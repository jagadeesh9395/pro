package com.tal.pro.controller;

import com.tal.pro.dto.JobDto;
import com.tal.pro.model.Candidate;
import com.tal.pro.model.Job;
import com.tal.pro.model.Recruiter;
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
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.util.Map;

@Controller
@RequestMapping("/recruiter/jobs")
public class JobController {

    private final JobService jobService;
    private final JobApplicationService jobApplicationService;
    private static final Logger log = org.slf4j.LoggerFactory.getLogger(JobController.class);

    @Autowired
    public JobController(JobService jobService, JobApplicationService jobApplicationService) {
        this.jobService = jobService;
        this.jobApplicationService = jobApplicationService;
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
    public String postJob(
            @Valid @ModelAttribute("job") JobDto jobDto,
            BindingResult result,
            @AuthenticationPrincipal Recruiter recruiter,
            RedirectAttributes redirectAttributes) {

        if (result.hasErrors()) {
            return "recruiter/post-job";
        }

        try {
            Job createdJob = jobService.postNewJob(jobDto, recruiter);
            redirectAttributes.addFlashAttribute("successMessage", "Job posted successfully!");
            redirectAttributes.addFlashAttribute("job", createdJob);
            redirectAttributes.addFlashAttribute("isEditMode", false);
            return "redirect:/recruiter/jobs/success";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error posting job: " + e.getMessage());
            return "redirect:/recruiter/jobs/new";
        }
    }

    @GetMapping("/success")
    public String showSuccessPage(@ModelAttribute("job") Job job, Model model) {
        if (!model.containsAttribute("job")) {
            return "redirect:/recruiter/dashboard";
        }
        return "recruiter/job-success";
    }
    
    @GetMapping("/{id}/edit")
    public String editJobForm(@PathVariable String id, Model model, @AuthenticationPrincipal Recruiter recruiter) {
        return jobService.getJobById(id)
                .map(job -> {
                    // If job doesn't have a postedBy, assign it to the current recruiter
                    if (job.getPostedBy() == null) {
                        job.setPostedBy(recruiter);
                        jobService.saveJob(job);
                    } 
                    // Check if the current user is the owner of the job
                    else if (!job.getPostedBy().getId().equals(recruiter.getId())) {
                        return "redirect:/access-denied";
                    }
                    
                    model.addAttribute("job", JobDto.fromJob(job));
                    model.addAttribute("jobTypes", Job.JobType.values());
                    model.addAttribute("isEditMode", true);
                    return "recruiter/post-job";
                })
                .orElse("redirect:/recruiter/dashboard");
    }

    @PutMapping("/{id}")
    public String updateJob(
            @PathVariable String id,
            @Valid @ModelAttribute("job") JobDto jobDto,
            BindingResult result,
            @AuthenticationPrincipal Recruiter recruiter,
            RedirectAttributes redirectAttributes) {

        if (result.hasErrors()) {
            return "recruiter/post-job";
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
            redirectAttributes.addFlashAttribute("successMessage", "Job updated successfully!");
            redirectAttributes.addFlashAttribute("job", updatedJob);
            redirectAttributes.addFlashAttribute("isEditMode", true);
            return "redirect:/recruiter/jobs/success";
            
        } catch (SecurityException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/access-denied";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/recruiter/dashboard";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error updating job: " + e.getMessage());
            return "redirect:/recruiter/jobs/" + id + "/edit";
        }
    }

    @GetMapping("/view/{id}")
    public String viewJob(@PathVariable String id, Model model, @AuthenticationPrincipal Object principal) {
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
            
            return "candidate/job-details";
        } catch (ResourceNotFoundException e) {
            return "redirect:/jobs?error=not_found";
        }
    }
}
