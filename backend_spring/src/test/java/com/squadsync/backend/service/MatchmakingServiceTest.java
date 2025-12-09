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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
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
    @Mock
    private DiscordBotService discordBotService;
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

    @Test
    public void testSessionDeletionAfterAvailabilityRemoval() {
        // Phase 1: Two users with overlapping availability
        LocalDateTime now = LocalDateTime.now().plusDays(1).withHour(10).withMinute(0).withSecond(0)
                .truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        LocalDateTime end = now.plusHours(2); // 120 minutes (valid duration)

        User u1 = new User();
        u1.setId("u1");
        User u2 = new User();
        u2.setId("u2");
        Game game = new Game();
        game.setId("g1");

        // Slots
        AvailabilitySlot s1 = new AvailabilitySlot();
        s1.setId("s1");
        s1.setUser(u1);
        s1.setStartTime(now);
        s1.setEndTime(end);
        s1.setPreferences(List.of(createPreference(s1, game, 10)));

        AvailabilitySlot s2 = new AvailabilitySlot();
        s2.setId("s2");
        s2.setUser(u2);
        s2.setStartTime(now);
        s2.setEndTime(end);
        s2.setPreferences(List.of(createPreference(s2, game, 10)));

        List<AvailabilitySlot> initialSlots = new ArrayList<>(List.of(s1, s2));

        // Mocks for Phase 1
        lenient().when(gameRepository.findAll()).thenReturn(List.of(game));
        lenient().when(preferenceRepository.findByUserIdIn(anyList())).thenReturn(Collections.emptyList());
        lenient().when(sessionRepository.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));
        lenient().when(gameSessionService.getSessionStatus(any())).thenReturn(GameSession.SessionStatus.PRELIMINARY);

        // Initial Run: No existing sessions, 2 slots
        when(sessionRepository.findByEndTimeGreaterThanOrderByStartTimeAsc(any())).thenReturn(Collections.emptyList());
        when(slotRepository.findByEndTimeGreaterThanOrderByStartTimeAsc(any())).thenReturn(initialSlots);
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

        // Run Phase 1
        List<GameSessionDto> phase1Result = matchmakingService.runMatchmaking();
        Assertions.assertEquals(1, phase1Result.size(), "Should create 1 session initially");
        GameSessionDto createdSessionDto = phase1Result.get(0);

        // Phase 2: User 1 removes availability
        // Create an Entity representation of the DTO to return from the repo
        GameSession distinctSession = new GameSession();
        distinctSession.setId(createdSessionDto.getId() != null ? createdSessionDto.getId() : "generated-id");
        distinctSession.setGame(game);
        distinctSession.setStartTime(now);
        distinctSession.setEndTime(end);
        distinctSession.setSessionScore(createdSessionDto.getSessionScore());
        // Set players
        distinctSession.setPlayers(new ArrayList<>());
        GameSessionPlayer p1 = new GameSessionPlayer();
        p1.setUser(u1);
        p1.setSession(distinctSession);
        p1.setStatus(GameSessionPlayer.SessionPlayerStatus.PENDING);
        GameSessionPlayer p2 = new GameSessionPlayer();
        p2.setUser(u2);
        p2.setSession(distinctSession);
        p2.setStatus(GameSessionPlayer.SessionPlayerStatus.PENDING);
        distinctSession.getPlayers().add(p1);
        distinctSession.getPlayers().add(p2);

        // Update Mocks
        // Existing session IS returned now
        when(sessionRepository.findByEndTimeGreaterThanOrderByStartTimeAsc(any())).thenReturn(List.of(distinctSession));

        // Slots: Only s2 remains
        List<AvailabilitySlot> phase2Slots = new ArrayList<>(List.of(s2));
        when(slotRepository.findByEndTimeGreaterThanOrderByStartTimeAsc(any())).thenReturn(phase2Slots);
        when(slotRepository.findAllById(anyList())).thenAnswer(invocation -> {
            List<String> ids = invocation.getArgument(0);
            List<AvailabilitySlot> result = new ArrayList<>();
            for (String id : ids) {
                // s1 is gone
                if (id.equals("s2"))
                    result.add(s2);
            }
            return result;
        });

        // Run Phase 2
        List<GameSessionDto> phase2Result = matchmakingService.runMatchmaking();

        // Assertions
        Assertions.assertTrue(phase2Result.isEmpty(), "Should not return any sessions after availability removal");
    }

    @Test
    public void testDiscordNotificationForConfirmedSessions() {
        // Setup scenarios:
        // 1. Existing Confirmed Session
        // 2. New Session that becomes Confirmed

        LocalDateTime now = LocalDateTime.now().plusHours(1);
        User u1 = new User();
        u1.setId("u1");
        User u2 = new User();
        u2.setId("u2");
        Game game = new Game();
        game.setId("g1");
        game.setTitle("Game 1");

        // 1. Existing Confirmed Session
        GameSession existingConfirmed = new GameSession();
        existingConfirmed.setId("existing-1");
        existingConfirmed.setGame(game);
        existingConfirmed.setStartTime(now);
        existingConfirmed.setEndTime(now.plusHours(1));
        // Status is CONFIRMED
        when(gameSessionService.getSessionStatus(existingConfirmed)).thenReturn(GameSession.SessionStatus.CONFIRMED);

        // 2. New Potential Session (will be created from slots)
        AvailabilitySlot s1 = new AvailabilitySlot();
        s1.setId("s1");
        s1.setUser(u1);
        s1.setStartTime(now);
        s1.setEndTime(now.plusHours(1));
        s1.setPreferences(List.of(createPreference(s1, game, 10)));
        AvailabilitySlot s2 = new AvailabilitySlot();
        s2.setId("s2");
        s2.setUser(u2);
        s2.setStartTime(now);
        s2.setEndTime(now.plusHours(1));
        s2.setPreferences(List.of(createPreference(s2, game, 10)));
        List<AvailabilitySlot> slots = new ArrayList<>(List.of(s1, s2));

        // Mocks
        // findByEndTimeGreaterThan... returns the existing session
        when(sessionRepository.findByEndTimeGreaterThanOrderByStartTimeAsc(any()))
                .thenReturn(List.of(existingConfirmed));

        // Slots setup
        when(slotRepository.findByEndTimeGreaterThanOrderByStartTimeAsc(any())).thenReturn(slots);
        when(slotRepository.findAllById(anyList())).thenAnswer(invocation -> {
            List<String> ids = invocation.getArgument(0);
            if (ids.contains("s1") && ids.contains("s2"))
                return slots;
            return new ArrayList<>();
        });

        when(gameRepository.findAll()).thenReturn(List.of(game));
        when(preferenceRepository.findByUserIdIn(anyList())).thenReturn(Collections.emptyList());

        // Mock saveAll to return the session that was created
        when(sessionRepository.saveAll(anyList())).thenAnswer(invocation -> {
            List<GameSession> saved = invocation.getArgument(0);
            // Assume the new session is also CONFIRMED for this test context
            for (GameSession s : saved) {
                when(gameSessionService.getSessionStatus(s)).thenReturn(GameSession.SessionStatus.CONFIRMED);
            }
            return saved;
        });

        // Run
        matchmakingService.runMatchmaking();

        // Verify
        // Expected: sendMatchmakingUpdates called with 2 sessions (1 existing + 1 new)
        org.mockito.ArgumentCaptor<List<GameSession>> captor = org.mockito.ArgumentCaptor.forClass(List.class);
        org.mockito.Mockito.verify(discordBotService).sendMatchmakingUpdates(captor.capture());

        List<GameSession> capturedSessions = captor.getValue();

        Assertions.assertTrue(capturedSessions.size() >= 1, "Should notify about sessions");
        Assertions.assertTrue(capturedSessions.contains(existingConfirmed),
                "Should include existing confirmed session");
    }

    @Test
    public void testPreliminarySessionNotification() {
        LocalDateTime now = LocalDateTime.now();
        Game game = new Game();
        game.setId("g1");
        game.setTitle("Test Game");

        // Preliminary session starting in 1 hour
        GameSession s1 = new GameSession();
        s1.setId("s1");
        s1.setGame(game);
        s1.setStartTime(now.plusHours(1));
        s1.setEndTime(now.plusHours(3));
        s1.setNotified(false);
        // Status implicitly preliminary or mocked
        when(gameSessionService.getSessionStatus(s1)).thenReturn(GameSession.SessionStatus.PRELIMINARY);

        // Preliminary session starting next week (should NOT be notified)
        GameSession s2 = new GameSession();
        s2.setId("s2");
        s2.setGame(game);
        s2.setStartTime(now.plusDays(7));
        s2.setEndTime(now.plusDays(7).plusHours(2));
        s2.setNotified(false);
        when(gameSessionService.getSessionStatus(s2)).thenReturn(GameSession.SessionStatus.PRELIMINARY);

        // Mocks for scheduled task
        // It calls findByEndTimeGreaterThanOrderByStartTimeAsc(now)
        when(sessionRepository.findByEndTimeGreaterThanOrderByStartTimeAsc(any())).thenReturn(List.of(s1, s2));
        when(sessionRepository.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));

        // Action
        matchmakingService.checkUpcomingPreliminarySessions();

        // Verify
        org.mockito.ArgumentCaptor<List<GameSession>> captor = org.mockito.ArgumentCaptor.forClass(List.class);
        org.mockito.Mockito.verify(discordBotService, org.mockito.Mockito.atLeastOnce())
                .sendPreliminarySessionNotifications(captor.capture());

        List<GameSession> notifiedSessions = captor.getValue();
        Assertions.assertEquals(1, notifiedSessions.size(), "Should verify exactly 1 session");
        Assertions.assertTrue(notifiedSessions.contains(s1), "Should notify session starting in 1h");
        Assertions.assertFalse(notifiedSessions.contains(s2), "Should NOT notify session starting in 1 week");

        // Check s1 setNotified(true)
        Assertions.assertTrue(s1.isNotified(), "Session should be marked as notified");
    }

    @Test
    public void testCheckAndNotifyPreliminary_SessionStartingNow() {
        // Given
        LocalDateTime now = LocalDateTime.now();
        GameSession session = new GameSession();
        session.setId("session-now");
        session.setGame(new Game());
        session.setStartTime(now); // Starts exactly now
        session.setEndTime(now.plusMinutes(60));
        session.setNotified(false);

        when(gameSessionService.getSessionStatus(session)).thenReturn(GameSession.SessionStatus.PRELIMINARY);
        when(sessionRepository.findByEndTimeGreaterThanOrderByStartTimeAsc(any(LocalDateTime.class)))
                .thenReturn(Collections.singletonList(session));

        // When
        matchmakingService.checkUpcomingPreliminarySessions();

        // Then
        org.mockito.ArgumentCaptor<List<GameSession>> captor = org.mockito.ArgumentCaptor.forClass(List.class);
        // We verify that it IS called. With the bug, this verification may fail if
        // using regular verify
        // or if we assert on captured values.
        org.mockito.Mockito.verify(discordBotService, org.mockito.Mockito.atLeastOnce())
                .sendPreliminarySessionNotifications(captor.capture());

        List<GameSession> captured = captor.getValue();
        // The bug prevents it from being added to the list if check is strictly > now
        Assertions.assertTrue(captured.stream().anyMatch(s -> s.getId().equals("session-now")),
                "Should notify for session starting now");
    }
}
