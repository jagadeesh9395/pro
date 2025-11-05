package com.tal.pro.service;

import com.tal.pro.dto.JobDto;
import com.tal.pro.model.Job;
import com.tal.pro.model.Recruiter;
import org.springframework.data.domain.Sort;
import com.tal.pro.repository.JobRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class JobService {

    private final JobRepository jobRepository;
    private final MongoTemplate mongoTemplate;

    @Autowired
    public JobService(JobRepository jobRepository, MongoTemplate mongoTemplate) {
        this.jobRepository = jobRepository;
        this.mongoTemplate = mongoTemplate;
    }

    public Job postNewJob(JobDto jobDto, Recruiter recruiter) {
        Job job = jobDto.toJob();
        job.setPostedBy(recruiter);
        return jobRepository.save(job);
    }

    public Job updateJob(String jobId, JobDto jobDto, Recruiter recruiter) {
        return jobRepository.findById(jobId)
                .map(existingJob -> {
                    if (!existingJob.getPostedBy().equals(recruiter)) {
                        throw new SecurityException("You are not authorized to update this job");
                    }
                    Job updatedJob = jobDto.toJob();
                    updatedJob.setId(existingJob.getId());
                    updatedJob.setPostedBy(recruiter);
                    updatedJob.setPostedAt(existingJob.getPostedAt());
                    return jobRepository.save(updatedJob);
                })
                .orElseThrow(() -> new IllegalArgumentException("Job not found with id: " + jobId));
    }

    public void deleteJob(String jobId, Recruiter recruiter) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Job not found with id: " + jobId));
        
        if (!job.getPostedBy().equals(recruiter)) {
            throw new SecurityException("You are not authorized to delete this job");
        }
        
        jobRepository.delete(job);
    }

    public Page<Job> getAllActiveJobs(Pageable pageable) {
        Query query = new Query()
            .addCriteria(Criteria.where("active").is(true))
            .with(Sort.by(Sort.Direction.DESC, "postedAt"))
            .with(pageable);
            
        List<Job> jobs = mongoTemplate.find(query, Job.class);
        long total = mongoTemplate.count(Query.of(query).limit(-1).skip(-1), Job.class);
        
        return new PageImpl<>(jobs, pageable, total);
    }

    public List<Job> searchJobs(String query) {
        if (query == null || query.trim().isEmpty()) {
            return jobRepository.findActiveJobs();
        }
        return jobRepository.searchJobs(query);
    }

    public Optional<Job> getJobById(String id) {
        return jobRepository.findById(id);
    }

    public List<Job> getJobsByRecruiter(Recruiter recruiter) {
        return jobRepository.findByPostedBy(recruiter.getId());
    }

    public Page<Job> searchJobsWithFilters(String query, String location, Job.JobType jobType, Pageable pageable) {
        Query mongoQuery = new Query();
        Criteria criteria = new Criteria();
        
        // Only show active jobs
        criteria.and("active").is(true);
        
        if (query != null && !query.isEmpty()) {
            criteria.andOperator(new Criteria().orOperator(
                Criteria.where("jobTitle").regex(query, "i"),
                Criteria.where("companyName").regex(query, "i"),
                Criteria.where("description").regex(query, "i"),
                Criteria.where("skills").regex(query, "i")
            ));
        }
        
        if (location != null && !location.isEmpty()) {
            criteria.and("location").regex(location, "i");
        }
        
        if (jobType != null) {
            criteria.and("jobType").is(jobType);
        }
        
        mongoQuery.addCriteria(criteria);
        
        // Get total count
        long total = mongoTemplate.count(mongoQuery, Job.class);
        
        // Apply pagination
        mongoQuery.with(pageable);
        
        // Execute query
        List<Job> jobs = mongoTemplate.find(mongoQuery, Job.class);
        
        return new PageImpl<>(jobs, pageable, total);
    }
}
