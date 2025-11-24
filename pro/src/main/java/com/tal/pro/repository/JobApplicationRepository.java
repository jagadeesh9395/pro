package com.tal.pro.repository;

import com.tal.pro.model.Candidate;
import com.tal.pro.model.Job;
import com.tal.pro.model.JobApplication;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
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
    
    @Query("{ $or: [ { 'candidate.$id': { $oid: ?0 } }, { 'email': ?0 } ] }")
    List<JobApplication> findByCandidateIdOrEmail(String identifier);

    @Query(value = "{ 'candidate.$id': { $oid: ?0 } }", fields = "{ 'statusHistory': 0 }")
    List<JobApplication> findBasicInfoByCandidateId(String candidateId);

    @Query(value = "{ 'job.$id': { $oid: ?0 }, 'candidate.$id': { $oid: ?1 } }", exists = true)
    boolean existsByJobIdAndCandidateId(String jobId, String candidateId);

    @Query(value = "{ 'job.$id': { $oid: ?0 }, 'candidate.$id': { $oid: ?1 } }", exists = true)
    boolean existsByJobAndCandidate(String jobId, String candidateId);
    
    @Query("{ 'job.postedBy.$id': { $oid: ?0 } }")
    List<JobApplication> findByJob_PostedById(String recruiterId);
    
    @Query(value = "{ 'job.postedBy.$id': { $oid: ?0 } }", fields = "{ 'candidate': 1, 'job': 1, 'status': 1, 'appliedAt': 1, 'updatedAt': 1, 'updatedBy': 1, 'fullName': 1, 'email': 1, 'phone': 1, 'resume': 1, 'coverLetter': 1, 'statusHistory': 1 }")
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
    
    /**
     * Find applications by recruiter ID with status filter
     */
    @Query(value = "{ 'job.postedBy.$id': { $oid: ?0 }, 'status': ?1 }", fields = "{ 'candidate': 1, 'job': 1, 'status': 1, 'appliedAt': 1, 'updatedAt': 1, 'updatedBy': 1, 'fullName': 1, 'email': 1, 'phone': 1, 'resume': 1, 'coverLetter': 1, 'statusHistory': 1 }")
    Page<JobApplication> findByJob_PostedByIdAndStatus(String recruiterId, JobApplication.ApplicationStatus status, Pageable pageable);
    @Query(value = "{ 'job.postedBy.$id' : ?0, $or: [ " +
            "{ 'fullName': { $regex: ?1, $options: 'i' } }, " +
            "{ 'email': { $regex: ?1, $options: 'i' } }, " +
            "{ 'job.jobTitle': { $regex: ?1, $options: 'i' } }, " +
            "{ 'job.companyName': { $regex: ?1, $options: 'i' } } " +
            "] }", fields = "{ 'candidate': 1, 'job': 1, 'status': 1, 'appliedAt': 1, 'updatedAt': 1, 'updatedBy': 1, 'fullName': 1, 'email': 1, 'phone': 1, 'resume': 1, 'coverLetter': 1, 'statusHistory': 1 }")
    Page<JobApplication> findByRecruiterIdWithSearch(String recruiterId, String searchTerm, Pageable pageable);

    @Query(value = "{ 'job.postedBy.$id' : ?0, 'status' : ?1, $or: [ " +
            "{ 'fullName': { $regex: ?2, $options: 'i' } }, " +
            "{ 'email': { $regex: ?2, $options: 'i' } }, " +
            "{ 'job.jobTitle': { $regex: ?2, $options: 'i' } }, " +
            "{ 'job.companyName': { $regex: ?2, $options: 'i' } } " +
            "] }", fields = "{ 'candidate': 1, 'job': 1, 'status': 1, 'appliedAt': 1, 'updatedAt': 1, 'updatedBy': 1, 'fullName': 1, 'email': 1, 'phone': 1, 'resume': 1, 'coverLetter': 1, 'statusHistory': 1 }")
    Page<JobApplication> findByRecruiterIdWithStatusAndSearch(
            String recruiterId,
            JobApplication.ApplicationStatus status,
            String searchTerm,
            Pageable pageable
    );

    @Aggregation(pipeline = {
            "{ $match: { 'job.postedBy.$id': ?0 } }",
            "{ $group: { _id: '$status', count: { $sum: 1 } } }"
    })
    List<Map<String, Object>> countApplicationsByStatusForRecruiter(String recruiterId);

//    @Query(value = "{ 'job.postedBy.$id': ?0 }", fields = "{ 'candidate': 1, 'job': 1, 'status': 1, 'appliedAt': 1, 'updatedAt': 1, 'updatedBy': 1, 'fullName': 1, 'email': 1, 'phone': 1, 'resume': 1, 'coverLetter': 1, 'statusHistory': 1 }", sort = "{ 'appliedAt': -1 }")
//    Page<JobApplication> findRecentApplications(String recruiterId, Pageable pageable);



        @Aggregation(pipeline = {
                "{ $lookup: { from: 'jobs', localField: 'jobId', foreignField: '_id', as: 'job' } }",
                "{ $unwind: { path: '$job', preserveNullAndEmptyArrays: true } }",
                "{ $lookup: { from: 'candidates', localField: 'candidateId', foreignField: '_id', as: 'candidate' } }",
                "{ $unwind: { path: '$candidate', preserveNullAndEmptyArrays: true } }",
                "{ $project: { " +
                        "fullName: 1, " +
                        "email: 1, " +
                        "phone: 1, " +
                        "resumePath: 1, " +
                        "status: 1, " +
                        "appliedAt: 1, " +
                        "updatedAt: 1, " +
                        "statusHistory: 1, " +
                        "notes: 1, " +
                        "job: { " +
                        "id: '$job._id', " +
                        "jobTitle: '$job.jobTitle', " +
                        "companyName: '$job.companyName', " +
                        "location: '$job.location' " +
                        "}, " +
                        "candidate: { " +
                        "id: '$candidate._id', " +
                        "fullName: '$candidate.fullName', " +
                        "email: '$candidate.email', " +
                        "phone: '$candidate.phone' " +
                        "} " +
                        "} }",
                "{ $sort: { appliedAt: -1 } }",
                "{ $limit: 10 }"
        })
        List<JobApplication> findRecentApplications();
    }
    

