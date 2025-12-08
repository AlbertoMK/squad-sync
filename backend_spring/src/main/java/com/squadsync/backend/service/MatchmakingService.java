package com.squadsync.backend.service;

import com.squadsync.backend.dto.GameDto;
import com.squadsync.backend.dto.GameSessionDto;
import com.squadsync.backend.dto.GameSessionPlayerDto;
import com.squadsync.backend.model.*;
import com.squadsync.backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MatchmakingService {

    private final AvailabilitySlotRepository slotRepository;
    private final GameRepository gameRepository;
    private final UserGamePreferenceRepository preferenceRepository;
    private final GameSessionRepository sessionRepository;

    private final GameSessionService gameSessionService;

    private static final int MIN_PLAYERS_FOR_SESSION = 2;

    @Transactional
    public List<GameSessionDto> runMatchmaking() {
        log.info("Running matchmaking algorithm...");

        LocalDateTime now = LocalDateTime.now();

        // Fetch all active sessions
        List<GameSession> activeSessions = sessionRepository.findByEndTimeGreaterThanOrderByStartTimeAsc(now);

        List<GameSession> confirmedSessions = new ArrayList<>();
        List<GameSession> preliminarySessions = new ArrayList<>();

        for (GameSession session : activeSessions) {
            GameSession.SessionStatus status = gameSessionService.getSessionStatus(session);
            if (status == GameSession.SessionStatus.CONFIRMED) {
                confirmedSessions.add(session);
            } else {
                preliminarySessions.add(session);
            }
        }

        // 1. Delete all PRELIMINARY sessions
        sessionRepository.deleteAll(preliminarySessions);
        log.info("Deleted {} PRELIMINARY sessions", preliminarySessions.size());

        // 2. Use CONFIRMED sessions to exclude overlapping players
        Set<String> busyUserIds = new HashSet<>();
        for (GameSession session : confirmedSessions) {
            session.getPlayers().forEach(p -> busyUserIds.add(p.getUser().getId()));
        }
        log.info("Found {} confirmed sessions, excluding {} users", confirmedSessions.size(), busyUserIds.size());

        List<AvailabilitySlot> slots = slotRepository.findByEndTimeGreaterThanOrderByStartTimeAsc(now);

        if (slots.isEmpty()) {
            log.info("No availability slots found");
            return Collections.emptyList();
        }

        // Filter out slots for users who are already in a confirmed session
        List<AvailabilitySlot> availableSlots = new ArrayList<>();
        for (AvailabilitySlot slot : slots) {
            boolean isBusy = false;
            for (GameSession session : confirmedSessions) {
                // Check if this user is in this session
                boolean userInSession = session.getPlayers().stream()
                        .anyMatch(p -> p.getUser().getId().equals(slot.getUser().getId()));

                if (userInSession) {
                    // Check time overlap
                    if (slot.getStartTime().isBefore(session.getEndTime()) &&
                            session.getStartTime().isBefore(slot.getEndTime())) {
                        isBusy = true;
                        break;
                    }
                }
            }

            if (!isBusy) {
                availableSlots.add(slot);
            }
        }

        log.info("Filtered down to {} available slots after checking confirmed sessions", availableSlots.size());

        List<TimeSlot> overlappingSlots = findOverlappingSlots(availableSlots);
        List<TimeSlot> viableSlots = overlappingSlots.stream()
                .filter(slot -> slot.getSlotIds().size() >= MIN_PLAYERS_FOR_SESSION)
                .collect(Collectors.toList());

        log.info("Found {} viable time slots", viableSlots.size());

        List<GameSession> potentialSessions = new ArrayList<>();
        for (TimeSlot slot : viableSlots) {
            GameSession session = createSessionForSlot(slot);
            if (session != null) {
                potentialSessions.add(session);
            }
        }

        log.info("Generated {} potential sessions", potentialSessions.size());

        // Conflict Resolution: Sort sessions to prioritize the best ones
        potentialSessions.sort((s1, s2) -> {
            // 1. Player Count (Descending)
            int p1 = s1.getPlayers().size();
            int p2 = s2.getPlayers().size();
            if (p1 != p2)
                return Integer.compare(p2, p1);

            // 2. Session Score (Descending)
            if (s1.getSessionScore() != s2.getSessionScore())
                return Double.compare(s2.getSessionScore(), s1.getSessionScore());

            // 3. Duration (Descending) - Longer sessions preferred? Or maybe earlier start?
            // Let's prefer longer sessions as they are harder to schedule
            long d1 = java.time.Duration.between(s1.getStartTime(), s1.getEndTime()).toMinutes();
            long d2 = java.time.Duration.between(s2.getStartTime(), s2.getEndTime()).toMinutes();
            return Long.compare(d2, d1);
        });

        List<GameSession> selectedSessions = new ArrayList<>();

        // We need to track which users are "booked" in the new preliminary sessions
        // to prevent them from being in multiple overlapping preliminary sessions.
        // Note: A user CAN be in multiple preliminary sessions if they don't overlap.
        // But here we are iterating through sessions that might overlap in time.

        // Actually, we need to check if the *sessions* overlap in time AND share users.
        // Or simpler: If a session overlaps in time with an already selected session,
        // AND they share at least one user, then they are incompatible for that user.
        // But wait, a user can't be in two places at once.
        // So if we select Session A (User 1, User 2) at 10:00-11:00.
        // We cannot select Session B (User 2, User 3) at 10:30-11:30.

        // Strategy: Keep a list of selected sessions. For each candidate, check if it
        // conflicts
        // with any already selected session.
        // Conflict = Time Overlap AND Shared User.

        for (GameSession candidate : potentialSessions) {
            boolean conflicts = false;
            for (GameSession selected : selectedSessions) {
                if (sessionsOverlap(candidate, selected) && sharePlayers(candidate, selected)) {
                    conflicts = true;
                    break;
                }
            }

            if (!conflicts) {
                selectedSessions.add(candidate);
            }
        }

        log.info("Selected {} non-conflicting sessions", selectedSessions.size());

        // Save selected sessions
        List<GameSession> savedSessions = sessionRepository.saveAll(selectedSessions);

        // Combine newly created sessions with existing confirmed sessions for the
        // return value
        List<GameSessionDto> result = new ArrayList<>();
        result.addAll(confirmedSessions.stream().map(this::mapToDto).collect(Collectors.toList()));
        result.addAll(savedSessions.stream().map(this::mapToDto).collect(Collectors.toList()));

        return result;
    }

    private boolean sessionsOverlap(GameSession s1, GameSession s2) {
        return s1.getStartTime().isBefore(s2.getEndTime()) &&
                s2.getStartTime().isBefore(s1.getEndTime());
    }

    private boolean sharePlayers(GameSession s1, GameSession s2) {
        Set<String> p1Ids = s1.getPlayers().stream().map(p -> p.getUser().getId()).collect(Collectors.toSet());
        for (GameSessionPlayer p2 : s2.getPlayers()) {
            if (p1Ids.contains(p2.getUser().getId())) {
                return true;
            }
        }
        return false;
    }

    private List<TimeSlot> findOverlappingSlots(List<AvailabilitySlot> slots) {
        // 1. Collect all unique time points
        Set<LocalDateTime> timePoints = new HashSet<>();
        for (AvailabilitySlot slot : slots) {
            timePoints.add(slot.getStartTime());
            timePoints.add(slot.getEndTime());
        }
        List<LocalDateTime> sortedTimePoints = new ArrayList<>(timePoints);
        Collections.sort(sortedTimePoints);

        List<TimeSlot> candidateSlots = new ArrayList<>();

        // 2. Iterate through atomic intervals
        for (int i = 0; i < sortedTimePoints.size() - 1; i++) {
            LocalDateTime start = sortedTimePoints.get(i);
            LocalDateTime end = sortedTimePoints.get(i + 1);

            // Skip zero-duration intervals
            if (start.equals(end))
                continue;

            // Find active slots in this interval
            // A slot is active if slot.start <= start && slot.end >= end
            List<String> activeSlotIds = new ArrayList<>();
            Set<String> activeUserIds = new HashSet<>();

            for (AvailabilitySlot slot : slots) {
                if (!slot.getStartTime().isAfter(start) && !slot.getEndTime().isBefore(end)) {
                    // Check if user already added (avoid same user multiple slots issue if any)
                    if (!activeUserIds.contains(slot.getUser().getId())) {
                        activeSlotIds.add(slot.getId());
                        activeUserIds.add(slot.getUser().getId());
                    }
                }
            }

            if (activeSlotIds.size() >= MIN_PLAYERS_FOR_SESSION) {
                candidateSlots.add(new TimeSlot(start, end, activeSlotIds));
            }
        }

        // 3. Merge consecutive slots with same users
        List<TimeSlot> mergedSlots = new ArrayList<>();
        if (candidateSlots.isEmpty())
            return mergedSlots;

        TimeSlot current = candidateSlots.get(0);
        for (int i = 1; i < candidateSlots.size(); i++) {
            TimeSlot next = candidateSlots.get(i);

            // Check if contiguous and same users
            Set<String> currentIds = new HashSet<>(current.getSlotIds());
            Set<String> nextIds = new HashSet<>(next.getSlotIds());

            boolean sameUsers = currentIds.equals(nextIds);
            boolean contiguous = current.endTime.equals(next.startTime);

            // Calculate potential new duration
            long potentialDuration = java.time.Duration.between(current.startTime, next.endTime).toMinutes();
            boolean durationWithinLimit = potentialDuration <= 240; // Max 4 hours

            if (contiguous && sameUsers && durationWithinLimit) {
                // Merge
                current.endTime = next.endTime;
            } else {
                mergedSlots.add(current);
                current = next;
            }
        }
        mergedSlots.add(current);

        // 4. Filter by duration
        return mergedSlots.stream()
                .filter(slot -> java.time.Duration.between(slot.startTime, slot.endTime).toMinutes() >= 60)
                .collect(Collectors.toList());
    }

    private GameSession createSessionForSlot(TimeSlot timeSlot) {
        List<Game> games = gameRepository.findAll();
        if (games.isEmpty())
            return null;

        List<AvailabilitySlot> slots = slotRepository.findAllById(timeSlot.getSlotIds());
        List<String> userIds = slots.stream().map(s -> s.getUser().getId()).collect(Collectors.toList());

        List<UserGamePreference> globalPreferences = preferenceRepository.findByUserIdIn(userIds);
        List<GameScore> gameScores = new ArrayList<>();

        for (Game game : games) {
            int score = 0;
            int playerCount = 0;

            for (AvailabilitySlot slot : slots) {
                int weight = 5; // Default

                // Check for override
                Optional<AvailabilityGamePreference> override = slot.getPreferences().stream()
                        .filter(p -> p.getGame().getId().equals(game.getId()))
                        .findFirst();

                if (override.isPresent()) {
                    weight = override.get().getWeight();
                } else {
                    // Fallback to global preference
                    weight = globalPreferences.stream()
                            .filter(p -> p.getUser().getId().equals(slot.getUser().getId())
                                    && p.getGame().getId().equals(game.getId()))
                            .findFirst()
                            .map(UserGamePreference::getWeight)
                            .orElse(5);
                }

                if (weight > 0) {
                    score += weight;
                    playerCount++;
                }
            }

            if (playerCount < MIN_PLAYERS_FOR_SESSION) {
                continue; // Not enough players for this game
            }

            score += playerCount * 2; // Participation bonus

            gameScores.add(new GameScore(game, score));
        }

        if (gameScores.isEmpty())
            return null;

        gameScores.sort((a, b) -> b.score - a.score);
        GameScore bestGame = gameScores.get(0);

        // Filter slots/users that are actually playing (didn't veto)
        List<AvailabilitySlot> participatingSlots = new ArrayList<>();
        for (AvailabilitySlot slot : slots) {
            int weight = 5;
            Optional<AvailabilityGamePreference> override = slot.getPreferences().stream()
                    .filter(p -> p.getGame().getId().equals(bestGame.game.getId()))
                    .findFirst();
            if (override.isPresent()) {
                weight = override.get().getWeight();
            } else {
                weight = globalPreferences.stream()
                        .filter(p -> p.getUser().getId().equals(slot.getUser().getId())
                                && p.getGame().getId().equals(bestGame.game.getId()))
                        .findFirst()
                        .map(UserGamePreference::getWeight)
                        .orElse(5);
            }

            if (weight > 0) {
                participatingSlots.add(slot);
            }
        }

        if (participatingSlots.size() < MIN_PLAYERS_FOR_SESSION)
            return null;

        GameSession session = new GameSession();
        session.setGame(bestGame.game);

        // Clamp start time to now if the slot started in the past
        LocalDateTime sessionStartTime = timeSlot.startTime;
        if (sessionStartTime.isBefore(LocalDateTime.now())) {
            sessionStartTime = LocalDateTime.now();
        }
        // Truncate to seconds for DB hygiene
        session.setStartTime(sessionStartTime.truncatedTo(java.time.temporal.ChronoUnit.SECONDS));

        session.setEndTime(timeSlot.endTime.truncatedTo(java.time.temporal.ChronoUnit.SECONDS));
        session.setEndTime(timeSlot.endTime);
        session.setSessionScore(bestGame.score);
        session.setSessionScore(bestGame.score);
        // Status is no longer set here, it's dynamic

        // Add players
        for (AvailabilitySlot slot : participatingSlots) {
            GameSessionPlayer player = new GameSessionPlayer();
            player.setSession(session);
            player.setUser(slot.getUser());
            player.setStatus(GameSessionPlayer.SessionPlayerStatus.PENDING);
            session.getPlayers().add(player);
        }

        return session;
    }

    public List<GameSessionDto> getUpcomingSessions() {
        return sessionRepository.findByEndTimeGreaterThanOrderByStartTimeAsc(LocalDateTime.now())
                .stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    private GameSessionDto mapToDto(GameSession session) {
        GameSessionDto dto = new GameSessionDto();
        dto.setId(session.getId());
        dto.setGameId(session.getGame().getId());
        // Convert Entity (LocalDateTime) to DTO (Instant) using UTC
        dto.setStartTime(session.getStartTime().toInstant(java.time.ZoneOffset.UTC));
        dto.setEndTime(session.getEndTime().toInstant(java.time.ZoneOffset.UTC));
        dto.setSessionScore(session.getSessionScore());
        dto.setCreatedAt(session.getCreatedAt());

        // Calculate dynamic status
        dto.setStatus(gameSessionService.getSessionStatus(session).name());

        // Map Game
        GameDto gameDto = new GameDto();
        gameDto.setId(session.getGame().getId());
        gameDto.setTitle(session.getGame().getTitle());
        gameDto.setCoverImageUrl(session.getGame().getCoverImageUrl());
        dto.setGame(gameDto);

        List<String> playerIds = session.getPlayers().stream()
                .map(p -> p.getUser().getId())
                .collect(Collectors.toList());
        dto.setPlayerIds(playerIds);

        List<GameSessionPlayerDto> players = session.getPlayers().stream()
                .map(p -> {
                    GameSessionPlayerDto u = new GameSessionPlayerDto();
                    u.setUserId(p.getUser().getId());
                    u.setUsername(p.getUser().getUsername());
                    u.setAvatarColor(p.getUser().getAvatarColor());
                    u.setStatus(p.getStatus().name());
                    return u;
                })
                .collect(Collectors.toList());
        dto.setPlayers(players);

        return dto;
    }

    private static class TimeSlot {
        LocalDateTime startTime;
        LocalDateTime endTime;
        List<String> slotIds; // Changed from userIds to slotIds to access preferences

        public TimeSlot(LocalDateTime startTime, LocalDateTime endTime, List<String> slotIds) {
            this.startTime = startTime;
            this.endTime = endTime;
            this.slotIds = slotIds;
        }

        public List<String> getSlotIds() {
            return slotIds;
        }
    }

    private static class GameScore {
        Game game;
        int score;

        public GameScore(Game game, int score) {
            this.game = game;
            this.score = score;
        }
    }

    public List<GameSession> findSessionsForUser(String userId) {
        // We need to fetch all active sessions and filter in memory or add a repository
        // method
        // For efficiency, let's add a repository method or just filter active sessions
        // here
        // Since we don't have a direct query for "sessions by user" yet, let's filter
        // active sessions
        LocalDateTime now = LocalDateTime.now();
        List<GameSession> activeSessions = sessionRepository.findByEndTimeGreaterThanOrderByStartTimeAsc(now);

        return activeSessions.stream()
                .filter(s -> s.getPlayers().stream().anyMatch(p -> p.getUser().getId().equals(userId)))
                .collect(Collectors.toList());
    }

    @Transactional
    public void removePlayerFromSession(String sessionId, String userId) {
        GameSession session = sessionRepository.findById(sessionId).orElse(null);
        if (session == null)
            return;

        boolean removed = session.getPlayers().removeIf(p -> p.getUser().getId().equals(userId));

        if (removed) {
            log.info("Removed user {} from session {} due to availability deletion", userId, sessionId);
            // If session has too few players, we might want to delete it or let dynamic
            // status handle it
            // Dynamic status will mark it as PRELIMINARY if < min players.
            // But if it has 0 or 1 player, maybe we should just keep it as is,
            // and let the next matchmaking run clean it up or fill it?
            // Actually, if we remove a player, the session might become invalid.
            // Let's save it.
            sessionRepository.save(session);
        }
    }
}
