package com.xiaomo.agent.tool.guard;

import com.fasterxml.jackson.annotation.JsonIgnoreType;

import java.io.Serializable;
import java.util.LinkedList;

@JsonIgnoreType
public class InfoGainTracker implements Serializable {

    private static final int MAX_SUMMARY_LENGTH = 500;

    private final LinkedList<String> recentSummaries = new LinkedList<>();
    private final int windowSize;
    private final double threshold;

    public InfoGainTracker(int windowSize, double threshold) {
        this.windowSize = windowSize;
        this.threshold = threshold;
    }

    public InfoGainLevel recordAndGetLevel(String toolResult) {
        String summary = truncate(toolResult);
        recentSummaries.addLast(summary);
        if (recentSummaries.size() > windowSize) {
            recentSummaries.removeFirst();
        }
        return evaluate();
    }

    private InfoGainLevel evaluate() {
        if (recentSummaries.size() < windowSize) {
            return InfoGainLevel.UNKNOWN;
        }

        double totalSimilarity = 0;
        int pairs = 0;
        String[] arr = recentSummaries.toArray(new String[0]);
        for (int i = 0; i < arr.length; i++) {
            for (int j = i + 1; j < arr.length; j++) {
                totalSimilarity += SimilarityUtils.computeSimilarity(arr[i], arr[j]);
                pairs++;
            }
        }

        double avgSimilarity = pairs > 0 ? totalSimilarity / pairs : 0;
        return avgSimilarity > threshold ? InfoGainLevel.LOW : InfoGainLevel.HIGH;
    }

    public double getLastSimilarity() {
        if (recentSummaries.size() < 2) return 0.0;
        String[] arr = recentSummaries.toArray(new String[0]);
        return SimilarityUtils.computeSimilarity(arr[arr.length - 2], arr[arr.length - 1]);
    }

    private String truncate(String text) {
        if (text == null) return "";
        return text.length() > MAX_SUMMARY_LENGTH ? text.substring(0, MAX_SUMMARY_LENGTH) : text;
    }

    public enum InfoGainLevel {
        UNKNOWN, HIGH, LOW
    }
}
