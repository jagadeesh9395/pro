package com.tal.pro.service;

import com.tal.pro.model.ResumeDocument;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.pdf.PDFParserConfig;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
/**
 * Service for parsing resume files (PDF/DOCX).
 */
public class ResumeParserService {

    private final ResumeSectionSplitterService sectionSplitterService;
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ResumeParserService.class);


    public ResumeParserService(ResumeSectionSplitterService sectionSplitterService) {
        this.sectionSplitterService = sectionSplitterService;
    }

    public ResumeDocument parseResume(MultipartFile file) {
        ResumeDocument resume = new ResumeDocument();
        try {
            // 1. Extract Text using Tika
            String text = extractText(file);
            System.out.println("Extracted Text Length: " + text.length());

            Map<String, String> sections = sectionSplitterService.split(text);
            String skillsText = sectionSplitterService.getSectionOrFallback(sections, ResumeSectionSplitterService.SKILLS, text);
            String educationText = sections.get(ResumeSectionSplitterService.EDUCATION);
            if (educationText == null || educationText.isBlank()) {
                educationText = sectionSplitterService.guessEducationSection(text);
            }
            if (educationText == null) {
                educationText = "";
            }
            String experienceText = sectionSplitterService.getSectionOrFallback(sections, ResumeSectionSplitterService.EXPERIENCE, text);
            String projectsText = sectionSplitterService.getSectionOrFallback(sections, ResumeSectionSplitterService.PROJECTS, text);
            String languagesText = sectionSplitterService.getSectionOrFallback(sections, ResumeSectionSplitterService.LANGUAGES, text);
            String achievementsText = sectionSplitterService.getSectionOrFallback(sections, ResumeSectionSplitterService.ACHIEVEMENTS, text);

            // 2. Parse Details using Regex
            resume.setFullName(extractName(text));
            resume.setEmail(extractEmail(text));
            resume.setPhone(extractPhone(text));
            resume.setLinkedinUrl(extractLink(text));
            resume.setSkills(extractSkills(skillsText));

            // 3. Heuristic Section Extraction
            resume.setEducation(extractEducation(educationText));
            resume.setExperience(extractExperience(experienceText));
            resume.setProjects(extractProjects(projectsText));
            resume.setLanguages(extractLanguages(languagesText));
            resume.setAchievements(extractAchievements(achievementsText));
            resume.setCertificates(new ArrayList<>()); // Placeholder

        } catch (Exception e) {
            e.printStackTrace();
        }
        return resume;
    }

    private String extractText(MultipartFile file) throws Exception {
        Parser parser = new AutoDetectParser();
        BodyContentHandler handler = new BodyContentHandler(-1); // No limit
        Metadata metadata = new Metadata();
        ParseContext parseContext = new ParseContext();

        PDFParserConfig pdfParserConfig = new PDFParserConfig();
        pdfParserConfig.setSortByPosition(true);
        parseContext.set(PDFParserConfig.class, pdfParserConfig);
        try (InputStream stream = file.getInputStream()) {
            parser.parse(stream, handler, metadata, parseContext);
        }
        return handler.toString();
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

    private String extractLink(String text) {
        Pattern p = Pattern.compile("(?i)linkedin\\.com/in/[a-zA-Z0-9_-]+");
        Matcher m = p.matcher(text);
        return m.find() ? "https://" + m.group() : "";
    }

    private String extractName(String text) {
        String[] lines = text.split("\n");
        for (String line : lines) {
            line = line.trim();
            // Basic heuristic: First line that isn't empty, has 2+ words, and no special
            // chars/numbers start
            if (!line.isEmpty() && line.split("\\s+").length >= 2 && line.length() < 50
                    && line.matches("^[a-zA-Z].*")) {
                return line;
            }
        }
        return "";
    }

    private List<String> extractSkills(String text) {
        List<String> foundSkills = new ArrayList<>();
        String[] commonSkills = {
                "Java", "Python", "C++", "C#", "JavaScript", "TypeScript", "React", "Angular", "Vue", "Spring",
                "Spring Boot",
                "Node.js", "Express", "Django", "Flask", "Docker", "Kubernetes", "AWS", "Azure", "GCP",
                "SQL", "MySQL", "PostgreSQL", "MongoDB", "NoSQL", "Redis", "Git", "Jenkins", "CI/CD",
                "HTML", "CSS", "SASS", "Less", "Bootstrap", "Tailwind", "Machine Learning", "AI", "Data Analysis"
        };

        String lowerText = text.toLowerCase();
        for (String skill : commonSkills) {
            // Check for word boundary to avoid partial matches (e.g. "Java" in "JavaScript"
            // - actually contains handles that but "C" in "Class" needs care)
            // For simplicity, using contains but verifying simple cases
            if (lowerText.contains(skill.toLowerCase())) {
                if (!foundSkills.contains(skill))
                    foundSkills.add(skill);
            }
        }
        return foundSkills;
    }

    private List<ResumeDocument.EducationItem> extractEducation(String text) {
        List<ResumeDocument.EducationItem> eduList = new ArrayList<>();

        // Enhanced education pattern
        Pattern eduPattern = Pattern.compile(
                "(?i)(?<degree>Bachelor(?:'s)?(?:\\s*\\(?=B\\)|\\s*\\(?=BS\\)|\\s*\\(?=B\\.S\\.\\)|\\s*\\(?=B\\.Sc\\.\\)|\\s*\\(?=B\\.?[A-Za-z]+\\))?|" +
                        "Master(?:'s)?(?:\\s*\\(?=M\\)|\\s*\\(?=MS\\)|\\s*\\(?=M\\.S\\.\\)|\\s*\\(?=M\\.?[A-Za-z]+\\))?|" +
                        "Ph\\s*D|Doctorate|B\\.?[A-Z]+\\.?|M\\.?[A-Z]+\\.?|Diploma|Associate(?:'s)?|Certificate|Certification)" +
                        "\\s*(?:in|of|,)?\\s*([A-Za-z\\.\\s&]+)?" +
                        "(?:\\s*[\\(\\[]?(?<start>20\\d{2}|19\\d{2}|[A-Za-z]{3,}\\s+\\d{4})?\\s*[-–—]\\s*(?<end>20\\d{2}|19\\d{2}|Present|Current|[A-Za-z]{3,}\\s+\\d{4}|Ongoing)?[\\]\\)])?\\s*" +
                        "(?:at|from|,)?\\s*(?<institution>[A-Z][A-Za-z\\.\\s&'-]+(?:University|College|Institute|School|Academy|Polytechnic|Univ\\.?|Coll\\.?|Inst\\.?|Sch\\.?)?)" +
                        "(?:,|\\s*\\()?\\s*(?<location>(?:[A-Z][a-z]+[\\s,]*)+[A-Z]{2,}|[A-Z][a-z]+(?:[\\s,]*[A-Z][a-z]+)*)?",
                Pattern.MULTILINE | Pattern.DOTALL
        );

        Matcher matcher = eduPattern.matcher(text);

        while (matcher.find()) {
            ResumeDocument.EducationItem edu = new ResumeDocument.EducationItem();

            // Extract degree
            String degree = matcher.group("degree");
            if (matcher.group(2) != null) {
                degree += " " + matcher.group(2).trim();
            }
            edu.setDegree(degree.trim());

            // Extract years
            String startYear = matcher.group("start");
            String endYear = matcher.group("end");
            if (startYear != null) {
                String yearRange = startYear;
                if (endYear != null) {
                    yearRange += " - " + endYear;
                } else if (startYear.matches("\\d{4}")) {
                    yearRange = "Graduated " + startYear;
                }
                edu.setYear(yearRange);
            }

            // Extract institution
            if (matcher.group("institution") != null) {
                edu.setSchool(matcher.group("institution").trim());
            }

            // Extract location
            if (matcher.group("location") != null) {
                String school = edu.getSchool() != null ? edu.getSchool() : "";
                edu.setSchool((school + ", " + matcher.group("location").trim()).replaceAll("\\s*,\\s*,", ","));
            }

            eduList.add(edu);
        }

        // Fallback to simpler pattern if no matches found
        if (eduList.isEmpty()) {
            String[] lines = text.split("\\n");
            Pattern degreePattern = Pattern.compile("(?i)\\b(?:Bachelor(?:'s)?(?:\\s+(?:of|in)\\s+[A-Za-z.& ]{2,40})?|Master(?:'s)?(?:\\s+(?:of|in)\\s+[A-Za-z.& ]{2,40})?|B\\.?\\s?Tech|M\\.?\\s?Tech|B\\.?\\s?E|M\\.?\\s?E|B\\.?\\s?Sc|M\\.?\\s?Sc|B\\.?\\s?S|M\\.?\\s?S|BCA|MCA|MBA|PGDM|BBA|BA|MA|B\\.?\\s?Com|M\\.?\\s?Com|Ph\\.?\\s?D|Doctorate|Diploma|Associate(?:'s)?|Certificate|Certification)\\b");
            Pattern yearPattern = Pattern.compile("(\\d{4})\\s*[–-]\\s*(\\d{4}|Present|Current)");

            for (int i = 0; i < lines.length; i++) {
                String line = lines[i].trim();
                Matcher degreeMatcher = degreePattern.matcher(line);
                if (degreeMatcher.find()) {
                    ResumeDocument.EducationItem edu = new ResumeDocument.EducationItem();
                    edu.setDegree(degreeMatcher.group().trim());

                    Matcher yearMatcher = yearPattern.matcher(line);
                    if (yearMatcher.find()) {
                        edu.setYear(yearMatcher.group(0));
                    } else if (i + 1 < lines.length) {
                        Matcher nextYearMatcher = yearPattern.matcher(lines[i + 1]);
                        if (nextYearMatcher.find()) {
                            edu.setYear(nextYearMatcher.group(0));
                        }
                    }

                    if (i > 0) {
                        String prev = lines[i - 1].trim();
                        if (!prev.isEmpty() && prev.length() > 3) {
                            edu.setSchool(prev);
                        }
                    }
                    eduList.add(edu);
                }
            }
        }

        return eduList;
    }

    private List<ResumeDocument.ExperienceItem> extractExperience(String text) {
        List<ResumeDocument.ExperienceItem> expList = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        // Enhanced work experience pattern
        Pattern expPattern = Pattern.compile(
                "(?i)(?<title>(?:(?:Senior|Junior|Lead|Principal|Staff|Associate|Intern(?:al|ship)?|Full[- ]?Stack|Front[- ]?End|Back[- ]?End|Software|Web|Mobile|UI/UX|Data|DevOps|QA|Test|Automation|Cloud|Security|Network|Systems|Database|AI|ML|Machine Learning|Artificial Intelligence|Big Data|Business Intelligence|Product|Project|Program|Technical|Solution|Enterprise|Application|Embedded|Firmware|Game|Mobile|Android|iOS|React|Angular|Vue|Node\\.?js|Python|Java|JavaScript|TypeScript|Ruby|PHP|C#|C\\+\\+|Go|Rust|Scala|Kotlin|Swift|Dart|Flutter|React Native|Xamarin|Ionic|PhoneGap|Cordova|Electron|jQuery|Bootstrap|Sass|Less|Webpack|Babel|Gulp|Grunt|Docker|Kubernetes|AWS|Azure|GCP|Google Cloud Platform|Amazon Web Services|Microsoft Azure|Heroku|Firebase|MongoDB|PostgreSQL|MySQL|SQL|NoSQL|Redis|Elasticsearch|GraphQL|REST|API|Microservices|CI/CD|Jenkins|GitHub Actions|GitLab CI|CircleCI|Travis CI|Agile|Scrum|Kanban|TDD|BDD|DDD|OOP|Functional Programming|Procedural Programming|Object-Oriented Programming)[\\s-]?){1,3})" +
                        "\\s*(?:at|@|\\|\\s*|\\s+at\\s+|\\s+@\\s+)\\s*" +
                        "(?<company>[A-Z][A-Za-z0-9&\\-\\s\\.',]+(?:Inc\\.?|LLC|L\\.L\\.C\\.?|Ltd\\.?|Corp\\.?|Corporation|Company|Co\\.?|Pvt\\.?|L\\.P\\.?|LLP|GmbH|AG|S\\.A\\.?|P\\.?L\\.?C\\.?|Group|Technologies|Solutions|Systems|Software|Consulting|Services)?)" +
                        "\\s*" +
                        "(?:\\(?(?<start>[A-Za-z]{3,}\\s+\\d{4}|\\d{1,2}/\\d{4}|(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\\s+\\d{4}|(?:January|February|March|April|May|June|July|August|September|October|November|December)\\s+\\d{4})\\s*[-–—]\\s*(?<end>[A-Za-z]{3,}\\s+\\d{4}|\\d{1,2}/\\d{4}|(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\\s+\\d{4}|(?:January|February|March|April|May|June|July|August|September|October|November|December)\\s+\\d{4}|Present|Current|Now|Till Date|Till Now|Present\\)?|Ongoing)\\)?)?" +
                        "(?:\\s*\\|\\s*(?<location>[A-Z][A-Za-z\\s,]+(?:,\\s*[A-Z]{2})?(?:,\\s*[A-Z]{2,3})?))?",
                Pattern.MULTILINE | Pattern.DOTALL
        );

        Matcher matcher = expPattern.matcher(text);

        while (matcher.find()) {
            ResumeDocument.ExperienceItem exp = new ResumeDocument.ExperienceItem();

            // Extract job title
            if (matcher.group("title") != null) {
                exp.setRole(matcher.group("title").trim());
            }

            // Extract company
            if (matcher.group("company") != null) {
                exp.setCompany(matcher.group("company").trim());
            }

            // Extract duration
            String startDate = matcher.group("start");
            String endDate = matcher.group("end");
            if (startDate != null) {
                String duration = startDate;
                if (endDate != null) {
                    duration += " - " + endDate;
                }
                String cleanDuration = cleanDuration(duration);
                log.debug("Setting duration from expPattern: {} -> {}", duration, cleanDuration);
                exp.setDuration(cleanDuration);
            }

            // Extract location if available
            if (matcher.group("location") != null) {
                exp.setLocation(cleanLocation(matcher.group("location").trim()));
            }

            // Try to extract description from the surrounding text
            int start = matcher.start();
            int end = matcher.end();
            int nextSection = text.length();

            // Look for the next section (either another experience or education)
            Matcher nextExpMatcher = expPattern.matcher(text.substring(end));
            if (nextExpMatcher.find()) {
                nextSection = end + nextExpMatcher.start();
            } else {
                // Look for education section
                Pattern eduSectionPattern = Pattern.compile("(?i)(?:education|academic background|degrees)", Pattern.MULTILINE);
                Matcher eduMatcher = eduSectionPattern.matcher(text.substring(end));
                if (eduMatcher.find()) {
                    nextSection = end + eduMatcher.start();
                }
            }

            // Extract the description from the current position to the next section
            String description = text.substring(end, Math.min(end + 1000, nextSection)).trim();
            // Clean up the description
            description = description.replaceAll("(?m)^[\\s\\p{Punct}]*$", "").trim();
            if (!description.isEmpty()) {
                exp.setDescription(description);
            }

            expList.add(exp);
            seen.add(normalizeExpKey(exp));
        }

        Pattern yearRangePattern = Pattern.compile("(?i)(\\d{4})\\s*[–—-]\\s*(\\d{4}|Present|Current|Now|Ongoing)");
        Pattern durationPattern = Pattern.compile(
                "(?i)(?:\\b(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Sept|Oct|Nov|Dec)[a-z]*\\b\\s+\\d{4}|\\d{1,2}/\\d{4}|\\d{4})\\s*[–—-]\\s*(?:\\b(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Sept|Oct|Nov|Dec)[a-z]*\\b\\s+\\d{4}|\\d{1,2}/\\d{4}|\\d{4}|Present|Current|Now|Ongoing)"
        );
        Pattern workedLinePattern = Pattern.compile(
                "(?i)\\b(?:currently\\s+working|presently\\s+working|currently\\s+at|working\\s+at|working\\s+in|previously\\s+worked|worked\\s+at|worked\\s+in)\\b" +
                        "(?:\\s+as\\s+(?<role>[^,()\\n]{2,80}))?" +
                        "(?:\\s+(?:at|in))?\\s+" +
                        "(?<company>[^,()\\n]{2,120})" +
                        "(?:,\\s*(?<location>[^()\\n]{2,120}))?" +
                        "(?:\\s*\\((?<duration>[^)\\n]{3,80})\\))?");
        Pattern locationRoleFromToPattern = Pattern.compile(
                "(?i)^\\s*(?<location>[A-Za-z][A-Za-z\\s]{1,80})\\s+as\\s+(?<role>[^,()\\n]{2,120}?)\\s+from\\s+" +
                        "(?<start>(?:\\b(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Sept|Oct|Nov|Dec)[a-z]*\\b\\s+\\d{4}|\\d{1,2}/\\d{4}|\\d{4}))\\s+" +
                        "(?:to|[-–—])\\s+" +
                        "(?<end>(?:\\b(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Sept|Oct|Nov|Dec)[a-z]*\\b\\s+\\d{4}|\\d{1,2}/\\d{4}|\\d{4}|Present|Current|Now|Ongoing))\\s*$");
        Pattern roleHintPattern = Pattern.compile(
                "(?i)\\b(?:Senior|Junior|Lead|Principal|Staff|Associate)?\\s*(?:Software\\s+Engineer|Software\\s+Developer|Developer|Engineer|SDE|SDET|Manager|Product\\s+Manager|Project\\s+Manager|Analyst|Business\\s+Analyst|Designer|Specialist|Consultant|Architect|Administrator|Intern(?:ship)?|Director|Coordinator|Officer|QA|Tester|DevOps|Data\\s+Scientist)\\b");
        Pattern bulletLinePattern = Pattern.compile("^\\s*(?:[-*•]|\\d+\\.)\\s+.+");
        Pattern headerStopPattern = Pattern.compile("(?i)^\\s*(?:education|skills|projects|languages|certifications|certificates|licenses|achievements|awards|honors)\\b.*$");

        String[] linesForWorked = text.split("\\n");
        for (int i = 0; i < linesForWorked.length; i++) {
            String line = linesForWorked[i].trim();
            if (line.isEmpty()) {
                continue;
            }

            Matcher locationRoleFromToMatcher = locationRoleFromToPattern.matcher(line);
            if (locationRoleFromToMatcher.find()) {
                ResumeDocument.ExperienceItem exp = new ResumeDocument.ExperienceItem();
                exp.setLocation(cleanLocation(locationRoleFromToMatcher.group("location").trim()));
                exp.setRole(locationRoleFromToMatcher.group("role").trim());
                String start = locationRoleFromToMatcher.group("start").trim();
                String end = locationRoleFromToMatcher.group("end").trim();
                String durationStr = start + " - " + end;
                String cleanDuration = cleanDuration(durationStr);
                log.debug("Setting duration from locationRoleFromToPattern: {} -> {}", durationStr, cleanDuration);
                exp.setDuration(cleanDuration);

                String key = normalizeExpKey(exp);
                if (!key.isBlank() && !seen.contains(key)) {
                    expList.add(exp);
                    seen.add(key);
                }
                continue;
            }
            Matcher workedMatcher = workedLinePattern.matcher(line);
            if (!workedMatcher.find()) {
                continue;
            }

            ResumeDocument.ExperienceItem exp = new ResumeDocument.ExperienceItem();
            String role = workedMatcher.group("role");
            String company = workedMatcher.group("company");
            String location = workedMatcher.group("location");
            String duration = workedMatcher.group("duration");

            if (role != null && !role.isBlank()) {
                exp.setRole(role.trim());
            } else {
                Matcher roleHint = roleHintPattern.matcher(line);
                if (roleHint.find()) {
                    exp.setRole(roleHint.group().trim());
                } else if (i > 0) {
                    String prev = linesForWorked[i - 1].trim();
                    if (!prev.isEmpty() && prev.length() <= 120
                            && !headerStopPattern.matcher(prev).matches()
                            && !workedLinePattern.matcher(prev).find()
                            && !expPattern.matcher(prev).find()) {
                        Matcher prevHint = roleHintPattern.matcher(prev);
                        if (prevHint.find()) {
                            exp.setRole(prevHint.group().trim());
                        }
                    }
                }
            }
            if (company != null && !company.isBlank()) {
                exp.setCompany(company.trim());
            }
            if (location != null && !location.isBlank()) {
                exp.setLocation(cleanLocation(location.trim()));
            }

            if (duration != null && !duration.isBlank()) {
                String cleanDuration = cleanDuration(duration.trim());
                log.debug("Setting duration from workedLinePattern: {} -> {}", duration, cleanDuration);
                exp.setDuration(cleanDuration);
            } else {
                Matcher dur = durationPattern.matcher(line);
                if (dur.find()) {
                    String cleanDuration = cleanDuration(dur.group().trim());
                    log.debug("Setting duration from durationPattern: {} -> {}", dur.group().trim(), cleanDuration);
                    exp.setDuration(cleanDuration);
                } else {
                    Matcher yr = yearRangePattern.matcher(line);
                    if (yr.find()) {
                        exp.setDuration(cleanDuration(yr.group(1) + " - " + yr.group(2)));
                    } else if (i + 1 < linesForWorked.length) {
                        String nextLine = linesForWorked[i + 1];
                        Matcher durNext = durationPattern.matcher(nextLine);
                        if (durNext.find()) {
                            exp.setDuration(cleanDuration(durNext.group().trim()));
                        } else {
                            Matcher yrNext = yearRangePattern.matcher(nextLine);
                            if (yrNext.find()) {
                                exp.setDuration(cleanDuration(yrNext.group(1) + " - " + yrNext.group(2)));
                            }
                        }
                    }
                }
            }

            if ((exp.getLocation() == null || exp.getLocation().isBlank()) && i + 1 < linesForWorked.length) {
                String nextLine = linesForWorked[i + 1].trim();
                if (!nextLine.isEmpty()
                        && nextLine.length() <= 80
                        && nextLine.contains(",")
                        && !nextLine.matches(".*\\d.*")
                        && !bulletLinePattern.matcher(nextLine).matches()
                        && !headerStopPattern.matcher(nextLine).matches()
                        && !workedLinePattern.matcher(nextLine).find()
                        && !expPattern.matcher(nextLine).find()) {
                    exp.setLocation(cleanLocation(nextLine));
                }
            }

            StringBuilder responsibilities = new StringBuilder();
            int taken = 0;
            for (int j = i + 1; j < linesForWorked.length && taken < 6; j++) {
                String next = linesForWorked[j].trim();
                if (next.isEmpty()) {
                    if (responsibilities.length() > 0) {
                        break;
                    }
                    continue;
                }
                if (headerStopPattern.matcher(next).matches()) {
                    break;
                }
                if (expPattern.matcher(next).find()) {
                    break;
                }
                if (workedLinePattern.matcher(next).find()) {
                    break;
                }

                if (bulletLinePattern.matcher(next).matches()) {
                    String cleaned = next.replaceAll("^\\s*(?:[-*•]|\\d+\\.)\\s+", "").trim();
                    if (!cleaned.isEmpty() && !cleaned.matches(".*@.*\\..*")) {
                        if (responsibilities.length() > 0) {
                            responsibilities.append(" ");
                        }
                        responsibilities.append(cleaned);
                        taken++;
                    }
                } else if (responsibilities.length() > 0) {
                    break;
                }
            }
            if (responsibilities.length() > 0) {
                exp.setDescription(responsibilities.toString().trim());
            }

            String key = normalizeExpKey(exp);
            if (!key.isBlank() && !seen.contains(key)) {
                expList.add(exp);
                seen.add(key);
            }
        }

        // Fallback to simpler pattern if no matches found
        if (expList.isEmpty()) {
            Pattern rolePattern = Pattern.compile(
                    "(?i)\\b(?:Senior|Junior|Lead|Principal|Staff|Associate)?\\s*(?:Software\\s+Engineer|Software\\s+Developer|Developer|Engineer|SDE|SDET|Manager|Product\\s+Manager|Project\\s+Manager|Analyst|Business\\s+Analyst|Designer|Specialist|Consultant|Architect|Administrator|Intern(?:ship)?|Director|Coordinator|Officer|QA|Tester|DevOps|Data\\s+Scientist)\\b");
            String[] lines = text.split("\\n");
            Pattern yearPattern = Pattern.compile("(\\d{4})\\s*[–-]\\s*(\\d{4}|Present|Current)");

            for (int i = 0; i < lines.length; i++) {
                String line = lines[i].trim();
                Matcher roleMatcher = rolePattern.matcher(line);
                if (roleMatcher.find()) {
                    ResumeDocument.ExperienceItem exp = new ResumeDocument.ExperienceItem();
                    exp.setRole(roleMatcher.group().trim());

                    // Look for company name in nearby lines
                    if (i > 0) {
                        String prevLine = lines[i - 1].trim();
                        if (!prevLine.isEmpty() && !prevLine.matches(".*@.*\\..*")) { // Not an email
                            exp.setCompany(prevLine);
                        }
                    }

                    // Look for years in current or next line
                    Matcher yearMatcher = yearPattern.matcher(line);
                    if (yearMatcher.find()) {
                        exp.setDuration(cleanDuration(yearMatcher.group(0)));
                    } else if (i + 1 < lines.length) {
                        Matcher nextYearMatcher = yearPattern.matcher(lines[i + 1]);
                        if (nextYearMatcher.find()) {
                            exp.setDuration(cleanDuration(nextYearMatcher.group(0)));
                        }
                    }

                    // Add description if available in next few lines
                    StringBuilder description = new StringBuilder();
                    int descLines = 0;
                    for (int j = i + 1; j < Math.min(i + 5, lines.length) && descLines < 3; j++) {
                        String descLine = lines[j].trim();
                        if (!descLine.isEmpty() && !descLine.matches(".*@.*\\..*")) {
                            if (description.length() > 0) {
                                description.append(" ");
                            }
                            description.append(descLine);
                            descLines++;
                        }
                    }
                    exp.setDescription(description.toString());

                    expList.add(exp);
                }
            }
        }

        expList.sort(Comparator
                .comparingInt((ResumeDocument.ExperienceItem e) -> extractEndYearScore(e.getDuration())).reversed()
                .thenComparingInt(e -> extractStartYearScore(e.getDuration())).reversed());

        return expList;
    }

    private String normalizeExpKey(ResumeDocument.ExperienceItem exp) {
        if (exp == null) {
            return "";
        }
        String role = exp.getRole() == null ? "" : exp.getRole().trim().toLowerCase();
        String company = exp.getCompany() == null ? "" : exp.getCompany().trim().toLowerCase();
        String duration = exp.getDuration() == null ? "" : cleanDuration(exp.getDuration()).trim().toLowerCase();
        String location = exp.getLocation() == null ? "" : exp.getLocation().trim().toLowerCase();
        return (role + "|" + company + "|" + duration + "|" + location).trim();
    }

    private String cleanLocation(String rawLocation) {
        if (rawLocation == null) {
            return "";
        }
        String loc = rawLocation.replaceAll("\\s+", " ").trim();
        if (loc.isEmpty()) {
            return "";
        }

        String lower = loc.toLowerCase();
        int cut = loc.length();

        int asIdx = lower.indexOf(" as ");
        if (asIdx >= 0) {
            cut = Math.min(cut, asIdx);
        }
        int fromIdx = lower.indexOf(" from ");
        if (fromIdx >= 0) {
            cut = Math.min(cut, fromIdx);
        }
        int parenIdx = loc.indexOf('(');
        if (parenIdx >= 0) {
            cut = Math.min(cut, parenIdx);
        }

        Matcher digit = Pattern.compile("\\d").matcher(loc);
        if (digit.find()) {
            cut = Math.min(cut, digit.start());
        }

        loc = loc.substring(0, Math.max(0, cut)).trim();
        loc = loc.replaceAll("[\\s,;\\-|]+$", "").trim();
        return loc;
    }

    private String cleanDuration(String rawDuration) {
        if (rawDuration == null) {
            return "";
        }
        // Simple cleaning - just remove extra spaces and normalize dashes
        return rawDuration.trim()
                .replaceAll("\\s+", " ")
                .replaceAll("\\s*[-–—]\\s*", " - ");
    }
    
    /**
     * Formats the duration string consistently using the same logic as the sorting
     */
    private String getFormattedDuration(String duration) {
        if (duration == null || duration.trim().isEmpty()) {
            return "";
        }
        
        // First try to extract years for consistent formatting
        int startYear = extractStartYearScore(duration);
        int endYear = extractEndYearScore(duration);
        
        if (startYear > 0 || endYear > 0) {
            String startStr = startYear > 0 ? String.valueOf(startYear) : "";
            String endStr = "";
            
            if (endYear == 9999) {
                endStr = "Present";
            } else if (endYear > 0) {
                endStr = String.valueOf(endYear);
            }
            
            if (!startStr.isEmpty() && !endStr.isEmpty()) {
                return startStr + " - " + endStr;
            } else if (!startStr.isEmpty()) {
                return startStr + " - Present";
            } else if (!endStr.isEmpty()) {
                return endStr + " - Present";
            }
        }
        
        // Fallback to cleaned duration if year extraction fails
        return duration.replaceAll("\\s*[-–—]\\s*", " - ").trim();
    }

    private int extractEndYearScore(String duration) {
        if (duration == null) {
            return 0;
        }
        String d = cleanDuration(duration).trim();
        if (d.isEmpty()) {
            return 0;
        }
        if (d.matches("(?i).*(present|current|now|ongoing).*")) {
            return 9999;
        }
        Matcher m = Pattern.compile("(19\\d{2}|20\\d{2})").matcher(d);
        int last = 0;
        while (m.find()) {
            last = Integer.parseInt(m.group(1));
        }
        return last;
    }

    private int extractStartYearScore(String duration) {
        if (duration == null) {
            return 0;
        }
        String d = cleanDuration(duration).trim();
        if (d.isEmpty()) {
            return 0;
        }
        Matcher m = Pattern.compile("(19\\d{2}|20\\d{2})").matcher(d);
        if (m.find()) {
            return Integer.parseInt(m.group(1));
        }
        return 0;
    }

    private List<ResumeDocument.ProjectItem> extractProjects(String text) {
        List<ResumeDocument.ProjectItem> projects = new ArrayList<>();
        String lowerText = text.toLowerCase();
        int startIndex = lowerText.indexOf("projects");
        if (startIndex == -1)
            return projects;

        String sectionText = text.substring(startIndex);
        // Stop at next major section
        int endIndex = sectionText.length();
        int eduIndex = sectionText.toLowerCase().indexOf("education");
        int skillIndex = sectionText.toLowerCase().indexOf("skills");

        if (eduIndex != -1)
            endIndex = Math.min(endIndex, eduIndex);
        if (skillIndex != -1)
            endIndex = Math.min(endIndex, skillIndex);

        String relevantText = sectionText.substring(0, endIndex);
        String[] lines = relevantText.split("\n");

        ResumeDocument.ProjectItem currentItem = null;
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.equalsIgnoreCase("Projects"))
                continue;

            // Heuristic: Short lines might be titles, long lines descriptions
            // Or bullet points
            if (line.length() < 50 && !line.startsWith("-") && currentItem == null) {
                currentItem = new ResumeDocument.ProjectItem();
                currentItem.setTitle(line);
                currentItem.setDescription("");
            } else if (currentItem != null) {
                if (line.length() < 50 && !line.startsWith("-") && !line.contains(" ")) {
                    // Possible next title? Hard to tell. Assume heavy description for now.
                    // For safety, let's treat it as description unless it's clearly a header
                    currentItem.setDescription(currentItem.getDescription() + " " + line);
                } else {
                    currentItem.setDescription(currentItem.getDescription() + " " + line);
                }

                // If description gets long enough, maybe cut off?
                // For now, let's just collect until we hit a "Title-like" line again?
                // Actually, project extraction is very hard without structural cues.
                // Let's simplified: Detect "Project:" prefix
            }
        }
        // Fallback: If heuristic failed, just return a generic item if text exists
        if (projects.isEmpty() && relevantText.length() > 20) {
            ResumeDocument.ProjectItem item = new ResumeDocument.ProjectItem();
            item.setTitle("Project Section Extraction");
            item.setDescription("Details found in resume text: "
                    + relevantText.substring(0, Math.min(relevantText.length(), 200)) + "...");
            projects.add(item);
        }

        return projects;
    }

    private List<String> extractLanguages(String text) {
        List<String> found = new ArrayList<>();
        String[] languages = { "English", "Spanish", "French", "German", "Mandarin", "Chinese", "Japanese", "Hindi",
                "Russian", "Portuguese", "Arabic" };
        String lowerText = text.toLowerCase();
        for (String lang : languages) {
            if (lowerText.contains(lang.toLowerCase())) {
                found.add(lang);
            }
        }
        return found;
    }

    private List<String> extractAchievements(String text) {
        // Very basic: look for "Actions" or "Awards" section
        List<String> achs = new ArrayList<>();
        if (text.toLowerCase().contains("award")) {
            achs.add("Awards/Achievements section detected in resume.");
        }
        return achs;
    }
}
