package com.tal.pro.service;

import com.tal.pro.model.Candidate;
import com.tal.pro.model.Job;
import com.tal.pro.model.JobApplication;

import java.util.List;
import java.util.Optional;

public interface JobApplicationService {
    JobApplication submitApplication(String jobId, Candidate candidate, JobApplication application);
    Optional<JobApplication> getApplicationById(String applicationId);
    List<JobApplication> getApplicationsByCandidate(Candidate candidate);
    List<JobApplication> getApplicationsByJobId(String jobId);
    boolean hasCandidateApplied(String jobId, String candidateId);
    
    /**
     * Check if a candidate has applied to a specific job
     * @param candidate The candidate to check
     * @param job The job to check
     * @return true if the candidate has applied to the job, false otherwise
     */
    default boolean hasApplied(Candidate candidate, Job job) {
        return hasCandidateApplied(job.getId(), candidate.getId());
    }
}
