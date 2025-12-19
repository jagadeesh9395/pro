package com.tal.pro.service;

import com.tal.pro.model.ResumeDocument;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
/**
 * Service for generating AI suggestions for resumes.
 */
public class AiSuggestionService {

    public List<String> analyzeResume(ResumeDocument resume) {
        List<String> suggestions = new ArrayList<>();

        // 1. Check Personal Details
        if (resume.getSummary() == null || resume.getSummary().length() < 50) {
            suggestions.add(
                    "⚠️ **Summary is too short.** A strong professional summary should be at least 2-3 sentences highlighting your key experience and goals.");
        }
        if (resume.getLinkedinUrl() == null || resume.getLinkedinUrl().isEmpty()) {
            suggestions
                    .add("ℹ️ **Add a LinkedIn URL.** Recruiters often look for online profiles to verify experience.");
        }

        // 2. Check Skills
        if (resume.getSkills() == null || resume.getSkills().size() < 5) {
            suggestions.add(
                    "⚠️ **Add more Skills.** ATS systems scan for keywords. Aim for at least 8-10 relevant technical or soft skills.");
        }

        // 3. Check Education
        if (resume.getEducation() == null || resume.getEducation().isEmpty()) {
            suggestions.add("❗ **Missing Education.** Please add your most recent degree or qualification.");
        }

        // 4. Check Experience
        if (resume.getExperience() == null || resume.getExperience().isEmpty()) {
            suggestions.add(
                    "❗ **Missing Work Experience.** If you are a fresher, consider adding Internships or Volunteer work here.");
        } else {
            // Check descriptions for impact
            for (ResumeDocument.ExperienceItem exp : resume.getExperience()) {
                if (exp.getDescription() != null && !exp.getDescription().matches(".*\\d+.*")) {
                    suggestions.add("💡 **Quantify your impact** in role: " + exp.getRole()
                            + ". Use numbers (e.g., 'Increased revenue by 20%') to make it impactful.");
                }
            }
        }

        // 5. Check Projects
        if (resume.getProjects() == null || resume.getProjects().isEmpty()) {
            suggestions.add(
                    "💡 **Add Projects.** Showcasing personal or academic projects is a great way to demonstrate practical skills.");
        }

        if (suggestions.isEmpty()) {
            suggestions.add("✅ **Great Job!** Your resume looks well-structured. Good luck!");
        }

        return suggestions;
    }
}
