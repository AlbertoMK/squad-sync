package com.squadsync.backend.listener;

import com.squadsync.backend.event.GameSessionUpdatedEvent;
import com.squadsync.backend.model.GameSession;
import com.squadsync.backend.service.DiscordBotService;
import com.squadsync.backend.service.GameSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Collections;

@Component
@RequiredArgsConstructor
@Slf4j
public class DiscordNotificationListener {

    private final DiscordBotService discordBotService;
    private final GameSessionService gameSessionService;

    @EventListener
    public void handleGameSessionUpdated(GameSessionUpdatedEvent event) {
        GameSession session = event.getSession();
        GameSession.SessionStatus status = gameSessionService.getSessionStatus(session);

        log.info("Handling GameSessionUpdatedEvent for session {}: Status={}", session.getId(), status);

        if (status == GameSession.SessionStatus.CONFIRMED) {
            if (session.getNotificationStatus() != GameSession.NotificationStatus.CONFIRMED_SENT) {
                // Send update immediately
                discordBotService.sendMatchmakingUpdates(Collections.singletonList(session));
                gameSessionService.updateNotificationStatus(session.getId(),
                        GameSession.NotificationStatus.CONFIRMED_SENT);
            }
        } else if (status == GameSession.SessionStatus.PRELIMINARY) {
            java.time.LocalDateTime now = java.time.LocalDateTime.now();
            java.time.LocalDateTime twoHoursLater = now.plusHours(2);

            if (session.getNotificationStatus() == GameSession.NotificationStatus.NONE &&
                    session.getStartTime().isBefore(twoHoursLater) &&
                    session.getEndTime().isAfter(now)) {

                discordBotService.sendPreliminarySessionNotifications(Collections.singletonList(session));

                // Mark as notified and save
                gameSessionService.updateNotificationStatus(session.getId(),
                        GameSession.NotificationStatus.PRELIMINARY_SENT);
            }
        }
    }
}
