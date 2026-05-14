package com.example.fast_cv.telegram.ai_integration.service;


import com.example.fast_cv.telegram.ai_integration.model.CvData;
import com.example.fast_cv.telegram.ai_integration.providers.AiProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Sends raw user text to an AI provider and parses the returned JSON
 * into a structured CvData object.
 *
 * The AI provider is injected — swap Claude for GPT with zero changes here.
 */
@Service
public class CvParserService {

    private final AiProvider ai;

    private static final String SYSTEM_PROMPT =
        "You are a CV data extractor. The user will provide raw CV text.\n" +
        "Your task: extract structured information and return ONLY a valid JSON object.\n" +
        "No explanations, no markdown, no code blocks — just the raw JSON.\n\n" +
        "Required JSON structure:\n" +
        "{\n" +
        "  \"education\": [\n" +
        "    {\n" +
        "      \"degree\": \"Master of Science in Computer Science\",\n" +
        "      \"university\": \"MIT\",\n" +
        "      \"gpa\": \"3.9\",\n" +
        "      \"duration\": \"2020-2022\",\n" +
        "      \"additionally\": \"Best Thesis Award, Dean's List\"\n" +
        "    }\n" +
        "  ],\n" +
        "  \"projects\": [\n" +
        "    {\n" +
        "      \"title\": \"Smart Home App\",\n" +
        "      \"about\": \"IoT-based home automation system\",\n" +
        "      \"additionally\": \"Used by 500+ users\"\n" +
        "    }\n" +
        "  ],\n" +
        "  \"experience\": [\n" +
        "    {\n" +
        "      \"company\": \"Google\",\n" +
        "      \"duration\": \"2022-2024\",\n" +
        "      \"role\": \"Software Engineer\",\n" +
        "      \"additionally\": \"Led team of 5, improved performance by 30%\"\n" +
        "    }\n" +
        "  ],\n" +
        "  \"hard_skills\": [\"Java\", \"Python\", \"SQL\"],\n" +
        "  \"soft_skills\": [\"Leadership\", \"Communication\"]\n" +
        "}\n\n" +
        "Rules:\n" +
        "- If a field is missing or unknown, use empty string \"\" for strings or [] for arrays.\n" +
        "- duration format must always be \"yyyy-yyyy\" or \"yyyy-present\".\n" +
        "- gpa: extract only the numeric value, e.g. \"3.8\". If not mentioned, use \"\".\n" +
        "- additionally: combine all extra achievements/notes into one string.\n" +
        "- Return ONLY the JSON. Nothing else.";

    public CvParserService(AiProvider ai) {
        this.ai = ai;
    }

    /**
     * Sends raw CV text to the AI and returns a structured CvData object.
     *
     * @param rawText  free-form text from the user (experience, education, etc.)
     * @return parsed CvData ready for Word document generation
     */
    public CvData parse(String rawText) {
        String response = ai.call(SYSTEM_PROMPT, rawText);
        String json     = cleanJson(response);
        return parseJson(json);
    }

    // ─── JSON Cleaning ────────────────────────────────────────────────

    /**
     * Strips markdown code blocks if the AI wrapped the JSON anyway.
     * e.g. ```json { ... } ``` → { ... }
     */
    private String cleanJson(String raw) {
        String cleaned = raw.trim();
        if (cleaned.startsWith("```")) {
            int firstNewline = cleaned.indexOf('\n');
            if (firstNewline != -1) cleaned = cleaned.substring(firstNewline + 1);
            if (cleaned.endsWith("```")) cleaned = cleaned.substring(0, cleaned.lastIndexOf("```"));
        }
        return cleaned.trim();
    }

    // ─── JSON Parsing (no external library) ──────────────────────────

    private CvData parseJson(String json) {
        CvData data = new CvData();

        data.setEducation(parseEducationArray(json));
        data.setProjects(parseProjectsArray(json));
        data.setExperience(parseExperienceArray(json));
        data.setHardSkills(parseStringArray(json, "hard_skills"));
        data.setSoftSkills(parseStringArray(json, "soft_skills"));

        return data;
    }

    private List<CvData.EducationEntry> parseEducationArray(String json) {
        List<CvData.EducationEntry> list = new ArrayList<>();
        String section = extractArraySection(json, "education");
        for (String obj : splitObjects(section)) {
            CvData.EducationEntry e = new CvData.EducationEntry();
            e.setDegree(      extractField(obj, "degree"));
            e.setUniversity(  extractField(obj, "university"));
            e.setGpa(         extractField(obj, "gpa"));
            e.setDuration(    extractField(obj, "duration"));
            e.setAdditionally(extractField(obj, "additionally"));
            list.add(e);
        }
        return list;
    }

    private List<CvData.ProjectEntry> parseProjectsArray(String json) {
        List<CvData.ProjectEntry> list = new ArrayList<>();
        String section = extractArraySection(json, "projects");
        for (String obj : splitObjects(section)) {
            CvData.ProjectEntry p = new CvData.ProjectEntry();
            p.setTitle(       extractField(obj, "title"));
            p.setAbout(       extractField(obj, "about"));
            p.setAdditionally(extractField(obj, "additionally"));
            list.add(p);
        }
        return list;
    }

    private List<CvData.ExperienceEntry> parseExperienceArray(String json) {
        List<CvData.ExperienceEntry> list = new ArrayList<>();
        String section = extractArraySection(json, "experience");
        for (String obj : splitObjects(section)) {
            CvData.ExperienceEntry ex = new CvData.ExperienceEntry();
            ex.setCompany(    extractField(obj, "company"));
            ex.setDuration(   extractField(obj, "duration"));
            ex.setRole(       extractField(obj, "role"));
            ex.setAdditionally(extractField(obj, "additionally"));
            list.add(ex);
        }
        return list;
    }

    // ─── Low-level JSON helpers ───────────────────────────────────────

    /** Extracts the content of a top-level JSON array by key */
    private String extractArraySection(String json, String key) {
        String search = "\"" + key + "\"";
        int keyIdx = json.indexOf(search);
        if (keyIdx == -1) return "";

        int bracketOpen = json.indexOf('[', keyIdx);
        if (bracketOpen == -1) return "";

        int depth = 0;
        int i = bracketOpen;
        for (; i < json.length(); i++) {
            if (json.charAt(i) == '[') depth++;
            else if (json.charAt(i) == ']') {
                depth--;
                if (depth == 0) break;
            }
        }
        return json.substring(bracketOpen + 1, i);
    }

    /** Splits a JSON array body into individual object strings */
    private List<String> splitObjects(String arrayBody) {
        List<String> objects = new ArrayList<>();
        int depth = 0;
        int start = -1;
        for (int i = 0; i < arrayBody.length(); i++) {
            char c = arrayBody.charAt(i);
            if (c == '{') {
                if (depth == 0) start = i;
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0 && start != -1) {
                    objects.add(arrayBody.substring(start, i + 1));
                    start = -1;
                }
            }
        }
        return objects;
    }

    /** Extracts a string field value from a JSON object string */
    private String extractField(String obj, String key) {
        String search = "\"" + key + "\"";
        int keyIdx = obj.indexOf(search);
        if (keyIdx == -1) return "";

        int colon = obj.indexOf(':', keyIdx + search.length());
        if (colon == -1) return "";

        // Skip whitespace after colon
        int valStart = colon + 1;
        while (valStart < obj.length() && Character.isWhitespace(obj.charAt(valStart))) valStart++;

        if (valStart >= obj.length()) return "";

        char first = obj.charAt(valStart);

        // String value
        if (first == '"') {
            StringBuilder sb = new StringBuilder();
            for (int i = valStart + 1; i < obj.length(); i++) {
                char c = obj.charAt(i);
                if (c == '\\' && i + 1 < obj.length()) {
                    char next = obj.charAt(i + 1);
                    if (next == '"') { sb.append('"'); i++; }
                    else if (next == 'n') { sb.append('\n'); i++; }
                    else if (next == 't') { sb.append('\t'); i++; }
                    else { sb.append(c); }
                } else if (c == '"') {
                    break;
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        }

        // Number or null — read until comma or brace
        StringBuilder sb = new StringBuilder();
        for (int i = valStart; i < obj.length(); i++) {
            char c = obj.charAt(i);
            if (c == ',' || c == '}') break;
            sb.append(c);
        }
        String val = sb.toString().trim();
        return val.equals("null") ? "" : val;
    }

    /** Parses a top-level string array like "hard_skills": ["Java", "SQL"] */
    private List<String> parseStringArray(String json, String key) {
        List<String> result = new ArrayList<>();
        String section = extractArraySection(json, key);
        if (section.isBlank()) return result;

        boolean inString = false;
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < section.length(); i++) {
            char c = section.charAt(i);
            if (c == '"' && !inString) {
                inString = true;
                current.setLength(0);
            } else if (c == '"' && inString) {
                inString = false;
                String val = current.toString().trim();
                if (!val.isEmpty()) result.add(val);
            } else if (inString) {
                if (c == '\\' && i + 1 < section.length()) {
                    char next = section.charAt(i + 1);
                    if (next == '"') { current.append('"'); i++; }
                    else current.append(c);
                } else {
                    current.append(c);
                }
            }
        }
        return result;
    }
}
