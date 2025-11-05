package com.tal.pro.repository;

import com.tal.pro.model.Job;
import com.tal.pro.model.Recruiter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface JobRepository extends MongoRepository<Job, String> {
    
    /**
     * Find all jobs posted by a specific recruiter
     */
    @Query("{ 'postedBy.$id': ?#{{'$oid' : ?0} } }")
    List<Job> findByPostedBy(String recruiterId);
    
    /**
     * Find all active jobs sorted by posted date (newest first)
     */
    @Query(value = "{ 'active': true }", sort = "{ 'postedAt' : -1 }")
    List<Job> findActiveJobs();
    
    /**
     * Search jobs with a text query across multiple fields
     * Uses MongoDB text search for better performance
     */
    @Query("""
        {
            $text: { 
                $search: ?0,
                $caseSensitive: false,
                $diacriticSensitive: false
            },
            'active': true
        }
    """)
    List<Job> searchJobs(@Param("query") String query);
    
    /**
     * Count jobs posted by a specific recruiter
     */
    @Query(value = "{ 'postedBy.$id': ?0 }", count = true)
    long countByPostedBy(String recruiterId);
    
    /**
     * Check if a job exists with the given ID and posted by the specified recruiter
     */
    @Query("{ '_id': ?0, 'postedBy.$id': ?1 }")
    boolean existsByIdAndPostedBy(String jobId, String recruiterId);
    
    /**
     * Advanced job search with multiple filters
     * @param query Search text
     * @param location Location filter
     * @param jobType Job type filter
     * @param minSalary Minimum salary filter
     * @param maxSalary Maximum salary filter
     * @param pageable Pagination information
     * @return Page of matching jobs
     */
    @Query("""
        {
            $and: [
                { 'active': true },
                { $or: [
                    { 'jobTitle': { $regex: ?#{[0] ? {$regex: #query, $options: 'i'} : {$exists: true}} },
                    { 'companyName': { $regex: ?#{[0] ? {$regex: #query, $options: 'i'} : {$exists: true}} },
                    { 'description': { $regex: ?#{[0] ? {$regex: #query, $options: 'i'} : {$exists: true}} },
                    { 'skills': { $regex: ?#{[0] ? {$regex: #query, $options: 'i'} : {$exists: true}} }
                ]},
                { 'location': { $regex: ?#{[1] ? {$regex: #location, $options: 'i'} : '.*'} },
                { 'jobType': ?#{[2] != null ? #jobType : { $exists: true} } },
                { 'minSalary': { $gte: ?#{[3] != null ? #minSalary : 0} } },
                { 'maxSalary': { $lte: ?#{[4] != null ? #maxSalary : NumberInt(1000000)} } }
            ]
        }
    """)
    Page<Job> searchJobsWithFilters(
        @Param("query") String query,
        @Param("location") String location,
        @Param("jobType") Job.JobType jobType,
        @Param("minSalary") Double minSalary,
        @Param("maxSalary") Double maxSalary,
        Pageable pageable
    );
    
    /**
     * Find jobs by multiple job types
     */
    @Query("{ 'jobType': { $in: ?0 }, 'active': true }")
    List<Job> findByJobTypes(List<Job.JobType> jobTypes, Pageable pageable);
    
    /**
     * Find featured jobs (active and sorted by posted date)
     */
    @Query(value = "{ 'active': true, 'featured': true }", sort = "{ 'postedAt': -1 }")
    List<Job> findFeaturedJobs(Pageable pageable);
}
