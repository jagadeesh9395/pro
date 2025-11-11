package com.tal.pro.controller;

import com.tal.pro.dto.JobApplicationDto;
import com.tal.pro.exception.ResourceNotFoundException;
import com.tal.pro.model.Candidate;
import com.tal.pro.model.Job;
import com.tal.pro.model.JobApplication;
import com.tal.pro.service.JobService;
import com.tal.pro.service.JobApplicationService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
import java.util.Optional;
import java.util.UUID;

@Controller
@RequestMapping("/jobs")
public class JobApplicationController {

    private final JobService jobService;
    private final JobApplicationService jobApplicationService;

    @Value("${file.upload-dir:uploads}")
    private String uploadDir;

    @Autowired
    public JobApplicationController(JobService jobService, JobApplicationService jobApplicationService) {
        this.jobService = jobService;
        this.jobApplicationService = jobApplicationService;
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
            @AuthenticationPrincipal Candidate candidate,
            Model model) {

        return jobService.getJobById(jobId)
                .map(job -> {
                    model.addAttribute("job", job);

                    JobApplicationDto applicationDto = new JobApplicationDto();
                    if (candidate != null) {
                        applicationDto.setFullName(candidate.getFullName());
                        applicationDto.setEmail(candidate.getEmail());
                        applicationDto.setPhone(candidate.getPhoneNumber());
                    }

                    model.addAttribute("applicationDto", applicationDto);
                    return "candidate/apply-job";
                })
                .orElse("redirect:/jobs");
    }

    @PostMapping("/{jobId}/apply")
    public String submitApplication(
            @PathVariable String jobId,
            @ModelAttribute("applicationDto") @Valid JobApplicationDto applicationDto,
            BindingResult result,
            @RequestParam("resumeFile") MultipartFile resumeFile,
            @AuthenticationPrincipal Candidate candidate,
            RedirectAttributes redirectAttributes) {

        if (result.hasErrors()) {
            return "candidate/apply-job";
        }

        if (resumeFile.isEmpty()) {
            result.rejectValue("resumeFile", "file.required", "Please upload your resume");
            return "candidate/apply-job";
        }

        try {
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
            }
            String newFilename = UUID.randomUUID().toString() + fileExtension;

            // Save the file
            Path filePath = uploadPath.resolve(newFilename);
            Files.copy(resumeFile.getInputStream(), filePath);

            // Save application
            JobApplication application = new JobApplication();
            application.setFullName(applicationDto.getFullName());
            application.setEmail(applicationDto.getEmail());
            application.setPhone(applicationDto.getPhone());
            application.setResumePath(newFilename);
            application.setCoverLetter(applicationDto.getCoverLetter());

            jobApplicationService.submitApplication(jobId, candidate, application);

            redirectAttributes.addFlashAttribute("success", "Application submitted successfully!");
            return "redirect:/candidate/dashboard";

        } catch (IOException e) {
            redirectAttributes.addFlashAttribute("error", "Error uploading file: " + e.getMessage());
            return "redirect:/jobs/" + jobId + "/apply";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error submitting application: " + e.getMessage());
            return "redirect:/jobs/" + jobId + "/apply";
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

