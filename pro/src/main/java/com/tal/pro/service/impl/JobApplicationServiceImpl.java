package com.tal.pro.service.impl;

import com.tal.pro.exception.ResourceNotFoundException;
import com.tal.pro.model.ApplicationStatusHistory;
import com.tal.pro.model.Candidate;
import com.tal.pro.model.Job;
import com.tal.pro.model.JobApplication;
import com.tal.pro.repository.JobApplicationRepository;
import com.tal.pro.service.JobApplicationService;
import com.tal.pro.service.JobService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class JobApplicationServiceImpl implements JobApplicationService {

    private final JobApplicationRepository jobApplicationRepository;
    private final JobService jobService;

    @Autowired
    public JobApplicationServiceImpl(JobApplicationRepository jobApplicationRepository, JobService jobService) {
        this.jobApplicationRepository = jobApplicationRepository;
        this.jobService = jobService;
    }

    @Override
    @Transactional
    public JobApplication submitApplication(String jobId, Candidate candidate, JobApplication application) {
        // Set job and candidate references
        Job job = jobService.getJobById(jobId).orElseThrow(() -> new RuntimeException("Job not found"));
        application.setJob(job);
        application.setCandidate(candidate);
        application.setStatus(JobApplication.ApplicationStatus.APPLIED);
        application.setAppliedAt(LocalDateTime.now());
        application.setUpdatedAt(LocalDateTime.now());
        application.setUpdatedBy(candidate.getId());
        
        // Initialize status history
        application.setStatusHistory(new ArrayList<>());
        addStatusHistory(application, JobApplication.ApplicationStatus.APPLIED, "Application submitted", candidate.getId());

        // Save the application
        return jobApplicationRepository.save(application);
    }

    @Override
    public Optional<JobApplication> getApplicationById(String applicationId) {
        return jobApplicationRepository.findById(applicationId);
    }

    @Override
    public List<JobApplication> getApplicationsByCandidateId(String candidateId) {
        System.out.println("DEBUG: Fetching applications for candidate ID: " + candidateId);
        
        // Fetch applications with job details
        List<JobApplication> applications = jobApplicationRepository.findByCandidateIdOrderByAppliedAtDesc(candidateId);
        
        System.out.println("DEBUG: Found " + applications.size() + " applications for candidate: " + candidateId);
        
        // Eagerly load job details for each application
        applications.forEach(application -> {
            System.out.println("DEBUG: Processing application ID: " + application.getId() + 
                             ", Job ID: " + (application.getJob() != null ? application.getJob().getId() : "null"));
            
            if (application.getJob() != null && application.getJob().getId() != null) {
                System.out.println("DEBUG: Looking up job with ID: " + application.getJob().getId());
                jobService.getJobById(application.getJob().getId())
                    .ifPresentOrElse(
                        job -> {
                            System.out.println("DEBUG: Found job: " + job.getJobTitle());
                            application.setJob(job);
                        },
                        () -> System.out.println("DEBUG: Job not found for ID: " + application.getJob().getId())
                    );
            } else {
                System.out.println("DEBUG: Application has no job reference or job ID is null");
            }
        });
        
        return applications;
    }

    @Override
    public List<JobApplication> getApplicationsByJobId(String jobId) {
        return jobApplicationRepository.findByJobId(jobId);
    }

    @Override
    public boolean hasCandidateApplied(String jobId, String candidateId) {
        return jobApplicationRepository.existsByJobIdAndCandidateId(jobId, candidateId);
    }

    @Override
    @Transactional
    public JobApplication updateApplicationStatus(String applicationId, JobApplication.ApplicationStatus status, String updatedBy) {
        return updateApplicationStatus(applicationId, status, "Status updated", updatedBy);
    }
    
    @Override
    @Transactional
    public JobApplication updateApplicationStatus(String applicationId, JobApplication.ApplicationStatus status, 
                                                String notes, String updatedBy) {
        JobApplication application = jobApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new RuntimeException("Application not found"));

        // Only update if status has changed
        if (application.getStatus() != status) {
            // Add to status history
            addStatusHistory(application, status, notes, updatedBy);
            
            // Update status and timestamps
            application.setStatus(status);
            application.setUpdatedAt(LocalDateTime.now());
            application.setUpdatedBy(updatedBy);
            
            return jobApplicationRepository.save(application);
        }
        
        return application;
    }
    
    /**
     * Adds a new status history entry to the application
     */
    private void addStatusHistory(JobApplication application, JobApplication.ApplicationStatus status, 
                                String notes, String changedBy) {
        if (application.getStatusHistory() == null) {
            application.setStatusHistory(new ArrayList<>());
        }
        
        ApplicationStatusHistory history = new ApplicationStatusHistory();
        history.setStatus(status);
        history.setNotes(notes);
        history.setChangedAt(LocalDateTime.now());
        history.setChangedBy(changedBy);
        
        application.getStatusHistory().add(history);
        
        // Keep only the last 50 status updates to prevent unbounded growth
        if (application.getStatusHistory().size() > 50) {
            application.setStatusHistory(
                application.getStatusHistory().subList(
                    application.getStatusHistory().size() - 50, 
                    application.getStatusHistory().size()
                )
            );
        }
    }

    @Override
    public boolean hasApplied(Candidate candidate, Job job) {
        return jobApplicationRepository.existsByJobIdAndCandidateId(job.getId(), candidate.getId());
    }

    @Override
    @Transactional
    public JobApplication updateApplication(JobApplication application, String updatedBy) {
        // If status has changed, add to history
        JobApplication existing = jobApplicationRepository.findById(application.getId())
                .orElseThrow(() -> new RuntimeException("Application not found"));
                
        if (existing.getStatus() != application.getStatus()) {
            addStatusHistory(application, application.getStatus(), 
                           "Application updated with status: " + application.getStatus().getDisplayName(), 
                           updatedBy);
        }
        
        application.setUpdatedAt(LocalDateTime.now());
        application.setUpdatedBy(updatedBy);
        
        return jobApplicationRepository.save(application);
    }

    @Override
    public Page<JobApplication> getApplicationsByRecruiterId(String recruiterId, Pageable pageable) {
        return jobApplicationRepository.findByJob_PostedById(recruiterId, pageable);
    }

    @Override
    public Page<JobApplication> getApplicationsByRecruiterId(String recruiterId, String jobId, 
                                                          JobApplication.ApplicationStatus status, 
                                                          Pageable pageable) {
        return jobApplicationRepository.findByRecruiterIdAndJobIdAndStatus(
            recruiterId, jobId, status, pageable);
    }
    
    @Override
    @Transactional(readOnly = true)
    public Page<JobApplication> getApplicationsByRecruiterIdWithFilters(
            String recruiterId, String status, String search, Pageable pageable) {
        
        // Convert status string to enum if provided
        JobApplication.ApplicationStatus statusEnum = null;
        if (status != null && !status.isEmpty()) {
            try {
                statusEnum = JobApplication.ApplicationStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException e) {
                // If invalid status is provided, return empty page
                return Page.empty(pageable);
            }
        }
        
        Page<JobApplication> applicationsPage;
        
        // If search term is provided, search in candidate name, email, or job title
        if (search != null && !search.trim().isEmpty()) {
            String searchTerm = search.trim().toLowerCase();
            if (statusEnum != null) {
                applicationsPage = jobApplicationRepository.findByRecruiterIdWithStatusAndSearch(
                    recruiterId, statusEnum, searchTerm, pageable);
            } else {
                applicationsPage = jobApplicationRepository.findByRecruiterIdWithSearch(
                    recruiterId, searchTerm, pageable);
            }
        } else if (statusEnum != null) {
            // Only status filter
            applicationsPage = jobApplicationRepository.findByJob_PostedByIdAndStatus(recruiterId, statusEnum, pageable);
        } else {
            // No filters, return all applications for recruiter
            applicationsPage = jobApplicationRepository.findByJob_PostedById(recruiterId, pageable);
        }
        
        // Ensure related entities are loaded
        List<JobApplication> applications = applicationsPage.getContent();
        for (JobApplication application : applications) {
            // This will trigger lazy loading of the job if not already loaded
            if (application.getJob() != null) {
                // If you need to access job details, they will be loaded here
                Job job = application.getJob();
                // Accessing job properties to ensure they're loaded
                job.getId();
                job.getJobTitle();
                job.getCompanyName();
            }
            
            // This will trigger lazy loading of the candidate if not already loaded
            if (application.getCandidate() != null) {
                // If you need to access candidate details, they will be loaded here
                Candidate candidate = application.getCandidate();
                // Accessing candidate properties to ensure they're loaded
                candidate.getId();
                candidate.getFullName();
                candidate.getEmail();
            }
        }
        
        return applicationsPage;
    }
    
    @Override
    public Map<JobApplication.ApplicationStatus, Long> getApplicationStatusCounts(String recruiterId) {
        // Initialize map with all possible statuses set to 0
        Map<JobApplication.ApplicationStatus, Long> statusCounts = new EnumMap<>(JobApplication.ApplicationStatus.class);
        for (JobApplication.ApplicationStatus status : JobApplication.ApplicationStatus.values()) {
            statusCounts.put(status, 0L);
        }
        
        // Get counts from repository and update the map
        List<Map<String, Object>> counts = jobApplicationRepository.countApplicationsByStatusForRecruiter(recruiterId);
        for (Map<String, Object> count : counts) {
            JobApplication.ApplicationStatus status = JobApplication.ApplicationStatus.valueOf(count.get("status").toString());
            Long countValue = ((Number) count.get("count")).longValue();
            statusCounts.put(status, countValue);
        }
        
        return statusCounts;
    }

    @Override
    public Page<JobApplication> getApplicationsByStatus(JobApplication.ApplicationStatus status, Pageable pageable) {
        return jobApplicationRepository.findByStatus(status, pageable);
    }

    @Override
    public JobApplication addNoteToApplication(String applicationId, String note, String updatedBy) {
        return jobApplicationRepository.findById(applicationId)
                .map(application -> {
                    String currentNotes = application.getNotes() != null ? application.getNotes() + "\n" : "";
                    application.setNotes(currentNotes + "[" + updatedBy + "] " + note);
                    application.setUpdatedBy(updatedBy);
                    return jobApplicationRepository.save(application);
                })
                .orElseThrow(() -> new RuntimeException("Job application not found with id: " + applicationId));
    }
    
    @Override
    public List<ApplicationStatusHistory> getApplicationStatusHistory(String applicationId) {
        return jobApplicationRepository.findStatusHistoryById(applicationId)
                .map(JobApplication::getStatusHistory)
                .orElseGet(ArrayList::new);
    }
    
    @Override
    @Transactional
    public JobApplication withdrawApplication(String applicationId, String username) {
        JobApplication application = jobApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found with id: " + applicationId));
        
        // Verify the candidate owns this application
        if (!application.getCandidateId().equals(username)) {
            throw new IllegalStateException("You are not authorized to withdraw this application");
        }
        
        // Check if the application can be withdrawn
        if (!application.canWithdraw()) {
            throw new IllegalStateException("This application cannot be withdrawn as it is already " + 
                                         application.getStatus().getDisplayName().toLowerCase());
        }
        
        // Update the application status
        return updateApplicationStatus(applicationId, 
                                    JobApplication.ApplicationStatus.WITHDRAWN, 
                                    "Application withdrawn by candidate", 
                                    username);
    }

    @Override
    public List<JobApplication> findRecentApplications() {
        // Add your implementation here, for example:
        return jobApplicationRepository.findAll(Sort.by(Sort.Direction.DESC, "appliedAt"))
                .stream()
                .limit(10) // or whatever limit you need
                .collect(Collectors.toList());
    }
}
