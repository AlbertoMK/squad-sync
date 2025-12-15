package com.squadsync.backend.service;

import com.squadsync.backend.dto.GameSessionDto;
import com.squadsync.backend.model.*;
import com.squadsync.backend.repository.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class MatchmakingRulesTest {

        @Mock
        private AvailabilitySlotRepository slotRepository;
        @Mock
        private GameRepository gameRepository;
        @Mock
        private UserGamePreferenceRepository preferenceRepository;
        @Mock
        private GameSessionRepository sessionRepository;
        @Mock
        private GameSessionService gameSessionService;
        @Mock
        private org.springframework.context.ApplicationEventPublisher eventPublisher;

        @InjectMocks
        private MatchmakingService matchmakingService;

        // Helper to create basic objects
        private User createUser(String id) {
                User u = new User();
                u.setId(id);
                return u;
        }

        private Game createGame(String id) {
                Game g = new Game();
                g.setId(id);
                g.setTitle("Game " + id);
                return g;
        }

        private AvailabilityGamePreference createPreference(AvailabilitySlot slot, Game game, int weight) {
                AvailabilityGamePreference p = new AvailabilityGamePreference();
                p.setAvailabilitySlot(slot);
                p.setGame(game);
                p.setWeight(weight);
                return p;
        }

        private void setupMocks(List<AvailabilitySlot> slots, Game game) {
                when(sessionRepository.findByEndTimeGreaterThanOrderByStartTimeAsc(any()))
                                .thenReturn(Collections.emptyList());
                when(slotRepository.findByEndTimeGreaterThanOrderByStartTimeAsc(any())).thenReturn(slots);
                when(slotRepository.findAllById(anyList())).thenAnswer(invocation -> {
                        List<String> ids = invocation.getArgument(0);
                        List<AvailabilitySlot> result = new ArrayList<>();
                        for (String id : ids) {
                                slots.stream().filter(s -> s.getId().equals(id)).findFirst().ifPresent(result::add);
                        }
                        return result;
                });
                when(gameRepository.findAll()).thenReturn(List.of(game));
                when(preferenceRepository.findByUserIdIn(anyList())).thenReturn(Collections.emptyList());
                when(sessionRepository.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));
                when(gameSessionService.getSessionStatus(any())).thenReturn(GameSession.SessionStatus.PRELIMINARY);
        }

        // A: 2 jugadores tienen una ventana compartida de 4h: se generan 2 sesiones de
        // 2h.
        @Test
        public void testScenarioA_4hWindow_SplitIntoTwo2hSessions() {
                LocalDateTime baseTime = LocalDateTime.now().plusDays(1).withHour(10).withMinute(0).withSecond(0)
                                .truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
                Game game = createGame("g1");
                User u1 = createUser("u1");
                User u2 = createUser("u2");

                List<AvailabilitySlot> slots = new ArrayList<>();
                // U1: 10:00 - 14:00 (4h)
                AvailabilitySlot s1 = new AvailabilitySlot();
                s1.setId("s1");
                s1.setUser(u1);
                s1.setStartTime(baseTime);
                s1.setEndTime(baseTime.plusHours(4));
                s1.setPreferences(List.of(createPreference(s1, game, 10)));
                slots.add(s1);

                // U2: 10:00 - 14:00 (4h)
                AvailabilitySlot s2 = new AvailabilitySlot();
                s2.setId("s2");
                s2.setUser(u2);
                s2.setStartTime(baseTime);
                s2.setEndTime(baseTime.plusHours(4));
                s2.setPreferences(List.of(createPreference(s2, game, 10)));
                slots.add(s2);

                setupMocks(slots, game);

                List<GameSessionDto> results = matchmakingService.runMatchmaking();

                Assertions.assertEquals(2, results.size(), "Should verify 4h window splits into 2 sessions");

                // Verify durations
                long duration1 = java.time.Duration.between(results.get(0).getStartTime(), results.get(0).getEndTime())
                                .toMinutes();
                long duration2 = java.time.Duration.between(results.get(1).getStartTime(), results.get(1).getEndTime())
                                .toMinutes();

                Assertions.assertEquals(120, duration1, "Session 1 should be 2h");
                Assertions.assertEquals(120, duration2, "Session 2 should be 2h");
        }

        // B: 2 jugadores tienen una ventana compartida de 2h y media: se genera una
        // sola sesión de 2h y media.
        @Test
        public void testScenarioB_2h30mWindow_OneSession() {
                LocalDateTime baseTime = LocalDateTime.now().plusDays(1).withHour(10).withMinute(0).withSecond(0)
                                .truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
                Game game = createGame("g1");
                User u1 = createUser("u1");
                User u2 = createUser("u2");

                List<AvailabilitySlot> slots = new ArrayList<>();
                // U1: 10:00 - 12:30 (2.5h)
                AvailabilitySlot s1 = new AvailabilitySlot();
                s1.setId("s1");
                s1.setUser(u1);
                s1.setStartTime(baseTime);
                s1.setEndTime(baseTime.plusMinutes(150));
                s1.setPreferences(List.of(createPreference(s1, game, 10)));
                slots.add(s1);

                // U2: 10:00 - 12:30 (2.5h)
                AvailabilitySlot s2 = new AvailabilitySlot();
                s2.setId("s2");
                s2.setUser(u2);
                s2.setStartTime(baseTime);
                s2.setEndTime(baseTime.plusMinutes(150));
                s2.setPreferences(List.of(createPreference(s2, game, 10)));
                slots.add(s2);

                setupMocks(slots, game);

                List<GameSessionDto> results = matchmakingService.runMatchmaking();

                Assertions.assertEquals(1, results.size(),
                                "Should verify 2.5h window results in 1 session (since remainder 0.5h < 1h)");

                long duration = java.time.Duration.between(results.get(0).getStartTime(), results.get(0).getEndTime())
                                .toMinutes();
                Assertions.assertEquals(150, duration, "Session should be 2h 30m (150 mins)");
        }

        // C: 2 jugadores tienen una ventana compartida de 3h: se genera una sesión de
        // 2h y otra de 1h.
        @Test
        public void testScenarioC_3hWindow_Split2hAnd1h() {
                LocalDateTime baseTime = LocalDateTime.now().plusDays(1).withHour(10).withMinute(0).withSecond(0)
                                .truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
                Game game = createGame("g1");
                User u1 = createUser("u1");
                User u2 = createUser("u2");

                List<AvailabilitySlot> slots = new ArrayList<>();
                // U1: 10:00 - 13:00 (3h)
                AvailabilitySlot s1 = new AvailabilitySlot();
                s1.setId("s1");
                s1.setUser(u1);
                s1.setStartTime(baseTime);
                s1.setEndTime(baseTime.plusHours(3));
                s1.setPreferences(List.of(createPreference(s1, game, 10)));
                slots.add(s1);

                // U2: 10:00 - 13:00 (3h)
                AvailabilitySlot s2 = new AvailabilitySlot();
                s2.setId("s2");
                s2.setUser(u2);
                s2.setStartTime(baseTime);
                s2.setEndTime(baseTime.plusHours(3));
                s2.setPreferences(List.of(createPreference(s2, game, 10)));
                slots.add(s2);

                setupMocks(slots, game);

                List<GameSessionDto> results = matchmakingService.runMatchmaking();

                Assertions.assertEquals(2, results.size(), "Should verify 3h window splits into 2 sessions");

                // Sorting isn't strictly guaranteed by ID but usually by time. Since start time
                // is same for first split attempt, we check containment.
                boolean has2h = results.stream()
                                .anyMatch(s -> java.time.Duration.between(s.getStartTime(), s.getEndTime())
                                                .toMinutes() == 120);
                boolean has1h = results.stream()
                                .anyMatch(s -> java.time.Duration.between(s.getStartTime(), s.getEndTime())
                                                .toMinutes() == 60);

                Assertions.assertTrue(has2h, "Should contain a 2h session");
                Assertions.assertTrue(has1h, "Should contain a 1h session");
        }

        // D: J1(9-13), J2(10-15), J3(11-12) -> Session 1 (10-12, 3 players), Session 2
        // (12-13, 2 players).
        @Test
        public void testScenarioD_3PlayerHybrid() {
                LocalDateTime baseTime = LocalDateTime.now().plusDays(1).withHour(9).withMinute(0).withSecond(0)
                                .truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
                // 9:00 base
                Game game = createGame("g1");
                User u1 = createUser("u1");
                User u2 = createUser("u2");
                User u3 = createUser("u3");

                List<AvailabilitySlot> slots = new ArrayList<>();

                // J1: 9-13 (4h)
                AvailabilitySlot s1 = new AvailabilitySlot();
                s1.setId("s1");
                s1.setUser(u1);
                s1.setStartTime(baseTime);
                s1.setEndTime(baseTime.plusHours(4)); // 13:00
                s1.setPreferences(List.of(createPreference(s1, game, 10)));
                slots.add(s1);

                // J2: 10-15 (5h) - Starts +1h from base
                AvailabilitySlot s2 = new AvailabilitySlot();
                s2.setId("s2");
                s2.setUser(u2);
                s2.setStartTime(baseTime.plusHours(1)); // 10:00
                s2.setEndTime(baseTime.plusHours(6)); // 15:00
                s2.setPreferences(List.of(createPreference(s2, game, 10)));
                slots.add(s2);

                // J3: 11-12 (1h) - Starts +2h from base
                AvailabilitySlot s3 = new AvailabilitySlot();
                s3.setId("s3");
                s3.setUser(u3);
                s3.setStartTime(baseTime.plusHours(2)); // 11:00
                s3.setEndTime(baseTime.plusHours(3)); // 12:00
                s3.setPreferences(List.of(createPreference(s3, game, 10)));
                slots.add(s3);

                setupMocks(slots, game);

                List<GameSessionDto> results = matchmakingService.runMatchmaking();

                // Validation Logic:
                // Overlap of 3 players (J1,J2,J3) is strictly 11-12.
                // Overlap of 2 players (J1,J2) is 10-13.
                // Requested logic:
                // Session 1: 10-12 (2h) -> Should include J1, J2 AND J3 (as guest/partial).
                // Session 2: 12-13 (1h) -> Should include J1, J2.

                Assertions.assertEquals(2, results.size(), "Should create 2 sessions");

                // Verify Session 1: 10:00 - 12:00
                GameSessionDto session1 = results.stream()
                                .filter(s -> s.getStartTime().equals(baseTime.plusHours(1)))
                                .findFirst().orElse(null);

                Assertions.assertNotNull(session1, "Should find a session starting at 10:00");
                long dur1 = java.time.Duration.between(session1.getStartTime(), session1.getEndTime()).toMinutes();
                Assertions.assertEquals(120, dur1, "Session 1 should be 2h long (10-12)");
                Assertions.assertEquals(3, session1.getPlayers().size(),
                                "Session 1 should have 3 players (J1, J2, J3)");

                // Verify Session 2: 12:00 - 13:00
                GameSessionDto session2 = results.stream()
                                .filter(s -> s.getStartTime().equals(baseTime.plusHours(3)))
                                .findFirst().orElse(null);

                Assertions.assertNotNull(session2, "Should find a session starting at 12:00");
                long dur2 = java.time.Duration.between(session2.getStartTime(), session2.getEndTime()).toMinutes();
                Assertions.assertEquals(60, dur2, "Session 2 should be 1h long (12-13)");
                Assertions.assertEquals(2, session2.getPlayers().size(), "Session 2 should have 2 players (J1, J2)");
        }
}
