package com.tal.pro.service.resume;

import com.tal.pro.model.ResumeDocument;
import java.util.List;

public class ResumeAnalysisResult {
    private String resumeId;
    private String fullName;
    private String currentRole;
    private String currentCompany;
    private String summary;
    private List<String> suggestions;
    private ResumeDocument extractedData;

    public ResumeAnalysisResult() {
    }

    public String getResumeId() {
        return resumeId;
    }

    public void setResumeId(String resumeId) {
        this.resumeId = resumeId;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getCurrentRole() {
        return currentRole;
    }

    public void setCurrentRole(String currentRole) {
        this.currentRole = currentRole;
    }

    public String getCurrentCompany() {
        return currentCompany;
    }

    public void setCurrentCompany(String currentCompany) {
        this.currentCompany = currentCompany;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public List<String> getSuggestions() {
        return suggestions;
    }

    public void setSuggestions(List<String> suggestions) {
        this.suggestions = suggestions;
    }

    public ResumeDocument getExtractedData() {
        return extractedData;
    }

    public void setExtractedData(ResumeDocument extractedData) {
        this.extractedData = extractedData;
    }
}
