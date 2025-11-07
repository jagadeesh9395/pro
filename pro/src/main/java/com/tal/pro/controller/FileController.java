package com.tal.pro.controller;

import com.tal.pro.model.Candidate;
import com.tal.pro.repository.CandidateRepository;
import com.tal.pro.service.CandidateService;
import com.tal.pro.util.FileUploadUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Controller
@RequestMapping("/api/files")
@Slf4j
public class FileController {

    private final FileUploadUtil fileUploadUtil;
    private final CandidateService candidateService;
    private final CandidateRepository candidateRepository;

    @Autowired
    public FileController(FileUploadUtil fileUploadUtil,
                         CandidateService candidateService,
                         CandidateRepository candidateRepository) {
        this.fileUploadUtil = fileUploadUtil;
        this.candidateService = candidateService;
        this.candidateRepository = candidateRepository;
    }

    @PostMapping("/upload-resume")
    public String uploadResume(@RequestParam("file") MultipartFile file,
                             @AuthenticationPrincipal UserDetails userDetails,
                             RedirectAttributes redirectAttributes) {
        try {
            String username = userDetails.getUsername();
            String fileName = fileUploadUtil.storeFile(file);
            
            // Update candidate's resume information
            Candidate candidate = candidateRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Candidate not found"));
                
            // Delete old file if exists
            if (candidate.getResumeFileId() != null && !candidate.getResumeFileId().isEmpty()) {
                try {
                    fileUploadUtil.deleteFile(candidate.getResumeFileId());
                } catch (IOException e) {
                    log.warn("Failed to delete old resume file: " + e.getMessage());
                }
            }
            
            // Update candidate's resume info
            candidate.setResumeFileId(fileName);
            candidate.setResumeUrl("/api/files/resume/" + fileName);
            candidateRepository.save(candidate);
            
            redirectAttributes.addFlashAttribute("successMessage", "Resume uploaded successfully!");
            return "redirect:/candidate/dashboard";
            
        } catch (Exception e) {
            log.error("Failed to upload resume", e);
            redirectAttributes.addFlashAttribute("errorMessage", "Failed to upload resume: " + e.getMessage());
            return "redirect:/candidate/dashboard";
        }
    }

    @GetMapping("/resume/{filename:.+}")
    @ResponseBody
    public ResponseEntity<Resource> serveResume(@PathVariable String filename) {
        try {
            Path file = fileUploadUtil.loadFile(filename);
            Resource resource = new UrlResource(file.toUri());
            
            if (resource.exists() || resource.isReadable()) {
                return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(fileUploadUtil.getContentType(filename)))
                    .header(HttpHeaders.CONTENT_DISPOSITION, 
                        "attachment; filename=\"" + resource.getFilename() + "\"")
                    .body(resource);
            } else {
                throw new RuntimeException("Could not read the file!");
            }
        } catch (Exception e) {
            log.error("Error serving file: " + filename, e);
            return ResponseEntity.notFound().build();
        }
    }
    
    @GetMapping("/resume")
    public ResponseEntity<Resource> getCandidateResume(@AuthenticationPrincipal UserDetails userDetails) {
        try {
            String username = userDetails.getUsername();
            Candidate candidate = candidateRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Candidate not found"));
                
            if (candidate.getResumeFileId() == null || candidate.getResumeFileId().isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            
            return serveResume(candidate.getResumeFileId());
            
        } catch (Exception e) {
            log.error("Error getting candidate resume", e);
            return ResponseEntity.notFound().build();
        }
    }
}
