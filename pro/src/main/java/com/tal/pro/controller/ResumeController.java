package com.tal.pro.controller;
import com.tal.pro.model.Resume;
import com.tal.pro.service.ResumeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.Principal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/resumes")
@RequiredArgsConstructor
@Slf4j
public class ResumeController {

    private final ResumeService resumeService;

    @PostMapping("/upload")
    public ResponseEntity<?> uploadResume(@RequestParam("file") MultipartFile file, Principal principal) {
        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body("Please select a file to upload");
            }

            Resume resume = resumeService.uploadAndConvertResume(file, principal.getName());
            
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
    
    @PutMapping("/{id}")
    public ResponseEntity<?> updateResume(@PathVariable String id, 
                                        @RequestParam("file") MultipartFile file,
                                        Principal principal) {
        try {
            log.info("Received request to update resume with ID: {}", id);
            
            if (file == null || file.isEmpty()) {
                log.warn("No file provided for resume update");
                return ResponseEntity.badRequest().body("Please select a file to upload");
            }
            
            log.info("Processing resume update for user: {}", principal.getName());
            Resume updatedResume = resumeService.updateResume(id, file, principal.getName());
            
            if (updatedResume == null) {
                log.error("Failed to update resume: updateResume returned null");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("Failed to update resume");
            }
            
            log.info("Resume updated successfully. ID: {}", updatedResume.getId());
            
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Resume updated successfully");
            response.put("resumeId", updatedResume.getId());
            response.put("fileName", updatedResume.getOriginalFileName());
            response.put("fileSize", updatedResume.getOriginalFileSize());
            response.put("uploadedAt", updatedResume.getUploadedAt());
            
            return ResponseEntity.ok(response);
            
        } catch (IOException e) {
            log.error("Error updating resume: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to update resume: " + e.getMessage());
        } catch (RuntimeException e) {
            log.error("Error updating resume: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Resume not found or not authorized: " + e.getMessage());
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getResume(@PathVariable String id) {
        try {
            return resumeService.getResumeById(id)
                .map(resume -> {
                    // Return HTML content if available
                    if (resume.getHtmlContent() != null) {
                        HttpHeaders headers = new HttpHeaders();
                        headers.setContentType(MediaType.TEXT_HTML);
                        return new ResponseEntity<>(resume.getHtmlContent(), headers, HttpStatus.OK);
                    }
                    // Fallback to file download if no HTML content
                    return getResumeFile(id);
                })
                .orElse(ResponseEntity.notFound().build());
            
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Resume not found: " + e.getMessage());
        }
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<?> getResumeFile(@PathVariable String id) {
        try {
            Optional<Resume> resumeOpt = resumeService.getResumeById(id);
            if (resumeOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("Resume not found with id: " + id);
            }

            Resume resume = resumeOpt.get();
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
