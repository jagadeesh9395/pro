package com.tal.pro.service;

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

    JobApplication updateApplication(JobApplication application, String updatedBy);

    List<JobApplication> getApplicationsByRecruiterId(String recruiterId);

    List<JobApplication> getApplicationsByStatus(JobApplication.ApplicationStatus status);

    JobApplication addNoteToApplication(String applicationId, String note, String updatedBy);

}
