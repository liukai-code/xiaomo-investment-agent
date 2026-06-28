package com.itlk.myclaudecode.tool.guard;

import java.io.Serializable;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

public class RepetitionDetector implements Serializable {

    private final Map<String, LinkedList<String>> callHistory = new HashMap<>();
    private final int threshold;

    public RepetitionDetector(int threshold) {
        this.threshold = threshold;
    }

    public RepetitionResult recordAndDetect(String toolName, String args) {
        LinkedList<String> history = callHistory.computeIfAbsent(toolName, k -> new LinkedList<>());
        String normalized = normalize(args);
        history.addLast(normalized);
        if (history.size() > threshold) {
            history.removeFirst();
        }

        if (history.size() < threshold) {
            return RepetitionResult.NONE;
        }

        String[] arr = history.toArray(new String[0]);

        boolean allIdentical = true;
        for (int i = 1; i < arr.length; i++) {
            if (!arr[0].equals(arr[i])) {
                allIdentical = false;
                break;
            }
        }
        if (allIdentical) {
            return RepetitionResult.STUCK_IDENTICAL;
        }

        double totalSimilarity = 0;
        int pairs = 0;
        for (int i = 0; i < arr.length; i++) {
            for (int j = i + 1; j < arr.length; j++) {
                totalSimilarity += SimilarityUtils.computeSimilarity(arr[i], arr[j]);
                pairs++;
            }
        }
        double avgSimilarity = pairs > 0 ? totalSimilarity / pairs : 0;
        if (avgSimilarity > 0.8) {
            return RepetitionResult.STUCK_SIMILAR;
        }

        return RepetitionResult.NONE;
    }

    private String normalize(String args) {
        if (args == null) return "";
        return args.trim().replaceAll("\\s+", " ");
    }

    public enum RepetitionResult {
        NONE, STUCK_IDENTICAL, STUCK_SIMILAR
    }
}
