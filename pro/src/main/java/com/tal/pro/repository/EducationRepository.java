package com.tal.pro.repository;

import com.tal.pro.model.Education;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface EducationRepository extends MongoRepository<Education, String> {
    List<Education> findByCandidateId(String candidateId);
    Optional<Education> findByIdAndCandidateId(String id, String candidateId);
    void deleteByIdAndCandidateId(String id, String candidateId);
}
