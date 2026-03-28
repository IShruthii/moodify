package com.moodify.service.impl;

import com.moodify.dto.AnalyticsResponse;
import com.moodify.dto.AnalyticsResponse.BadgeInfo;
import com.moodify.dto.MoodFrequency;
import com.moodify.entity.Badge;
import com.moodify.entity.MoodEntry;
import com.moodify.entity.UserBadge;
import com.moodify.entity.UserFeedback;
import com.moodify.repository.BadgeRepository;
import com.moodify.repository.MoodEntryRepository;
import com.moodify.repository.UserBadgeRepository;
import com.moodify.repository.UserFeedbackRepository;
import com.moodify.repository.UserRepository;
import com.moodify.service.AnalyticsService;
import com.moodify.util.MoodData;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AnalyticsServiceImpl implements AnalyticsService {

    private final MoodEntryRepository moodEntryRepository;
    private final UserBadgeRepository userBadgeRepository;
    private final BadgeRepository badgeRepository;
    private final UserRepository userRepository;
    private final UserFeedbackRepository feedbackRepository;

    public AnalyticsServiceImpl(MoodEntryRepository moodEntryRepository,
                                 UserBadgeRepository userBadgeRepository,
                                 BadgeRepository badgeRepository,
                                 UserRepository userRepository,
                                 UserFeedbackRepository feedbackRepository) {
        this.moodEntryRepository = moodEntryRepository;
        this.userBadgeRepository = userBadgeRepository;
        this.badgeRepository = badgeRepository;
        this.userRepository = userRepository;
        this.feedbackRepository = feedbackRepository;
    }

    @Override
    public AnalyticsResponse getAnalytics(Long userId) {
        AnalyticsResponse response = new AnalyticsResponse();

        int currentStreak = calculateCurrentStreak(userId);
        int longestStreak = calculateLongestStreak(userId);
        long totalEntries = moodEntryRepository.countAllMoodsByUserId(userId);
        long positiveEntries = moodEntryRepository.countPositiveMoodsByUserId(userId);
        double positiveRatio = totalEntries > 0 ? (double) positiveEntries / totalEntries * 100 : 0;

        response.setCurrentStreak(currentStreak);
        response.setLongestStreak(longestStreak);
        response.setTotalEntries((int) totalEntries);
        response.setPositiveRatio(Math.round(positiveRatio * 10.0) / 10.0);

        List<MoodFrequency> frequencies = moodEntryRepository.findMoodFrequencyByUserId(userId);
        enrichMoodFrequencies(frequencies);

        String topMood = frequencies.isEmpty() ? "NEUTRAL" : frequencies.get(0).getMood();
        response.setMostFrequentMood(topMood);
        response.setMostFrequentMoodEmoji(MoodData.getMoodInfo(topMood).getEmoji());
        response.setMoodFrequencies(frequencies);

        response.setCalendarData(buildCalendarData(userId, 90));

        List<UserBadge> userBadges = userBadgeRepository.findByUserId(userId);
        response.setBadges(userBadges.stream().map(this::mapToBadgeInfo).collect(Collectors.toList()));

        return response;
    }

    @Override
    @Transactional
    public void checkAndAwardBadges(Long userId) {
        int streak = calculateCurrentStreak(userId);
        long totalEntries = moodEntryRepository.countAllMoodsByUserId(userId);

        Map<String, Boolean> eligibility = new LinkedHashMap<>();
        eligibility.put("FIRST_MOOD", streak >= 0 && totalEntries >= 1);
        eligibility.put("STREAK_3", streak >= 3);
        eligibility.put("STREAK_7", streak >= 7);
        eligibility.put("STREAK_14", streak >= 14);
        eligibility.put("STREAK_30", streak >= 30);
        eligibility.put("ENTRIES_10", totalEntries >= 10);
        eligibility.put("ENTRIES_50", totalEntries >= 50);

        List<String> codesToAward = eligibility.entrySet().stream()
                .filter(Map.Entry::getValue)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        if (codesToAward.isEmpty()) return;

        Set<String> existingCodes = userBadgeRepository.findByUserId(userId).stream()
                .map(ub -> ub.getBadge().getCode())
                .collect(Collectors.toSet());

        codesToAward.removeAll(existingCodes);

        if (!codesToAward.isEmpty()) {
            List<Badge> badges = badgeRepository.findByCodeIn(codesToAward);
            userRepository.findById(userId).ifPresent(user -> {
                for (Badge badge : badges) {
                    userBadgeRepository.save(new UserBadge(user, badge));
                }
            });
        }
    }

    @Override
    public int calculateCurrentStreak(Long userId) {
        // Fetch only distinct dates to simplify streak calculation
        List<LocalDate> dates = moodEntryRepository.findEntryDatesByUserIdAndEntryDateGreaterThanEqual(
                userId, LocalDate.now().minusDays(100)); // 100 days is enough for current streak

        if (dates.isEmpty()) return 0;

        Set<LocalDate> entryDates = new HashSet<>(dates);
        int streak = 0;
        LocalDate checkDate = LocalDate.now();

        // If no entry today, check if there was one yesterday (streak might still be alive)
        if (!entryDates.contains(checkDate)) {
            checkDate = checkDate.minusDays(1);
        }

        while (entryDates.contains(checkDate)) {
            streak++;
            checkDate = checkDate.minusDays(1);
        }

        return streak;
    }

    private int calculateLongestStreak(Long userId) {
        List<LocalDate> sortedDates = moodEntryRepository.findEntryDatesByUserId(userId).stream()
                .distinct()
                .sorted()
                .collect(Collectors.toList());

        if (sortedDates.isEmpty()) return 0;

        int longest = 1;
        int current = 1;

        for (int i = 1; i < sortedDates.size(); i++) {
            if (sortedDates.get(i).equals(sortedDates.get(i - 1).plusDays(1))) {
                current++;
                longest = Math.max(longest, current);
            } else {
                current = 1;
            }
        }

        return longest;
    }

    private Map<String, String> buildCalendarData(Long userId, int daysBack) {
        LocalDate start = LocalDate.now().minusDays(daysBack);
        List<MoodEntry> entries = moodEntryRepository
                .findByUserIdAndEntryDateBetweenOrderByEntryDateAsc(userId, start, LocalDate.now());

        Map<String, String> calendarData = new LinkedHashMap<>();
        DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE;

        for (MoodEntry entry : entries) {
            calendarData.put(entry.getEntryDate().format(formatter), entry.getMood());
        }

        return calendarData;
    }

    @Override
    public Map<String, Object> getMonthlyReport(Long userId, int year, int month) {
        YearMonth ym = YearMonth.of(year, month);
        LocalDate start = ym.atDay(1);
        LocalDate end   = ym.atEndOfMonth();

        List<MoodEntry> moods = moodEntryRepository
                .findByUserIdAndEntryDateBetweenOrderByEntryDateAsc(userId, start, end);

        List<UserFeedback> feedbacks = feedbackRepository
                .findByUserIdAndCreatedAtBetweenOrderByCreatedAtAsc(
                        userId,
                        start.atStartOfDay(),
                        end.plusDays(1).atStartOfDay()); // Correct boundary check

        Map<String, Long> moodFreqRaw = moods.stream()
                .collect(Collectors.groupingBy(MoodEntry::getMood, Collectors.counting()));

        long positive = moods.stream()
                .filter(m -> m.getPositivityScore() != null && m.getPositivityScore() >= 5)
                .count();
        double positiveRatio = moods.isEmpty() ? 0 : Math.round((double) positive / moods.size() * 1000.0) / 10.0;

        double avgRating = feedbacks.stream()
                .mapToInt(UserFeedback::getRating)
                .average().orElse(0.0);

        DateTimeFormatter fmt = DateTimeFormatter.ISO_LOCAL_DATE;
        Map<String, String> moodCalendar = new LinkedHashMap<>();
        for (MoodEntry e : moods) {
            moodCalendar.put(e.getEntryDate().format(fmt), e.getMood());
        }

        Map<String, Integer> feedbackCalendar = new LinkedHashMap<>();
        for (UserFeedback f : feedbacks) {
            feedbackCalendar.put(f.getCreatedAt().toLocalDate().format(fmt), f.getRating());
        }

        String topMood = moodFreqRaw.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey).orElse("NEUTRAL");

        String userName = userRepository.findById(userId)
                .map(com.moodify.entity.User::getName).orElse("User");

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("userName", userName);
        report.put("year", year);
        report.put("month", month);
        report.put("monthName", ym.getMonth().getDisplayName(
                java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH));
        report.put("totalMoodEntries", moods.size());
        report.put("positiveRatio", positiveRatio);
        report.put("mostFrequentMood", topMood);
        report.put("mostFrequentMoodEmoji", MoodData.getMoodInfo(topMood).getEmoji());
        report.put("currentStreak", calculateCurrentStreak(userId));
        report.put("totalFeedback", feedbacks.size());
        report.put("averageFeedbackRating", Math.round(avgRating * 10.0) / 10.0);
        report.put("moodFrequency", moodFreqRaw);
        report.put("calendarData", moodCalendar);
        report.put("feedbackCalendar", feedbackCalendar);
        report.put("recentFeedbackComments", feedbacks.stream()
                .filter(f -> f.getComment() != null && !f.getComment().isBlank())
                .map(f -> Map.of(
                        "rating", f.getRating(),
                        "comment", f.getComment(),
                        "date", f.getCreatedAt().toLocalDate().format(fmt),
                        "sessionType", f.getSessionType() != null ? f.getSessionType() : "GENERAL"
                ))
                .collect(Collectors.toList()));

        return report;
    }

    private BadgeInfo mapToBadgeInfo(UserBadge ub) {
        BadgeInfo bi = new BadgeInfo();
        bi.setCode(ub.getBadge().getCode());
        bi.setName(ub.getBadge().getName());
        bi.setDescription(ub.getBadge().getDescription());
        bi.setEmoji(ub.getBadge().getEmoji());
        bi.setEarnedAt(ub.getEarnedAt().toLocalDate().toString());
        return bi;
    }

    private void enrichMoodFrequencies(List<MoodFrequency> frequencies) {
        for (MoodFrequency freq : frequencies) {
            freq.setEmoji(MoodData.getMoodInfo(freq.getMood()).getEmoji());
        }
    }
}
