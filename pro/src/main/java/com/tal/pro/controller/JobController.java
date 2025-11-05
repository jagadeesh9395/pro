package com.tal.pro.controller;

import com.tal.pro.dto.JobDto;
import com.tal.pro.model.Candidate;
import com.tal.pro.model.Job;
import com.tal.pro.model.Recruiter;
import com.tal.pro.service.JobService;
import com.tal.pro.service.JobApplicationService;
import com.tal.pro.exception.ResourceNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/recruiter/jobs")
public class JobController {

    private final JobService jobService;
    private final JobApplicationService jobApplicationService;

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
        return "recruiter/post-job";
    }

    @PostMapping("/post")
    public String postJob(
            @ModelAttribute("job") @Valid JobDto jobDto,
            BindingResult result,
            @AuthenticationPrincipal Recruiter recruiter,
            RedirectAttributes redirectAttributes) {

        if (result.hasErrors()) {
            return "recruiter/post-job";
        }

        try {
            jobService.postNewJob(jobDto, recruiter);
            redirectAttributes.addFlashAttribute("success", "Job posted successfully!");
            return "redirect:/recruiter/dashboard";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error posting job: " + e.getMessage());
            return "redirect:/recruiter/jobs/new";
        }
    }

    @GetMapping("/{id}/edit")
    public String editJobForm(@PathVariable String id, Model model, @AuthenticationPrincipal Recruiter recruiter) {
        return jobService.getJobById(id)
                .map(job -> {
                    if (!job.getPostedBy().equals(recruiter)) {
                        return "redirect:/access-denied";
                    }
                    model.addAttribute("jobDto", JobDto.fromJob(job));
                    return "recruiter/edit-job";
                })
                .orElse("redirect:/recruiter/dashboard");
    }

    @PostMapping("/{id}")
    public String updateJob(
            @PathVariable String id,
            @ModelAttribute("jobDto") @Valid JobDto jobDto,
            BindingResult result,
            @AuthenticationPrincipal Recruiter recruiter,
            RedirectAttributes redirectAttributes,
            Model model) {

        if (result.hasErrors()) {
            model.addAttribute("jobTypes", Job.JobType.values());
            return "recruiter/edit-job";
        }

        return jobService.getJobById(id)
                .map(existingJob -> {
                    // Check if the recruiter owns this job
                    if (!existingJob.getPostedBy().equals(recruiter)) {
                        redirectAttributes.addFlashAttribute("error", "You are not authorized to update this job.");
                        return "redirect:/access-denied";
                    }

                    try {
                        jobService.updateJob(id, jobDto, recruiter);
                        redirectAttributes.addFlashAttribute("success", "Job updated successfully!");
                        return "redirect:/recruiter/dashboard";
                    } catch (Exception e) {
                        redirectAttributes.addFlashAttribute("error", "Error updating job: " + e.getMessage());
                        return "redirect:/recruiter/jobs/" + id + "/edit";
                    }
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("error", "Job not found.");
                    return "redirect:/recruiter/dashboard";
                });
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
