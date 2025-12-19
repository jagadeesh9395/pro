package com.tal.pro.repository;

import com.tal.pro.model.ResumeDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PublicResumeRepository extends MongoRepository<ResumeDocument, String> {
}
