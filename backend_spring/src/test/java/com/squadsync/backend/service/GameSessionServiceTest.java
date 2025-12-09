package com.squadsync.backend.service;

import com.squadsync.backend.model.Game;
import com.squadsync.backend.model.GameSession;
import com.squadsync.backend.model.GameSessionPlayer;
import com.squadsync.backend.model.GameSessionPlayer.SessionPlayerStatus;
import com.squadsync.backend.repository.AvailabilitySlotRepository;
import com.squadsync.backend.repository.GameSessionRepository;
import com.squadsync.backend.repository.UserRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;

@ExtendWith(MockitoExtension.class)
public class GameSessionServiceTest {

    @Mock
    private GameSessionRepository sessionRepository;
    @Mock
    private AvailabilitySlotRepository availabilitySlotRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private GameSessionService gameSessionService;

    @Test
    public void testGetSessionStatus_Preliminary() {
        GameSession session = new GameSession();
        Game game = new Game();
        game.setMinPlayers(2);
        session.setGame(game);
        session.setStartTime(LocalDateTime.now().plusHours(2)); // > 1 hr
        session.setEndTime(LocalDateTime.now().plusHours(4));

        GameSessionPlayer player1 = new GameSessionPlayer();
        player1.setStatus(SessionPlayerStatus.ACCEPTED);
        session.getPlayers().add(player1);

        // 1 player, > 1 hour -> PRELIMINARY
        Assertions.assertEquals(GameSession.SessionStatus.PRELIMINARY, gameSessionService.getSessionStatus(session));
    }

    @Test
    public void testGetSessionStatus_Confirmed_Dynamic() {
        // Reproduce the user scenario:
        // A session has minimum players accepted, and time is < 1 hour.

        GameSession session = new GameSession();
        Game game = new Game();
        game.setMinPlayers(2);
        session.setGame(game);

        // Time is soon (< 1h)
        session.setStartTime(LocalDateTime.now().plusMinutes(30));
        session.setEndTime(LocalDateTime.now().plusMinutes(90));

        // 2 players accepted
        GameSessionPlayer player1 = new GameSessionPlayer();
        player1.setStatus(SessionPlayerStatus.ACCEPTED);

        GameSessionPlayer player2 = new GameSessionPlayer();
        player2.setStatus(SessionPlayerStatus.ACCEPTED);

        session.getPlayers().add(player1);
        session.getPlayers().add(player2);

        // Before fix: This might have been PRELIMINARY if status was stale.
        // With fix: Dynamic calculation should return CONFIRMED.
        Assertions.assertEquals(GameSession.SessionStatus.CONFIRMED, gameSessionService.getSessionStatus(session));
    }

    @Test
    public void testGetSessionStatus_BrieflyBeforeStart() {
        // Even if start time is passed (e.g. session started 5 mins ago), it should
        // stay confirmed?
        // Logic says: startsSoon = startTime.isBefore(now.plusHours(1)).
        // If startTime is in past, isBefore returns true. So yes.

        GameSession session = new GameSession();
        Game game = new Game();
        game.setMinPlayers(2);
        session.setGame(game);

        session.setStartTime(LocalDateTime.now().minusMinutes(5));
        session.setEndTime(LocalDateTime.now().plusMinutes(55));

        GameSessionPlayer player1 = new GameSessionPlayer();
        player1.setStatus(SessionPlayerStatus.ACCEPTED);
        GameSessionPlayer player2 = new GameSessionPlayer();
        player2.setStatus(SessionPlayerStatus.ACCEPTED);

        session.getPlayers().add(player1);
        session.getPlayers().add(player2);

        Assertions.assertEquals(GameSession.SessionStatus.CONFIRMED, gameSessionService.getSessionStatus(session));
    }
}
