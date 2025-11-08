package com.tal.pro.service;
import com.tal.pro.model.Resume;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface ResumeService {
    Resume uploadAndConvertResume(MultipartFile file) throws IOException;
    Resume getResumeById(String id);
    void deleteResume(String id);
}
