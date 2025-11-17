package com.tal.pro.repository;

import com.tal.pro.model.WorkExperience;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface WorkExperienceRepository extends MongoRepository<WorkExperience, String> {
    List<WorkExperience> findByCandidateId(String candidateId);
    Optional<WorkExperience> findByIdAndCandidateId(String id, String candidateId);
    void deleteByIdAndCandidateId(String id, String candidateId);
}
