package com.squadsync.backend.service;

import com.squadsync.backend.model.AvailabilitySlot;
import com.squadsync.backend.model.GameSession;
import com.squadsync.backend.model.GameSessionPlayer;
import com.squadsync.backend.repository.AvailabilitySlotRepository;
import com.squadsync.backend.repository.GameSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class GameSessionService {

    private final GameSessionRepository sessionRepository;
    private final AvailabilitySlotRepository availabilitySlotRepository;
    private final com.squadsync.backend.repository.UserRepository userRepository;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;

    @Transactional
    public void acceptSession(String sessionId, String userId) {
        GameSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Session not found"));

        Optional<GameSessionPlayer> existingPlayer = session.getPlayers().stream()
                .filter(p -> p.getUser().getId().equals(userId))
                .findFirst();

        if (existingPlayer.isPresent()) {
            existingPlayer.get().setStatus(GameSessionPlayer.SessionPlayerStatus.ACCEPTED);
        } else {
            // User is joining the session (was not originally invited/matched)
            com.squadsync.backend.model.User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            GameSessionPlayer newPlayer = new GameSessionPlayer();
            newPlayer.setSession(session);
            newPlayer.setUser(user);
            newPlayer.setStatus(GameSessionPlayer.SessionPlayerStatus.ACCEPTED);
            session.getPlayers().add(newPlayer);
        }

        sessionRepository.save(session);
        eventPublisher.publishEvent(new com.squadsync.backend.event.GameSessionUpdatedEvent(this, session));
    }

    @Transactional
    public void rejectSession(String sessionId, String userId, String reason) {
        GameSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Session not found"));

        GameSessionPlayer player = session.getPlayers().stream()
                .filter(p -> p.getUser().getId().equals(userId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("User is not part of this session"));

        player.setStatus(GameSessionPlayer.SessionPlayerStatus.REJECTED);
        player.setRejectionReason(reason);

        if ("NOT_AVAILABLE".equals(reason)) {
            // Remove availability slot
            List<AvailabilitySlot> slots = availabilitySlotRepository.findByUserId(userId);
            for (AvailabilitySlot slot : slots) {
                // Check overlap with session
                if (slot.getStartTime().isBefore(session.getEndTime()) &&
                        slot.getEndTime().isAfter(session.getStartTime())) {
                    availabilitySlotRepository.delete(slot);
                }
            }
        }

        sessionRepository.save(session);
    }

    public GameSession.SessionStatus getSessionStatus(GameSession session) {
        long acceptedPlayers = session.getPlayers().stream()
                .filter(p -> p.getStatus() == GameSessionPlayer.SessionPlayerStatus.ACCEPTED)
                .count();

        int minPlayers = Math.max(2, session.getGame().getMinPlayers());
        boolean enoughPlayers = acceptedPlayers >= minPlayers;

        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        boolean startsSoon = session.getStartTime().isBefore(now.plusHours(1));

        if (enoughPlayers && startsSoon) {
            return GameSession.SessionStatus.CONFIRMED;
        }

        return GameSession.SessionStatus.PRELIMINARY;
    }

    @Transactional
    public void updateNotificationStatus(String sessionId, GameSession.NotificationStatus status) {
        GameSession session = sessionRepository.findById(sessionId).orElse(null);
        if (session != null) {
            session.setNotificationStatus(status);
            sessionRepository.save(session);
        }
    }
}
