package com.xiaomo.agent.tool.guard;

import com.fasterxml.jackson.annotation.JsonIgnoreType;
import java.io.Serializable;

@JsonIgnoreType
public class SearchSessionTracker implements Serializable {

    private final int maxSearchRounds;
    private int searchCount = 0;

    public SearchSessionTracker(int maxSearchRounds) {
        this.maxSearchRounds = maxSearchRounds;
    }

    public SearchResult recordSearch() {
        searchCount++;
        return new SearchResult(searchCount, searchCount > maxSearchRounds);
    }

    public int getSearchCount() {
        return searchCount;
    }

    public record SearchResult(int totalSearches, boolean isOverLimit) {}
}