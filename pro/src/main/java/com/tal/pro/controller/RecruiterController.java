package com.tal.pro.controller;

import com.tal.pro.criteria.ResumeSearchCriteria;
import com.tal.pro.dto.ApplicationDetailsDto;
import com.tal.pro.event.ApplicationStatusEvent;
import com.tal.pro.model.*;
import com.tal.pro.repository.JobApplicationRepository;
import com.tal.pro.repository.RecruiterRepository;
import com.tal.pro.service.CandidateService;
import com.tal.pro.service.JobApplicationService;
import com.tal.pro.service.JobService;
import com.tal.pro.service.ResumeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Controller
@RequestMapping("/recruiter")
public class RecruiterController {

    @Autowired
    private RecruiterRepository recruiterRepository;

    @Autowired
    private ResumeService resumeService;

    @Autowired
    private JobApplicationService jobApplicationService;

    @Autowired
    private JobService jobService;

    @Autowired
    private CandidateService candidateService;
    @Autowired
    private JobApplicationRepository jobApplicationRepository;

    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;

    @GetMapping("/upcoming-interviews")
    @ResponseBody
    public List<JobApplication> getUpcomingInterviews(Principal principal) {
        String recruiterEmail = principal.getName();
        // Get interviews scheduled for the next 7 days
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime weekFromNow = now.plusDays(7);

        return jobApplicationService.findByRecruiterAndInterviewDateBetween(
                recruiterEmail, now, weekFromNow);
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model, Principal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search,
            HttpServletRequest request) {

        // Add current path for active menu highlighting
        String requestURI = request.getRequestURI();
        model.addAttribute("currentPath", requestURI);
        try {
            if (principal == null) {
                return "redirect:/auth/login?error=not_authenticated";
            }

            String username = principal.getName();
            Recruiter recruiter = recruiterRepository.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("Recruiter not found"));

            // Create pageable with sorting
            Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "appliedAt"));

            // Get all applications for the recruiter
            Page<JobApplication> applicationsPage = jobApplicationService.getApplicationsByRecruiterId(
                    recruiter.getId(), pageable);

            // Get recent applications (first page, sorted by most recent)
            // List<JobApplication> recentApplications = applicationsPage.getContent();
            List<JobApplication> recentApplications = jobApplicationService.findRecentApplications();
            // Get all status counts for the dashboard stats
            Map<JobApplication.ApplicationStatus, Long> allStatusCounts = jobApplicationService
                    .getApplicationStatusCounts(recruiter.getId());

            // Add all necessary attributes to the model
            model.addAttribute("recruiter", recruiter);
            model.addAttribute("currentUser", recruiter);
            model.addAttribute("username", username);
            model.addAttribute("fullName", recruiter.getFullName());
            model.addAttribute("email", recruiter.getEmail());
            model.addAttribute("companyName", recruiter.getCompany());
            model.addAttribute("isRecruiter", true);

            // Applications data
            model.addAttribute("recentApplications", recentApplications);
            model.addAttribute("totalApplications", applicationsPage.getTotalElements());
            model.addAttribute("currentPage", applicationsPage.getNumber());
            model.addAttribute("totalPages", applicationsPage.getTotalPages());
            model.addAttribute("totalItems", applicationsPage.getTotalElements());
            model.addAttribute("pageSize", size);

            // Status data for stats
            model.addAttribute("statusCounts", allStatusCounts);

            // Search data
            model.addAttribute("searchQuery", search != null && !search.isEmpty() ? search : "");

            // Stats for the dashboard cards
            model.addAttribute("totalCandidates", allStatusCounts.values().stream().mapToLong(Long::longValue).sum());
            model.addAttribute("newCandidates",
                    allStatusCounts.getOrDefault(JobApplication.ApplicationStatus.APPLIED, 0L) +
                            allStatusCounts.getOrDefault(JobApplication.ApplicationStatus.UNDER_REVIEW, 0L));
            model.addAttribute("interviewScheduled",
                    allStatusCounts.getOrDefault(JobApplication.ApplicationStatus.INTERVIEW_SCHEDULED, 0L));
            model.addAttribute("hiredCount", allStatusCounts.getOrDefault(JobApplication.ApplicationStatus.HIRED, 0L));

            // Get upcoming interviews for the next 7 days
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime weekFromNow = now.plusDays(7);
            List<JobApplication> upcomingInterviews = jobApplicationService
                    .findUpcomingInterviewsForRecruiter(
                            recruiter.getId(),
                            now,
                            weekFromNow)
                    .stream()
                    .sorted(Comparator.comparing(JobApplication::getInterviewDate))
                    .collect(Collectors.toList());
            model.addAttribute("upcomingInterviews", upcomingInterviews);

            return "recruiter/dashboard";
        } catch (Exception e) {
            e.printStackTrace();
            model.addAttribute("error", "Error loading dashboard: " + e.getMessage());
            return "error";
        }
    }

    /**
     * View all interviews
     */
    @GetMapping("/interviews")
    public String viewInterviews(
            Model model,
            Principal principal,
            HttpServletRequest request) {

        try {
            if (principal == null) {
                return "redirect:/auth/login?error=not_authenticated";
            }

            // Add current path for active menu highlighting
            String requestURI = request.getRequestURI();
            model.addAttribute("currentPath", requestURI);

            String username = principal.getName();
            Recruiter recruiter = recruiterRepository.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("Recruiter not found"));

            // Get all upcoming interviews (next 3 months) sorted by date
            LocalDateTime now = LocalDateTime.now();
            List<JobApplication> upcomingInterviews = jobApplicationService
                    .findUpcomingInterviewsForRecruiter(
                            recruiter.getId(),
                            now,
                            now.plusMonths(3)) // Show next 3 months of interviews
                    .stream()
                    .sorted(Comparator.comparing(JobApplication::getInterviewDate))
                    .collect(Collectors.toList());

            // Calculate interview statistics
            Map<String, Long> interviewStats = jobApplicationService.getInterviewStats(recruiter.getId());

            // Add all necessary attributes to the model
            model.addAttribute("recruiter", recruiter);
            model.addAttribute("currentUser", recruiter);
            model.addAttribute("upcomingInterviews", upcomingInterviews);
            model.addAttribute("interviewStats", interviewStats);
            model.addAttribute("now", now);

            // Add interview count for the notification badge
            model.addAttribute("upcomingInterviewCount", upcomingInterviews.size());

            return "recruiter/interviews";

        } catch (Exception e) {
            log.error("Error loading interviews", e);
            model.addAttribute("error", "Error loading interviews: " + e.getMessage());
            return "error";
        }
    }

    @GetMapping("/applications")
    public String viewApplications(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String jobId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Model model,
            Principal principal,
            HttpServletRequest request) {

        try {
            if (principal == null) {
                return "redirect:/auth/login?error=not_authenticated";
            }

            // Add current path for active menu highlighting
            String requestURI = request.getRequestURI();
            model.addAttribute("currentPath", requestURI);

            String username = principal.getName();
            Recruiter recruiter = recruiterRepository.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("Recruiter not found"));

            // Build criteria
            JobApplication.ApplicationStatus statusEnum = null;
            if (status != null && !status.isEmpty()) {
                try {
                    statusEnum = JobApplication.ApplicationStatus.valueOf(status);
                } catch (IllegalArgumentException e) {
                    // Invalid status, ignore
                }
            }

            // Get applications with filters
            Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "appliedAt"));
            Page<JobApplication> applicationsPage = jobApplicationService.getApplicationsByRecruiterId(
                    recruiter.getId(), jobId, statusEnum, pageable);

            // Add attributes to model
            model.addAttribute("recruiter", recruiter);
            model.addAttribute("currentUser", recruiter);
            model.addAttribute("applications", applicationsPage.getContent());
            model.addAttribute("totalItems", applicationsPage.getTotalElements());
            model.addAttribute("totalPages", applicationsPage.getTotalPages());
            model.addAttribute("currentPage", page);
            model.addAttribute("pageSize", size);
            model.addAttribute("statusFilter", status);
            model.addAttribute("jobId", jobId);

            // Add job list for filter dropdown
            List<Job> jobs = jobService.getJobsByRecruiter(recruiter);
            model.addAttribute("jobs", jobs);

            return "recruiter/applications";
        } catch (Exception e) {
            e.printStackTrace();
            return "redirect:/recruiter/dashboard?error=" + e.getMessage();
        }
    }

    @GetMapping("/applications/{id}")
    public String viewApplication(@PathVariable String id, Model model, Principal principal,
            RedirectAttributes redirectAttributes) {
        try {
            if (id == null || id.trim().isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Application ID cannot be empty");
                return "redirect:/recruiter/applications";
            }

            // Get the current user
            String username = principal.getName();
            Recruiter recruiter = recruiterRepository.findByUsername(username)
                    .orElseThrow(() -> {
                        log.error("Recruiter not found for username: {}", username);
                        return new RuntimeException("Recruiter not found");
                    });

            // Find the application
            JobApplication application = null;

            try {
                // First try to find by application ID (handles both ObjectId and email lookups)
                Optional<JobApplication> applicationOpt = jobApplicationService.getApplicationById(id);

                if (applicationOpt.isPresent()) {
                    application = applicationOpt.get();
                } else {
                    // If not found by ID, try to find by candidate ID or email
                    applicationOpt = jobApplicationService.findByCandidateId(id);
                    if (applicationOpt.isPresent()) {
                        application = applicationOpt.get();
                    }
                }
            } catch (IllegalArgumentException e) {
                log.error("Invalid ID format provided: {}", id);
                redirectAttributes.addFlashAttribute("error", "Invalid application ID format");
                return "redirect:/recruiter/applications";
            } catch (Exception e) {
                log.error("Error looking up application: {}", e.getMessage());
                redirectAttributes.addFlashAttribute("error", "Error looking up application: " + e.getMessage());
                return "redirect:/recruiter/applications";
            }

            if (application == null) {
                redirectAttributes.addFlashAttribute("error", "Application not found");
                return "redirect:/recruiter/applications";
            }

            // Verify recruiter has access to this application
            if (application.getJob() == null) {
                log.error("Job is null for application ID: {}", application.getId());
                return "redirect:/recruiter/applications?error=Invalid+job+data";
            }

            if (application.getJob().getPostedBy() == null) {
                // Try to fix missing postedBy if the current recruiter is the owner (fallback)
                log.warn("Job postedBy is null for job ID: {}. Checking if current recruiter owns it.",
                        application.getJob().getId());
                return "redirect:/recruiter/applications?error=Invalid+job+poster+data";
            }

            String jobPosterId;
            try {
                // Safe access to ID which might trigger lazy loading validation
                jobPosterId = application.getJob().getPostedBy().getId();
            } catch (IllegalArgumentException e) {
                log.error("Invalid recruiter ID format in job reference: {}", e.getMessage());
                return "redirect:/recruiter/applications?error=Invalid+recruiter+ID+format";
            } catch (Exception e) {
                log.error("Error accessing job poster ID: {}", e.getMessage());
                return "redirect:/recruiter/applications?error=Error+validating+access";
            }

            if (jobPosterId == null || !jobPosterId.equals(recruiter.getId())) {
                return "redirect:/recruiter/applications?error=Unauthorized+access";
            }

            // Log the application data being added to the model
            log.info("Adding application to model - ID: {}, Status: {}", application.getId(), application.getStatus());

            // Add application and related data to the model
            model.addAttribute("application", application);
            model.addAttribute("appliedAt", application.getAppliedAt());
            model.addAttribute("currentUser", recruiter);
            model.addAttribute("applicationId", application.getId()); // Add ID as a separate attribute for easy access

            // Add candidate details to the model
            if (application.getCandidate() != null) {
                model.addAttribute("candidate", application.getCandidate());
                model.addAttribute("candidateFullName", application.getCandidate().getFullName());
                model.addAttribute("candidateEmail", application.getEmail() != null ? application.getEmail()
                        : (application.getCandidate().getEmail() != null ? application.getCandidate().getEmail() : ""));
            }

            if (application.getJob() != null) {
                model.addAttribute("job", application.getJob());
                model.addAttribute("jobTitle", application.getJob().getJobTitle());
            }

            // Log the model attributes for debugging
            log.debug("Model attributes - Application ID: {}, Status: {}",
                    application.getId(), application.getStatus());

            // Add candidate details to the model
            Candidate candidate = null;
            String candidateId = null;

            // Try to get candidate ID from different possible sources
            if (application.getCandidate() != null && application.getCandidate().getId() != null) {
                candidateId = application.getCandidate().getId();
            } else if (application.getCandidateId() != null && !application.getCandidateId().isEmpty()) {
                candidateId = application.getCandidateId();
            }

            // Add status history if available
            if (application.getStatusHistory() != null && !application.getStatusHistory().isEmpty()) {
                // Sort status history by date (newest first)
                List<JobApplication.ApplicationStatusHistory> sortedHistory = new ArrayList<>(
                        application.getStatusHistory());
                model.addAttribute("statusHistory", sortedHistory);
            }

            if (candidateId != null) {
                try {
                    // Fetch the complete candidate details
                    Optional<Candidate> candidateOpt = candidateService.getCandidateById(candidateId);

                    if (candidateOpt.isPresent()) {
                        candidate = candidateOpt.get();
                        // Update the application with the complete candidate details
                        application.setCandidate(candidate);

                        // Update application with candidate details if missing
                        // If application is missing contact info but candidate has it, update the
                        // application
                        if ((application.getFullName() == null || application.getFullName().isEmpty())
                                && candidate.getFullName() != null) {
                            application.setFullName(candidate.getFullName());
                        }
                        if ((application.getEmail() == null || application.getEmail().isEmpty())
                                && candidate.getEmail() != null) {
                            application.setEmail(candidate.getEmail());
                        }
                        if ((application.getPhone() == null || application.getPhone().isEmpty())
                                && candidate.getPhoneNumber() != null) {
                            application.setPhone(candidate.getPhoneNumber());
                        }

                        // Save the candidate ID separately for easier access
                        application.setCandidateId(candidateId);
                    } else {
                        System.err.println("Candidate not found with ID: " + candidateId);
                    }
                } catch (Exception e) {
                    System.err.println("Error loading candidate details: " + e.getMessage());
                    e.printStackTrace();
                }
            } else {
                System.err.println("No candidate ID found in the application");
            }

            // Sort status history by date (newest first), handling null values
            if (application.getStatusHistory() != null) {
                try {
                    application.getStatusHistory().sort((h1, h2) -> {
                        // Handle null updatedAt values by placing them at the end
                        if (h1 == null || h1.getUpdatedAt() == null) {
                            return (h2 == null || h2.getUpdatedAt() == null) ? 0 : 1;
                        }
                        if (h2 == null || h2.getUpdatedAt() == null) {
                            return -1;
                        }
                        return h2.getUpdatedAt().compareTo(h1.getUpdatedAt());
                    });
                    log.debug("Successfully sorted status history for application: {}", application.getId());
                } catch (Exception e) {
                    log.error("Error sorting status history for application {}: {}",
                            application.getId(), e.getMessage(), e);
                    // Continue without sorting if there's an error
                }
            }

            // Create a new map with the data we want to pass to the view
            Map<String, Object> modelMap = new HashMap<>();

            // Add basic application info
            modelMap.put("applicationId", application.getId());
            modelMap.put("fullName", application.getFullName());
            modelMap.put("email", application.getEmail());
            modelMap.put("phone", application.getPhone());
            modelMap.put("resumePath", application.getResumePath());
            modelMap.put("coverLetter", application.getCoverLetter());
            modelMap.put("appliedAt", application.getAppliedAt());
            modelMap.put("status", application.getStatus());

            // Add candidate info if available
            if (application.getCandidate() != null) {
                modelMap.put("candidateFullName", application.getCandidate().getFullName());
                modelMap.put("candidateEmail", application.getCandidate().getEmail());
                modelMap.put("candidatePhone", application.getCandidate().getPhoneNumber());
                modelMap.put("candidateSkills", application.getCandidate().getSkills());
                modelMap.put("candidateLocation", application.getCandidate().getLocation());
                if (application.getCandidate().getResume() != null) {
                    modelMap.put("resumeUrl", application.getCandidate().getResumeUrl());
                }
            }

            // Add job details to the model with proper error handling
            try {
                if (application.getJob() != null) {
                    Job job = application.getJob();
                    System.out.println("Loading job details for job ID: " + job.getId());

                    // Basic job information
                    modelMap.put("jobId", job.getId());
                    modelMap.put("jobTitle", job.getJobTitle());
                    modelMap.put("jobDescription", job.getDescription());
                    modelMap.put("jobLocation", job.getLocation());
                    modelMap.put("jobType", job.getJobType() != null ? job.getJobType().name() : "Not specified");

                    // Salary information
                    modelMap.put("minSalary", job.getMinSalary());
                    modelMap.put("maxSalary", job.getMaxSalary());

                    // Skills and experience
                    modelMap.put("skills", job.getSkills() != null ? job.getSkills() : "Not specified");
                    modelMap.put("experience", job.getExperience() != null ? job.getExperience() : "Not specified");

                    // Company and posting info
                    modelMap.put("companyName", job.getCompanyName() != null ? job.getCompanyName() : "Not specified");
                    modelMap.put("postedAt", job.getPostedAt() != null ? job.getPostedAt() : "N/A");
                    modelMap.put("lastModifiedAt", job.getLastModifiedAt() != null ? job.getLastModifiedAt() : "N/A");

                    // Job requirements and details
                    modelMap.put("requirements",
                            job.getRequirements() != null ? job.getRequirements() : "No specific requirements listed");

                } else {
                    modelMap.put("jobError", "No job details available for this application");
                }
            } catch (Exception e) {
                System.err.println("Error loading job details: " + e.getMessage());
                e.printStackTrace();
                modelMap.put("jobError", "Error loading job details: " + e.getMessage());
            }

            // Create and populate appDetails DTO
            ApplicationDetailsDto appDetails = ApplicationDetailsDto.fromJobApplication(application);
            modelMap.put("appDetails", appDetails);

            // Add the map to the model
            model.addAllAttributes(modelMap);

            // Also add the original objects for backward compatibility
            model.addAttribute("application", application);
            model.addAttribute("job", application.getJob()); // Add job object directly
            model.addAttribute("recruiter", recruiter);
            model.addAttribute("currentUser", recruiter);

            return "recruiter/application-details";
        } catch (IllegalArgumentException e) {
            log.error("Invalid ID format in viewApplication: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Invalid ID format: " + e.getMessage());
            return "redirect:/recruiter/applications";
        } catch (Exception e) {
            System.err.println("Error in viewApplication: " + e.getMessage());
            e.printStackTrace();
            return "redirect:/recruiter/applications?error="
                    + URLEncoder.encode(e.getMessage(), StandardCharsets.UTF_8);
        }
    }

    @PostMapping("/applications/{id}/status")
    public String updateApplicationStatus(
            @PathVariable String id,
            @RequestParam JobApplication.ApplicationStatus status,
            @RequestParam(required = false) String notes,
            Principal principal,
            RedirectAttributes redirectAttributes) {

        log.info("Received status update request - ID: {}, Status: {}", id, status);

        try {
            if (principal == null) {
                log.warn("Unauthenticated access attempt to update application status");
                return "redirect:/auth/login?error=not_authenticated";
            }

            // Validate application ID
            if (id == null || id.trim().isEmpty() || id.equals("appId")) {
                log.warn("Invalid application ID provided for status update: {}", id);
                redirectAttributes.addFlashAttribute("error", "Application ID is invalid");
                return "redirect:/recruiter/applications";
            }

            // Check for common template literals or invalid formats
            if (id.equals("application.id") || id.equals("${application.id}") || id.contains("{")) {
                log.error("Template literal detected in application ID: {}", id);
                redirectAttributes.addFlashAttribute("error", "Invalid application ID format");
                return "redirect:/recruiter/applications";
            }

            // Get the current user
            String username = principal.getName();
            Recruiter recruiter = recruiterRepository.findByUsername(username)
                    .orElseThrow(() -> {
                        log.error("Recruiter not found for username: {}", username);
                        return new RuntimeException("Recruiter not found");
                    });

            log.debug("Recruiter {} attempting to update status for application ID: {}", username, id);

            // Find the application
            Optional<JobApplication> applicationOpt = jobApplicationService.getApplicationById(id);
            if (!applicationOpt.isPresent()) {
                log.warn("Application not found - ID: {}", id);
                redirectAttributes.addFlashAttribute("error", "Application not found");
                return "redirect:/recruiter/applications";
            }

            JobApplication application = applicationOpt.get();
            log.info("Found application - ID: {}, Current Status: {}", application.getId(), application.getStatus());

            // Verify job data is present
            if (application.getJob() == null) {
                log.error("Job is null for application ID: {}", application.getId());
                // Instead of failing, we'll continue but log the issue
                // The status update might still be valid even if job data is missing
                log.warn("Proceeding with status update despite missing job data");
            } else if (application.getJob().getPostedBy() != null) {
                // Verify recruiter has access to this application only if job poster exists
                String jobPosterId = application.getJob().getPostedBy().getId();
                if (jobPosterId == null || !jobPosterId.equals(recruiter.getId())) {
                    log.warn("Unauthorized access attempt. Recruiter ID: {}, Job Poster ID: {}",
                            recruiter.getId(), jobPosterId);
                    redirectAttributes.addFlashAttribute("error", "You are not authorized to update this application");
                    return "redirect:/recruiter/applications";
                }
            } else {
                log.warn("Job poster is null for job ID: {}", application.getJob().getId());
            }

            log.info("Updating application status - Application ID: {}, Status: {}, Updated By: {}",
                    id, status, username);

            try {
                // Save the old status for the event
                JobApplication.ApplicationStatus oldStatus = application.getStatus();

                // Update the application status
                application.setStatus(status);
                application.setUpdatedAt(LocalDateTime.now());
                application.setUpdatedBy(recruiter.getId());

                // Add to status history
                if (application.getStatusHistory() == null) {
                    application.setStatusHistory(new ArrayList<>());
                }
                application.getStatusHistory().add(new JobApplication.ApplicationStatusHistory(
                        status,
                        notes,
                        recruiter.getId(),
                        LocalDateTime.now()));

                // Save the updated application
                jobApplicationRepository.save(application);
                log.info("Successfully updated application status - ID: {}, New Status: {}", id, status);

                // Publish status update event
                try {
                    String jobTitle = application.getJob() != null ? application.getJob().getJobTitle() : "Unknown Job";
                    applicationEventPublisher.publishEvent(new ApplicationStatusEvent(
                            application.getId(),
                            application.getJob() != null ? application.getJob().getId() : null,
                            application.getCandidateId(),
                            oldStatus,
                            status,
                            recruiter.getId(),
                            notes,
                            jobTitle,
                            LocalDateTime.now()));
                    log.debug("Published status update event for application ID: {}", id);
                } catch (Exception e) {
                    log.error("Error publishing status update event for application {}: {}", id, e.getMessage(), e);
                    // Don't fail the entire operation if event publishing fails
                }

                redirectAttributes.addFlashAttribute("success", "Application status updated successfully");
                return "redirect:/recruiter/applications/" + id;

            } catch (Exception e) {
                log.error("Error updating application status - ID: {}, Error: {}", id, e.getMessage(), e);
                String errorMessage = "Error updating status: " +
                        (e.getMessage() != null ? e.getMessage() : "Unknown error occurred");
                redirectAttributes.addFlashAttribute("error", errorMessage);
                return "redirect:/recruiter/applications/" + id;
            }

        } catch (Exception e) {
            log.error("Unexpected error in updateApplicationStatus - ID: {}, Error: {}",
                    id, e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error",
                    "An unexpected error occurred while updating the application status");
            return "redirect:/recruiter/applications";
        }
    }

    @PostMapping("/applications/{id}/notes")
    public String addApplicationNote(
            @PathVariable String id,
            @RequestParam String note,
            Principal principal,
            RedirectAttributes redirectAttributes) {

        try {
            if (principal == null) {
                return "redirect:/auth/login?error=not_authenticated";
            }

            String username = principal.getName();
            Recruiter recruiter = recruiterRepository.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("Recruiter not found"));

            // Add note to application
            jobApplicationService.addNoteToApplication(id, note, recruiter.getId());

            redirectAttributes.addFlashAttribute("success", "Note added successfully!");
            return "redirect:/recruiter/applications/" + id;

        } catch (Exception e) {
            log.error("Error adding note to application - ID: {}, Error: {}", id, e.getMessage(), e);
            String errorMessage = "Error adding note: " +
                    (e.getMessage() != null ? e.getMessage() : "Unknown error occurred");
            redirectAttributes.addFlashAttribute("error", errorMessage);
            return "redirect:/recruiter/applications/" + id;
        }
    }

    @PostMapping("/applications/{id}/schedule-interview")
    public String scheduleInterview(
            @PathVariable String id,
            @RequestParam("interviewDate") @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) LocalDateTime interviewDate,
            @RequestParam("interviewType") String interviewType,
            @RequestParam("location") String location,
            @RequestParam(value = "instructions", required = false) String instructions,
            Principal principal,
            RedirectAttributes redirectAttributes) {

        try {
            log.info("Scheduling interview for application {} with date {}", id, interviewDate);

            if (principal == null) {
                return "redirect:/auth/login?error=not_authenticated";
            }

            String username = principal.getName();
            Recruiter recruiter = recruiterRepository.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("Recruiter not found"));

            // Find the application
            JobApplication application = jobApplicationService.getApplicationById(id)
                    .orElseThrow(() -> new RuntimeException("Application not found"));

            // Update application with interview details
            application.setInterviewDate(interviewDate);
            application.setInterviewType(interviewType);
            application.setInterviewLocation(location);
            application.setInterviewNotes(instructions);

            // Update status to INTERVIEW_SCHEDULED
            application.setStatus(JobApplication.ApplicationStatus.INTERVIEW_SCHEDULED);
            application.setUpdatedAt(LocalDateTime.now());
            application.setUpdatedBy(recruiter.getId());

            // Add to status history
            if (application.getStatusHistory() == null) {
                application.setStatusHistory(new ArrayList<>());
            }
            application.getStatusHistory().add(new JobApplication.ApplicationStatusHistory(
                    JobApplication.ApplicationStatus.INTERVIEW_SCHEDULED,
                    "Interview scheduled for " + interviewDate,
                    recruiter.getId(),
                    LocalDateTime.now()));

            jobApplicationRepository.save(application);
            log.info("Interview scheduled successfully for application {}", id);

            redirectAttributes.addFlashAttribute("success", "Interview scheduled successfully!");
            return "redirect:/recruiter/applications/" + id;

        } catch (Exception e) {
            log.error("Error scheduling interview for application {}", id, e);
            redirectAttributes.addFlashAttribute("error", "Error scheduling interview: " + e.getMessage());
            return "redirect:/recruiter/applications/" + id;
        }
    }

    @GetMapping("/search-candidates")
    public String searchCandidates(
            @RequestParam(value = "q", required = false) String query,
            @RequestParam(value = "location", required = false) String location,
            @RequestParam(value = "skills", required = false) List<String> skills,
            @RequestParam(value = "uploadedBefore", required = false) String uploadedBefore,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            Model model,
            Principal principal,
            HttpServletRequest request) {

        try {
            if (principal == null) {
                return "redirect:/auth/login?error=not_authenticated";
            }

            // Add current path for active menu highlighting
            String requestURI = request.getRequestURI();
            model.addAttribute("currentPath", requestURI);

            // Get current recruiter
            String username = principal.getName();
            Recruiter recruiter = recruiterRepository.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("Recruiter not found"));

            // Create search criteria
            ResumeSearchCriteria criteria = ResumeSearchCriteria.builder()
                    .keyword(query).build();
            // .city(location)
            // .state(location)
            // .uploadedBefore(uploadedBefore)
            // .build();

            // Set skills if provided
            // if (skills != null && !skills.isEmpty()) {
            // criteria.setProgrammingLanguages(skills);
            // criteria.setFrameworks(skills);
            // }
            //
            // // Set individual fields for better search
            // if (query != null) {
            // criteria.setFullName(query);
            // criteria.setCompanyName(query);
            // criteria.setJobTitle(query);
            // criteria.setInstitution(query);
            // criteria.setDegree(query);
            // }

            // Perform search
            List<Resume> searchResults = resumeService.searchResumes(criteria);

            // Create pagination
            Pageable pageable = PageRequest.of(page, size);
            int start = (int) pageable.getOffset();
            int end = Math.min((start + pageable.getPageSize()), searchResults.size());
            Page<Resume> resumePage = new PageImpl<>(
                    searchResults.subList(start, end),
                    pageable,
                    searchResults.size());

            // Add recruiter info
            model.addAttribute("recruiter", recruiter);
            model.addAttribute("currentUser", recruiter);
            model.addAttribute("username", username);
            model.addAttribute("fullName", recruiter.getFullName());
            model.addAttribute("email", recruiter.getEmail());
            model.addAttribute("companyName", recruiter.getCompany());
            model.addAttribute("isRecruiter", true);

            // Add search results and pagination info
            model.addAttribute("candidates", resumePage.getContent());
            model.addAttribute("totalItems", resumePage.getTotalElements());
            model.addAttribute("totalPages", resumePage.getTotalPages());
            model.addAttribute("currentPage", page);
            model.addAttribute("pageSize", size);
            model.addAttribute("query", query != null ? query : "");
            model.addAttribute("location", location);
            model.addAttribute("skills", skills);

            return "recruiter/search-results";
        } catch (Exception e) {
            e.printStackTrace();
            return "redirect:/auth/login?error=access_denied";
        }
    }

    @GetMapping("/jobs")
    public String jobs(Model model, Principal principal, HttpServletRequest request) {
        try {
            if (principal == null) {
                return "redirect:/auth/login?error=not_authenticated";
            }

            // Add current path for active menu highlighting
            String requestURI = request.getRequestURI();
            model.addAttribute("currentPath", requestURI);

            String username = principal.getName();
            Recruiter recruiter = recruiterRepository.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("Recruiter not found"));

            List<Job> jobs = jobService.getJobsByRecruiter(recruiter);

            model.addAttribute("jobs", jobs);
            model.addAttribute("recruiter", recruiter);
            model.addAttribute("currentUser", recruiter);
            model.addAttribute("username", username);
            model.addAttribute("fullName", recruiter.getFullName());

            return "recruiter/jobs";
        } catch (Exception e) {
            e.printStackTrace();
            return "redirect:/recruiter/dashboard?error=" + e.getMessage();
        }
    }

    @GetMapping("/candidates")
    public String candidates() {
        return "recruiter/candidates";
    }

    @GetMapping("/profile")
    public String profile(Model model, Principal principal, HttpServletRequest request) {
        try {
            if (principal == null) {
                return "redirect:/auth/login?error=not_authenticated";
            }

            // Add current path for active menu highlighting
            String requestURI = request.getRequestURI();
            model.addAttribute("currentPath", requestURI);

            String username = principal.getName();
            Recruiter recruiter = recruiterRepository.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("Recruiter not found"));

            model.addAttribute("recruiter", recruiter);
            model.addAttribute("currentUser", recruiter);
            model.addAttribute("username", username);
            model.addAttribute("fullName", recruiter.getFullName());
            model.addAttribute("email", recruiter.getEmail());
            model.addAttribute("companyName", recruiter.getCompany());
            model.addAttribute("website", recruiter.getWebsite());
            model.addAttribute("isRecruiter", true);

            return "recruiter/profile";
        } catch (Exception e) {
            e.printStackTrace();
            return "redirect:/auth/login?error=access_denied";
        }
    }

    @PostMapping("/profile/update")
    public String updateProfile(@ModelAttribute("recruiter") Recruiter recruiterDetails,
            Principal principal,
            RedirectAttributes redirectAttributes) {
        try {
            if (principal == null) {
                return "redirect:/auth/login?error=not_authenticated";
            }

            String username = principal.getName();
            Recruiter recruiter = recruiterRepository.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("Recruiter not found"));

            // Update recruiter details
            recruiter.setFullName(recruiterDetails.getFullName());
            recruiter.setEmail(recruiterDetails.getEmail());
            recruiter.setCompany(recruiterDetails.getCompany());
            recruiter.setPhoneNumber(recruiterDetails.getPhoneNumber());
            recruiter.setWebsite(recruiterDetails.getWebsite());
            recruiter.setCompanyDescription(recruiterDetails.getCompanyDescription());

            recruiterRepository.save(recruiter);

            redirectAttributes.addFlashAttribute("success", "Profile updated successfully!");
            return "redirect:/recruiter/profile";

        } catch (Exception e) {
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("error", "Error updating profile: " + e.getMessage());
            return "redirect:/recruiter/profile";
        }
    }

    @PostMapping("/profile/change-password")
    public String changePassword(@RequestParam String currentPassword,
            @RequestParam String newPassword,
            @RequestParam String confirmPassword,
            Principal principal,
            RedirectAttributes redirectAttributes) {
        try {
            if (principal == null) {
                return "redirect:/auth/login?error=not_authenticated";
            }

            // Add your password change logic here
            // 1. Verify current password
            // 2. Check if new password and confirm password match
            // 3. Update the password

            // For now, just show a success message
            redirectAttributes.addFlashAttribute("success", "Password changed successfully!");
            return "redirect:/recruiter/profile";

        } catch (Exception e) {
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("error", "Error changing password: " + e.getMessage());
            return "redirect:/recruiter/profile";
        }
    }
}
