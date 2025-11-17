package com.tal.pro.controller;

import com.tal.pro.model.Candidate;
import com.tal.pro.model.WorkExperience;
import com.tal.pro.repository.CandidateRepository;
import com.tal.pro.repository.WorkExperienceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/experience")
public class WorkExperienceController {

    @Autowired
    private WorkExperienceRepository workExperienceRepository;

    @Autowired
    private CandidateRepository candidateRepository;

    @PostMapping
    public ResponseEntity<?> addWorkExperience(@RequestBody WorkExperience workExperience, 
                                             Authentication authentication) {
        try {
            String username = authentication.getName();
            Candidate candidate = candidateRepository.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("Candidate not found"));
            
            workExperience.setCandidateId(candidate.getId());
            WorkExperience savedExperience = workExperienceRepository.save(workExperience);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("experience", savedExperience);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Failed to add work experience: " + e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateWorkExperience(@PathVariable String id,
                                               @RequestBody WorkExperience updatedExperience,
                                               Authentication authentication) {
        try {
            String username = authentication.getName();
            Candidate candidate = candidateRepository.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("Candidate not found"));
            
            WorkExperience experience = workExperienceRepository.findByIdAndCandidateId(id, candidate.getId())
                    .orElseThrow(() -> new RuntimeException("Work experience not found"));
            
            experience.setCompany(updatedExperience.getCompany());
            experience.setPosition(updatedExperience.getPosition());
            experience.setEmploymentType(updatedExperience.getEmploymentType());
            experience.setLocation(updatedExperience.getLocation());
            experience.setStartDate(updatedExperience.getStartDate());
            experience.setEndDate(updatedExperience.getEndDate());
            experience.setCurrentlyWorking(updatedExperience.isCurrentlyWorking());
            experience.setDescription(updatedExperience.getDescription());
            
            WorkExperience savedExperience = workExperienceRepository.save(experience);
            
            return ResponseEntity.ok().body(
                new HashMap<String, Object>() {{
                    put("success", true);
                    put("experience", savedExperience);
                }}
            );
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Failed to update work experience: " + e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteWorkExperience(@PathVariable String id,
                                                Authentication authentication) {
        try {
            String username = authentication.getName();
            Candidate candidate = candidateRepository.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("Candidate not found"));
            
            WorkExperience experience = workExperienceRepository.findByIdAndCandidateId(id, candidate.getId())
                    .orElseThrow(() -> new RuntimeException("Work experience not found"));
            
            workExperienceRepository.delete(experience);
            return ResponseEntity.ok().body("Work experience deleted successfully!");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to delete work experience: " + e.getMessage());
        }
    }
    
    @GetMapping
    public ResponseEntity<?> getWorkExperiences(Authentication authentication) {
        try {
            String username = authentication.getName();
            Candidate candidate = candidateRepository.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("Candidate not found"));
            
            return ResponseEntity.ok(workExperienceRepository.findByCandidateId(candidate.getId()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to fetch work experience data: " + e.getMessage());
        }
    }

    @GetMapping("/{id}")
    @ResponseBody
    public ResponseEntity<WorkExperience> getWorkExperience(@PathVariable String id, Authentication authentication) {
        try {
            String username = authentication.getName();
            Candidate candidate = candidateRepository.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("Candidate not found"));
            
            WorkExperience experience = workExperienceRepository.findByIdAndCandidateId(id, candidate.getId())
                    .orElseThrow(() -> new RuntimeException("Work experience not found"));
            
            return ResponseEntity.ok(experience);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }
}
