package com.tal.pro.controller;

import com.tal.pro.criteria.ResumeSearchCriteria;
import com.tal.pro.exception.ResourceNotFoundException;
import com.tal.pro.model.Job;
import com.tal.pro.model.JobApplication;
import com.tal.pro.model.Recruiter;
import com.tal.pro.model.Resume;
import com.tal.pro.repository.RecruiterRepository;
import com.tal.pro.service.JobApplicationService;
import com.tal.pro.service.JobService;
import com.tal.pro.service.ResumeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.util.List;
import java.util.Map;
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
            if (principal == null) {
                return "redirect:/auth/login?error=not_authenticated";
            }
            
            String username = principal.getName();
            Recruiter recruiter = recruiterRepository.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("Recruiter not found"));
            
            // Get application with status history
            JobApplication application = jobApplicationService.getApplicationById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Application not found"));
            
            // Verify recruiter has access to this application
            if (!application.getJob().getPostedBy().getId().equals(recruiter.getId())) {
                throw new SecurityException("You don't have permission to view this application");
            }
            
            // Sort status history by date (newest first)
            if (application.getStatusHistory() != null) {
                application.getStatusHistory().sort((h1, h2) -> 
                    h2.getChangedAt().compareTo(h1.getChangedAt()));
            }
            
            model.addAttribute("application", application);
            model.addAttribute("recruiter", recruiter);
            model.addAttribute("currentUser", recruiter);
            
            return "recruiter/application-details";
        } catch (Exception e) {
            e.printStackTrace();
            return "redirect:/recruiter/applications?error=" + e.getMessage();
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
}
