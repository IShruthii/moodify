package com.moodify.repository;

import com.moodify.entity.Badge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BadgeRepository extends JpaRepository<Badge, Long> {
    Optional<Badge> findByCode(String code);

    @Query("SELECT b FROM Badge b WHERE b.code IN :codes")
    List<Badge> findByCodeIn(@Param("codes") List<String> codes);
}
