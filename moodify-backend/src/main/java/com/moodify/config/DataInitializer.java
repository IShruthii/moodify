package com.moodify.config;

import com.moodify.entity.Badge;
import com.moodify.repository.BadgeRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
public class DataInitializer implements CommandLineRunner {

    private final BadgeRepository badgeRepository;

    public DataInitializer(BadgeRepository badgeRepository) {
        this.badgeRepository = badgeRepository;
    }

    @SuppressWarnings("null")
    @Override
    public void run(String... args) {
        if (badgeRepository.count() == 0) {
            List<Badge> badges = Arrays.asList(
                    new Badge("FIRST_MOOD", "First Check-In", "Logged your very first mood", "🌱", 0),
                    new Badge("STREAK_3", "3-Day Streak", "Checked in 3 days in a row", "🔥", 3),
                    new Badge("STREAK_7", "Week Warrior", "Checked in 7 days in a row", "⚡", 7),
                    new Badge("STREAK_14", "Fortnight Focus", "Checked in 14 days in a row", "💎", 14),
                    new Badge("STREAK_30", "Monthly Master", "Checked in 30 days in a row", "👑", 30),
                    new Badge("ENTRIES_10", "Mood Explorer", "Logged 10 mood entries", "🗺️", 0),
                    new Badge("ENTRIES_50", "Mood Veteran", "Logged 50 mood entries", "🏆", 0));
            badgeRepository.saveAll(badges);
        }
    }
}
