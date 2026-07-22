package com.xiaomo.agent.tool.guard;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SimilarityUtils {

    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\d+\\.?\\d*");
    private static final Pattern WORD_PATTERN = Pattern.compile("[\\w一-鿿]+");

    private static final double WEIGHT_JACCARD = 0.5;
    private static final double WEIGHT_NUMERIC = 0.3;
    private static final double WEIGHT_LENGTH = 0.2;

    private SimilarityUtils() {}

    public static double computeSimilarity(String text1, String text2) {
        if (text1 == null || text2 == null) return 0.0;
        if (text1.equals(text2)) return 1.0;

        double jaccard = jaccardWordSimilarity(text1, text2);
        double numeric = numericSimilarity(text1, text2);
        double length = lengthSimilarity(text1, text2);

        return WEIGHT_JACCARD * jaccard + WEIGHT_NUMERIC * numeric + WEIGHT_LENGTH * length;
    }

    static double jaccardWordSimilarity(String text1, String text2) {
        Set<String> words1 = extractWords(text1);
        Set<String> words2 = extractWords(text2);

        if (words1.isEmpty() && words2.isEmpty()) return 1.0;
        if (words1.isEmpty() || words2.isEmpty()) return 0.0;

        Set<String> intersection = new HashSet<>(words1);
        intersection.retainAll(words2);

        Set<String> union = new HashSet<>(words1);
        union.addAll(words2);

        return (double) intersection.size() / union.size();
    }

    static double numericSimilarity(String text1, String text2) {
        Set<String> nums1 = extractNumbers(text1);
        Set<String> nums2 = extractNumbers(text2);

        if (nums1.isEmpty() && nums2.isEmpty()) return 1.0;
        if (nums1.isEmpty() || nums2.isEmpty()) return 0.0;

        Set<String> intersection = new HashSet<>(nums1);
        intersection.retainAll(nums2);

        Set<String> union = new HashSet<>(nums1);
        union.addAll(nums2);

        return (double) intersection.size() / union.size();
    }

    static double lengthSimilarity(String text1, String text2) {
        int len1 = text1.length();
        int len2 = text2.length();
        if (len1 == 0 && len2 == 0) return 1.0;
        if (len1 == 0 || len2 == 0) return 0.0;
        return (double) Math.min(len1, len2) / Math.max(len1, len2);
    }

    private static Set<String> extractWords(String text) {
        Set<String> words = new HashSet<>();
        Matcher matcher = WORD_PATTERN.matcher(text.toLowerCase());
        while (matcher.find()) {
            words.add(matcher.group());
        }
        return words;
    }

    private static Set<String> extractNumbers(String text) {
        Set<String> numbers = new HashSet<>();
        Matcher matcher = NUMBER_PATTERN.matcher(text);
        while (matcher.find()) {
            numbers.add(matcher.group());
        }
        return numbers;
    }
}
