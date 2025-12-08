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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class MatchmakingServiceTest {
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
    @InjectMocks
    private MatchmakingService matchmakingService;

    @Test
    public void testOverlappingSlotsScenario() {
        // Setup
        LocalDateTime now = LocalDateTime.now().plusDays(1);
        // Users
        User u1 = new User();
        u1.setId("u1");
        User u2 = new User();
        u2.setId("u2");
        User u3 = new User();
        u3.setId("u3");
        // Game
        Game game = new Game();
        game.setId("g1");
        game.setTitle("Test Game");
        // Slots
        // User 1: 20:00 - 23:00
        AvailabilitySlot s1 = new AvailabilitySlot();
        s1.setId("s1");
        s1.setUser(u1);
        s1.setStartTime(
                now.withHour(20).withMinute(0).withSecond(0).truncatedTo(java.time.temporal.ChronoUnit.SECONDS));
        s1.setEndTime(now.withHour(23).withMinute(0).withSecond(0).truncatedTo(java.time.temporal.ChronoUnit.SECONDS));
        s1.setPreferences(List.of(createPreference(s1, game, 10)));
        // User 2: 20:00 - 23:00
        AvailabilitySlot s2 = new AvailabilitySlot();
        s2.setId("s2");
        s2.setUser(u2);
        s2.setStartTime(
                now.withHour(20).withMinute(0).withSecond(0).truncatedTo(java.time.temporal.ChronoUnit.SECONDS));
        s2.setEndTime(now.withHour(23).withMinute(0).withSecond(0).truncatedTo(java.time.temporal.ChronoUnit.SECONDS));
        s2.setPreferences(List.of(createPreference(s2, game, 10)));
        // User 3: 20:30 - 21:30
        AvailabilitySlot s3 = new AvailabilitySlot();
        s3.setId("s3");
        s3.setUser(u3);
        s3.setStartTime(
                now.withHour(20).withMinute(30).withSecond(0).truncatedTo(java.time.temporal.ChronoUnit.SECONDS));
        s3.setEndTime(now.withHour(21).withMinute(30).withSecond(0).truncatedTo(java.time.temporal.ChronoUnit.SECONDS));
        s3.setPreferences(List.of(createPreference(s3, game, 10)));
        List<AvailabilitySlot> slots = List.of(s1, s2, s3);

        // Mocks
        when(sessionRepository.findByEndTimeGreaterThanOrderByStartTimeAsc(any())).thenReturn(Collections.emptyList());
        when(slotRepository.findByEndTimeGreaterThanOrderByStartTimeAsc(any())).thenReturn(slots);
        when(slotRepository.findAllById(anyList())).thenAnswer(invocation -> {
            List<String> ids = invocation.getArgument(0);
            List<AvailabilitySlot> result = new ArrayList<>();
            for (String id : ids) {
                if (id.equals("s1"))
                    result.add(s1);
                if (id.equals("s2"))
                    result.add(s2);
                if (id.equals("s3"))
                    result.add(s3);
            }
            return result;
        });
        when(gameRepository.findAll()).thenReturn(List.of(game));
        when(preferenceRepository.findByUserIdIn(anyList())).thenReturn(Collections.emptyList());
        when(sessionRepository.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));
        when(gameSessionService.getSessionStatus(any())).thenReturn(GameSession.SessionStatus.PRELIMINARY);

        // Run
        var result = matchmakingService.runMatchmaking();

        // Assert
        Assertions.assertFalse(result.isEmpty(), "Should create at least one session");
        Assertions.assertTrue(result.stream().anyMatch(s -> s.getPlayers().size() == 3),
                "Should have a 3-player session");
    }

    @Test
    public void testFragmentedAvailabilityScenario() {
        // Setup
        LocalDateTime now = LocalDateTime.now().plusDays(1);
        // Users
        User u1 = new User();
        u1.setId("u1");
        User u2 = new User();
        u2.setId("u2");

        // Game
        Game game = new Game();
        game.setId("g1");

        List<AvailabilitySlot> slots = new ArrayList<>();

        // User 1: 20:00 - 23:00 (Single Slot)
        AvailabilitySlot s1 = new AvailabilitySlot();
        s1.setId("s1_1");
        s1.setUser(u1);
        s1.setStartTime(
                now.withHour(20).withMinute(0).withSecond(0).truncatedTo(java.time.temporal.ChronoUnit.SECONDS));
        s1.setEndTime(now.withHour(23).withMinute(0).withSecond(0).truncatedTo(java.time.temporal.ChronoUnit.SECONDS));
        s1.setPreferences(List.of(createPreference(s1, game, 10)));
        slots.add(s1);

        // User 2: Broken into 30 min slots from 20:00 to 23:00
        for (int i = 0; i < 6; i++) {
            AvailabilitySlot s2 = new AvailabilitySlot();
            s2.setId("s2_" + i);
            s2.setUser(u2);
            LocalDateTime start = now.withHour(20).withMinute(0).withSecond(0).plusMinutes(i * 30);
            s2.setStartTime(start.truncatedTo(java.time.temporal.ChronoUnit.SECONDS));
            s2.setEndTime(start.plusMinutes(30).truncatedTo(java.time.temporal.ChronoUnit.SECONDS));
            s2.setPreferences(List.of(createPreference(s2, game, 10)));
            slots.add(s2);
        }

        // Mocks
        when(sessionRepository.findByEndTimeGreaterThanOrderByStartTimeAsc(any())).thenReturn(Collections.emptyList());
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

        // Run
        var result = matchmakingService.runMatchmaking();

        // Assert
        // With current buggy logic, we expect this to FAIL (create multiple small
        // sessions instead of 1 big one)
        Assertions.assertEquals(1, result.size(), "Should create exactly one merged session");
        GameSessionDto session = result.get(0);
        long minutes = java.time.Duration.between(session.getStartTime(), session.getEndTime()).toMinutes();
        Assertions.assertEquals(180, minutes, "Session should be 3 hours long");
    }

    private AvailabilityGamePreference createPreference(AvailabilitySlot slot, Game game, int weight) {
        AvailabilityGamePreference p = new AvailabilityGamePreference();
        p.setAvailabilitySlot(slot);
        p.setGame(game);
        p.setWeight(weight);
        return p;
    }

    @Test
    public void testMaxSessionDurationConstraint() {
        // Setup scenarios: User 1 (09:00 - 22:00) vs User 2 (09:00 - 14:00)
        // Overlap: 5 hours (09:00 - 14:00)
        // Constraint: Max session duration = 4 hours (240 mins)

        LocalDateTime now = LocalDateTime.now().plusDays(1).withHour(9).withMinute(0).withSecond(0)
                .truncatedTo(java.time.temporal.ChronoUnit.SECONDS);

        // Define Users
        User u1 = new User();
        u1.setId("u1");
        User u2 = new User();
        u2.setId("u2");

        // Define Game
        Game game = new Game();
        game.setId("g1");

        List<AvailabilitySlot> slots = new ArrayList<>();

        // User 1: 09:00 - 22:00 (13 hours)
        AvailabilitySlot s1 = new AvailabilitySlot();
        s1.setId("s1");
        s1.setUser(u1);
        s1.setStartTime(now);
        s1.setEndTime(now.withHour(22));
        s1.setPreferences(List.of(createPreference(s1, game, 10)));
        slots.add(s1);

        // User 2: 09:00 - 14:00 (5 hours)
        AvailabilitySlot s2 = new AvailabilitySlot();
        s2.setId("s2");
        s2.setUser(u2);
        s2.setStartTime(now);
        s2.setEndTime(now.withHour(14));
        s2.setPreferences(List.of(createPreference(s2, game, 10)));
        slots.add(s2);

        // Mocks setup
        when(sessionRepository.findByEndTimeGreaterThanOrderByStartTimeAsc(any())).thenReturn(Collections.emptyList());
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

        // Run Matchmaking
        var result = matchmakingService.runMatchmaking();

        // Assertions
        Assertions.assertFalse(result.isEmpty(), "Should create a session");
        GameSessionDto session = result.get(0);
        long durationMinutes = java.time.Duration.between(session.getStartTime(), session.getEndTime()).toMinutes();

        Assertions.assertEquals(240, durationMinutes,
                "Session duration should be capped at 4 hours (240 min) even if availability is 5 hours");
    }

    @Test
    public void testSessionSplittingForLongAvailability() {
        // Setup scenarios: User 1 & User 2 available 09:00 - 14:00 (5 hours)
        // Expected: 2 sessions. One 4h (240m), One 1h (60m).

        LocalDateTime now = LocalDateTime.now().plusDays(1).withHour(9).withMinute(0).withSecond(0)
                .truncatedTo(java.time.temporal.ChronoUnit.SECONDS);

        User u1 = new User();
        u1.setId("u1");
        User u2 = new User();
        u2.setId("u2");
        Game game = new Game();
        game.setId("g1");

        List<AvailabilitySlot> slots = new ArrayList<>();

        // User 1: 09:00 - 14:00
        AvailabilitySlot s1 = new AvailabilitySlot();
        s1.setId("s1");
        s1.setUser(u1);
        s1.setStartTime(now);
        s1.setEndTime(now.withHour(14));
        s1.setPreferences(List.of(createPreference(s1, game, 10)));
        slots.add(s1);

        // User 2: 09:00 - 14:00
        AvailabilitySlot s2 = new AvailabilitySlot();
        s2.setId("s2");
        s2.setUser(u2);
        s2.setStartTime(now);
        s2.setEndTime(now.withHour(14));
        s2.setPreferences(List.of(createPreference(s2, game, 10)));
        slots.add(s2);

        // Mocks
        when(sessionRepository.findByEndTimeGreaterThanOrderByStartTimeAsc(any())).thenReturn(Collections.emptyList());
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

        // Run
        var result = matchmakingService.runMatchmaking();

        // Assert
        Assertions.assertEquals(2, result.size(), "Should create 2 sessions");

        boolean has4HourSession = result.stream()
                .anyMatch(s -> java.time.Duration.between(s.getStartTime(), s.getEndTime()).toMinutes() == 240);
        boolean has1HourSession = result.stream()
                .anyMatch(s -> java.time.Duration.between(s.getStartTime(), s.getEndTime()).toMinutes() == 60);

        Assertions.assertTrue(has4HourSession, "Should have a 4h session");
        Assertions.assertTrue(has1HourSession, "Should have a 1h session");
    }

    @Test
    public void testRunMatchmakingPreservesAcceptances() {
        // Setup: Existing PRELIMINARY session where U1 has ACCEPTED
        LocalDateTime now = LocalDateTime.now().plusDays(1).withHour(10).withMinute(0).withSecond(0)
                .truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        LocalDateTime end = now.plusHours(1);

        User u1 = new User();
        u1.setId("u1");
        User u2 = new User();
        u2.setId("u2");
        Game game = new Game();
        game.setId("g1");

        // Existing Session
        GameSession existingSession = new GameSession();
        existingSession.setId("sess1");
        existingSession.setGame(game);
        existingSession.setStartTime(now);
        existingSession.setEndTime(end);

        GameSessionPlayer p1 = new GameSessionPlayer();
        p1.setUser(u1);
        p1.setSession(existingSession);
        p1.setStatus(GameSessionPlayer.SessionPlayerStatus.ACCEPTED);

        GameSessionPlayer p2 = new GameSessionPlayer();
        p2.setUser(u2);
        p2.setSession(existingSession);
        p2.setStatus(GameSessionPlayer.SessionPlayerStatus.PENDING);

        existingSession.setPlayers(new ArrayList<>(List.of(p1, p2)));

        // Availability Slots (to regenerate the same session)
        List<AvailabilitySlot> slots = new ArrayList<>();
        AvailabilitySlot s1 = new AvailabilitySlot();
        s1.setId("s1");
        s1.setUser(u1);
        s1.setStartTime(now);
        s1.setEndTime(end);
        s1.setPreferences(List.of(createPreference(s1, game, 10)));
        slots.add(s1);

        AvailabilitySlot s2 = new AvailabilitySlot();
        s2.setId("s2");
        s2.setUser(u2);
        s2.setStartTime(now);
        s2.setEndTime(end);
        s2.setPreferences(List.of(createPreference(s2, game, 10)));
        slots.add(s2);

        // Mocks
        // Return existing session initially
        when(sessionRepository.findByEndTimeGreaterThanOrderByStartTimeAsc(any())).thenReturn(List.of(existingSession));
        when(gameSessionService.getSessionStatus(existingSession)).thenReturn(GameSession.SessionStatus.PRELIMINARY);

        // Return slots
        when(slotRepository.findByEndTimeGreaterThanOrderByStartTimeAsc(any())).thenReturn(slots);
        when(slotRepository.findAllById(anyList())).thenAnswer(invocation -> {
            List<String> ids = invocation.getArgument(0);
            List<AvailabilitySlot> result = new ArrayList<>();
            for (String id : ids) {
                if (id.equals("s1"))
                    result.add(s1);
                if (id.equals("s2"))
                    result.add(s2);
            }
            return result;
        });

        when(gameRepository.findAll()).thenReturn(List.of(game));
        when(preferenceRepository.findByUserIdIn(anyList())).thenReturn(Collections.emptyList());

        // Mock saveAll to return the input list (simulating save)
        when(sessionRepository.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));

        // ACTION
        List<GameSessionDto> result = matchmakingService.runMatchmaking();

        // ASSERT
        Assertions.assertFalse(result.isEmpty());
        GameSessionDto newSession = result.get(0);

        // Find player u1 and check status
        var u1Status = newSession.getPlayers().stream().filter(p -> p.getUserId().equals("u1")).findFirst().get()
                .getStatus();
        var u2Status = newSession.getPlayers().stream().filter(p -> p.getUserId().equals("u2")).findFirst().get()
                .getStatus();

        Assertions.assertEquals("ACCEPTED", u1Status, "User 1 should remain ACCEPTED if session is identical");
        Assertions.assertEquals("PENDING", u2Status, "User 2 should remain PENDING");
    }
}
