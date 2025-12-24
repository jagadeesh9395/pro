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

    @Autowired
    private com.tal.pro.service.resume.SmartResumeService smartResumeService;

    @GetMapping("/resume-builder/upload")
    public String showUploadPage() {
        return "smart-resume/upload";
    }

    @PostMapping("/resume-builder/upload")
    public String handleUpload(@RequestParam("file") MultipartFile file, jakarta.servlet.http.HttpSession session)
            throws Exception {
        com.tal.pro.service.resume.ResumeAnalysisResult result = smartResumeService.initialAnalyze(file);
        session.setAttribute("analysis", result);
        return "redirect:/resume-builder/welcome";
    }

    @GetMapping("/resume-builder/welcome")
    public String showWelcomePage(jakarta.servlet.http.HttpSession session, Model model) {
        Object analysis = session.getAttribute("analysis");
        if (analysis == null)
            return "redirect:/resume-builder/upload";
        model.addAttribute("analysis", analysis);
        return "smart-resume/welcome";
    }

    @GetMapping("/resume-builder/analysis")
    public String showStrategyPage(jakarta.servlet.http.HttpSession session, Model model) {
        Object analysis = session.getAttribute("analysis");
        if (analysis == null)
            return "redirect:/resume-builder/upload";
        model.addAttribute("analysis", analysis);
        return "smart-resume/strategy";
    }

    @GetMapping("/resume-builder/templates")
    public String showTemplatesPage(jakarta.servlet.http.HttpSession session, Model model) {
        return "smart-resume/templates";
    }

    @PostMapping("/resume-builder/edit/save")
    @ResponseBody
    public ResponseEntity<String> saveSection(@RequestBody com.tal.pro.service.resume.ResumeAnalysisResult updatedData,
            jakarta.servlet.http.HttpSession session) {
        com.tal.pro.service.resume.ResumeAnalysisResult analysis = (com.tal.pro.service.resume.ResumeAnalysisResult) session
                .getAttribute("analysis");
        if (analysis == null)
            return ResponseEntity.status(401).body("Session expired");

        // Update the session data
        if (updatedData.getFullName() != null)
            analysis.setFullName(updatedData.getFullName());
        if (updatedData.getSummary() != null)
            analysis.setSummary(updatedData.getSummary());
        if (updatedData.getExtractedData() != null) {
            ResumeDocument currentDoc = analysis.getExtractedData();
            ResumeDocument newDoc = updatedData.getExtractedData();

            if (newDoc.getEmail() != null)
                currentDoc.setEmail(newDoc.getEmail());
            if (newDoc.getPhone() != null)
                currentDoc.setPhone(newDoc.getPhone());
            if (newDoc.getLinkedinUrl() != null)
                currentDoc.setLinkedinUrl(newDoc.getLinkedinUrl());
            if (newDoc.getExperience() != null)
                currentDoc.setExperience(newDoc.getExperience());
            if (newDoc.getEducation() != null)
                currentDoc.setEducation(newDoc.getEducation());
            if (newDoc.getSkills() != null)
                currentDoc.setSkills(newDoc.getSkills());
            if (newDoc.getSummary() != null)
                currentDoc.setSummary(newDoc.getSummary());
        }

        session.setAttribute("analysis", analysis);
        return ResponseEntity.ok("Saved");
    }

    @GetMapping("/resume-builder/edit/{section}")
    public String editSection(@PathVariable String section, jakarta.servlet.http.HttpSession session, Model model) {
        com.tal.pro.service.resume.ResumeAnalysisResult analysis = (com.tal.pro.service.resume.ResumeAnalysisResult) session
                .getAttribute("analysis");
        if (analysis == null)
            return "redirect:/resume-builder/upload";

        model.addAttribute("analysis", analysis);
        model.addAttribute("section", section);

        // Map section to specific fragment/form
        return "smart-resume/edit-" + section;
    }

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
