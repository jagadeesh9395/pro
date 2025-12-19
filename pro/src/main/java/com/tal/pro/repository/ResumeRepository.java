package com.tal.pro.repository;

import com.tal.pro.model.Resume;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ResumeRepository extends MongoRepository<Resume, String> {
    List<Resume> findByHtmlContent(String htmlContent);
    // Add other methods if inferred from ResumeServiceImpl usage, but standard
    // MongoRepository methods might suffice or custom queries were there.
    // From ResumeServiceImpl usage:
    // findByHtmlContent(String)
    // findByFirstNameIgnoreCase(String) - inferred from commented out code
    // But since commented out, maybe not needed.
    // The lint error only complained about type mismatch, so basic restoration is
    // priority.
}
