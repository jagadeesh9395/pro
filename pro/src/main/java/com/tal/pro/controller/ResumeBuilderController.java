package com.tal.pro.controller;

import com.tal.pro.model.ResumeDocument;
import com.tal.pro.service.AiSuggestionService;
import com.tal.pro.service.PublicResumeService;
import com.tal.pro.service.ResumeParserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

@Controller
public class ResumeBuilderController {

    @Autowired
    private PublicResumeService resumeService;

    @Autowired
    private ResumeParserService resumeParserService;

    @Autowired
    private AiSuggestionService aiSuggestionService;

    @GetMapping("/resume-builder")
    public String resumeBuilder(@RequestParam(required = false) String id, Model model) {
        if (id != null) {
            model.addAttribute("resumeId", id);
        }
        return "resume-builder";
    }

    @PostMapping("/api/public/resume")
    @ResponseBody
    public ResponseEntity<ResumeDocument> saveResume(@RequestBody ResumeDocument resume) {
        System.out.println("Received save request for resume: " + resume);
        try {
            ResumeDocument saved = resumeService.saveResume(resume);
            System.out.println("Successfully saved resume with ID: " + saved.getId());
            return ResponseEntity.ok(saved);
        } catch (Exception e) {
            System.err.println("Error saving resume: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    @GetMapping("/api/public/resume/{id}")
    @ResponseBody
    public ResponseEntity<ResumeDocument> getResume(@PathVariable String id) {
        Optional<ResumeDocument> resume = resumeService.getResume(id);
        return resume.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/api/public/resume/upload")
    @ResponseBody
    public ResponseEntity<ResumeDocument> uploadResume(@RequestParam("file") MultipartFile file) {
        ResumeDocument parsed = resumeParserService.parseResume(file);
        return ResponseEntity.ok(parsed);
    }

    @PostMapping("/api/public/resume/analyze")
    @ResponseBody
    public ResponseEntity<List<String>> analyzeResume(@RequestBody ResumeDocument resume) {
        List<String> suggestions = aiSuggestionService.analyzeResume(resume);
        return ResponseEntity.ok(suggestions);
    }
}
