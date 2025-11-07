package com.tal.pro.service;

import com.mongodb.client.gridfs.model.GridFSFile;
import com.tal.pro.model.Candidate;
import com.tal.pro.model.Education;
import com.tal.pro.model.Experience;
import com.tal.pro.model.Reference;
import com.tal.pro.payload.request.ProfileUpdateRequest;
import com.tal.pro.repository.CandidateRepository;
import com.tal.pro.repository.EducationRepository;
import com.tal.pro.repository.ExperienceRepository;
import com.tal.pro.repository.ReferenceRepository;
import org.apache.commons.io.IOUtils;
import org.apache.tika.exception.TikaException;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.xml.sax.SAXException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class CandidateService {

    @Autowired
    private CandidateRepository candidateRepository;

    @Autowired
    private EducationRepository educationRepository;

    @Autowired
    private ExperienceRepository experienceRepository;

    @Autowired
    private ReferenceRepository referenceRepository;

    @Autowired
    private FileStorageService fileStorageService;

    @Autowired
    private GridFsTemplate gridFsTemplate;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Transactional(readOnly = true)
    public Candidate getCandidateProfile(String candidateId) {
        return candidateRepository.findById(candidateId)
                .orElseThrow(() -> new UsernameNotFoundException("Candidate not found with id: " + candidateId));
    }

    @Transactional
    public void addEducation(String candidateId, Education education) {
        education.setCandidateId(candidateId);
        educationRepository.save(education);
    }

    @Transactional
    public void addExperience(String candidateId, Experience experience) {
        experience.setCandidateId(candidateId);
        experienceRepository.save(experience);
    }

    @Transactional
    public void addReference(String candidateId, Reference reference) {
        reference.setCandidateId(candidateId);
        referenceRepository.save(reference);
    }

    @Transactional(readOnly = true)
    public List<Education> getEducations(String candidateId) {
        return educationRepository.findByCandidateId(candidateId);
    }

    @Transactional(readOnly = true)
    public List<Experience> getExperiences(String candidateId) {
        return experienceRepository.findByCandidateId(candidateId);
    }

    @Transactional(readOnly = true)
    public List<Reference> getReferences(String candidateId) {
        return referenceRepository.findByCandidateId(candidateId);
    }

    @Transactional
    public void deleteEducation(String educationId, String candidateId) {
        educationRepository.deleteByIdAndCandidateId(educationId, candidateId);
    }

    @Transactional
    public void deleteExperience(String experienceId, String candidateId) {
        experienceRepository.deleteByIdAndCandidateId(experienceId, candidateId);
    }

    @Transactional
    public void deleteReference(String referenceId, String candidateId) {
        referenceRepository.deleteByIdAndCandidateId(referenceId, candidateId);
    }

    /**
     * Updates a candidate's profile information and handles resume upload if provided.
     *
     * @param username The username of the candidate to update
     * @param profileUpdateRequest The updated profile data
     * @return The updated Candidate entity
     * @throws UsernameNotFoundException if no candidate is found with the given username
     * @throws IOException if there's an error processing the resume file
     * @throws IllegalArgumentException if the file type is not supported
     */
    @Transactional
    public Candidate updateProfile(String username, ProfileUpdateRequest profileUpdateRequest) 
            throws IOException, TikaException, SAXException {
        
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username cannot be null or empty");
        }
        if (profileUpdateRequest == null) {
            throw new IllegalArgumentException("Profile update request cannot be null");
        }

        Candidate candidate = candidateRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with username: " + username));

        // Initialize work experiences if null
        if (candidate.getWorkExperiences() == null) {
            candidate.setWorkExperiences(new ArrayList<>());
        }

        updateBasicProfileInfo(candidate, profileUpdateRequest);
        
        // Handle resume file upload if present
        if (profileUpdateRequest.getResumeFile() != null && !profileUpdateRequest.getResumeFile().isEmpty()) {
            processResumeUpload(candidate, profileUpdateRequest.getResumeFile());
        }

        candidate.setUpdatedAt(LocalDate.now());
        return candidateRepository.save(candidate);
    }

    /**
     * Updates basic profile information from the update request to the candidate entity.
     */
    private void updateBasicProfileInfo(Candidate candidate, ProfileUpdateRequest request) {
        if (request.getFullName() != null) {
            candidate.setFullName(request.getFullName());
        }
        if (request.getEmail() != null) {
            candidate.setEmail(request.getEmail());
        }
        if (request.getPhoneNumber() != null) {
            candidate.setPhoneNumber(request.getPhoneNumber());
        }
        if (request.getCurrentAddress() != null) {
            candidate.setCurrentAddress(request.getCurrentAddress());
        }
        if (request.getSkills() != null) {
            candidate.setSkills(request.getSkills());
        }
        if (request.getCertifications() != null) {
            candidate.setCertifications(request.getCertifications());
        }
        if (request.getCoverLetter() != null) {
            candidate.setCoverLetter(request.getCoverLetter());
        }
    }

    /**
     * Processes resume file upload including validation and storage.
     */
    private void processResumeUpload(Candidate candidate, MultipartFile resumeFile)
            throws IOException, TikaException, SAXException {
        
        String originalFilename = resumeFile.getOriginalFilename();
        if (originalFilename == null || originalFilename.trim().isEmpty()) {
            throw new IllegalArgumentException("Resume file name cannot be empty");
        }

        String fileExtension = fileStorageService.getFileExtension(originalFilename);
        if (!fileStorageService.isSupportedFileType(fileExtension)) {
            throw new IllegalArgumentException("Unsupported file type. Please upload PDF, DOC, DOCX, TXT, or RTF files.");
        }

        // Parse and store the resume content
        fileStorageService.parseFile(resumeFile);

        // Store the file in GridFS and get the file ID
        String fileId = storeResumeFile(
                originalFilename,
                resumeFile.getContentType(),
                resumeFile.getBytes()
        );

        // Update candidate's resume information
        candidate.setResumeFileId(fileId);
        candidate.setResumeUrl("/api/candidate/resume/" + fileId);
    }

    private String storeResumeFile(String filename, String contentType, byte[] content) {
        // Store file in GridFS and return the file ID
        ObjectId fileId = gridFsTemplate.store(
                new ByteArrayInputStream(content),
                filename,
                contentType
        );
        return fileId.toString();
    }
}
