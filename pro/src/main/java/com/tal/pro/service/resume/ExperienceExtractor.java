package com.tal.pro.service.resume;

import com.tal.pro.model.ResumeDocument;
import com.tal.pro.service.ResumeSectionSplitterService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class ExperienceExtractor implements SectionExtractor<List<ResumeDocument.ExperienceItem>> {

    @Override
    public List<ResumeDocument.ExperienceItem> extract(String text, Map<String, String> sections) {
        String experienceText = sections.get(ResumeSectionSplitterService.EXPERIENCE);
        if (experienceText == null || experienceText.isBlank()) {
            experienceText = text; // Fallback to full text if section not found
        }
        return extractExperienceItems(experienceText);
    }

    @Override
    public String getSectionName() {
        return "experience";
    }

    private List<ResumeDocument.ExperienceItem> extractExperienceItems(String text) {
        List<ResumeDocument.ExperienceItem> expList = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        Pattern expPattern = Pattern.compile(
                "(?i)(?<title>(?:(?:Senior|Junior|Lead|Principal|Staff|Associate|Intern(?:al|ship)?|Full[- ]?Stack|Front[- ]?End|Back[- ]?End|Software|Web|Mobile|UI/UX|Data|DevOps|QA|Test|Automation|Cloud|Security|Network|Systems|Database|AI|ML|Machine Learning|Artificial Intelligence|Big Data|Business Intelligence|Product|Project|Program|Technical|Solution|Enterprise|Application|Embedded|Firmware|Game|Mobile|Android|iOS|React|Angular|Vue|Node\\.?js|Python|Java|JavaScript|TypeScript|Ruby|PHP|C#|C\\+\\+|Go|Rust|Scala|Kotlin|Swift|Dart|Flutter|React Native|Xamarin|Ionic|PhoneGap|Cordova|Electron|jQuery|Bootstrap|Sass|Less|Webpack|Babel|Gulp|Grunt|Docker|Kubernetes|AWS|Azure|GCP|Google Cloud Platform|Amazon Web Services|Microsoft Azure|Heroku|Firebase|MongoDB|PostgreSQL|MySQL|SQL|NoSQL|Redis|Elasticsearch|GraphQL|REST|API|Microservices|CI/CD|Jenkins|GitHub Actions|GitLab CI|CircleCI|Travis CI|Agile|Scrum|Kanban|TDD|BDD|DDD|OOP|Functional Programming|Procedural Programming|Object-Oriented Programming)[\\s-]?){1,3})"
                        +
                        "\\s*(?:at|@|\\|\\s*|\\s+at\\s+|\\s+@\\s+)\\s*" +
                        "(?<company>[A-Z][A-Za-z0-9&\\-\\s\\.',]+(?:Inc\\.?|LLC|Ltd\\.?|Corp\\.?|Corporation|Company|Pvt\\.?|Pvt Ltd)?)"
                        +
                        "\\s*" +
                        "(?:\\(?(?<start>(?:(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\\s+)?\\d{2,4}|\\d{1,2}/\\d{2,4})\\s*[-–—]\\s*(?<end>(?:(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\\s+)?\\d{2,4}|\\d{1,2}/\\d{2,4}|Present|Current|Till Date|Ongoing)\\)?)?"
                        +
                        "(?:\\s*[|,]\\s*(?<location>[A-Z][A-Za-z\\s,]+))?",
                Pattern.MULTILINE | Pattern.DOTALL);

        Matcher matcher = expPattern.matcher(text);

        while (matcher.find()) {
            ResumeDocument.ExperienceItem exp = new ResumeDocument.ExperienceItem();
            if (matcher.group("title") != null)
                exp.setRole(matcher.group("title").trim());
            if (matcher.group("company") != null)
                exp.setCompany(matcher.group("company").trim());

            String startDate = matcher.group("start");
            String endDate = matcher.group("end");
            if (startDate != null) {
                String duration = startDate + (endDate != null ? " - " + endDate : "");
                exp.setDuration(cleanDuration(duration));
            }

            if (matcher.group("location") != null) {
                exp.setLocation(cleanLocation(matcher.group("location").trim()));
            }

            // Simplified description grabbing for the extractor
            int end = matcher.end();
            String description = text.substring(end, Math.min(end + 500, text.length())).trim();
            description = description.split("\n\n")[0]; // Grab until next double newline roughly
            exp.setDescription(description.trim());

            if (!seen.contains(normalizeKey(exp))) {
                expList.add(exp);
                seen.add(normalizeKey(exp));
            }
        }

        return expList;
    }

    private String cleanDuration(String raw) {
        return raw == null ? "" : raw.trim().replaceAll("\\s+", " ").replaceAll("\\s*[-–—]\\s*", " - ");
    }

    private String cleanLocation(String raw) {
        return raw == null ? "" : raw.replaceAll("\\s+", " ").trim();
    }

    private String normalizeKey(ResumeDocument.ExperienceItem exp) {
        return (exp.getRole() + "|" + exp.getCompany()).toLowerCase();
    }
}
