package com.tal.pro.repository;

import com.tal.pro.model.Candidate;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

import org.springframework.data.mongodb.repository.Query;

@Repository
public interface CandidateRepository extends MongoRepository<Candidate, String> {
    Optional<Candidate> findById(String id);
    Optional<Candidate> findByUsername(String username);
    
    @Query(value = "{ 'username' : ?0 }", fields = "{ 'resume' : 1, 'resumeUrl' : 1, 'username' : 1, 'email' : 1, 'fullName' : 1, 'skills' : 1, 'experience' : 1 }")
    Optional<Candidate> findByUsernameWithResume(String username);
    
    Boolean existsByUsername(String username);
    Boolean existsByEmail(String email);
}
