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
    // private final DiscordBotService discordBotService; // Decoupled via Events
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;

    private static final int MIN_PLAYERS_FOR_SESSION = 2;
    private static final int MAX_SESSION_DURATION_MINUTES = 240;
    private static final int MIN_SESSION_DURATION_MINUTES = 60;
    private static final int DEFAULT_PREFERENCE_WEIGHT = 5;
    private static final int PARTICIPATION_BONUS_MULTIPLIER = 2;

    @Transactional
    public List<GameSessionDto> runMatchmaking() {
        log.info("Running matchmaking algorithm...");

        LocalDateTime now = LocalDateTime.now();

        // Fetch and categorize sessions
        List<GameSession> activeSessions = sessionRepository.findByEndTimeGreaterThanOrderByStartTimeAsc(now);
        List<GameSession> confirmedSessions = new ArrayList<>();
        List<GameSession> preliminarySessions = new ArrayList<>();

        for (GameSession session : activeSessions) {
            if (gameSessionService.getSessionStatus(session) == GameSession.SessionStatus.CONFIRMED) {
                confirmedSessions.add(session);
            } else {
                preliminarySessions.add(session);
            }
        }

        // Map preliminary sessions by signature for reuse
        Map<String, GameSession> existingSessionsMap = new HashMap<>();
        for (GameSession session : preliminarySessions) {
            existingSessionsMap.put(generateSessionSignature(session), session);
        }

        List<AvailabilitySlot> slots = slotRepository.findByEndTimeGreaterThanOrderByStartTimeAsc(now);
        if (slots.isEmpty()) {
            // No availability, so no *new* sessions can be formed.
            // Existing preliminary sessions rely on availability, so they are likely
            // invalid.
            sessionRepository.deleteAll(preliminarySessions);
            return confirmedSessions.stream().map(this::mapToDto).collect(Collectors.toList());
        }

        // Filter valid slots (not overlapping with confirmed sessions)
        List<AvailabilitySlot> availableSlots = filterAvailableSlots(slots, confirmedSessions);
        log.info("Filtered down to {} available slots", availableSlots.size());

        List<TimeSlot> viableSlots = findOverlappingSlots(availableSlots);
        log.info("Found {} viable time slots", viableSlots.size());

        // Generate potential sessions
        List<GameSession> potentialSessions = new ArrayList<>();
        for (TimeSlot slot : viableSlots) {
            GameSession candidateRequest = createSessionForSlot(slot);

            if (candidateRequest != null) {
                String signature = generateSessionSignature(candidateRequest);

                if (existingSessionsMap.containsKey(signature)) {
                    // Reuse existing session
                    GameSession existingSession = existingSessionsMap.get(signature);

                    // Sync players: Update existingSession players to match candidateRequest
                    // We want to keep existing players (to preserve status) if they are still in
                    // the candidate
                    // We want to add new players
                    // We want to remove players that are no longer in the candidate

                    Set<String> newCandidateUserIds = candidateRequest.getPlayers().stream()
                            .map(p -> p.getUser().getId())
                            .collect(Collectors.toSet());

                    // 1. Remove players not in the new candidate list
                    existingSession.getPlayers().removeIf(p -> !newCandidateUserIds.contains(p.getUser().getId()));

                    // 2. Add players that are in candidate but not in existing
                    // Create a map for faster lookup of existing players
                    Map<String, GameSessionPlayer> existingPlayersMap = existingSession.getPlayers().stream()
                            .collect(Collectors.toMap(p -> p.getUser().getId(), p -> p));

                    for (GameSessionPlayer candidatePlayer : candidateRequest.getPlayers()) {
                        String userId = candidatePlayer.getUser().getId();
                        if (existingPlayersMap.containsKey(userId)) {
                            // Player exists. Check if we need to revive them (e.g. they declined before but
                            // are
                            // available now)
                            GameSessionPlayer existingPlayer = existingPlayersMap.get(userId);
                            if (existingPlayer.getStatus() == GameSessionPlayer.SessionPlayerStatus.REJECTED) {
                                existingPlayer.setStatus(GameSessionPlayer.SessionPlayerStatus.PENDING);
                            }
                        } else {
                            // This is a new player for this session
                            candidatePlayer.setSession(existingSession); // Repoint to existing session
                            existingSession.getPlayers().add(candidatePlayer);
                        }
                    }

                    // Sync score
                    existingSession.setSessionScore(candidateRequest.getSessionScore());

                    potentialSessions.add(existingSession);
                } else {
                    // New session entirely
                    potentialSessions.add(candidateRequest);
                }
            }
        }

        // Sort sessions by priority: Players > Score > Duration
        potentialSessions.sort((s1, s2) -> {
            int p1 = s1.getPlayers().size();
            int p2 = s2.getPlayers().size();
            if (p1 != p2)
                return Integer.compare(p2, p1);

            if (Double.compare(s1.getSessionScore(), s2.getSessionScore()) != 0)
                return Double.compare(s2.getSessionScore(), s1.getSessionScore());

            long d1 = java.time.Duration.between(s1.getStartTime(), s1.getEndTime()).toMinutes();
            long d2 = java.time.Duration.between(s2.getStartTime(), s2.getEndTime()).toMinutes();
            return Long.compare(d2, d1);
        });

        // Select non-conflicting sessions
        List<GameSession> selectedSessions = new ArrayList<>();
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

        // Identify sessions to delete (were preliminary but not selected in this round)
        Set<String> selectedSessionIds = selectedSessions.stream()
                .map(GameSession::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        List<GameSession> sessionsToDelete = new ArrayList<>();
        for (GameSession original : preliminarySessions) {
            if (!selectedSessionIds.contains(original.getId())) {
                sessionsToDelete.add(original);
            }
        }

        log.info("Preliminary: {}, Confirmed: {}, Potential: {}, Selected: {}, ToDelete: {}",
                preliminarySessions.size(), confirmedSessions.size(), potentialSessions.size(), selectedSessions.size(),
                sessionsToDelete.size());

        if (!sessionsToDelete.isEmpty()) {
            sessionRepository.deleteAll(sessionsToDelete);
            log.info("Deleted {} obsolete PRELIMINARY sessions", sessionsToDelete.size());
        }

        log.info("Selected {} sessions", selectedSessions.size());
        List<GameSession> savedSessions = sessionRepository.saveAll(selectedSessions);

        // Notify Discord via Events
        // We want to trigger checks for:
        // 1. Existing Confirmed Sessions (to report them again, if desired, or at least
        // consistent with legacy 'allConfirmed' logic - test expects this)
        // 2. Newly Saved Sessions (Confirmed or Preliminary)

        List<GameSession> allSessionsToNotify = new ArrayList<>();
        allSessionsToNotify.addAll(confirmedSessions);
        allSessionsToNotify.addAll(savedSessions);

        checkAndNotifyPreliminary(allSessionsToNotify);

        // Result construction
        List<GameSessionDto> result = new ArrayList<>();
        result.addAll(confirmedSessions.stream().map(this::mapToDto).collect(Collectors.toList()));
        result.addAll(savedSessions.stream().map(this::mapToDto).collect(Collectors.toList()));

        log.info("Returning {} sessions ({} confirmed + {} saved)", result.size(), confirmedSessions.size(),
                savedSessions.size());

        return result;
    }

    private void checkAndNotifyPreliminary(List<GameSession> sessions) {
        // Just trigger event for each session, allowing Listener to handle notification
        // logic
        for (GameSession session : sessions) {
            eventPublisher.publishEvent(new com.squadsync.backend.event.GameSessionUpdatedEvent(this, session));
        }
    }

    @org.springframework.scheduling.annotation.Scheduled(cron = "0 1,31 * * * *")
    public void checkUpcomingPreliminarySessions() {
        log.info("Running scheduled check for upcoming preliminary sessions...");
        LocalDateTime now = LocalDateTime.now();
        List<GameSession> activeSessions = sessionRepository.findByEndTimeGreaterThanOrderByStartTimeAsc(now);

        checkAndNotifyPreliminary(activeSessions);
    }

    private List<AvailabilitySlot> filterAvailableSlots(List<AvailabilitySlot> slots,
            List<GameSession> confirmedSessions) {
        List<AvailabilitySlot> availableSlots = new ArrayList<>();
        for (AvailabilitySlot slot : slots) {
            boolean isBusy = false;
            for (GameSession session : confirmedSessions) {
                boolean userInSession = session.getPlayers().stream()
                        .anyMatch(p -> p.getUser().getId().equals(slot.getUser().getId()));

                if (userInSession) {
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
        return availableSlots;
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
        Set<LocalDateTime> timePoints = new HashSet<>();
        for (AvailabilitySlot slot : slots) {
            timePoints.add(slot.getStartTime());
            timePoints.add(slot.getEndTime());
        }
        List<LocalDateTime> sortedTimePoints = new ArrayList<>(timePoints);
        Collections.sort(sortedTimePoints);

        List<TimeSlot> atomicSlots = new ArrayList<>();

        // Create atomic intervals
        for (int i = 0; i < sortedTimePoints.size() - 1; i++) {
            LocalDateTime start = sortedTimePoints.get(i);
            LocalDateTime end = sortedTimePoints.get(i + 1);

            if (start.equals(end))
                continue;

            List<String> activeSlotIds = new ArrayList<>();
            Set<String> activeUserIds = new HashSet<>();

            for (AvailabilitySlot slot : slots) {
                if (!slot.getStartTime().isAfter(start) && !slot.getEndTime().isBefore(end)) {
                    if (!activeUserIds.contains(slot.getUser().getId())) {
                        activeSlotIds.add(slot.getId());
                        activeUserIds.add(slot.getUser().getId());
                    }
                }
            }

            if (activeUserIds.size() >= MIN_PLAYERS_FOR_SESSION) {
                atomicSlots.add(new TimeSlot(start, end, activeSlotIds, activeUserIds));
            }
        }

        // Merge contiguous slots
        List<TimeSlot> mergedSlots = new ArrayList<>();
        if (atomicSlots.isEmpty())
            return mergedSlots;

        TimeSlot current = atomicSlots.get(0);
        for (int i = 1; i < atomicSlots.size(); i++) {
            TimeSlot next = atomicSlots.get(i);

            boolean contiguous = current.endTime.equals(next.startTime);

            // Check intersection of users for continuity "core" group
            Set<String> intersection = new HashSet<>(current.getUserIds());
            intersection.retainAll(next.getUserIds());

            boolean hasCoreGroup = intersection.size() >= MIN_PLAYERS_FOR_SESSION;

            if (contiguous && hasCoreGroup) {
                // Merge
                current.endTime = next.endTime;
                // Union of slots and users
                current.slotIds.addAll(next.slotIds);
                current.userIds.addAll(next.userIds);
                // De-duplicate Ids if list wasn't distinct (it is list of strings)
                current.slotIds = current.slotIds.stream().distinct().collect(Collectors.toList());
            } else {
                mergedSlots.add(current);
                current = next;
            }
        }
        mergedSlots.add(current);

        // Filter and Split by Duration constraints
        List<TimeSlot> finalSlots = new ArrayList<>();
        for (TimeSlot slot : mergedSlots) {
            long totalMinutes = java.time.Duration.between(slot.startTime, slot.endTime).toMinutes();

            if (totalMinutes < MIN_SESSION_DURATION_MINUTES) {
                continue;
            }

            // Smart Splitting Logic
            LocalDateTime chunkStart = slot.startTime;
            while (chunkStart.isBefore(slot.endTime)) {

                // If remaining time is small enough for one chunk (<= MAX, currently 240)
                // AND logic for "remainder vs split" ?
                // Actually we just iterate carving out 2h chunks unless remainder logic
                // applies.

                long remaining = java.time.Duration.between(chunkStart, slot.endTime).toMinutes();

                // Base target: 2 hours (120 min)
                long targetChunk = 120; // Preferred duration

                if (remaining <= targetChunk) {
                    // Just finish it
                    if (remaining >= MIN_SESSION_DURATION_MINUTES) {
                        finalSlots.add(new TimeSlot(chunkStart, slot.endTime, slot.slotIds, slot.userIds));
                    }
                    break;
                } else {
                    // remaining > 120
                    // Check remainder if we cut at 120
                    long remainderAfterCut = remaining - targetChunk;

                    if (remainderAfterCut < 60) {
                        // Remainder is small (< 1h).
                        // Extend current chunk to include it, IF it fits in MAX (240).
                        long extendedDuration = targetChunk + remainderAfterCut;

                        if (extendedDuration <= MAX_SESSION_DURATION_MINUTES) {
                            // Perfect, make one big chunk
                            finalSlots.add(new TimeSlot(chunkStart, slot.endTime, slot.slotIds, slot.userIds));
                            break;
                        } else {
                            LocalDateTime chunkEnd = chunkStart.plusMinutes(targetChunk);
                            finalSlots.add(new TimeSlot(chunkStart, chunkEnd, slot.slotIds, slot.userIds));
                            chunkStart = chunkEnd;
                        }
                    } else {
                        // Remainder > 60. Split strictly at 2h.
                        LocalDateTime chunkEnd = chunkStart.plusMinutes(targetChunk);
                        finalSlots.add(new TimeSlot(chunkStart, chunkEnd, slot.slotIds, slot.userIds));
                        chunkStart = chunkEnd;
                    }
                }
            }
        }

        return finalSlots;
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
                int weight = DEFAULT_PREFERENCE_WEIGHT; // Default

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
                            .orElse(DEFAULT_PREFERENCE_WEIGHT);
                }

                if (weight > 0) {
                    score += weight;
                    playerCount++;
                }
            }

            if (playerCount < MIN_PLAYERS_FOR_SESSION) {
                continue; // Not enough players for this game
            }

            score += playerCount * PARTICIPATION_BONUS_MULTIPLIER; // Participation bonus

            gameScores.add(new GameScore(game, score));
        }

        if (gameScores.isEmpty())
            return null;

        gameScores.sort((a, b) -> b.score - a.score);
        GameScore bestGame = gameScores.get(0);

        // Filter slots/users that are actually playing (didn't veto)
        List<AvailabilitySlot> participatingSlots = new ArrayList<>();
        for (AvailabilitySlot slot : slots) {
            int weight = DEFAULT_PREFERENCE_WEIGHT;
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
                        .orElse(DEFAULT_PREFERENCE_WEIGHT);
            }

            if (weight > 0) {
                // Ensure the slot actually overlaps with the chosen session time (for partial
                // availability handling)
                LocalDateTime slotStart = slot.getStartTime();
                LocalDateTime slotEnd = slot.getEndTime();
                LocalDateTime sessionStart = timeSlot.startTime;
                LocalDateTime sessionEnd = timeSlot.endTime;

                // Check overlap: start < sessionEnd && end > sessionStart
                if (slotStart.isBefore(sessionEnd) && slotEnd.isAfter(sessionStart)) {
                    participatingSlots.add(slot);
                }
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
        session.setSessionScore(bestGame.score);

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
        List<String> slotIds;
        Set<String> userIds;

        public TimeSlot(LocalDateTime startTime, LocalDateTime endTime, List<String> slotIds, Set<String> userIds) {
            this.startTime = startTime;
            this.endTime = endTime;
            this.slotIds = slotIds;
            this.userIds = userIds;
        }

        public List<String> getSlotIds() {
            return slotIds;
        }

        public Set<String> getUserIds() {
            return userIds;
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

    private String generateSessionSignature(GameSession session) {
        // Use a delimiter safe for IDs and Timestamps
        return session.getGame().getId() + "|" + session.getStartTime().toString() + "|"
                + session.getEndTime().toString();
    }
}
