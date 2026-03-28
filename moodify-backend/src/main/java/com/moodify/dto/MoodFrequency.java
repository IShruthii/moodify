package com.moodify.dto;

public class MoodFrequency {
    private String mood;
    private String emoji;
    private long count;

    public MoodFrequency() {}

    public MoodFrequency(String mood, long count) {
        this.mood = mood;
        this.count = count;
    }

    public MoodFrequency(String mood, String emoji, long count) {
        this.mood = mood;
        this.emoji = emoji;
        this.count = count;
    }

    public String getMood() { return mood; }
    public void setMood(String mood) { this.mood = mood; }

    public String getEmoji() { return emoji; }
    public void setEmoji(String emoji) { this.emoji = emoji; }

    public long getCount() { return count; }
    public void setCount(long count) { this.count = count; }
}
