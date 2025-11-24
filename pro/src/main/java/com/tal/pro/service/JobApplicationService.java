package com.tal.pro.service;

import com.tal.pro.model.ApplicationStatusHistory;
import com.tal.pro.model.Candidate;
import com.tal.pro.model.Job;
import com.tal.pro.model.JobApplication;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface JobApplicationService {

    JobApplication submitApplication(String jobId, Candidate candidate, JobApplication application);

    boolean hasApplied(Candidate candidate, Job job);

    boolean hasCandidateApplied(String jobId, String candidateId);

    Optional<JobApplication> getApplicationById(String applicationId);

    List<JobApplication> getApplicationsByCandidateId(String candidateId);
    
    /**
     * Find an application by candidate ID
     * @param candidateId The ID of the candidate
     * @return Optional containing the application if found
     */
    Optional<JobApplication> findByCandidateId(String candidateId);

    List<JobApplication> getApplicationsByJobId(String jobId);

    JobApplication updateApplicationStatus(String applicationId, JobApplication.ApplicationStatus status, String updatedBy);
    
    /**
     * Updates the status of an application and adds a note to the status history
     * @param applicationId The ID of the application to update
     * @param status The new status
     * @param notes Notes about the status change
     * @param updatedBy ID of the user making the change
     * @return The updated application
     */
    JobApplication updateApplicationStatus(String applicationId, JobApplication.ApplicationStatus status, 
                                         String notes, String updatedBy);

    JobApplication updateApplication(JobApplication application, String updatedBy);

    /**
     * Get paginated applications for jobs posted by a specific recruiter
     * @param recruiterId ID of the recruiter
     * @param pageable Pagination information
     * @return Page of job applications
     */
    Page<JobApplication> getApplicationsByRecruiterId(String recruiterId, Pageable pageable);
    
    /**
     * Get filtered applications for jobs posted by a specific recruiter with search and status filters
     * @param recruiterId ID of the recruiter
     * @param status Optional status to filter by (as string)
     * @param search Optional search term to filter by candidate name, email, or job title
     * @param pageable Pagination information
     * @return Page of job applications matching the criteria
     */
    Page<JobApplication> getApplicationsByRecruiterIdWithFilters(
            String recruiterId, String status, String search, Pageable pageable);
    
    /**
     * Get filtered applications for jobs posted by a specific recruiter
     * @param recruiterId ID of the recruiter
     * @param jobId Optional job ID to filter by
     * @param status Optional status to filter by
     * @param pageable Pagination information
     * @return Page of job applications
     */
    Page<JobApplication> getApplicationsByRecruiterId(String recruiterId, String jobId,
                                                      JobApplication.ApplicationStatus status,
                                                      Pageable pageable);

    /**
     * Get applications by status
     * @param status Status to filter by
     * @param pageable Pagination information
     * @return Page of job applications with the specified status
     */
    Page<JobApplication> getApplicationsByStatus(JobApplication.ApplicationStatus status, Pageable pageable);

    /**
     * Get counts of applications grouped by status for a specific recruiter
     * @param recruiterId ID of the recruiter
     * @return Map of status to count of applications
     */
    Map<JobApplication.ApplicationStatus, Long> getApplicationStatusCounts(String recruiterId);

    /**
     * Adds a note to an application and updates the last modified timestamp
     * @param applicationId The ID of the application to add the note to
     * @param note The note to add
     * @param updatedBy ID of the user adding the note
     * @return The updated application with the new note
     */
    JobApplication addNoteToApplication(String applicationId, String note, String updatedBy);
    
    /**
     * Gets the full status history for an application
     * @param applicationId The ID of the application
     * @return List of status history entries, ordered by most recent first
     */
    List<ApplicationStatusHistory> getApplicationStatusHistory(String applicationId);
    
    /**
     * Withdraw a job application
     * @param applicationId The ID of the application to withdraw
     * @param username The username of the candidate withdrawing the application
     * @return The updated application
     * @throws ResourceNotFoundException if the application is not found
     * @throws IllegalStateException if the application cannot be withdrawn
     */
    JobApplication withdrawApplication(String applicationId, String username);

    List<JobApplication> findRecentApplications();

}
