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
    public List<JobApplication> getApplicationsByCandidate(Candidate candidate) {
        return jobApplicationRepository.findByCandidateId(candidate.getId());
    }
    
    @Override
    public List<JobApplication> getApplicationsByJobId(String jobId) {
        return jobApplicationRepository.findByJobId(jobId);
    }
    
    @Override
    public boolean hasCandidateApplied(String jobId, String candidateId) {
        return jobApplicationRepository.existsByJobIdAndCandidateId(jobId, candidateId);
    }
}
