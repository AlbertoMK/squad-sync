package com.squadsync.backend.listener;

import com.squadsync.backend.event.GameSessionUpdatedEvent;
import com.squadsync.backend.model.GameSession;
import com.squadsync.backend.service.DiscordBotService;
import com.squadsync.backend.service.GameSessionService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DiscordNotificationListenerTest {

    @Mock
    private DiscordBotService discordBotService;

    @Mock
    private GameSessionService gameSessionService;

    @InjectMocks
    private DiscordNotificationListener discordNotificationListener;

    @Test
    public void testHandleGameSessionUpdated_ConfirmedSession() {
        // Given
        GameSession session = new GameSession();
        session.setId("conf-1");

        // Mock service returning CONFIRMED
        when(gameSessionService.getSessionStatus(session)).thenReturn(GameSession.SessionStatus.CONFIRMED);

        // When
        discordNotificationListener.handleGameSessionUpdated(new GameSessionUpdatedEvent(this, session));

        // Then
        ArgumentCaptor<List<GameSession>> captor = ArgumentCaptor.forClass(List.class);
        verify(discordBotService).sendMatchmakingUpdates(captor.capture());

        List<GameSession> captured = captor.getValue();
        Assertions.assertEquals(1, captured.size());
        Assertions.assertEquals("conf-1", captured.get(0).getId());
    }

    @Test
    public void testHandleGameSessionUpdated_PreliminarySession_ToNotify() {
        // Given
        LocalDateTime now = LocalDateTime.now();
        GameSession session = new GameSession();
        session.setId("prelim-1");
        session.setNotificationStatus(GameSession.NotificationStatus.NONE);
        session.setStartTime(now.plusMinutes(90)); // Starts in 1.5h (within 2h window)
        session.setEndTime(now.plusHours(3));

        when(gameSessionService.getSessionStatus(session)).thenReturn(GameSession.SessionStatus.PRELIMINARY);

        // When
        discordNotificationListener.handleGameSessionUpdated(new GameSessionUpdatedEvent(this, session));

        // Then
        ArgumentCaptor<List<GameSession>> captor = ArgumentCaptor.forClass(List.class);
        verify(discordBotService).sendPreliminarySessionNotifications(captor.capture());

        List<GameSession> captured = captor.getValue();
        Assertions.assertEquals(1, captured.size());
        Assertions.assertEquals("prelim-1", captured.get(0).getId());

        // Verify it was marked as notified
        verify(gameSessionService).updateNotificationStatus("prelim-1",
                GameSession.NotificationStatus.PRELIMINARY_SENT);
    }

    @Test
    public void testHandleGameSessionUpdated_PreliminarySession_AlreadyNotified() {
        // Given
        GameSession session = new GameSession();
        session.setId("prelim-notified");
        session.setNotificationStatus(GameSession.NotificationStatus.PRELIMINARY_SENT);

        when(gameSessionService.getSessionStatus(session)).thenReturn(GameSession.SessionStatus.PRELIMINARY);

        // When
        discordNotificationListener.handleGameSessionUpdated(new GameSessionUpdatedEvent(this, session));

        // Then
        verify(discordBotService, never()).sendPreliminarySessionNotifications(anyList());
        verify(gameSessionService, never()).updateNotificationStatus(anyString(), any());
    }

    @Test
    public void testHandleGameSessionUpdated_PreliminarySession_TooFarInFuture() {
        // Given
        LocalDateTime now = LocalDateTime.now();
        GameSession session = new GameSession();
        session.setId("prelim-future");
        session.setNotificationStatus(GameSession.NotificationStatus.NONE);
        session.setStartTime(now.plusHours(5)); // Starts in 5h (outside 2h window)
        session.setEndTime(now.plusHours(7));

        when(gameSessionService.getSessionStatus(session)).thenReturn(GameSession.SessionStatus.PRELIMINARY);

        // When
        discordNotificationListener.handleGameSessionUpdated(new GameSessionUpdatedEvent(this, session));

        // Then
        verify(discordBotService, never()).sendPreliminarySessionNotifications(anyList());
        verify(gameSessionService, never()).updateNotificationStatus(anyString(), any());
    }
}
