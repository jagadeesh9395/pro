package com.tal.pro.service.impl;

import com.tal.pro.criteria.ResumeSearchCriteria;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.xml.sax.SAXException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeServiceImpl implements ResumeService {

    private final ResumeRepository resumeRepository;
    private final CandidateRepository candidateRepository;

    @Override
    @Transactional
    public Resume uploadAndConvertResume(MultipartFile file, String username) throws IOException {
        log.info("Processing new resume upload for user: {}", username);

        // Check file size before processing to prevent overflow
        validateFile(file);

        // Get the candidate
        Candidate candidate = candidateRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Candidate not found: " + username));

        // Create Resume document
        Resume resume = new Resume();
        resume.setOriginalFileName(file.getOriginalFilename());
        resume.setOriginalFileType(file.getContentType());
        resume.setOriginalFileSize(file.getSize());
        resume.setUploadedAt(LocalDateTime.now());
        resume.setOriginalFileData(file.getBytes());
        resume.setCandidate(candidate);

        // Convert to HTML using Tika
        String htmlContent = convertToHtml(file.getBytes());
        resume.setHtmlContent(htmlContent);

        // Save to MongoDB
        Resume savedResume = resumeRepository.save(resume);
        log.info("New resume saved with ID: {} for user: {}", savedResume.getId(), username);

        // Update candidate's resume reference
        candidate.setResume(savedResume);
        candidateRepository.save(candidate);

        return savedResume;
    }

    @Override
    @Transactional
    public Resume updateResume(String id, MultipartFile file, String username) throws IOException {
        log.info("Updating resume with ID: {} for user: {}", id, username);

        // Check file size before processing to prevent overflow
        validateFile(file);

        // Get the candidate
        Candidate candidate = candidateRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Candidate not found: " + username));

        // Get the existing resume and verify ownership
        Resume existingResume = resumeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Resume not found: " + id));

        if (!existingResume.getCandidate().getId().equals(candidate.getId())) {
            throw new SecurityException("Not authorized to update this resume");
        }

        // Create a new Resume instance with the same ID to ensure update operation
        Resume updatedResume = new Resume();
        updatedResume.setId(existingResume.getId()); // This is crucial for update
        updatedResume.setOriginalFileName(file.getOriginalFilename());
        updatedResume.setOriginalFileType(file.getContentType());
        updatedResume.setOriginalFileSize(file.getSize());
        updatedResume.setUploadedAt(LocalDateTime.now());
        updatedResume.setCandidate(candidate);
        
        // Update the file data and convert to HTML
        byte[] fileData = file.getBytes();
        updatedResume.setOriginalFileData(fileData);
        
        // Convert to HTML using Tika
        String htmlContent = convertToHtml(fileData);
        updatedResume.setHtmlContent(htmlContent);
        
        try {
            // Save the updated resume - this should update the existing document
            Resume savedResume = resumeRepository.save(updatedResume);
            
            // Update the candidate's resume reference if needed
            if (candidate.getResume() == null || !candidate.getResume().getId().equals(savedResume.getId())) {
                candidate.setResume(savedResume);
                candidateRepository.save(candidate);
            }
            
            log.info("Resume updated with ID: {} for user: {}", savedResume.getId(), username);
            return savedResume;
        } catch (Exception e) {
            log.error("Error updating resume: {}", e.getMessage(), e);
            throw new IOException("Failed to update resume: " + e.getMessage(), e);
        }
    }

    private void validateFile(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IOException("File is empty");
        }

        // Check file size before processing to prevent overflow
        long maxSize = 100 * 1024 * 1024; // 100MB limit
        if (file.getSize() > maxSize) {
            throw new IOException("File size exceeds maximum limit of 100MB: " + file.getSize() + " bytes");
        }

        // Validate file type
        String contentType = file.getContentType();
        if (contentType == null || !(contentType.equals("application/pdf") ||
                contentType.equals("application/msword") ||
                contentType.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))) {
            throw new IOException("Unsupported file type. Please upload a PDF, DOC, or DOCX file.");
        }
    }

    @Override
    public Optional<Resume> getResumeById(String id) {
        return resumeRepository.findById(id);
    }

    @Override
    @Transactional
    public void deleteResume(String id) {
        // First find the resume to get the candidate ID
        Resume resume = resumeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Resume not found: " + id));

        // Remove the resume reference from the candidate
        candidateRepository.findById(resume.getCandidate().getId()).ifPresent(candidate -> {
            if (candidate.getResume() != null && candidate.getResume().getId().equals(id)) {
                candidate.setResume(null);
                candidateRepository.save(candidate);
            }
        });

        // Delete the resume
        resumeRepository.deleteById(id);
        log.info("Resume deleted with ID: {}", id);
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
            htmlBuilder.append("strong, b { font-weight: 600; color: #2c3e50; }\n");
            htmlBuilder.append("em, i { font-style: italic; color: #5a6c7d; }\n");
            htmlBuilder.append("a { color: #667eea; text-decoration: none; }\n");
            htmlBuilder.append("a:hover { text-decoration: underline; }\n");
            htmlBuilder.append("</style>\n");
            htmlBuilder.append("</head>\n<body>\n");
            htmlBuilder.append("<pre>").append(escapeHtml(textContent)).append("</pre>\n");
            htmlBuilder.append("</body>\n</html>");

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


    @Override
    public String getResumeHtmlContent(String id, boolean maskPersonalInfo) {
        Optional<Resume> resumeOpt = resumeRepository.findById(id);
        if (resumeOpt.isEmpty()) {
            return null;
        }

        String htmlContent = resumeOpt.get().getHtmlContent();

        if (maskPersonalInfo) {
            // Extract the content between <pre> tags to avoid breaking HTML
            Pattern pattern = Pattern.compile("(?s)(?<=<pre>)(.*?)(?=</pre>)");
            Matcher matcher = pattern.matcher(htmlContent);
            StringBuffer sb = new StringBuffer();

            while (matcher.find()) {
                String matchedText = matcher.group(1);
                String processedText = escapeHtml(maskPersonalInfo(matchedText));
                matcher.appendReplacement(sb, Matcher.quoteReplacement(processedText));
            }
            matcher.appendTail(sb);
            htmlContent = sb.toString();
        }

        return htmlContent;
    }

    public String getResumeHtmlContent(String id) {
        return getResumeHtmlContent(id, false);
    }

    /**
     * Search resumes by various criteria
     */
    public List<Resume> searchResumes(ResumeSearchCriteria criteria) {
        log.info("Searching resumes with criteria: {}", criteria);

        List<Resume> results = new ArrayList<>();

        // If no criteria specified, return all resumes
        if (!hasAnyCriteria(criteria)) {
            List<Resume> allResumes = resumeRepository.findAll();
            log.debug("All resumes in database ({}):", allResumes.size());
            allResumes.forEach(resume ->
                    log.debug("Resume ID: {}, Uploaded At: {}", resume.getId(), resume.getUploadedAt())
            );
            return allResumes;
        }

        // Try specific searches first
        boolean hasResults = false;

        // Basic text search across multiple fields - search HTML content since structured fields may not be populated
        if (criteria.getKeyword() != null && !criteria.getKeyword().trim().isEmpty()) {
            List<Resume> keywordResults = resumeRepository.findByHtmlContent(criteria.getKeyword());
            log.debug("Keyword search for '{}' returned {} results", criteria.getKeyword(), keywordResults.size());
            if (!keywordResults.isEmpty()) {
                results.addAll(keywordResults);
                hasResults = true;
            }
        }

        // Name search (OR logic within name fields)
//        if (criteria.getFirstName() != null && !criteria.getFirstName().trim().isEmpty()) {
//            List<Resume> nameResults = resumeRepository.findByFirstNameIgnoreCase(criteria.getFirstName());
//            log.debug("First name search for '{}' returned {} results", criteria.getFirstName(), nameResults.size());
//            if (!nameResults.isEmpty()) {
//                results.addAll(nameResults);
//                hasResults = true;
//            }
//        }
//
//        if (criteria.getLastName() != null && !criteria.getLastName().trim().isEmpty()) {
//            List<Resume> nameResults = resumeRepository.findByLastNameIgnoreCase(criteria.getLastName());
//            log.debug("Last name search for '{}' returned {} results", criteria.getLastName(), nameResults.size());
//            if (!nameResults.isEmpty()) {
//                results.addAll(nameResults);
//                hasResults = true;
//            }
//        }
//
//        if (criteria.getFullName() != null && !criteria.getFullName().trim().isEmpty()) {
//            List<Resume> nameResults = resumeRepository.findByFirstNameIgnoreCaseOrLastNameIgnoreCase(
//                    criteria.getFullName(), criteria.getFullName());
//            log.debug("Full name search for '{}' returned {} results", criteria.getFullName(), nameResults.size());
//            if (!nameResults.isEmpty()) {
//                results.addAll(nameResults);
//                hasResults = true;
//            }
//        }
//
//        // Contact search
//        if (criteria.getEmail() != null && !criteria.getEmail().trim().isEmpty()) {
//            List<Resume> emailResults = resumeRepository.findByEmail(criteria.getEmail());
//            if (!emailResults.isEmpty()) {
//                results.addAll(emailResults);
//                hasResults = true;
//            }
//        }

//        if (criteria.getPhone() != null && !criteria.getPhone().trim().isEmpty()) {
//            // Search phone in HTML content since structured phone field may not be populated
//            List<Resume> phoneResults = resumeRepository.findByHtmlContent(criteria.getPhone());
//            if (!phoneResults.isEmpty()) {
//                results.addAll(phoneResults);
//                hasResults = true;
//            }
//        }

//        // Location search
//        if (criteria.getCity() != null && !criteria.getCity().trim().isEmpty()) {
//            List<Resume> cityResults = resumeRepository.findByCityIgnoreCase(criteria.getCity());
//            if (!cityResults.isEmpty()) {
//                results.addAll(cityResults);
//                hasResults = true;
//            }
//        }
//
//        if (criteria.getState() != null && !criteria.getState().trim().isEmpty()) {
//            List<Resume> stateResults = resumeRepository.findByStateIgnoreCase(criteria.getState());
//            if (!stateResults.isEmpty()) {
//                results.addAll(stateResults);
//                hasResults = true;
//            }
//        }
//
//        // Skills search
//        if (criteria.getProgrammingLanguages() != null && !criteria.getProgrammingLanguages().isEmpty()) {
//            List<Resume> skillResults = resumeRepository.findByProgrammingLanguagesIn(criteria.getProgrammingLanguages());
//            if (!skillResults.isEmpty()) {
//                results.addAll(skillResults);
//                hasResults = true;
//            }
//        }
//
//        if (criteria.getFrameworks() != null && !criteria.getFrameworks().isEmpty()) {
//            List<Resume> skillResults = resumeRepository.findByFrameworksIn(criteria.getFrameworks());
//            if (!skillResults.isEmpty()) {
//                results.addAll(skillResults);
//                hasResults = true;
//            }
//        }
//
//        // Experience search
//        if (criteria.getCompanyName() != null && !criteria.getCompanyName().trim().isEmpty()) {
//            List<Resume> expResults = resumeRepository.findByCompanyNameRegex(criteria.getCompanyName());
//            if (!expResults.isEmpty()) {
//                results.addAll(expResults);
//                hasResults = true;
//            }
//        }
//
//        if (criteria.getJobTitle() != null && !criteria.getJobTitle().trim().isEmpty()) {
//            List<Resume> expResults = resumeRepository.findByJobTitleRegex(criteria.getJobTitle());
//            if (!expResults.isEmpty()) {
//                results.addAll(expResults);
//                hasResults = true;
//            }
//        }
//
//        // Education search
//        if (criteria.getDegree() != null && !criteria.getDegree().trim().isEmpty()) {
//            List<Resume> eduResults = resumeRepository.findByDegreeRegex(criteria.getDegree());
//            if (!eduResults.isEmpty()) {
//                results.addAll(eduResults);
//                hasResults = true;
//            }
//        }
//
//        if (criteria.getInstitution() != null && !criteria.getInstitution().trim().isEmpty()) {
//            List<Resume> eduResults = resumeRepository.findByInstitutionRegex(criteria.getInstitution());
//            if (!eduResults.isEmpty()) {
//                results.addAll(eduResults);
//                hasResults = true;
//            }
//        }
//
//        // Date range search
//        if ((criteria.getUploadedAfter() != null && !criteria.getUploadedAfter().trim().isEmpty()) ||
//                (criteria.getUploadedBefore() != null && !criteria.getUploadedBefore().trim().isEmpty())) {
//            LocalDateTime startDate = criteria.getUploadedAfter() != null && !criteria.getUploadedAfter().trim().isEmpty() ?
//                    LocalDateTime.parse(criteria.getUploadedAfter()) : LocalDateTime.MIN;
//            LocalDateTime endDate = criteria.getUploadedBefore() != null && !criteria.getUploadedBefore().trim().isEmpty() ?
//                    LocalDateTime.parse(criteria.getUploadedBefore()) : LocalDateTime.now();
//
//            List<Resume> dateResults = resumeRepository.findByUploadedAtBetween(startDate, endDate);
//            if (!dateResults.isEmpty()) {
//                results.addAll(dateResults);
//                hasResults = true;
//            }
//        }

        // If no specific searches returned results, return empty list instead of all resumes
        if (!hasResults) {
            log.info("No search results found for criteria: {}, returning empty results", criteria);
            return new ArrayList<>();
        }

        // Remove duplicates and return
        List<Resume> finalResults = results.stream()
                .distinct()
                .collect(Collectors.toList());

        log.info("Search completed. Total results: {} (from {} initial matches)", finalResults.size(), results.size());
        return finalResults;
    }

    /**
     * Check if search criteria has any non-null values
     */
    public boolean hasAnyCriteria(ResumeSearchCriteria criteria) {
        return criteria.getKeyword() != null ||
                (criteria.getUploadedAfter() != null && !criteria.getUploadedAfter().trim().isEmpty()) ||
                (criteria.getUploadedBefore() != null && !criteria.getUploadedBefore().trim().isEmpty());

//        criteria.getFirstName() != null || criteria.getLastName() != null ||
//                criteria.getFullName() != null || criteria.getEmail() != null ||
//                criteria.getPhone() != null || criteria.getCity() != null ||
//                criteria.getState() != null || criteria.getProgrammingLanguages() != null ||
//                criteria.getFrameworks() != null || criteria.getCompanyName() != null ||
//                criteria.getJobTitle() != null || criteria.getDegree() != null ||
//                criteria.getInstitution() != null ||
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private String maskPersonalInfo(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        // Mask email addresses - keep first 3 chars, then ***@mail
        text = text.replaceAll("(?i)([a-zA-Z0-9._%+-]{3})[a-zA-Z0-9._%+-]*@[a-z0-9.-]+\\.[a-z]{2,}", "$1***@mail");

        // Mask phone numbers - keep last 4 digits, mask the rest with *
        text = text.replaceAll("(\\+?\\(?\\d{1,3}\\)?[-.\s]?)?\\d{2,3}[-.\s]?\\d{2,3}[-.\s]?(\\d{4})", "******$2");

        return text;
    }

}
