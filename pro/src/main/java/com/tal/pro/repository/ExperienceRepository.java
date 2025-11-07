package com.tal.pro.repository;

import com.tal.pro.model.Experience;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExperienceRepository extends MongoRepository<Experience, String> {
    List<Experience> findByCandidateId(String candidateId);
    void deleteByCandidateId(String candidateId);
    void deleteByIdAndCandidateId(String id, String candidateId);
}
