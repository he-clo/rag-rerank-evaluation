package com.example;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 对应 testset.json 的顶层结构：{ "groups": [...], "meta": {...} }
 */
public class TestSet {

    private List<TestCase> groups;
    private Meta meta;

    public List<TestCase> getGroups() { return groups; }
    public void setGroups(List<TestCase> groups) { this.groups = groups; }

    public Meta getMeta() { return meta; }
    public void setMeta(Meta meta) { this.meta = meta; }

    public static class Meta {
        @JsonProperty("total_groups")
        private Integer totalGroups;
        @JsonProperty("negatives_per_positive")
        private Integer negativesPerPositive;
        @JsonProperty("seed")
        private Integer seed;
        @JsonProperty("source")
        private String source;
        @JsonProperty("description")
        private String description;

        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }

        public Integer getTotalGroups() { return totalGroups; }
        public void setTotalGroups(Integer totalGroups) { this.totalGroups = totalGroups; }

        public Integer getNegativesPerPositive() { return negativesPerPositive; }
        public void setNegativesPerPositive(Integer negativesPerPositive) { this.negativesPerPositive = negativesPerPositive; }

        public Integer getSeed() { return seed; }
        public void setSeed(Integer seed) { this.seed = seed; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }
}
