package com.moodify.repository;

import com.moodify.dto.MoodFrequency;
import com.moodify.entity.MoodEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface MoodEntryRepository extends JpaRepository<MoodEntry, Long> {

    List<MoodEntry> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<MoodEntry> findByUserIdAndEntryDateBetweenOrderByEntryDateAsc(Long userId, LocalDate start, LocalDate end);

    Optional<MoodEntry> findTopByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<MoodEntry> findByUserIdAndEntryDate(Long userId, LocalDate date);

    @Query("SELECT new com.moodify.dto.MoodFrequency(m.mood, COUNT(m)) " +
           "FROM MoodEntry m WHERE m.user.id = :userId GROUP BY m.mood ORDER BY COUNT(m) DESC")
    List<MoodFrequency> findMoodFrequencyByUserId(@Param("userId") Long userId);

    @Query("SELECT COUNT(m) FROM MoodEntry m WHERE m.user.id = :userId AND m.positivityScore >= 5")
    Long countPositiveMoodsByUserId(@Param("userId") Long userId);

    @Query("SELECT COUNT(m) FROM MoodEntry m WHERE m.user.id = :userId")
    Long countAllMoodsByUserId(@Param("userId") Long userId);

    List<MoodEntry> findByUserIdAndEntryDateGreaterThanEqualOrderByEntryDateAsc(Long userId, LocalDate date);

    @Query("SELECT m.entryDate FROM MoodEntry m WHERE m.user.id = :userId ORDER BY m.entryDate DESC")
    List<LocalDate> findEntryDatesByUserId(@Param("userId") Long userId);

    @Query("SELECT DISTINCT m.entryDate FROM MoodEntry m WHERE m.user.id = :userId AND m.entryDate >= :date ORDER BY m.entryDate ASC")
    List<LocalDate> findEntryDatesByUserIdAndEntryDateGreaterThanEqual(@Param("userId") Long userId, @Param("date") LocalDate date);
}
