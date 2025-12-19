package com.tal.pro.service;

import com.tal.pro.model.ResumeDocument;
import com.tal.pro.repository.PublicResumeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class PublicResumeService {

    @Autowired
    private PublicResumeRepository resumeRepository;

    public ResumeDocument saveResume(ResumeDocument resume) {
        return resumeRepository.save(resume);
    }

    public Optional<ResumeDocument> getResume(String id) {
        return resumeRepository.findById(id);
    }
}
