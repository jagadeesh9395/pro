package com.tal.pro.service.resume;

import com.tal.pro.model.ResumeDocument;
import org.springframework.stereotype.Service;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class PersonalInformationExtractor implements SectionExtractor<ResumeDocument> {

    @Override
    public ResumeDocument extract(String text, Map<String, String> sections) {
        ResumeDocument doc = new ResumeDocument();
        doc.setFullName(extractName(text));
        doc.setEmail(extractEmail(text));
        doc.setPhone(extractPhone(text));
        doc.setLinkedinUrl(extractLinkedin(text));
        return doc;
    }

    @Override
    public String getSectionName() {
        return "personal_info";
    }

    private String extractName(String text) {
        String[] lines = text.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (!line.isEmpty() && line.split("\\s+").length >= 2 && line.length() < 50
                    && line.matches("^[a-zA-Z].*")) {
                return line;
            }
        }
        return "";
    }

    private String extractEmail(String text) {
        Pattern p = Pattern.compile("(?i)[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
        Matcher m = p.matcher(text);
        return m.find() ? m.group() : "";
    }

    private String extractPhone(String text) {
        Pattern p = Pattern.compile("(\\+?\\d{1,3}[-.\\s]?)?\\(?\\d{3}\\)?[-.\\s]?\\d{3}[-.\\s]?\\d{4}");
        Matcher m = p.matcher(text);
        return m.find() ? m.group() : "";
    }

    private String extractLinkedin(String text) {
        Pattern p = Pattern.compile("(?i)linkedin\\.com/in/[a-zA-Z0-9_-]+");
        Matcher m = p.matcher(text);
        return m.find() ? "https://" + m.group() : "";
    }
}
