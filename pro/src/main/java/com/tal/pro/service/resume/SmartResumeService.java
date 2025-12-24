package com.tal.pro.service.resume;

import com.tal.pro.model.ResumeDocument;
import com.tal.pro.service.AiSuggestionService;
import com.tal.pro.service.ResumeParserService;
import com.tal.pro.service.ResumeSectionSplitterService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class SmartResumeService {

    private final ResumeParserService legacyParser; // To reuse Tika code
    private final ResumeSectionSplitterService splitter;
    private final PersonalInformationExtractor personalExtractor;
    private final ExperienceExtractor experienceExtractor;
    private final EducationExtractor educationExtractor;
    private final SkillsExtractor skillsExtractor;
    private final SummaryExtractor summaryExtractor;
    private final AiSuggestionService aiSuggestionService;

    public ResumeAnalysisResult initialAnalyze(MultipartFile file) throws Exception {
        // Step 1: Extract full text using legacy parser's extractText

        // Step 2: Use legacy parser for initial document (contains heuristics)
        ResumeDocument fullDoc = legacyParser.parseResume(file);

        ResumeAnalysisResult result = new ResumeAnalysisResult();
        result.setFullName(fullDoc.getFullName());
        result.setSummary(fullDoc.getSummary());

        if (fullDoc.getExperience() != null && !fullDoc.getExperience().isEmpty()) {
            ResumeDocument.ExperienceItem latest = fullDoc.getExperience().get(0);
            result.setCurrentRole(latest.getRole());
            result.setCurrentCompany(latest.getCompany());
        }

        result.setSuggestions(aiSuggestionService.analyzeResume(fullDoc));
        result.setExtractedData(fullDoc);

        return result;
    }

    public ResumeDocument extractSectionData(String fullText, String section) {
        Map<String, String> sections = splitter.split(fullText);
        ResumeDocument doc = new ResumeDocument();

        switch (section) {
            case "personal":
                return personalExtractor.extract(fullText, sections);
            case "experience":
                doc.setExperience(experienceExtractor.extract(fullText, sections));
                break;
            case "education":
                doc.setEducation(educationExtractor.extract(fullText, sections));
                break;
            case "skills":
                doc.setSkills(skillsExtractor.extract(fullText, sections));
                break;
            case "summary":
                doc.setSummary(summaryExtractor.extract(fullText, sections));
                break;
        }
        return doc;
    }
}
