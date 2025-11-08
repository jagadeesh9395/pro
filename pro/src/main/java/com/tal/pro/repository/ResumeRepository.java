package com.tal.pro.repository;
import com.tal.pro.model.Resume;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ResumeRepository extends MongoRepository<Resume, String> {
    // Custom query methods can be added here if needed
}
