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

    String getResumeHtmlContent(String id, boolean maskSensitiveInfo);

    List<Resume> searchResumes(ResumeSearchCriteria criteria);

    void deleteResume(String id);

    default boolean hasAnyCriteria(ResumeSearchCriteria criteria) {
        return criteria != null && (
                (criteria.getKeyword() != null && !criteria.getKeyword().trim().isEmpty()) ||
                        criteria.getUploadedBefore() != null
        );
    }

}
