package com.example.fast_cv.telegram.ai_integration.service;

import com.example.fast_cv.telegram.ai_integration.model.CvData;
import org.apache.poi.xwpf.usermodel.*;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.List;

/**
 * Generates a .docx resume matching the black-and-white minimalist template.
 *
 * Layout is achieved WITHOUT tables — two-column appearance is simulated via
 * paragraph indentation and tab stops:
 *
 *   LEFT_W twips → indent for right-column content
 *   0             → left-column label, placed via hanging indent trick:
 *                   indent.left = LEFT_W, indent.hanging = LEFT_W
 *                   so the first line starts at column 0,
 *                   continuation lines wrap at LEFT_W.
 *
 * This eliminates all XWPFTable usage and the border/margin boilerplate
 * that came with it.
 */
@Component
public class ResumeDocumentBuilder {

    // ── Colours (B&W minimalist) ──────────────────────────────────────────────
    private static final String BLACK      = "000000";
    private static final String DARK       = "1A1A1A";
    private static final String MID        = "555555";
    private static final String LIGHT_GRAY = "AAAAAA";
    private static final String DIVIDER_C  = "CCCCCC";

    // ── Page geometry (A4) ────────────────────────────────────────────────────
    private static final int PAGE_W   = 11906;
    private static final int PAGE_H   = 16838;
    private static final int MARGIN_H = 900;
    private static final int MARGIN_V = 900;
    private static final int CONTENT_W = PAGE_W - MARGIN_H * 2; // 10106

    // ── Two-column split (label ~22%, content ~78%) ───────────────────────────
    private static final int LEFT_W  = (int) (CONTENT_W * 0.22); // ~2223
    private static final int RIGHT_W = CONTENT_W - LEFT_W;       // ~7883

    // ── Font sizes (half-points) ──────────────────────────────────────────────
    private static final int FS_NAME  = 52; // 26 pt
    private static final int FS_TITLE = 26; // 13 pt
    private static final int FS_LABEL = 18; //  9 pt
    private static final int FS_ROLE  = 22; // 11 pt
    private static final int FS_BODY  = 20; // 10 pt
    private static final int FS_SMALL = 18; //  9 pt

    // ─────────────────────────────────────────────────────────────────────────
    //  Public API
    // ─────────────────────────────────────────────────────────────────────────

    public byte[] build(CvData cv) throws IOException {
        try (XWPFDocument doc = new XWPFDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            setPageLayout(doc);
            addNameBlock(doc, cv);
            addDividerLine(doc);

            if (hasItems(cv.getHardSkills()))  addSkillsSection(doc, "SKILLS",      cv.getHardSkills(), false);
            if (hasItems(cv.getSoftSkills()))  addSkillsSection(doc, "SOFT SKILLS", cv.getSoftSkills(), true);
            if (hasItems(cv.getEducation()))   addEducationSection(doc, cv.getEducation());
            if (hasItems(cv.getExperience()))  addExperienceSection(doc, cv.getExperience());
            if (hasItems(cv.getProjects()))    addProjectsSection(doc, cv.getProjects());

            doc.write(out);
            return out.toByteArray();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Page layout
    // ─────────────────────────────────────────────────────────────────────────

    private void setPageLayout(XWPFDocument doc) {
        CTSectPr sect = doc.getDocument().getBody().addNewSectPr();

        CTPageSz sz = sect.addNewPgSz();
        sz.setW(BigInteger.valueOf(PAGE_W));
        sz.setH(BigInteger.valueOf(PAGE_H));

        CTPageMar mar = sect.addNewPgMar();
        mar.setTop(BigInteger.valueOf(MARGIN_V));
        mar.setBottom(BigInteger.valueOf(MARGIN_V));
        mar.setLeft(BigInteger.valueOf(MARGIN_H));
        mar.setRight(BigInteger.valueOf(MARGIN_H));
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Header block: Name + Job title
    // ─────────────────────────────────────────────────────────────────────────

    private void addNameBlock(XWPFDocument doc, CvData cv) {
        if (notEmpty(cv.getFullName())) {
            XWPFParagraph p = doc.createParagraph();
            setSpacing(p, 0, 60);
            XWPFRun r = p.createRun();
            r.setText(cv.getFullName().toUpperCase());
            r.setFontSize(FS_NAME / 2);
            r.setColor(BLACK);
            r.setFontFamily("Calibri Light");
        }

        if (notEmpty(cv.getPosition())) {
            XWPFParagraph p = doc.createParagraph();
            setSpacing(p, 0, 80);
            XWPFRun r = p.createRun();
            r.setText(cv.getPosition().toUpperCase());
            r.setFontSize(FS_TITLE / 2);
            r.setColor(MID);
            r.setFontFamily("Calibri");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Divider line  (paragraph bottom border)
    // ─────────────────────────────────────────────────────────────────────────

    private void addDividerLine(XWPFDocument doc) {
        XWPFParagraph p = doc.createParagraph();
        setSpacing(p, 0, 0);

        CTPPr pPr = getOrAddPPr(p);
        CTPBdr bdr = pPr.isSetPBdr() ? pPr.getPBdr() : pPr.addNewPBdr();
        CTBorder bottom = bdr.addNewBottom();
        bottom.setVal(STBorder.SINGLE);
        bottom.setSz(BigInteger.valueOf(4));
        bottom.setSpace(BigInteger.valueOf(1));
        bottom.setColor(DIVIDER_C);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Core two-column row (no table)
    //
    //  The label paragraph uses a "hanging-first-line" trick:
    //    indent.left    = LEFT_W   → continuation lines start at right column
    //    indent.hanging = LEFT_W   → first line starts at 0 (label position)
    //    tab stop at LEFT_W        → a single \t jumps to right column
    //
    //  So a label+content line looks like:
    //    "SKILLS\t• Java"
    //  And subsequent right-column-only lines use:
    //    indent.left = LEFT_W, hanging = 0  (pure right-column indent)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Emits the first paragraph of a section: label in left column,
     * first content text in right column (separated by a tab).
     * Returns the paragraph so the caller can add runs to the right side.
     */
    private XWPFParagraph addLabelRow(XWPFDocument doc,
                                      String label,
                                      int spacingBefore,
                                      int spacingAfter) {
        XWPFParagraph p = doc.createParagraph();
        setSpacing(p, spacingBefore, spacingAfter);
        setTwoColumnIndent(p, true); // hanging: first line at 0

        // Label run (left column)
        XWPFRun lr = p.createRun();
        lr.setText(label);
        lr.setFontSize(FS_LABEL / 2);
        lr.setColor(LIGHT_GRAY);
        lr.setFontFamily("Calibri");

        // Tab run (jump to right column)
        XWPFRun tab = p.createRun();
        tab.addTab();

        return p;
    }

    /**
     * Emits a paragraph that is purely in the right column
     * (indented LEFT_W from the page margin).
     */
    private XWPFParagraph addRightParagraph(XWPFDocument doc, int spacingBefore, int spacingAfter) {
        XWPFParagraph p = doc.createParagraph();
        setSpacing(p, spacingBefore, spacingAfter);
        setTwoColumnIndent(p, false); // no hanging: indent = LEFT_W
        return p;
    }

    /**
     * Sets indentation and a tab stop at LEFT_W.
     *
     * @param hanging true  → hanging indent (first line at 0, rest at LEFT_W)
     *                false → all lines indented LEFT_W
     */
    private void setTwoColumnIndent(XWPFParagraph p, boolean hanging) {
        CTPPr pPr = getOrAddPPr(p);

        // Tab stop at the right-column start
        CTTabs tabs = pPr.isSetTabs() ? pPr.getTabs() : pPr.addNewTabs();
        CTTabStop ts = tabs.addNewTab();
        ts.setVal(STTabJc.LEFT);
        ts.setPos(BigInteger.valueOf(LEFT_W));

        // Indentation
        CTInd ind = pPr.isSetInd() ? pPr.getInd() : pPr.addNewInd();
        ind.setLeft(BigInteger.valueOf(LEFT_W));
        if (hanging) {
            ind.setHanging(BigInteger.valueOf(LEFT_W));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Skills section
    // ─────────────────────────────────────────────────────────────────────────

    private void addSkillsSection(XWPFDocument doc, String label, List<String> skills, boolean soft) {
        if (skills.isEmpty()) return;

        if (!soft && skills.size() >= 6) {
            // Two sub-columns inside the right column
            int half   = (int) Math.ceil(skills.size() / 2.0);
            int tabPos = LEFT_W + RIGHT_W / 2; // mid-point of right column

            for (int i = 0; i < half; i++) {
                String leftSkill  = skills.get(i);
                String rightSkill = (i + half < skills.size()) ? skills.get(i + half) : null;

                XWPFParagraph p;
                if (i == 0) {
                    p = addLabelRow(doc, label, 120, 40);
                } else {
                    p = addRightParagraph(doc, 0, 40);
                }

                // Left skill run (already in right column after tab or indent)
                XWPFRun r1 = p.createRun();
                r1.setText("•  " + leftSkill);
                r1.setFontSize(FS_BODY / 2);
                r1.setColor(DARK);
                r1.setFontFamily("Calibri");

                if (rightSkill != null) {
                    // Second tab stop for the inner sub-column
                    addInnerTabStop(p, tabPos);

                    XWPFRun tabRun = p.createRun();
                    tabRun.addTab();

                    XWPFRun r2 = p.createRun();
                    r2.setText("•  " + rightSkill);
                    r2.setFontSize(FS_BODY / 2);
                    r2.setColor(DARK);
                    r2.setFontFamily("Calibri");
                }
            }
        } else {
            // Single column bullet list
            for (int i = 0; i < skills.size(); i++) {
                XWPFParagraph p;
                if (i == 0) {
                    p = addLabelRow(doc, label, 120, 40);
                } else {
                    p = addRightParagraph(doc, 0, 40);
                }
                XWPFRun r = p.createRun();
                r.setText("•  " + skills.get(i));
                r.setFontSize(FS_BODY / 2);
                r.setColor(DARK);
                r.setFontFamily("Calibri");
            }
        }

        addDividerLine(doc);
    }

    /** Adds an additional tab stop inside a paragraph's existing pPr. */
    private void addInnerTabStop(XWPFParagraph p, int pos) {
        CTPPr pPr = getOrAddPPr(p);
        CTTabs tabs = pPr.isSetTabs() ? pPr.getTabs() : pPr.addNewTabs();
        CTTabStop ts = tabs.addNewTab();
        ts.setVal(STTabJc.LEFT);
        ts.setPos(BigInteger.valueOf(pos));
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Education section
    // ─────────────────────────────────────────────────────────────────────────

    private void addEducationSection(XWPFDocument doc, List<CvData.EducationEntry> list) {
        boolean firstEntry = true;
        for (CvData.EducationEntry edu : list) {

            int beforeFirst = firstEntry ? 120 : 80;
            boolean labelWritten = false;

            // Line 1: Degree
            if (notEmpty(edu.getDegree())) {
                XWPFParagraph p;
                if (!labelWritten) {
                    p = addLabelRow(doc, "EDUCATION", beforeFirst, 20);
                    labelWritten = true;
                } else {
                    p = addRightParagraph(doc, 0, 20);
                }
                XWPFRun r = p.createRun();
                r.setText(edu.getDegree().toUpperCase());
                r.setBold(true);
                r.setFontSize(FS_ROLE / 2);
                r.setColor(BLACK);
                r.setFontFamily("Calibri");
            }

            // Line 2: University + duration
            StringBuilder meta = new StringBuilder();
            if (notEmpty(edu.getUniversity())) meta.append(edu.getUniversity());
            if (notEmpty(edu.getDuration()))   appendSep(meta).append(edu.getDuration());
            if (meta.length() > 0) {
                XWPFParagraph p;
                if (!labelWritten) {
                    p = addLabelRow(doc, "EDUCATION", beforeFirst, 20);
                    labelWritten = true;
                } else {
                    p = addRightParagraph(doc, 0, 20);
                }
                XWPFRun r = p.createRun();
                r.setText(meta.toString());
                r.setFontSize(FS_BODY / 2);
                r.setColor(DARK);
                r.setFontFamily("Calibri");
            }

            // Line 3: GPA
            if (notEmpty(edu.getGpa())) {
                XWPFParagraph p = addRightParagraph(doc, 0, 20);
                XWPFRun r = p.createRun();
                r.setText("GPA: " + edu.getGpa());
                r.setFontSize(FS_SMALL / 2);
                r.setColor(MID);
                r.setFontFamily("Calibri");
            }

            // Line 4: Additionally
            if (notEmpty(edu.getAdditionally())) {
                XWPFParagraph p = addRightParagraph(doc, 0, 0);
                XWPFRun r = p.createRun();
                r.setText(edu.getAdditionally());
                r.setFontSize(FS_SMALL / 2);
                r.setColor(MID);
                r.setFontFamily("Calibri");
            }

            firstEntry = false;
        }
        addDividerLine(doc);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Experience section
    // ─────────────────────────────────────────────────────────────────────────

    private void addExperienceSection(XWPFDocument doc, List<CvData.ExperienceEntry> list) {
        boolean firstEntry = true;
        for (CvData.ExperienceEntry exp : list) {

            int beforeFirst = firstEntry ? 120 : 100;
            boolean labelWritten = false;

            // Line 1: Role
            if (notEmpty(exp.getRole())) {
                XWPFParagraph p;
                if (!labelWritten) {
                    p = addLabelRow(doc, "EXPERIENCE", beforeFirst, 20);
                    labelWritten = true;
                } else {
                    p = addRightParagraph(doc, 0, 20);
                }
                XWPFRun r = p.createRun();
                r.setText(exp.getRole().toUpperCase());
                r.setBold(true);
                r.setFontSize(FS_ROLE / 2);
                r.setColor(BLACK);
                r.setFontFamily("Calibri");
            }

            // Line 2: Company + duration
            StringBuilder meta = new StringBuilder();
            if (notEmpty(exp.getCompany()))  meta.append(exp.getCompany());
            if (notEmpty(exp.getDuration())) appendSep(meta).append(exp.getDuration());
            if (meta.length() > 0) {
                XWPFParagraph p;
                if (!labelWritten) {
                    p = addLabelRow(doc, "EXPERIENCE", beforeFirst, 40);
                    labelWritten = true;
                } else {
                    p = addRightParagraph(doc, 0, 40);
                }
                XWPFRun r = p.createRun();
                r.setText(meta.toString());
                r.setBold(true);
                r.setFontSize(FS_SMALL / 2);
                r.setColor(MID);
                r.setFontFamily("Calibri");
            }

            // Lines 3+: Additionally (split by \n)
            if (notEmpty(exp.getAdditionally())) {
                for (String line : exp.getAdditionally().split("\n")) {
                    if (line.isBlank()) continue;
                    XWPFParagraph p = addRightParagraph(doc, 0, 20);
                    XWPFRun r = p.createRun();
                    r.setText(line.trim());
                    r.setFontSize(FS_BODY / 2);
                    r.setColor(DARK);
                    r.setFontFamily("Calibri");
                }
            }

            firstEntry = false;
        }
        addDividerLine(doc);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Projects section
    // ─────────────────────────────────────────────────────────────────────────

    private void addProjectsSection(XWPFDocument doc, List<CvData.ProjectEntry> list) {
        boolean firstEntry = true;
        for (CvData.ProjectEntry proj : list) {

            int beforeFirst = firstEntry ? 120 : 100;
            boolean labelWritten = false;

            // Title
            if (notEmpty(proj.getTitle())) {
                XWPFParagraph p;
                if (!labelWritten) {
                    p = addLabelRow(doc, "PROJECTS", beforeFirst, 20);
                    labelWritten = true;
                } else {
                    p = addRightParagraph(doc, 0, 20);
                }
                XWPFRun r = p.createRun();
                r.setText(proj.getTitle().toUpperCase());
                r.setBold(true);
                r.setFontSize(FS_ROLE / 2);
                r.setColor(BLACK);
                r.setFontFamily("Calibri");
            }

            // About
            if (notEmpty(proj.getAbout())) {
                XWPFParagraph p = addRightParagraph(doc, 0, 20);
                XWPFRun r = p.createRun();
                r.setText(proj.getAbout());
                r.setFontSize(FS_BODY / 2);
                r.setColor(DARK);
                r.setFontFamily("Calibri");
            }

            // Additionally
            if (notEmpty(proj.getAdditionally())) {
                XWPFParagraph p = addRightParagraph(doc, 0, 0);
                XWPFRun r = p.createRun();
                r.setText(proj.getAdditionally());
                r.setFontSize(FS_SMALL / 2);
                r.setColor(MID);
                r.setFontFamily("Calibri");
            }

            firstEntry = false;
        }
        addDividerLine(doc);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Paragraph helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void setSpacing(XWPFParagraph p, int before, int after) {
        CTPPr pPr = getOrAddPPr(p);
        CTSpacing sp = pPr.isSetSpacing() ? pPr.getSpacing() : pPr.addNewSpacing();
        sp.setBefore(BigInteger.valueOf(before));
        sp.setAfter(BigInteger.valueOf(after));
        sp.setLineRule(STLineSpacingRule.AUTO);
    }

    private CTPPr getOrAddPPr(XWPFParagraph p) {
        CTPPr pPr = p.getCTP().getPPr();
        return pPr != null ? pPr : p.getCTP().addNewPPr();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Util
    // ─────────────────────────────────────────────────────────────────────────

    private boolean notEmpty(String s) {
        return s != null && !s.isBlank();
    }

    private boolean hasItems(List<?> list) {
        return list != null && !list.isEmpty();
    }

    private StringBuilder appendSep(StringBuilder sb) {
        if (sb.length() > 0) sb.append("  |  ");
        return sb;
    }
}
