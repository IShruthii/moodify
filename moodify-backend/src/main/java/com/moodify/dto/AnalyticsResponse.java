package com.moodify.dto;

import java.util.List;
import java.util.Map;

public class AnalyticsResponse {

    private int currentStreak;
    private int longestStreak;
    private int totalEntries;
    private double positiveRatio;
    private String mostFrequentMood;
    private String mostFrequentMoodEmoji;
    private List<BadgeInfo> badges;
    private Map<String, String> calendarData;
    private List<MoodFrequency> moodFrequencies;

    public AnalyticsResponse() {}

    public static class BadgeInfo {
        private String code;
        private String name;
        private String description;
        private String emoji;
        private String earnedAt;

        public BadgeInfo() {}

        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public String getEmoji() { return emoji; }
        public void setEmoji(String emoji) { this.emoji = emoji; }

        public String getEarnedAt() { return earnedAt; }
        public void setEarnedAt(String earnedAt) { this.earnedAt = earnedAt; }
    }

    public int getCurrentStreak() { return currentStreak; }
    public void setCurrentStreak(int currentStreak) { this.currentStreak = currentStreak; }

    public int getLongestStreak() { return longestStreak; }
    public void setLongestStreak(int longestStreak) { this.longestStreak = longestStreak; }

    public int getTotalEntries() { return totalEntries; }
    public void setTotalEntries(int totalEntries) { this.totalEntries = totalEntries; }

    public double getPositiveRatio() { return positiveRatio; }
    public void setPositiveRatio(double positiveRatio) { this.positiveRatio = positiveRatio; }

    public String getMostFrequentMood() { return mostFrequentMood; }
    public void setMostFrequentMood(String mostFrequentMood) { this.mostFrequentMood = mostFrequentMood; }

    public String getMostFrequentMoodEmoji() { return mostFrequentMoodEmoji; }
    public void setMostFrequentMoodEmoji(String mostFrequentMoodEmoji) { this.mostFrequentMoodEmoji = mostFrequentMoodEmoji; }

    public List<BadgeInfo> getBadges() { return badges; }
    public void setBadges(List<BadgeInfo> badges) { this.badges = badges; }

    public Map<String, String> getCalendarData() { return calendarData; }
    public void setCalendarData(Map<String, String> calendarData) { this.calendarData = calendarData; }

    public List<MoodFrequency> getMoodFrequencies() { return moodFrequencies; }
    public void setMoodFrequencies(List<MoodFrequency> moodFrequencies) { this.moodFrequencies = moodFrequencies; }
}
