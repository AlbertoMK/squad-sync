package com.squadsync.backend.repository;

import com.squadsync.backend.model.GameSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface GameSessionRepository extends JpaRepository<GameSession, String> {
    List<GameSession> findByStartTimeGreaterThanEqualOrderByStartTimeAsc(LocalDateTime startTime);

    List<GameSession> findByGameIdOrderByCreatedAtDesc(String gameId);

    List<GameSession> findByEndTimeGreaterThanOrderByStartTimeAsc(LocalDateTime now);
}
