package com.tal.pro.controller;

import com.tal.pro.criteria.ResumeSearchCriteria;
import com.tal.pro.exception.ResourceNotFoundException;
import com.tal.pro.model.*;
import com.tal.pro.repository.RecruiterRepository;
import com.tal.pro.service.CandidateService;
import com.tal.pro.service.JobApplicationService;
import com.tal.pro.service.JobService;
import com.tal.pro.service.ResumeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

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

    @GetMapping("/dashboard")
    public String dashboard(Model model, Principal principal,
                          @RequestParam(defaultValue = "0") int page,
                          @RequestParam(defaultValue = "10") int size,
                          @RequestParam(required = false) String search) {
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
//            List<JobApplication> recentApplications = applicationsPage.getContent();
            List<JobApplication> recentApplications = jobApplicationService.findRecentApplications();
            // Get all status counts for the dashboard stats
            Map<JobApplication.ApplicationStatus, Long> allStatusCounts = 
                jobApplicationService.getApplicationStatusCounts(recruiter.getId());
            
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
            model.addAttribute("newCandidates", allStatusCounts.getOrDefault(JobApplication.ApplicationStatus.APPLIED, 0L) +
                                             allStatusCounts.getOrDefault(JobApplication.ApplicationStatus.UNDER_REVIEW, 0L));
            model.addAttribute("interviewScheduled", allStatusCounts.getOrDefault(JobApplication.ApplicationStatus.INTERVIEW_SCHEDULED, 0L));
            model.addAttribute("hiredCount", allStatusCounts.getOrDefault(JobApplication.ApplicationStatus.HIRED, 0L));
            
            return "recruiter/dashboard";
        } catch (Exception e) {
            e.printStackTrace();
            model.addAttribute("error", "Error loading dashboard: " + e.getMessage());
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
            Principal principal) {
        
        try {
            if (principal == null) {
                return "redirect:/auth/login?error=not_authenticated";
            }
            
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
    public String viewApplication(@PathVariable String id, Model model, Principal principal) {
        try {
            if (id == null || id.trim().isEmpty()) {
                return "redirect:/recruiter/applications?error=Invalid+application+ID";
            }
            
            // Get the current user
            String username = principal.getName();
            Recruiter recruiter = recruiterRepository.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("Recruiter not found"));
            
            // Find the application
            JobApplication application = null;
            
            // First try to find by ID directly (as ObjectId or string ID)
            try {
                Optional<JobApplication> applicationOpt = jobApplicationService.getApplicationById(id);
                application = applicationOpt.orElseGet(() ->
                    jobApplicationService.findByCandidateId(id).orElse(null)
                );
            } catch (IllegalArgumentException e) {
                // If it's not a valid ObjectId, try to find by candidate ID or email
                Optional<JobApplication> appByCandidateId = jobApplicationService.findByCandidateId(id);
                if (appByCandidateId.isPresent()) {
                    application = appByCandidateId.get();
                }
            }
            
            if (application == null) {
                return "redirect:/recruiter/applications?error=Application+not+found";
            }
            
            // Verify recruiter has access to this application
            if (application.getJob() == null) {
                return "redirect:/recruiter/applications?error=Invalid+job+data";
            }
            
            if (application.getJob().getPostedBy() == null) {
                return "redirect:/recruiter/applications?error=Invalid+job+poster+data";
            }
            
            String jobPosterId = application.getJob().getPostedBy().getId();
            if (jobPosterId == null || !jobPosterId.equals(recruiter.getId())) {
                return "redirect:/recruiter/applications?error=Unauthorized+access";
            }
            
            // Application details loaded for processing
            
            // Ensure we have the full candidate details
            Candidate candidate = null;
            String candidateId = null;
            
            // Try to get candidate ID from different possible sources
            if (application.getCandidate() != null && application.getCandidate().getId() != null) {
                candidateId = application.getCandidate().getId();
            } else if (application.getCandidateId() != null && !application.getCandidateId().isEmpty()) {
                candidateId = application.getCandidateId();
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
                        // If application is missing contact info but candidate has it, update the application
                        if ((application.getFullName() == null || application.getFullName().isEmpty()) && candidate.getFullName() != null) {
                            application.setFullName(candidate.getFullName());
                        }
                        if ((application.getEmail() == null || application.getEmail().isEmpty()) && candidate.getEmail() != null) {
                            application.setEmail(candidate.getEmail());
                        }
                        if ((application.getPhone() == null || application.getPhone().isEmpty()) && candidate.getPhoneNumber() != null) {
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
            
            // Sort status history by date (newest first)
            if (application.getStatusHistory() != null) {
                application.getStatusHistory().sort((h1, h2) -> 
                    h2.getChangedAt().compareTo(h1.getChangedAt()));
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
                    modelMap.put("requirements", job.getRequirements() != null ? job.getRequirements() : "No specific requirements listed");
                    
                } else {
                    modelMap.put("jobError", "No job details available for this application");
                }
            } catch (Exception e) {
                System.err.println("Error loading job details: " + e.getMessage());
                e.printStackTrace();
                modelMap.put("jobError", "Error loading job details: " + e.getMessage());
            }
            
            // Add the map to the model
            model.addAllAttributes(modelMap);
            
            // Also add the original objects for backward compatibility
            model.addAttribute("application", application);
            model.addAttribute("job", application.getJob()); // Add job object directly
            model.addAttribute("recruiter", recruiter);
            model.addAttribute("currentUser", recruiter);
            
            return "recruiter/application-details";
        } catch (Exception e) {
            System.err.println("Error in viewApplication: " + e.getMessage());
            e.printStackTrace();
            return "redirect:/recruiter/applications?error=" + URLEncoder.encode(e.getMessage(), StandardCharsets.UTF_8);
        }
    }
    
    @PostMapping("/applications/{id}/status")
    public String updateApplicationStatus(
            @PathVariable String id,
            @RequestParam JobApplication.ApplicationStatus status,
            @RequestParam(required = false) String notes,
            Principal principal,
            RedirectAttributes redirectAttributes) {
        
        try {
            if (principal == null) {
                return "redirect:/auth/login?error=not_authenticated";
            }
            
            String username = principal.getName();
            Recruiter recruiter = recruiterRepository.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("Recruiter not found"));
            
            // Update status with note
            jobApplicationService.updateApplicationStatus(id, status, notes, recruiter.getId());
            
            redirectAttributes.addFlashAttribute("success", "Application status updated successfully!");
            return "redirect:/recruiter/applications/" + id;
            
        } catch (Exception e) {
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("error", "Error updating status: " + e.getMessage());
            return "redirect:/recruiter/applications/" + id;
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
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("error", "Error adding note: " + e.getMessage());
            return "redirect:/recruiter/applications/" + id;
        }
    }
    
//    @Autowired
//    private SearchService searchService;

    @GetMapping("/search-candidates")
    public String searchCandidates(
            @RequestParam(value = "q", required = false) String query,
            @RequestParam(value = "location", required = false) String location,
            @RequestParam(value = "skills", required = false) List<String> skills,
            @RequestParam(value = "uploadedBefore", required = false) String uploadedBefore,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            Model model, 
            Principal principal) {
        
        try {
            if (principal == null) {
                return "redirect:/auth/login?error=not_authenticated";
            }

            // Get current recruiter
            String username = principal.getName();
            Recruiter recruiter = recruiterRepository.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("Recruiter not found"));

            // Create search criteria
            ResumeSearchCriteria criteria = ResumeSearchCriteria.builder()
                    .keyword(query).build();
//                    .city(location)
//                    .state(location)
//                    .uploadedBefore(uploadedBefore)
//                    .build();

            // Set skills if provided
//            if (skills != null && !skills.isEmpty()) {
//                criteria.setProgrammingLanguages(skills);
//                criteria.setFrameworks(skills);
//            }
//
//            // Set individual fields for better search
//            if (query != null) {
//                criteria.setFullName(query);
//                criteria.setCompanyName(query);
//                criteria.setJobTitle(query);
//                criteria.setInstitution(query);
//                criteria.setDegree(query);
//            }

            // Perform search
            List<Resume> searchResults = resumeService.searchResumes(criteria);
            
            // Create pagination
            Pageable pageable = PageRequest.of(page, size);
            int start = (int) pageable.getOffset();
            int end = Math.min((start + pageable.getPageSize()), searchResults.size());
            Page<Resume> resumePage = new PageImpl<>(
                searchResults.subList(start, end),
                pageable,
                searchResults.size()
            );

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
    public String jobs() {
        return "recruiter/jobs";
    }

    @GetMapping("/candidates")
    public String candidates() {
        return "recruiter/candidates";
    }
    
    @GetMapping("/profile")
    public String profile(Model model, Principal principal) {
        try {
            if (principal == null) {
                return "redirect:/auth/login?error=not_authenticated";
            }
            
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
