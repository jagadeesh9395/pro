package com.tal.pro.repository;

import com.tal.pro.model.Resume;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ResumeRepository extends MongoRepository<Resume, String> {

    @Query("{'$and': [{'$or': [{'htmlContent': {'$regex': ?0, '$options': 'i'}}, {'candidate.fullName': {'$regex': ?0, '$options': 'i'}}, {'candidate.skills': {'$regex': ?0, '$options': 'i'}}, {'candidate.experience': {'$regex': ?0, '$options': 'i'}}, {'candidate.location': {'$regex': ?0, '$options': 'i'}}, {'candidate.education': {'$regex': ?0, '$options': 'i'}}, {'candidate.email': {'$regex': ?0, '$options': 'i'}}]}, {'isPublic': true}]}")
        //@Query(fields = "{'id': 1, 'candidate.fullName': 1, 'candidate.skills': 1, 'candidate.experience': 1, 'uploadedAt': 1, 'candidate.location': 1}")
    Page<Resume> searchByContent(String searchText, Pageable pageable);

    /**
     * Find resumes by multiple skills (AND condition)
     */
    @Query("{'candidate.skills': {'$all': ?0}}")
    Page<Resume> findBySkills(List<String> skills, Pageable pageable);

    /**
     * Find resumes by location (case-insensitive partial match)
     */
    @Query("{'candidate.location': {'$regex': ?0, '$options': 'i'}}")
    Page<Resume> findByLocation(String location, Pageable pageable);
    @Query("{'htmlContent': {$regex: ?0, $options: 'i'}}")
    List<Resume> findByHtmlContent(String keyword);
}
