package com.tal.pro.repository;

import com.tal.pro.model.Education;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EducationRepository extends MongoRepository<Education, String> {
    List<Education> findByCandidateId(String candidateId);
    void deleteByCandidateId(String candidateId);
    void deleteByIdAndCandidateId(String id, String candidateId);
}
