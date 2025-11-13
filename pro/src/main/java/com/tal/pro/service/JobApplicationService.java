package com.tal.pro.service;

import com.tal.pro.model.ApplicationStatusHistory;
import com.tal.pro.model.Candidate;
import com.tal.pro.model.Job;
import com.tal.pro.model.JobApplication;

import java.util.List;
import java.util.Optional;

public interface JobApplicationService {
    JobApplication submitApplication(String jobId, Candidate candidate, JobApplication application);

    boolean hasApplied(Candidate candidate, Job job);

    boolean hasCandidateApplied(String jobId, String candidateId);

    Optional<JobApplication> getApplicationById(String applicationId);

    List<JobApplication> getApplicationsByCandidateId(String candidateId);

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

    List<JobApplication> getApplicationsByRecruiterId(String recruiterId);

    List<JobApplication> getApplicationsByStatus(JobApplication.ApplicationStatus status);

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

}
