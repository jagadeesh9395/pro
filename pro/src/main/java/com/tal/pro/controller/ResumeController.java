package com.tal.pro.controller;
import com.tal.pro.model.Resume;
import com.tal.pro.service.ResumeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/resumes")
@RequiredArgsConstructor
public class ResumeController {

    private final ResumeService resumeService;

    @PostMapping("/upload")
    public ResponseEntity<?> uploadResume(@RequestParam("file") MultipartFile file) {
        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body("Please select a file to upload");
            }

            Resume resume = resumeService.uploadAndConvertResume(file);
            
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Resume uploaded successfully");
            response.put("resumeId", resume.getId());
            response.put("fileName", resume.getOriginalFileName());
            response.put("fileSize", resume.getOriginalFileSize());
            response.put("uploadedAt", resume.getUploadedAt());
            
            return ResponseEntity.ok(response);
            
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to upload resume: " + e.getMessage());
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getResume(@PathVariable String id) {
        try {
            Resume resume = resumeService.getResumeById(id);
            
            // Return HTML content if available
            if (resume.getHtmlContent() != null) {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.TEXT_HTML);
                return new ResponseEntity<>(resume.getHtmlContent(), headers, HttpStatus.OK);
            }
            
            // Fallback to file download if no HTML content
            return getResumeFile(id);
            
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Resume not found: " + e.getMessage());
        }
    }

    @GetMapping("/{id}/file")
    public ResponseEntity<?> getResumeFile(@PathVariable String id) {
        try {
            Resume resume = resumeService.getResumeById(id);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", resume.getOriginalFileName());
            
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(resume.getOriginalFileData());
            
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Resume file not found: " + e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteResume(@PathVariable String id) {
        try {
            resumeService.deleteResume(id);
            return ResponseEntity.ok("Resume deleted successfully");
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Resume not found: " + e.getMessage());
        }
    }
}
