package com.tal.pro.repository;

import com.tal.pro.model.Recruiter;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RecruiterRepository extends MongoRepository<Recruiter, String> {
    Optional<Recruiter> findById(String id);
    Optional<Recruiter> findByUsername(String username);
}
