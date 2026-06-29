package com.itlk.myclaudecode.tool.guard;

import com.fasterxml.jackson.annotation.JsonIgnoreType;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

@JsonIgnoreType
public class FetchSessionTracker implements Serializable {

    private final int maxFetches;
    private final int maxConsecutiveNoNewInfo;
    private final Set<String> visitedUrls = new HashSet<>();
    private int fetchCount = 0;
    private int consecutiveNoNewInfo = 0;

    public FetchSessionTracker(int maxFetches, int maxConsecutiveNoNewInfo) {
        this.maxFetches = maxFetches;
        this.maxConsecutiveNoNewInfo = maxConsecutiveNoNewInfo;
    }

    public boolean isUrlVisited(String url) {
        return visitedUrls.contains(normalizeUrl(url));
    }

    public FetchResult recordFetch(String url, InfoGainTracker.InfoGainLevel infoGain) {
        fetchCount++;
        String normalizedUrl = normalizeUrl(url);
        boolean duplicate = !visitedUrls.add(normalizedUrl);

        if (infoGain == InfoGainTracker.InfoGainLevel.LOW) {
            consecutiveNoNewInfo++;
        } else if (infoGain == InfoGainTracker.InfoGainLevel.HIGH) {
            consecutiveNoNewInfo = 0;
        }

        return new FetchResult(fetchCount, duplicate, consecutiveNoNewInfo);
    }

    public boolean isOverMaxFetches() {
        return fetchCount >= maxFetches;
    }

    public boolean isStuckNoNewInfo() {
        return consecutiveNoNewInfo >= maxConsecutiveNoNewInfo;
    }

    public int getFetchCount() {
        return fetchCount;
    }

    public int getVisitedUrlCount() {
        return visitedUrls.size();
    }

    private String normalizeUrl(String url) {
        if (url == null) return "";
        return url.trim().toLowerCase()
                .replaceAll("/+$", "")
                .replaceAll("#.*$", "");
    }

    public record FetchResult(int totalFetches, boolean isDuplicateUrl, int consecutiveNoNewInfo) {}
}
