package com.tal.pro.repository;

import com.tal.pro.model.Reference;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReferenceRepository extends MongoRepository<Reference, String> {
    List<Reference> findByCandidateId(String candidateId);
    void deleteByCandidateId(String candidateId);
    void deleteByIdAndCandidateId(String id, String candidateId);
}
