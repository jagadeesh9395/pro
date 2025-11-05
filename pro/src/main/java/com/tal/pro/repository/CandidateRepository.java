package com.tal.pro.repository;

import com.tal.pro.model.Candidate;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CandidateRepository extends MongoRepository<Candidate, String> {
    Optional<Candidate> findById(String id);
    Optional<Candidate> findByUsername(String username);
    Boolean existsByUsername(String username);
    Boolean existsByEmail(String email);
}
