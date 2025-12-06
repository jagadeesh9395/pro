package com.tal.pro.service;

import com.tal.pro.dto.JobDto;
import com.tal.pro.model.Job;
import com.tal.pro.model.Recruiter;
import com.tal.pro.repository.JobRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Slf4j
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

    @Transactional
    public Job updateJob(String jobId, JobDto jobDto, Recruiter recruiter) {
        if (recruiter == null) {
            throw new SecurityException("You must be logged in as a recruiter to update a job");
        }

        return jobRepository.findById(jobId)
                .map(existingJob -> {
                    try {
                        log.info("Updating job: {}", jobId);
                        log.debug("Existing job: {}", existingJob);

                        // If job doesn't have a postedBy, assign it to the current recruiter
                        if (existingJob.getPostedBy() == null) {
                            log.info("Assigning job to recruiter: {}", recruiter.getId());
                            existingJob.setPostedBy(recruiter);
                            existingJob.setLastModifiedAt(java.time.LocalDateTime.now());
                            Job saved = jobRepository.save(existingJob);
                            log.debug("Assigned job: {}", saved);
                            return saved;
                        }

                        // Check if the current user is the owner of the job
                        if (!existingJob.getPostedBy().getId().equals(recruiter.getId())) {
                            throw new SecurityException("You are not authorized to update this job");
                        }

                        // Update job details from DTO
                        log.debug("Updating job details");
                        existingJob.setJobTitle(jobDto.getJobTitle());
                        existingJob.setCompanyName(jobDto.getCompanyName());
                        existingJob.setJobType(jobDto.getJobType());
                        existingJob.setLocation(jobDto.getLocation());
                        existingJob.setMinSalary(jobDto.getMinSalary());
                        existingJob.setMaxSalary(jobDto.getMaxSalary());
                        existingJob.setDescription(jobDto.getDescription());
                        existingJob.setRequirements(jobDto.getRequirements());
                        existingJob.setExperience(jobDto.getExperience());
                        existingJob.setSkills(jobDto.getSkills());
                        existingJob.setLastModifiedAt(java.time.LocalDateTime.now());

                        // Save and log the update
                        Job updatedJob = jobRepository.save(existingJob);
                        log.info("Successfully updated job: {}", updatedJob.getId());
                        return updatedJob;

                    } catch (Exception e) {
                        log.error("Error updating job: {}", e.getMessage(), e);
                        throw e;
                    }
                })
                .orElseThrow(() -> {
                    String errorMsg = "Job not found with id: " + jobId;
                    log.error(errorMsg);
                    return new IllegalArgumentException(errorMsg);
                });
    }

    /**
     * Saves a job entity to the database.
     *
     * @param job the job to save
     * @return the saved job
     */
    public Job saveJob(Job job) {
        return jobRepository.save(job);
    }

    @Transactional
    public void deleteJob(String jobId, Recruiter recruiter) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Job not found with id: " + jobId));

        if (!job.getPostedBy().getId().equals(recruiter.getId())) {
            throw new SecurityException("You are not authorized to delete this job");
        }

        // Soft delete by setting deleted to true
        job.setDeleted(true);
        // Also deactivate it to be safe
        job.setActive(false);
        jobRepository.save(job);
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
        // Validate ID format (MongoDB ObjectId must be 24 hex characters)
        if (id == null || id.trim().isEmpty() || id.length() != 24 || !id.matches("^[a-fA-F0-9]+$")) {
            log.warn("Invalid job ID format: {}", id);
            return Optional.empty();
        }

        try {
            return jobRepository.findById(id);
        } catch (IllegalArgumentException e) {
            log.error("Invalid job ID format: {}", id, e);
            return Optional.empty();
        } catch (Exception e) {
            log.error("Error fetching job with ID: {}", id, e);
            return Optional.empty();
        }
    }

    public List<Job> getJobsByRecruiter(Recruiter recruiter) {
        return jobRepository.findByPostedBy_IdAndDeletedFalse(new ObjectId(recruiter.getId()));
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
                    Criteria.where("skills").regex(query, "i")));
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
