package com.example.fast_cv.telegram.ai_integration.model;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Structured CV data returned by the AI.
 *
 * Maps exactly to the JSON:
 * {
 *   "education":   [{degree, university, gpa, duration, additionally}],
 *   "projects":    [{title, about, additionally}],
 *   "experience":  [{company, duration, role, additionally}],
 *   "hard_skills": ["", ...],
 *   "soft_skills": ["", ...]
 * }
 */
@Getter
@Setter
public class CvData {
    private String fullName;
    private String email;
    private String birthday;
    private String city;
    private String position;
    private String links;
    private List<EducationEntry>  education;
    private List<ProjectEntry>    projects;
    private List<ExperienceEntry> experience;
    private List<String>          hardSkills;
    private List<String>          softSkills;

    // ─── Nested: Education ────────────────────────────────────────────

    @Setter
    @Getter
    public static class EducationEntry {
        private String degree;
        private String university;
        private String gpa;
        private String duration;      // "yyyy-yyyy"
        private String additionally;

        @Override
        public String toString() {
            return "Education{" + degree + " @ " + university
                 + " [" + duration + "] GPA:" + gpa
                 + " | " + additionally + "}";
        }
    }

    // ─── Nested: Project ──────────────────────────────────────────────

    @Setter
    @Getter
    public static class ProjectEntry {
        private String title;
        private String about;
        private String additionally;

        @Override
        public String toString() {
            return "Project{" + title + " — " + about + " | " + additionally + "}";
        }
    }

    // ─── Nested: Experience ───────────────────────────────────────────

    @Setter
    @Getter
    public static class ExperienceEntry {
        private String company;
        private String duration;     // "yyyy-yyyy"
        private String role;
        private String additionally;

        @Override
        public String toString() {
            return "Experience{" + role + " @ " + company
                 + " [" + duration + "] | " + additionally + "}";
        }
    }

    @Override
    public String toString() {
        return "CvData {\n" +
               "  education  = " + education  + "\n" +
               "  projects   = " + projects   + "\n" +
               "  experience = " + experience + "\n" +
               "  hardSkills = " + hardSkills + "\n" +
               "  softSkills = " + softSkills + "\n" +
               "}";
    }
}
