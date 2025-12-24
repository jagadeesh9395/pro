package com.tal.pro.service.resume;

import com.tal.pro.service.ResumeSectionSplitterService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class SkillsExtractor implements SectionExtractor<List<String>> {

    @Override
    public List<String> extract(String text, Map<String, String> sections) {
        String skillsText = sections.get(ResumeSectionSplitterService.SKILLS);
        if (skillsText == null || skillsText.isBlank()) {
            skillsText = text;
        }
        return extractSkills(skillsText);
    }

    @Override
    public String getSectionName() {
        return "skills";
    }

    private List<String> extractSkills(String text) {
        List<String> foundSkills = new ArrayList<>();
        String[] commonSkills = {
                "Java", "Python", "C++", "C#", "JavaScript", "TypeScript", "React", "Angular", "Vue", "Spring",
                "Spring Boot", "Node.js", "Express", "Django", "Flask", "Docker", "Kubernetes", "AWS", "Azure", "GCP",
                "SQL", "MySQL", "PostgreSQL", "MongoDB", "NoSQL", "Redis", "Git", "Jenkins", "CI/CD",
                "HTML", "CSS", "SASS", "Less", "Bootstrap", "Tailwind", "Machine Learning", "AI", "Data Analysis",
                "Project Management", "Agile", "Scrum", "REST API", "Microservices"
        };

        String lowerText = text.toLowerCase();
        for (String skill : commonSkills) {
            if (lowerText.contains(skill.toLowerCase())) {
                if (!foundSkills.contains(skill))
                    foundSkills.add(skill);
            }
        }
        return foundSkills;
    }
}
