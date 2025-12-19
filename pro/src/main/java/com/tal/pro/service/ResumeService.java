package com.tal.pro.service;

import com.tal.pro.criteria.ResumeSearchCriteria;
import com.tal.pro.model.Resume;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

public interface ResumeService {

    Resume uploadAndConvertResume(MultipartFile file, String username) throws IOException;

    Resume updateResume(String id, MultipartFile file, String username) throws IOException;

    Optional<Resume> getResumeById(String id);

    void deleteResume(String id);

    String getResumeHtmlContent(String id, boolean maskPersonalInfo);

    String getResumeHtmlContent(String id);

    List<Resume> searchResumes(ResumeSearchCriteria criteria);
}
