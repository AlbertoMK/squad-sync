package com.squadsync.backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.squadsync.backend.dto.GameDto;
import com.squadsync.backend.dto.GameSessionDto;
import com.squadsync.backend.dto.UserDto;
import com.squadsync.backend.model.*;
import com.squadsync.backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
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
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    private static final int MIN_PLAYERS_FOR_SESSION = 3;

    @Scheduled(fixedRate = 300000) // Run every 5 minutes
    @Transactional
    public void runMatchmakingJob() {
        runMatchmaking();
    }

    @Transactional
    public List<GameSessionDto> runMatchmaking() {
        log.info("Running matchmaking algorithm...");

        LocalDateTime now = LocalDateTime.now();
        List<AvailabilitySlot> slots = slotRepository.findByStartTimeGreaterThanEqualOrderByStartTimeAsc(now);

        if (slots.isEmpty()) {
            log.info("No availability slots found");
            return Collections.emptyList();
        }

        List<TimeSlot> overlappingSlots = findOverlappingSlots(slots);
        List<TimeSlot> viableSlots = overlappingSlots.stream()
                .filter(slot -> slot.getUserIds().size() >= MIN_PLAYERS_FOR_SESSION)
                .collect(Collectors.toList());

        log.info("Found {} viable time slots", viableSlots.size());

        List<GameSession> sessions = new ArrayList<>();
        for (TimeSlot slot : viableSlots) {
            GameSession session = createSessionForSlot(slot);
            if (session != null) {
                sessions.add(session);
            }
        }

        log.info("Created {} game sessions", sessions.size());
        return sessions.stream().map(this::mapToDto).collect(Collectors.toList());
    }

    private List<TimeSlot> findOverlappingSlots(List<AvailabilitySlot> slots) {
        List<TimeSlot> overlaps = new ArrayList<>();
        Set<String> processed = new HashSet<>();

        for (int i = 0; i < slots.size(); i++) {
            AvailabilitySlot slotA = slots.get(i);
            if (processed.contains(slotA.getId()))
                continue;

            Set<String> overlappingUsers = new HashSet<>();
            overlappingUsers.add(slotA.getUser().getId());
            LocalDateTime overlapStart = slotA.getStartTime();
            LocalDateTime overlapEnd = slotA.getEndTime();

            for (int j = i + 1; j < slots.size(); j++) {
                AvailabilitySlot slotB = slots.get(j);

                if (doSlotsOverlap(slotA, slotB)) {
                    overlappingUsers.add(slotB.getUser().getId());

                    if (slotB.getStartTime().isAfter(overlapStart)) {
                        overlapStart = slotB.getStartTime();
                    }
                    if (slotB.getEndTime().isBefore(overlapEnd)) {
                        overlapEnd = slotB.getEndTime();
                    }
                }
            }

            if (overlappingUsers.size() >= 2) {
                overlaps.add(new TimeSlot(overlapStart, overlapEnd, new ArrayList<>(overlappingUsers)));
            }
            processed.add(slotA.getId());
        }
        return overlaps;
    }

    private boolean doSlotsOverlap(AvailabilitySlot slotA, AvailabilitySlot slotB) {
        return slotA.getStartTime().isBefore(slotB.getEndTime()) &&
                slotB.getStartTime().isBefore(slotA.getEndTime());
    }

    private GameSession createSessionForSlot(TimeSlot slot) {
        List<Game> games = gameRepository.findAll();
        if (games.isEmpty())
            return null;

        List<UserGamePreference> preferences = preferenceRepository.findByUserIdIn(slot.getUserIds());
        List<GameScore> gameScores = new ArrayList<>();

        for (Game game : games) {
            // Check recent sessions rotation
            List<GameSession> recentSessions = sessionRepository.findByGameIdOrderByCreatedAtDesc(game.getId());
            if (recentSessions.size() >= 3)
                continue;

            int score = 0;
            for (String userId : slot.getUserIds()) {
                int weight = preferences.stream()
                        .filter(p -> p.getUser().getId().equals(userId) && p.getGame().getId().equals(game.getId()))
                        .findFirst()
                        .map(UserGamePreference::getWeight)
                        .orElse(5);
                score += weight;
            }

            score += slot.getUserIds().size() * 2; // Participation bonus

            gameScores.add(new GameScore(game, score));
        }

        if (gameScores.isEmpty())
            return null;

        gameScores.sort((a, b) -> b.score - a.score);
        GameScore bestGame = gameScores.get(0);

        // Check existing session
        // Note: Simplified check, ideally check DB for exact overlap

        GameSession session = new GameSession();
        session.setGame(bestGame.game);
        session.setStartTime(slot.startTime);
        session.setEndTime(slot.endTime);
        session.setSessionScore(bestGame.score);
        try {
            session.setPlayerIds(objectMapper.writeValueAsString(slot.userIds));
        } catch (JsonProcessingException e) {
            log.error("Error serializing player IDs", e);
            return null;
        }

        return sessionRepository.save(session);
    }

    public List<GameSessionDto> getUpcomingSessions() {
        return sessionRepository.findByStartTimeGreaterThanEqualOrderByStartTimeAsc(LocalDateTime.now())
                .stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    private GameSessionDto mapToDto(GameSession session) {
        GameSessionDto dto = new GameSessionDto();
        dto.setId(session.getId());
        dto.setGameId(session.getGame().getId());
        dto.setStartTime(session.getStartTime());
        dto.setEndTime(session.getEndTime());
        dto.setSessionScore(session.getSessionScore());
        dto.setCreatedAt(session.getCreatedAt());

        // Map Game
        GameDto gameDto = new GameDto();
        gameDto.setId(session.getGame().getId());
        gameDto.setTitle(session.getGame().getTitle());
        gameDto.setCoverImageUrl(session.getGame().getCoverImageUrl());
        dto.setGame(gameDto);

        try {
            List<String> playerIds = objectMapper.readValue(session.getPlayerIds(), new TypeReference<List<String>>() {
            });
            dto.setPlayerIds(playerIds);

            List<UserDto> players = userRepository.findAllById(playerIds).stream()
                    .map(user -> {
                        UserDto u = new UserDto();
                        u.setId(user.getId());
                        u.setUsername(user.getUsername());
                        u.setAvatarColor(user.getAvatarColor());
                        return u;
                    })
                    .collect(Collectors.toList());
            dto.setPlayers(players);
        } catch (JsonProcessingException e) {
            log.error("Error deserializing player IDs", e);
        }

        return dto;
    }

    private static class TimeSlot {
        LocalDateTime startTime;
        LocalDateTime endTime;
        List<String> userIds;

        public TimeSlot(LocalDateTime startTime, LocalDateTime endTime, List<String> userIds) {
            this.startTime = startTime;
            this.endTime = endTime;
            this.userIds = userIds;
        }

        public List<String> getUserIds() {
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
}
