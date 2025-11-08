package com.tal.pro.service.impl;

import com.tal.pro.model.Candidate;
import com.tal.pro.model.Resume;
import com.tal.pro.repository.CandidateRepository;
import com.tal.pro.repository.ResumeRepository;
import com.tal.pro.service.ResumeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.xml.sax.SAXException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeServiceImpl implements ResumeService {

    private final ResumeRepository resumeRepository;
    private final CandidateRepository candidateRepository;

    @Override
    @Transactional
    public Resume uploadAndConvertResume(MultipartFile file) throws IOException {
        log.info("Processing file: {}", file.getOriginalFilename());

        // Check file size before processing to prevent overflow
        long maxSize = 100 * 1024 * 1024; // 100MB limit
        if (file.getSize() > maxSize) {
            throw new IOException("File size exceeds maximum limit of 100MB: " + file.getSize() + " bytes");
        }

        // Get current authenticated user (candidate)
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String currentUsername = authentication.getName();

        // Find the candidate
        Candidate candidate = candidateRepository.findByUsername(currentUsername)
                .orElseThrow(() -> new RuntimeException("Candidate not found with email: " + currentUsername));

        // Create Resume document
        Resume resume = new Resume();
        resume.setOriginalFileName(file.getOriginalFilename());
        resume.setOriginalFileType(file.getContentType());
        resume.setOriginalFileSize(file.getSize());
        resume.setUploadedAt(LocalDateTime.now());
        resume.setOriginalFileData(file.getBytes());

        // Set the candidate reference
        resume.setCandidate(candidate);


        // Convert to HTML using Tika
        String htmlContent = convertToHtml(file.getBytes());
        resume.setHtmlContent(htmlContent);

        // Save to MongoDB
        Resume savedResume = resumeRepository.save(resume);
        log.info("Resume saved with ID: {}", savedResume.getId());
        
        // Update candidate's resume reference and URL
        candidate.setResume(savedResume);
        candidate.setResumeUrl("/api/resumes/" + savedResume.getId());
        candidateRepository.save(candidate);
        
        log.info("Updated candidate {} with resume ID: {}", candidate.getUsername(), savedResume.getId());

        return savedResume;
    }

    @Override
    public Resume getResumeById(String id) {
        return resumeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Resume not found with id: " + id));
    }

    @Override
    public void deleteResume(String id) {
        if (!resumeRepository.existsById(id)) {
            throw new RuntimeException("Resume not found with id: " + id);
        }
        resumeRepository.deleteById(id);
        log.info("Deleted resume with ID: {}", id);
    }

    /**
     * Convert document to HTML format using Apache Tika
     */
    private String convertToHtml(byte[] fileData) throws IOException {
        try (InputStream inputStream = new ByteArrayInputStream(fileData)) {
            // Create parser and metadata
            Parser parser = new AutoDetectParser();
            BodyContentHandler handler = new BodyContentHandler(100 * 1024 * 1024); // 100MB limit to prevent overflow
            Metadata metadata = new Metadata();
            ParseContext parseContext = new ParseContext();

            // Parse document
            parser.parse(inputStream, handler, metadata, parseContext);

            // Get text content and wrap in HTML
            String textContent = handler.toString();

            // Create formatted HTML
            StringBuilder htmlBuilder = new StringBuilder();
            htmlBuilder.append("<!DOCTYPE html>\n");
            htmlBuilder.append("<html>\n<head>\n");
            htmlBuilder.append("<meta charset=\"UTF-8\">\n");
            htmlBuilder.append("<title>Resume</title>\n");
            htmlBuilder.append("<style>\n");
            htmlBuilder.append("body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; line-height: 1.8; padding: 40px; max-width: 1000px; margin: 0 auto; background: #f5f5f5; color: #333; }\n");
            htmlBuilder.append("pre { white-space: pre-wrap; word-wrap: break-word; font-family: 'Segoe UI', Arial, sans-serif; background: white; padding: 30px; border-radius: 8px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); font-size: 14px; line-height: 1.8; }\n");
            htmlBuilder.append("h1 { font-size: 28px; font-weight: 700; color: #2c3e50; margin: 20px 0 10px 0; border-bottom: 3px solid #667eea; padding-bottom: 10px; }\n");
            htmlBuilder.append("h2 { font-size: 24px; font-weight: 600; color: #34495e; margin: 18px 0 8px 0; border-bottom: 2px solid #95a5a6; padding-bottom: 8px; }\n");
            htmlBuilder.append("h3 { font-size: 20px; font-weight: 600; color: #4a5568; margin: 15px 0 8px 0; }\n");
            htmlBuilder.append("h4 { font-size: 18px; font-weight: 600; color: #5a6c7d; margin: 12px 0 6px 0; }\n");
            htmlBuilder.append("p { margin: 10px 0; line-height: 1.8; }\n");
            htmlBuilder.append("table { border-collapse: collapse; width: 100%; margin: 15px 0; background: white; box-shadow: 0 1px 3px rgba(0,0,0,0.1); }\n");
            htmlBuilder.append("th { background: #667eea; color: white; padding: 12px; text-align: left; font-weight: 600; border: 1px solid #5568d3; }\n");
            htmlBuilder.append("td { padding: 10px 12px; border: 1px solid #e1e8ed; }\n");
            htmlBuilder.append("tr:nth-child(even) { background: #f8f9fa; }\n");
            htmlBuilder.append("tr:hover { background: #e9ecef; }\n");
            htmlBuilder.append("ul, ol { margin: 10px 0; padding-left: 30px; }\n");
            htmlBuilder.append("li { margin: 5px 0; line-height: 1.6; }\n");
            htmlBuilder.append("em, i { font-style: italic; color: #5a6c7d; }\n");
            htmlBuilder.append("a { color: #667eea; text-decoration: none; }\n");
            htmlBuilder.append("a:hover { text-decoration: underline; }\n");
            htmlBuilder.append("</style>\n");
            htmlBuilder.append("</head>\n<body>\n");
            // Convert to HTML using Tika

            return htmlBuilder.toString();

        } catch (SAXException | TikaException e) {
            log.error("Error converting document to HTML", e);
            if (e.getMessage() != null && e.getMessage().contains("limit")) {
                throw new IOException("Document is too large to process (exceeds 100MB limit)", e);
            }
            throw new IOException("Failed to convert document: " + e.getMessage(), e);
        }
    }

    /**
     * Escape HTML special characters
     */
    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
