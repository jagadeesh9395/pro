package com.tal.pro.repository;

import com.tal.pro.model.Candidate;
import com.tal.pro.model.Job;
import com.tal.pro.model.JobApplication;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface JobApplicationRepository extends MongoRepository<JobApplication, String> {

    List<JobApplication> findByJob(Job job);

    @Query("{ 'job.$id': { $oid: ?0 } }")
    List<JobApplication> findByJobId(String jobId);

    Page<JobApplication> findByStatus(JobApplication.ApplicationStatus status, Pageable pageable);

    @Query("{ 'candidate.$id': { $oid: ?0 } }")
    List<JobApplication> findByCandidateId(String candidateId);
    
    @Query("{ 'candidate.$id': { $oid: ?0 } }")
    List<JobApplication> findByCandidateIdOrderByAppliedAtDesc(String candidateId);
    
    @Query(value = "{ 'candidate.$id': { $oid: ?0 } }", fields = "{ 'statusHistory': 0 }")
    List<JobApplication> findBasicInfoByCandidateId(String candidateId);

    @Query(value = "{ 'job.$id': { $oid: ?0 }, 'candidate.$id': { $oid: ?1 } }", exists = true)
    boolean existsByJobIdAndCandidateId(String jobId, String candidateId);

    @Query(value = "{ 'job.$id': { $oid: ?0 }, 'candidate.$id': { $oid: ?1 } }", exists = true)
    boolean existsByJobAndCandidate(String jobId, String candidateId);
    
    @Query("{ 'job.postedBy.$id': { $oid: ?0 } }")
    List<JobApplication> findByJob_PostedById(String recruiterId);
    
    @Query("{ 'job.postedBy.$id': { $oid: ?0 } }")
    Page<JobApplication> findByJob_PostedById(String recruiterId, Pageable pageable);
    
    @Query("{ 'job.postedBy.$id': { $oid: ?0 }, 'job.$id': { $oid: ?#{#jobId != null ? #jobId : 'null'} }, 'status': ?#{#status != null ? #status : {$exists: true}} }")
    Page<JobApplication> findByRecruiterIdAndJobIdAndStatus(
        String recruiterId, 
        @Param("jobId") String jobId, 
        @Param("status") JobApplication.ApplicationStatus status, 
        Pageable pageable
    );
    
    @Query(value = "{ '_id': ?0 }", fields = "{ 'statusHistory': 1 }")
    Optional<JobApplication> findStatusHistoryById(String applicationId);
}
