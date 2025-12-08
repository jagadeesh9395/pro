package com.tal.pro.controller;

import com.tal.pro.model.Candidate;
import com.tal.pro.model.Job;
import com.tal.pro.model.JobApplication;
import com.tal.pro.repository.CandidateRepository;
import com.tal.pro.service.JobApplicationService;
import com.tal.pro.service.JobService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.*;
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
    public String dashboard(Model model, Principal principal, HttpServletRequest request,
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
                        pageable);
            } else {
                // Get all active jobs if no search criteria
                jobsPage = jobService.getAllActiveJobs(pageable);
            }

            // Get candidate's applications with job details and status history
            List<JobApplication> applications = jobApplicationService.getApplicationsByCandidateId(candidate.getId());

            // Get upcoming interviews for the next 7 days
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime weekFromNow = now.plusDays(7);
            List<JobApplication> upcomingInterviews = jobApplicationService
                    .findUpcomingInterviewsForCandidate(
                            candidate.getId(),
                            now,
                            weekFromNow)
                    .stream()
                    .sorted(Comparator.comparing(JobApplication::getInterviewDate))
                    .collect(Collectors.toList());

            // Log the number of applications found for debugging
            System.out.println("Found " + applications.size() + " applications for candidate: " + candidate.getId());
            System.out.println(
                    "Found " + upcomingInterviews.size() + " upcoming interviews for candidate: " + candidate.getId());

            applications.forEach(app -> {
                System.out.println("Application ID: " + app.getId() +
                        ", Job: " + (app.getJob() != null ? app.getJob().getJobTitle() : "No Job") +
                        ", Status: " + (app.getStatus() != null ? app.getStatus().name() : "No Status"));

                // Sort status history by change date (newest first), handling null values
                if (app.getStatusHistory() != null) {
                    try {
                        app.getStatusHistory().sort((h1, h2) -> {
                            // Handle null history items
                            if (h1 == null && h2 == null)
                                return 0;
                            if (h1 == null)
                                return 1; // nulls last
                            if (h2 == null)
                                return -1; // nulls last

                            // Handle null timestamps
                            if (h1.getUpdatedAt() == null && h2.getUpdatedAt() == null)
                                return 0;
                            if (h1.getUpdatedAt() == null)
                                return 1; // nulls last
                            if (h2.getUpdatedAt() == null)
                                return -1; // nulls last

                            // Safe to compare since we've handled null cases
                            return h2.getUpdatedAt().compareTo(h1.getUpdatedAt());
                        });
                        System.out.println("Successfully sorted status history for application: " + app.getId());
                    } catch (Exception e) {
                        System.err.println(
                                "Error sorting status history for application " + app.getId() + ": " + e.getMessage());
                        e.printStackTrace();
                        // Continue with unsorted list if there's an error
                    }
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
                System.out.println("Filtered out " + (applications.size() - recentApplications.size())
                        + " applications with null appliedAt");
            }

            // Add status display names to model
            Map<String, String> statusDisplayMap = new java.util.LinkedHashMap<>();
            for (JobApplication.ApplicationStatus status : JobApplication.ApplicationStatus.values()) {
                statusDisplayMap.put(status.name(), status.getDisplayName());
            }
            // Convert to JSON string manually to avoid Thymeleaf template issues
            String statusDisplayJson = "{" + statusDisplayMap.entrySet().stream()
                    .map(e -> String.format("\"%s\":\"%s\"", e.getKey(), e.getValue()))
                    .collect(Collectors.joining(",")) + "}";

            model.addAttribute("statusDisplayNames", statusDisplayJson);
            model.addAttribute("fullName", candidate.getFullName());

            // Get the current request path for navigation highlighting
            String currentPath = request.getRequestURI();

            // Add data to the model
            model.addAttribute("candidate", candidate);
            model.addAttribute("currentUser", candidate);
            model.addAttribute("jobs", jobsPage.getContent());
            model.addAttribute("currentPage", page);
            model.addAttribute("totalPages", jobsPage.getTotalPages());
            model.addAttribute("totalItems", jobsPage.getTotalElements());
            model.addAttribute("pageSize", size);
            model.addAttribute("recentApplications", applications);
            model.addAttribute("upcomingInterviews", upcomingInterviews);
            model.addAttribute("now", now);
            model.addAttribute("currentPath", currentPath);

            // Add applications data
            model.addAttribute("recentApplications", recentApplications);
            model.addAttribute("totalApplications", applications.size());

            // Add job search results with pagination
            model.addAttribute("jobs", jobsPage.getContent());
            model.addAttribute("currentPage", jobsPage.getNumber());
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

            // Find upcoming interview
            JobApplication nextInterview = applications.stream()
                    .filter(app -> app.getStatus() == JobApplication.ApplicationStatus.INTERVIEW_SCHEDULED
                            && app.getInterviewDate() != null
                            && app.getInterviewDate().isAfter(java.time.LocalDateTime.now()))
                    .sorted((a1, a2) -> a1.getInterviewDate().compareTo(a2.getInterviewDate()))
                    .findFirst()
                    .orElse(null);

            if (nextInterview != null) {
                model.addAttribute("upcomingInterview", nextInterview);
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

    @PostMapping("/profile/update-personal")
    public String updatePersonalInfo(@RequestParam("fullName") String fullName,
            @RequestParam("email") String email,
            @RequestParam("phoneNumber") String phoneNumber,
            @RequestParam("location") String location,
            @RequestParam("experience") String experience,
            Principal principal,
            RedirectAttributes redirectAttributes) {
        try {
            String username = principal.getName();
            Candidate candidate = candidateRepository.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("Candidate not found"));

            // Update personal information
            candidate.setFullName(fullName);
            candidate.setEmail(email);
            candidate.setPhoneNumber(phoneNumber);
            candidate.setLocation(location);
            candidate.setExperience(experience);

            candidateRepository.save(candidate);

            redirectAttributes.addFlashAttribute("success", "Personal information updated successfully!");
            return "redirect:/candidate/profile";
        } catch (Exception e) {
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("error", "Failed to update personal information: " + e.getMessage());
            return "redirect:/candidate/profile#personal-info";
        }
    }

    @PostMapping("/profile/update-skills")
    public String updateSkills(@RequestParam("skills") String skills,
            Principal principal,
            RedirectAttributes redirectAttributes) {
        try {
            String username = principal.getName();
            Candidate candidate = candidateRepository.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("Candidate not found"));

            // Clean up the skills string by removing extra spaces and empty entries
            String cleanedSkills = "";
            if (skills != null && !skills.trim().isEmpty()) {
                cleanedSkills = Arrays.stream(skills.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.joining(", "));
            }

            candidate.setSkills(cleanedSkills);
            candidateRepository.save(candidate);

            redirectAttributes.addFlashAttribute("success", "Skills updated successfully!");
            return "redirect:/candidate/profile#skills";
        } catch (Exception e) {
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("error", "Failed to update skills: " + e.getMessage());
            return "redirect:/candidate/profile#skills";
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

    @GetMapping("/interviews")
    public String viewInterviews(Model model, Principal principal, HttpServletRequest request) {
        try {
            if (principal == null) {
                return "redirect:/auth/login?error=not_authenticated";
            }

            String username = principal.getName();
            Candidate candidate = candidateRepository.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("Candidate not found"));

            // Get all upcoming interviews
            LocalDateTime now = LocalDateTime.now();
            List<JobApplication> upcomingInterviews = jobApplicationService
                    .findUpcomingInterviewsForCandidate(
                            candidate.getId(),
                            now,
                            now.plusMonths(3)) // Show next 3 months of interviews
                    .stream()
                    .sorted(Comparator.comparing(JobApplication::getInterviewDate))
                    .collect(Collectors.toList());

            // Get the current request path for navigation highlighting
            String currentPath = request.getRequestURI();

            model.addAttribute("candidate", candidate);
            model.addAttribute("currentUser", candidate);
            model.addAttribute("upcomingInterviews", upcomingInterviews);
            model.addAttribute("now", now);
            model.addAttribute("currentPath", currentPath);

            // Add interview count for the notification badge
            model.addAttribute("upcomingInterviewCount", upcomingInterviews.size());

            return "candidate/interviews";
        } catch (Exception e) {
            e.printStackTrace();
            return "redirect:/candidate/dashboard?error=error_loading_interviews";
        }
    }

    @GetMapping("/applications/{id}")
    public String viewApplicationDetails(@PathVariable("id") String applicationId,
            Model model,
            Principal principal,
            HttpServletRequest request) {
        try {
            if (principal == null) {
                return "redirect:/auth/login?error=not_authenticated";
            }

            String username = principal.getName();
            Candidate candidate = candidateRepository.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("Candidate not found"));

            // Get the application by ID
            JobApplication application = jobApplicationService.getApplicationById(applicationId)
                    .orElseThrow(() -> new RuntimeException("Application not found"));

            // Verify the application belongs to this candidate
            if (!application.getCandidate().getId().equals(candidate.getId())) {
                return "redirect:/candidate/dashboard?error=unauthorized_access";
            }

            // Sort status history by change date (newest first)
            if (application.getStatusHistory() != null) {
                application.getStatusHistory().sort((h1, h2) -> {
                    if (h1 == null && h2 == null)
                        return 0;
                    if (h1 == null)
                        return 1;
                    if (h2 == null)
                        return -1;
                    if (h1.getUpdatedAt() == null && h2.getUpdatedAt() == null)
                        return 0;
                    if (h1.getUpdatedAt() == null)
                        return 1;
                    if (h2.getUpdatedAt() == null)
                        return -1;
                    return h2.getUpdatedAt().compareTo(h1.getUpdatedAt());
                });
            }

            // Get the current request path for navigation highlighting
            String currentPath = request.getRequestURI();

            model.addAttribute("application", application);
            model.addAttribute("candidate", candidate);
            model.addAttribute("currentUser", candidate);
            model.addAttribute("job", application.getJob());
            model.addAttribute("currentPath", currentPath);
            model.addAttribute("now", LocalDateTime.now());

            return "candidate/application-details";
        } catch (Exception e) {
            e.printStackTrace();
            return "redirect:/candidate/dashboard?error=error_loading_application";
        }
    }

    @PostMapping("/applications/{id}/withdraw")
    public String withdrawApplication(@PathVariable("id") String applicationId,
            Principal principal,
            RedirectAttributes redirectAttributes) {
        try {
            // Get the candidate to verify ownership
            String username = principal.getName();
            Candidate candidate = candidateRepository.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("Candidate not found"));

            // Withdraw the application using the candidate's ID
            jobApplicationService.withdrawApplication(applicationId, candidate.getId());

            redirectAttributes.addFlashAttribute("success", "Application withdrawn successfully!");
            return "redirect:/candidate/dashboard";

        } catch (Exception e) {
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("error", "Failed to withdraw application: " + e.getMessage());
            return "redirect:/candidate/applications/" + applicationId;
        }
    }
}
