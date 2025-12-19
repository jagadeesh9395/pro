package com.tal.pro.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ResumeSectionSplitterService {

    public static final String EDUCATION = "education";
    public static final String EXPERIENCE = "experience";
    public static final String SKILLS = "skills";
    public static final String PROJECTS = "projects";
    public static final String LANGUAGES = "languages";
    public static final String ACHIEVEMENTS = "achievements";
    public static final String CERTIFICATIONS = "certifications";
    public static final String SUMMARY = "summary";

    private static final class SectionMatch {
        private final String key;
        private final int start;

        private SectionMatch(String key, int start) {
            this.key = key;
            this.start = start;
        }
    }

    public Map<String, String> split(String text) {
        Map<String, String> sections = new LinkedHashMap<>();
        if (text == null || text.isBlank()) {
            return sections;
        }

        List<SectionMatch> matches = findSectionHeaders(text);
        if (matches.isEmpty()) {
            return sections;
        }

        for (int i = 0; i < matches.size(); i++) {
            SectionMatch current = matches.get(i);
            int sectionStart = current.start;
            int sectionEnd = (i + 1 < matches.size()) ? matches.get(i + 1).start : text.length();
            String chunk = text.substring(sectionStart, sectionEnd).trim();
            if (!chunk.isEmpty()) {
                sections.put(current.key, chunk);
            }
        }

        return sections;
    }

    private List<SectionMatch> findSectionHeaders(String text) {
        List<SectionMatch> matches = new ArrayList<>();

        Map<String, Pattern> headerPatterns = new LinkedHashMap<>();
        headerPatterns.put(SUMMARY, Pattern.compile("(?im)^\\s*(?:summary|professional summary|profile|objective)\\s*[:\\-–—]?\\s*$"));
        headerPatterns.put(SKILLS, Pattern.compile("(?im)^\\s*(?:skills|technical skills|core skills|key skills|technologies)\\s*[:\\-–—]?\\s*$"));
        headerPatterns.put(EXPERIENCE, Pattern.compile("(?im)^\\s*(?:experience|work experience|professional experience|employment history)\\s*[:\\-–—]?\\s*$"));
        headerPatterns.put(EDUCATION, Pattern.compile("(?im)^\\s*(?:education|academic background|academics|qualifications)(?:\\s*&\\s*(?:certifications|certificates|courses))?\\s*[:\\-–—]?\\s*$"));
        headerPatterns.put(PROJECTS, Pattern.compile("(?im)^\\s*(?:projects|project experience)\\s*[:\\-–—]?\\s*$"));
        headerPatterns.put(CERTIFICATIONS, Pattern.compile("(?im)^\\s*(?:certifications|certificates|licenses)\\s*[:\\-–—]?\\s*$"));
        headerPatterns.put(ACHIEVEMENTS, Pattern.compile("(?im)^\\s*(?:achievements|awards|honors)\\s*[:\\-–—]?\\s*$"));
        headerPatterns.put(LANGUAGES, Pattern.compile("(?im)^\\s*(?:languages)\\s*[:\\-–—]?\\s*$"));

        for (Map.Entry<String, Pattern> entry : headerPatterns.entrySet()) {
            Matcher m = entry.getValue().matcher(text);
            while (m.find()) {
                matches.add(new SectionMatch(entry.getKey(), m.start()));
            }
        }

        matches.sort((a, b) -> Integer.compare(a.start, b.start));

        List<SectionMatch> deduped = new ArrayList<>();
        String lastKey = null;
        int lastStart = -1;
        for (SectionMatch match : matches) {
            if (match.start == lastStart) {
                continue;
            }
            if (lastKey != null && lastKey.equals(match.key) && (match.start - lastStart) < 10) {
                continue;
            }
            deduped.add(match);
            lastKey = match.key;
            lastStart = match.start;
        }

        return deduped;
    }

    public String guessEducationSection(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        Pattern degreeHint = Pattern.compile(
                "(?i)\\b(?:Bachelor(?:'s)?|Master(?:'s)?|B\\.?\\s?Tech|M\\.?\\s?Tech|B\\.?\\s?E|M\\.?\\s?E|B\\.?\\s?Sc|M\\.?\\s?Sc|BCA|MCA|MBA|PGDM|BBA|BA|MA|Ph\\.?\\s?D|Doctorate|Diploma|Associate(?:'s)?|Certificate|Certification)\\b");
        Matcher m = degreeHint.matcher(text);
        if (!m.find()) {
            return "";
        }

        int start = text.lastIndexOf('\n', m.start());
        start = (start == -1) ? 0 : start + 1;

        int end = text.length();
        List<SectionMatch> headers = findSectionHeaders(text);
        for (SectionMatch header : headers) {
            if (header.start > start && !EDUCATION.equals(header.key)) {
                end = header.start;
                break;
            }
        }

        String chunk = text.substring(start, Math.min(end, text.length())).trim();
        if (chunk.length() > 2500) {
            chunk = chunk.substring(0, 2500).trim();
        }
        return chunk;
    }

    public String getSectionOrFallback(Map<String, String> sections, String key, String fallbackText) {
        if (sections == null) {
            return fallbackText;
        }
        String value = sections.get(key);
        if (value == null || value.isBlank()) {
            return fallbackText;
        }
        return value;
    }

    public boolean containsAnyHeader(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.contains("education") || lower.contains("experience") || lower.contains("skills") || lower.contains("projects");
    }
}
