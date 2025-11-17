package com.tal.pro.controller;

import com.tal.pro.model.Candidate;
import com.tal.pro.model.Education;
import com.tal.pro.repository.CandidateRepository;
import com.tal.pro.repository.EducationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/education")
public class EducationController {

    @Autowired
    private EducationRepository educationRepository;

    @Autowired
    private CandidateRepository candidateRepository;

    @PostMapping
    public ResponseEntity<?> addEducation(@RequestBody Education education, 
                                        Authentication authentication) {
        try {
            String username = authentication.getName();
            Candidate candidate = candidateRepository.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("Candidate not found"));
            
            education.setCandidateId(candidate.getId());
            Education savedEducation = educationRepository.save(education);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("education", savedEducation);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Failed to add education: " + e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateEducation(@PathVariable String id,
                                          @RequestBody Education updatedEducation,
                                          Authentication authentication) {
        try {
            String username = authentication.getName();
            Candidate candidate = candidateRepository.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("Candidate not found"));
            
            Education education = educationRepository.findByIdAndCandidateId(id, candidate.getId())
                    .orElseThrow(() -> new RuntimeException("Education not found"));
            
            education.setInstitution(updatedEducation.getInstitution());
            education.setDegree(updatedEducation.getDegree());
            education.setFieldOfStudy(updatedEducation.getFieldOfStudy());
            education.setStartDate(updatedEducation.getStartDate());
            education.setEndDate(updatedEducation.getEndDate());
            education.setCurrentlyStudying(updatedEducation.isCurrentlyStudying());
            education.setDescription(updatedEducation.getDescription());
            
            Education savedEducation = educationRepository.save(education);
            
            return ResponseEntity.ok().body(
                new HashMap<String, Object>() {{
                    put("success", true);
                    put("education", savedEducation);
                }}
            );
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Failed to update education: " + e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteEducation(@PathVariable String id,
                                          Authentication authentication) {
        try {
            String username = authentication.getName();
            Candidate candidate = candidateRepository.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("Candidate not found"));
            
            Education education = educationRepository.findByIdAndCandidateId(id, candidate.getId())
                    .orElseThrow(() -> new RuntimeException("Education not found"));
            
            educationRepository.delete(education);
            return ResponseEntity.ok().body("Education deleted successfully!");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to delete education: " + e.getMessage());
        }
    }
    
    @GetMapping
    public ResponseEntity<?> getEducations(Authentication authentication) {
        try {
            String username = authentication.getName();
            Candidate candidate = candidateRepository.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("Candidate not found"));
            
            return ResponseEntity.ok(educationRepository.findByCandidateId(candidate.getId()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to fetch education data: " + e.getMessage());
        }
    }

    @GetMapping("/{id}")
    @ResponseBody
    public ResponseEntity<Education> getEducation(@PathVariable String id, Authentication authentication) {
        try {
            String username = authentication.getName();
            Candidate candidate = candidateRepository.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("Candidate not found"));
            
            Education education = educationRepository.findByIdAndCandidateId(id, candidate.getId())
                    .orElseThrow(() -> new RuntimeException("Education not found"));
            
            return ResponseEntity.ok(education);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }
}
