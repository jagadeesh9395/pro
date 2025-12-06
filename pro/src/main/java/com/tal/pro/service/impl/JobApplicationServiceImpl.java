package com.tal.pro.service.impl;

import com.tal.pro.exception.ResourceNotFoundException;
import com.tal.pro.model.ApplicationStatusHistory;
import com.tal.pro.model.Candidate;
import com.tal.pro.model.Job;
import com.tal.pro.model.JobApplication;
import com.tal.pro.repository.JobApplicationRepository;
import com.tal.pro.event.ApplicationStatusEvent;
import com.tal.pro.repository.RecruiterRepository;
import com.tal.pro.service.JobApplicationService;
import com.tal.pro.service.JobService;
import com.tal.pro.service.KafkaProducerService;
import com.tal.pro.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class JobApplicationServiceImpl implements JobApplicationService {

    private final JobApplicationRepository jobApplicationRepository;
    private final JobService jobService;
    private final KafkaProducerService kafkaProducerService;

    private final NotificationService notificationService;
    private final RecruiterRepository recruiterRepository;

    @Autowired
    public JobApplicationServiceImpl(JobApplicationRepository jobApplicationRepository,
            JobService jobService,
            KafkaProducerService kafkaProducerService,
            NotificationService notificationService,
            RecruiterRepository recruiterRepository) {
        this.jobApplicationRepository = jobApplicationRepository;
        this.jobService = jobService;
        this.kafkaProducerService = kafkaProducerService;
        this.notificationService = notificationService;
        this.recruiterRepository = recruiterRepository;
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
        addStatusHistory(application, JobApplication.ApplicationStatus.APPLIED, "Application submitted",
                candidate.getId());

        // Save the application
        JobApplication savedApplication = jobApplicationRepository.save(application);

        // Send email notifications
        try {
            // Send confirmation email to candidate
            notificationService.sendJobApplicationConfirmation(
                    candidate.getId(),
                    candidate.getEmail(),
                    candidate.getFullName(),
                    job.getJobTitle(),
                    job.getCompanyName());

            // Send notification to recruiter
            if (job.getPostedBy() != null) {
                String recruiterEmail = job.getPostedBy().getEmail();
                String recruiterName = job.getPostedBy().getFullName();
                String candidateName = candidate.getFullName();
                String jobTitle = job.getJobTitle();

                notificationService.notifyRecruiterNewApplication(
                        job.getPostedBy().getId(),
                        recruiterEmail,
                        recruiterName,
                        candidateName,
                        candidate.getEmail(),
                        jobTitle,
                        job.getId(),
                        savedApplication.getId());
            }
        } catch (Exception e) {
            // Log the error but don't fail the application submission
            log.error("Failed to send email notifications for application {}", savedApplication.getId(), e);
        }

        return savedApplication;
    }

    @Override
    public Optional<JobApplication> getApplicationById(String applicationId) {
        if (applicationId == null || applicationId.trim().isEmpty()) {
            return Optional.empty();
        }

        // Check for common template literals or invalid formats
        if (applicationId.equals("application.id") || applicationId.equals("${application.id}")
                || applicationId.contains("{")) {
            return Optional.empty();
        }

        try {
            // First try to find by application ID
            Optional<JobApplication> app = jobApplicationRepository.findById(applicationId);
            if (app.isPresent()) {
                return app;
            }

            // If not found by ID, try to find by candidate email
            if (applicationId.contains("@")) { // Only try email lookup if it looks like an email
                List<JobApplication> apps = jobApplicationRepository.findByCandidateEmail(applicationId.toLowerCase());
                if (!apps.isEmpty()) {
                    return Optional.of(apps.get(0));
                }
            }

            return Optional.empty();

        } catch (IllegalArgumentException e) {
            // This will catch invalid ObjectId format errors
            try {
                if (applicationId.contains("@")) { // Only try email lookup if it looks like an email
                    List<JobApplication> apps = jobApplicationRepository
                            .findByCandidateEmail(applicationId.toLowerCase());
                    if (!apps.isEmpty()) {
                        return Optional.of(apps.get(0));
                    }
                }
                return Optional.empty();
            } catch (Exception ex) {
                return Optional.empty();
            }
        } catch (Exception e) {
            return Optional.empty();
        }
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
                                () -> System.out
                                        .println("DEBUG: Job not found for ID: " + application.getJob().getId()));
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
    public List<JobApplication> findByRecruiterAndInterviewDateBetween(
            String recruiterEmail, LocalDateTime startDate, LocalDateTime endDate) {
        return jobApplicationRepository.findByRecruiterEmailAndInterviewDateBetween(
                recruiterEmail, startDate, endDate);
    }

    @Override
    public Optional<JobApplication> findByCandidateId(String candidateId) {
        if (candidateId == null || candidateId.trim().isEmpty()) {
            return Optional.empty();
        }

        try {
            // First try to find by candidate ID (as ObjectId)
            List<JobApplication> applications = jobApplicationRepository.findByCandidateId(candidateId);
            if (!applications.isEmpty()) {
                return Optional.of(applications.get(0));
            }

            // If not found by ID, try by email (case-insensitive)
            applications = jobApplicationRepository.findByCandidateEmail(candidateId.toLowerCase());
            if (!applications.isEmpty()) {
                return Optional.of(applications.get(0));
            }

            return Optional.empty();

        } catch (IllegalArgumentException e) {
            // This will catch invalid ObjectId format errors

            try {
                List<JobApplication> applications = jobApplicationRepository
                        .findByCandidateEmail(candidateId.toLowerCase());
                if (!applications.isEmpty()) {
                    return Optional.of(applications.get(0));
                }
            } catch (Exception ex) {
            }

            return Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Override
    public boolean hasCandidateApplied(String jobId, String candidateId) {
        return jobApplicationRepository.existsByJobIdAndCandidateId(jobId, candidateId);
    }

    @Override
    @Transactional
    public JobApplication updateApplicationStatus(String applicationId, JobApplication.ApplicationStatus status,
            String updatedBy) {
        return updateApplicationStatus(applicationId, status, "Status updated", updatedBy);
    }

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Override
    @Transactional
    public JobApplication updateApplicationStatus(String applicationId, JobApplication.ApplicationStatus status,
            String notes, String updatedBy) {
        if (applicationId == null || applicationId.trim().isEmpty()) {
            throw new IllegalArgumentException("Application ID cannot be null or empty");
        }

        if (applicationId.length() != 24) {
        }

        if (status == null) {
            throw new IllegalArgumentException("Status cannot be null");
        }

        try {
            // Find the application
            JobApplication application = jobApplicationRepository.findById(applicationId)
                    .orElseThrow(() -> {
                        log.error("Application not found - ID: {}", applicationId);
                        return new RuntimeException("Application not found with ID: " + applicationId);
                    });

            // Only update if status has changed
            if (application.getStatus() != status) {
                log.debug("Status changed from {} to {} for application: {}",
                        application.getStatus(), status, applicationId);

                // Add to status history
                addStatusHistory(application, status, notes, updatedBy);

                // Update status and timestamps
                application.setStatus(status);
                application.setUpdatedAt(LocalDateTime.now());
                application.setUpdatedBy(updatedBy);

                try {
                    JobApplication updatedApp = jobApplicationRepository.save(application);
                    log.info("Successfully updated application status - ID: {}, New Status: {}",
                            applicationId, status);

                    // Send WebSocket notification
                    sendStatusUpdateNotification(updatedApp, notes, updatedBy);

                    return updatedApp;
                } catch (Exception e) {
                    log.error("Error saving application status update - ID: {}, Error: {}",
                            applicationId, e.getMessage(), e);
                    throw new RuntimeException("Failed to update application status: " + e.getMessage(), e);
                }
            }

            log.debug("No status change detected for application: {}", applicationId);
            return application;

        } catch (IllegalArgumentException e) {
            log.error("Invalid application ID format: {}", applicationId);
            throw new IllegalArgumentException("Invalid application ID format", e);
        } catch (RuntimeException e) {
            log.error("Error updating application status - ID: {}, Error: {}",
                    applicationId, e.getMessage(), e);
            throw e; // Re-throw to be handled by the controller
        } catch (Exception e) {
            log.error("Unexpected error updating application status - ID: {}, Error: {}",
                    applicationId, e.getMessage(), e);
            throw new RuntimeException("An unexpected error occurred while updating the application status", e);
        }
    }

    /**
     * Adds a new status history entry to the application
     */
    /**
     * Sends WebSocket notification for application status updates
     */
    private void sendStatusUpdateNotification(JobApplication application, String notes, String updatedBy) {
        try {
            ApplicationStatusEvent event = new ApplicationStatusEvent();
            event.setApplicationId(application.getId());
            event.setCandidateId(application.getCandidate().getId());
            event.setNewStatus(application.getStatus());
            event.setNotes(notes);
            event.setUpdatedBy(updatedBy);
            event.setTimestamp(LocalDateTime.now());
            event.setJobId(application.getJob() != null ? application.getJob().getId() : null);
            event.setJobTitle(application.getJob() != null ? application.getJob().getJobTitle() : null);

            // Send to candidate's private queue
            messagingTemplate.convertAndSendToUser(
                    event.getCandidateId(),
                    "/queue/status-updates",
                    event);

            // Send to application-specific topic
            messagingTemplate.convertAndSend(
                    "/topic/application/" + application.getId() + "/status-updates",
                    event);

            log.debug("Sent WebSocket notification for application status update: {}", application.getId());
        } catch (Exception e) {
            log.error("Failed to send WebSocket notification for application {}: {}",
                    application.getId(), e.getMessage(), e);
        }
    }

    private void addStatusHistory(JobApplication application, JobApplication.ApplicationStatus status,
            String notes, String changedBy) {
        if (application.getStatusHistory() == null) {
            application.setStatusHistory(new ArrayList<>());
        }

        JobApplication.ApplicationStatusHistory history = new JobApplication.ApplicationStatusHistory();
        history.setStatus(status);
        history.setNotes(notes);
        history.setUpdatedAt(LocalDateTime.now());
        history.setUpdatedBy(changedBy);

        application.getStatusHistory().add(history);

        // Keep only the last 50 status updates to prevent unbounded growth
        if (application.getStatusHistory().size() > 50) {
            application.setStatusHistory(
                    application.getStatusHistory().subList(
                            application.getStatusHistory().size() - 50,
                            application.getStatusHistory().size()));
        }
    }

    @Override
    public boolean hasApplied(Candidate candidate, Job job) {
        return jobApplicationRepository.existsByJobIdAndCandidateId(job.getId(), candidate.getId());
    }

    @Override
    public Optional<JobApplication> findByCandidateAndJob(Candidate candidate, Job job) {
        return jobApplicationRepository.findByJobIdAndCandidateId(job.getId(), candidate.getId());
    }

    @Override
    @Transactional
    public JobApplication updateApplication(JobApplication application, String updatedBy) {
        // If status has changed, add to history
        JobApplication existing = jobApplicationRepository.findById(application.getId())
                .orElseThrow(() -> new RuntimeException("Application not found"));

        if (existing.getStatus() != application.getStatus()) {
            JobApplication.ApplicationStatus oldStatus = existing.getStatus();
            addStatusHistory(application, application.getStatus(),
                    "Application updated with status: " + application.getStatus().getDisplayName(),
                    updatedBy);

            // Publish status update event
            publishApplicationStatusUpdate(application, oldStatus, updatedBy);
        }

        application.setUpdatedAt(LocalDateTime.now());
        application.setUpdatedBy(updatedBy);

        return jobApplicationRepository.save(application);
    }

    private void publishApplicationStatusUpdate(JobApplication application, JobApplication.ApplicationStatus oldStatus,
            String updatedBy) {
        if (application == null) {
            log.error("Cannot publish status update: application is null");
            return;
        }

        if (application.getId() == null || application.getId().trim().isEmpty()) {
            log.error("Cannot publish status update: application ID is null or empty");
            return;
        }

        if (application.getJob() == null || application.getJob().getId() == null) {
            log.error("Cannot publish status update: job or job ID is null");
            return;
        }

        if (application.getCandidate() == null || application.getCandidate().getId() == null) {
            log.error("Cannot publish status update: candidate or candidate ID is null");
            return;
        }

        try {
            ApplicationStatusEvent event = new ApplicationStatusEvent(
                    application.getId(),
                    application.getJob().getId(),
                    application.getCandidate().getId(),
                    oldStatus,
                    application.getStatus(),
                    updatedBy,
                    "Status updated from " + (oldStatus != null ? oldStatus : "N/A") +
                            " to "
                            + (application.getStatus() != null ? application.getStatus().getDisplayName() : "N/A"),
                    application.getJob().getJobTitle(),
                    LocalDateTime.now());
            kafkaProducerService.sendStatusUpdate(event);
        } catch (Exception e) {
            log.error("Failed to publish status update for application {}: {}",
                    application.getId(), e.getMessage(), e);
        }
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
            applicationsPage = jobApplicationRepository.findByJob_PostedByIdAndStatus(recruiterId, statusEnum,
                    pageable);
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
        Map<JobApplication.ApplicationStatus, Long> statusCounts = new EnumMap<>(
                JobApplication.ApplicationStatus.class);
        for (JobApplication.ApplicationStatus status : JobApplication.ApplicationStatus.values()) {
            statusCounts.put(status, 0L);
        }

        // Get counts from repository and update the map
        List<Map<String, Object>> counts = jobApplicationRepository.countApplicationsByStatusForRecruiter(recruiterId);
        for (Map<String, Object> count : counts) {
            JobApplication.ApplicationStatus status = JobApplication.ApplicationStatus
                    .valueOf(count.get("status").toString());
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
                .orElseGet(ArrayList::new)
                .stream()
                .map(history -> {
                    ApplicationStatusHistory statusHistory = new ApplicationStatusHistory();
                    statusHistory.setStatus(history.getStatus());
                    statusHistory.setNotes(history.getNotes());
                    statusHistory.setChangedAt(history.getUpdatedAt());
                    statusHistory.setChangedBy(history.getUpdatedBy());
                    return statusHistory;
                })
                .collect(Collectors.toList());
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

    @Override
    public Optional<JobApplication> findByJobIdAndCandidateId(String jobId, String candidateId) {
        return jobApplicationRepository.findByJobIdAndCandidateId(jobId, candidateId);
    }
    
    @Override
    public List<JobApplication> findUpcomingInterviewsForRecruiter(String recruiterId, LocalDateTime startDate, LocalDateTime endDate) {
        return jobApplicationRepository.findUpcomingInterviewsByRecruiterId(recruiterId, startDate, endDate);
    }
    
    @Override
    public List<JobApplication> findUpcomingInterviewsForCandidate(String candidateId, LocalDateTime startDate, LocalDateTime endDate) {
        return jobApplicationRepository.findUpcomingInterviewsByCandidateId(candidateId, startDate, endDate);
    }
}
