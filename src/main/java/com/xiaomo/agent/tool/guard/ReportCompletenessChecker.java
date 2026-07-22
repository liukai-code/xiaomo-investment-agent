package com.xiaomo.agent.tool.guard;

import com.fasterxml.jackson.annotation.JsonIgnoreType;

import java.io.Serializable;

@JsonIgnoreType
public class ReportCompletenessChecker implements Serializable {

    private final int minLength;
    private final int minSections;
    private final StringBuilder accumulatedText = new StringBuilder();

    public ReportCompletenessChecker(int minLength, int minSections) {
        this.minLength = minLength;
        this.minSections = minSections;
    }

    public void appendChunk(String chunk) {
        accumulatedText.append(chunk);
    }

    public boolean isReportSubstantial() {
        String text = accumulatedText.toString();
        if (text.length() < minLength) return false;
        long sectionCount = text.lines()
                .filter(line -> line.trim().startsWith("##"))
                .count();
        return sectionCount >= minSections;
    }

    public int getAccumulatedLength() {
        return accumulatedText.length();
    }
}
