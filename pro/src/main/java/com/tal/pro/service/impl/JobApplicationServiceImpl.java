package com.tal.pro.service.impl;

import com.tal.pro.model.Candidate;
import com.tal.pro.model.Job;
import com.tal.pro.model.JobApplication;
import com.tal.pro.repository.JobApplicationRepository;
import com.tal.pro.service.JobApplicationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class JobApplicationServiceImpl implements JobApplicationService {

    private final JobApplicationRepository jobApplicationRepository;

    @Autowired
    public JobApplicationServiceImpl(JobApplicationRepository jobApplicationRepository) {
        this.jobApplicationRepository = jobApplicationRepository;
    }

    @Override
    public JobApplication submitApplication(String jobId, Candidate candidate, JobApplication application) {
        application.setCandidate(candidate);
        Job job = new Job();
        job.setId(jobId);
        application.setJob(job);
        return jobApplicationRepository.save(application);
    }

    @Override
    public Optional<JobApplication> getApplicationById(String applicationId) {
        return jobApplicationRepository.findById(applicationId);
    }

    @Override
    public List<JobApplication> getApplicationsByCandidateId(String candidateId) {
        return jobApplicationRepository.findByCandidateId(candidateId);
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
    public JobApplication updateApplicationStatus(String applicationId, JobApplication.ApplicationStatus status, String updatedBy) {
        return jobApplicationRepository.findById(applicationId)
                .map(application -> {
                    application.setStatus(status);
                    application.setUpdatedBy(updatedBy);
                    return jobApplicationRepository.save(application);
                })
                .orElseThrow(() -> new RuntimeException("Job application not found with id: " + applicationId));
    }

    @Override
    public boolean hasApplied(Candidate candidate, Job job) {
        return jobApplicationRepository.existsByJobIdAndCandidateId(job.getId(), candidate.getId());
    }

    @Override
    public JobApplication updateApplication(JobApplication application, String updatedBy) {
        return jobApplicationRepository.findById(application.getId())
                .map(existingApp -> {
                    existingApp.setStatus(application.getStatus());
                    existingApp.setNotes(application.getNotes());
                    existingApp.setUpdatedBy(updatedBy);
                    return jobApplicationRepository.save(existingApp);
                })
                .orElseThrow(() -> new RuntimeException("Job application not found with id: " + application.getId()));
    }

    @Override
    public List<JobApplication> getApplicationsByRecruiterId(String recruiterId) {
        return jobApplicationRepository.findByJob_PostedById(recruiterId);
    }

    @Override
    public List<JobApplication> getApplicationsByStatus(JobApplication.ApplicationStatus status) {
        return jobApplicationRepository.findByStatus(status);
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
}

