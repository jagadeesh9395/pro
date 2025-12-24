package com.tal.pro.service.resume;

import com.tal.pro.model.ResumeDocument;
import com.tal.pro.service.ResumeSectionSplitterService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class EducationExtractor implements SectionExtractor<List<ResumeDocument.EducationItem>> {

    @Override
    public List<ResumeDocument.EducationItem> extract(String text, Map<String, String> sections) {
        String educationText = sections.get(ResumeSectionSplitterService.EDUCATION);
        if (educationText == null || educationText.isBlank()) {
            educationText = text;
        }
        return extractEducationItems(educationText);
    }

    @Override
    public String getSectionName() {
        return "education";
    }

    private List<ResumeDocument.EducationItem> extractEducationItems(String text) {
        List<ResumeDocument.EducationItem> eduList = new ArrayList<>();
        String[] lines = text.split("\\r?\\n");

        // Keywords for degrees
        String degreeRegex = "(?i)\\b(MCA|MBA|PGDM|BCA|B\\.Tech|M\\.Tech|Ph\\.?D|B\\.E\\.?|M\\.E\\.?|M\\.Sc|B\\.Sc|Bachelor|Master|Diploma|Associate|B\\.?Com|M\\.?Com|B\\.A|M\\.A)\\b";
        // Keywords for institutions
        String institutionRegex = "(?i)\\b.*?(University|College|Institute|School|Academy|Polytechnic|Univ\\.?|Coll\\.?|Inst\\.?|Sch\\.?|Engineering College|Technology Institute)\\b.*?";

        ResumeDocument.EducationItem currentItem = null;

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty())
                continue;

            Matcher degreeMatcher = Pattern.compile(degreeRegex).matcher(line);
            Matcher instMatcher = Pattern.compile(institutionRegex).matcher(line);

            boolean hasDegree = degreeMatcher.find();
            boolean hasInst = instMatcher.find();

            if (hasInst || hasDegree) {
                if (currentItem == null || (hasInst && currentItem.getSchool() != null)) {
                    if (currentItem != null && (currentItem.getSchool() != null || currentItem.getDegree() != null)) {
                        eduList.add(currentItem);
                    }
                    currentItem = new ResumeDocument.EducationItem();
                }

                if (hasInst) {
                    String inst = line;
                    if (hasDegree)
                        inst = inst.replace(degreeMatcher.group(0), "");
                    currentItem.setSchool(cleanText(inst));
                }

                if (hasDegree) {
                    currentItem.setDegree(degreeMatcher.group(0).trim());
                }
            }
        }

        if (currentItem != null && (currentItem.getSchool() != null || currentItem.getDegree() != null)) {
            eduList.add(currentItem);
        }

        return eduList;
    }

    private String cleanText(String text) {
        if (text == null)
            return null;
        return text.replaceAll("^[\\s:,.|\\-•*]+", "")
                .replaceAll("[\\s:,.|\\-•*]+$", "")
                .trim();
    }
}
