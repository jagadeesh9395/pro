package com.tal.pro.repository;

import com.tal.pro.model.Candidate;
import com.tal.pro.model.Job;
import com.tal.pro.model.JobApplication;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface JobApplicationRepository extends MongoRepository<JobApplication, String> {

    List<JobApplication> findByJob(Job job);

    @Query("{ 'job.$id': ?0 }")
    List<JobApplication> findByJobId(String jobId);

    @Query("{ 'candidate.$id': ?0 }")
    List<JobApplication> findByCandidateId(String candidateId);

    @Query(value = "{ 'job.$id': ?0, 'candidate.$id': ?1 }", exists = true)
    boolean existsByJobIdAndCandidateId(String jobId, String candidateId);

    @Query(value = "{ 'job.$id': ?0, 'candidate.$id': ?1 }", exists = true)
    boolean existsByJobAndCandidate(String jobId, String candidateId);
}
