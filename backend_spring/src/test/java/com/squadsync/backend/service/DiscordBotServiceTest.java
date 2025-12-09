package com.squadsync.backend.service;

import com.squadsync.backend.model.Game;
import com.squadsync.backend.model.GameSession;
import com.squadsync.backend.model.GameSessionPlayer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@SpringBootTest
public class DiscordBotServiceTest {

    @Autowired
    private DiscordBotService discordBotService;

    @Test
    public void testSendMatchmakingUpdates() throws InterruptedException {
        // Create dummy sessions
        Game game = new Game();
        game.setTitle("Test Game");
        game.setId("game-1");

        GameSession session = new GameSession();
        session.setId("session-1");
        session.setGame(game);
        session.setStartTime(LocalDateTime.now().plusHours(1));
        session.setEndTime(LocalDateTime.now().plusHours(2));
        session.setStatus(GameSession.SessionStatus.CONFIRMED);

        // Add dummy players
        session.setPlayers(new ArrayList<>());
        GameSessionPlayer player = new GameSessionPlayer();
        com.squadsync.backend.model.User user = new com.squadsync.backend.model.User();
        user.setDiscordId("1234567890");
        player.setUser(user);
        session.getPlayers().add(player);

        List<GameSession> sessions = new ArrayList<>();
        sessions.add(session);

        System.out.println("Testing Discord Bot Send Updates...");
        discordBotService.sendMatchmakingUpdates(sessions);

        Thread.sleep(3000);
    }

    @Test
    public void testSendMultipleMatchmakingUpdates() throws InterruptedException {
        List<GameSession> sessions = new ArrayList<>();

        // Session 1
        Game game1 = new Game();
        game1.setTitle("Game A");
        game1.setId("g1");
        GameSession s1 = new GameSession();
        s1.setId("s1");
        s1.setGame(game1);
        s1.setStartTime(LocalDateTime.now().plusHours(1));
        s1.setEndTime(LocalDateTime.now().plusHours(3));
        s1.setStatus(GameSession.SessionStatus.CONFIRMED);
        s1.setPlayers(new ArrayList<>());
        sessions.add(s1);

        // Session 2
        Game game2 = new Game();
        game2.setTitle("Game B");
        game2.setId("g2");
        GameSession s2 = new GameSession();
        s2.setId("s2");
        s2.setGame(game2);
        s2.setStartTime(LocalDateTime.now().plusHours(2));
        s2.setEndTime(LocalDateTime.now().plusHours(4));
        s2.setStatus(GameSession.SessionStatus.CONFIRMED);
        s2.setPlayers(new ArrayList<>());
        s2.setPlayers(new ArrayList<>());

        GameSessionPlayer p1 = new GameSessionPlayer();
        com.squadsync.backend.model.User u1 = new com.squadsync.backend.model.User();
        u1.setDiscordId("1111111111");
        p1.setUser(u1);
        s2.getPlayers().add(p1);

        GameSessionPlayer p2 = new GameSessionPlayer();
        com.squadsync.backend.model.User u2 = new com.squadsync.backend.model.User();
        u2.setDiscordId("2222222222");
        p2.setUser(u2);
        s2.getPlayers().add(p2);
        sessions.add(s2);

        System.out.println("Testing Discord Bot Send Multiple Updates...");
        discordBotService.sendMatchmakingUpdates(sessions);

        Thread.sleep(3000);
    }

    @Test
    public void testSendPreliminarySessionNotifications() throws InterruptedException {
        List<GameSession> sessions = new ArrayList<>();

        // Session 1 (Preliminary, starting in 1.5 hours)
        Game game1 = new Game();
        game1.setTitle("Preliminary Game");
        game1.setId("g-pre-1");

        GameSession s1 = new GameSession();
        s1.setId("s-pre-1");
        s1.setGame(game1);
        // Less than 2 hours away
        s1.setStartTime(LocalDateTime.now().plusMinutes(90));
        s1.setEndTime(LocalDateTime.now().plusMinutes(150));
        // Status might be PRELIMINARY if that enum exists, or just NOT CONFIRMED.
        // The service method sendPreliminarySessionNotifications treats them as
        // preliminary regardless of status enum for now,
        // but let's assume standard behavior.
        s1.setStatus(GameSession.SessionStatus.PRELIMINARY); // or similar if exists, defaulting to PENDING/CREATED

        s1.setPlayers(new ArrayList<>());
        s1.setPlayers(new ArrayList<>());

        GameSessionPlayer pp1 = new GameSessionPlayer();
        com.squadsync.backend.model.User userP1 = new com.squadsync.backend.model.User();
        userP1.setDiscordId("3333333333");
        pp1.setUser(userP1);
        s1.getPlayers().add(pp1);

        sessions.add(s1);

        System.out.println("Testing Discord Bot Send Preliminary Notifications...");
        discordBotService.sendPreliminarySessionNotifications(sessions);

        Thread.sleep(3000);
    }
}
