package com.squadsync.backend.service;

import com.squadsync.backend.dto.GameSessionDto;
import com.squadsync.backend.model.Game;
import com.squadsync.backend.model.GameSession;
import com.squadsync.backend.repository.GameSessionRepository;
import com.squadsync.backend.util.DateUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class TimezoneHandlingTest {

    @Mock
    private GameSessionRepository sessionRepository;

    @InjectMocks
    private MatchmakingService matchmakingService;

    @Test
    public void testMadridServerFix() {
        // SCENARIO:
        // DB Session Ends at 23:00 Madrid.
        // "Real" time is 23:30 Madrid.

        // 1. Setup "Real" Time: 23:30 Madrid
        LocalDateTime realNowMadrid = LocalDateTime.of(2023, 12, 12, 23, 30);
        Instant realNowInstant = realNowMadrid.atZone(ZoneId.of("Europe/Madrid")).toInstant();

        // 2. Fix: Ensure DateUtils uses Madrid Clock
        Clock madridClock = Clock.fixed(realNowInstant, ZoneId.of("Europe/Madrid"));
        DateUtils.setClock(madridClock);

        System.out.println("DEBUG: DateUtils.now() is " + DateUtils.now());

        // 3. Setup Session: Ends at 23:00 Madrid
        GameSession session = new GameSession();
        session.setId("1");
        Game game = new Game();
        game.setId("g1");
        game.setTitle("Test Game");
        session.setGame(game);
        session.setPlayers(new ArrayList<>());

        session.setStartTime(LocalDateTime.of(2023, 12, 12, 21, 0)); // 21:00 Madrid
        session.setEndTime(LocalDateTime.of(2023, 12, 12, 23, 0)); // 23:00 Madrid

        // Mock Repo
        when(sessionRepository.findByEndTimeGreaterThanOrderByStartTimeAsc(any())).thenReturn(List.of(session));

        // 4. Run getUpcomingSessions
        List<GameSessionDto> results = matchmakingService.getUpcomingSessions();

        // 5. Assert: Session SHOULD be filtered out because 23:00 (End) < 23:30 (Now)
        if (!results.isEmpty()) {
            // Explicitly print values to stderr so it shows up in truncated output if
            // possible
            System.err.println(
                    "FAILURE DEBUG: SessionEndTime=" + session.getEndTime() + ", DateUtils.now()=" + DateUtils.now());
            Assertions.fail("Session should be filtered out. Session End: " + session.getEndTime()
                    + ", DateUtils.now(): " + DateUtils.now());
        }

        // Cleanup
        DateUtils.resetClock();
    }
}
