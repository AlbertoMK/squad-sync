package com.squadsync.backend.event;

import com.squadsync.backend.model.GameSession;
import org.springframework.context.ApplicationEvent;

import java.time.LocalDateTime;

public class GameSessionUpdatedEvent extends ApplicationEvent {
    private final GameSession session;
    private final LocalDateTime timestamp;

    public GameSessionUpdatedEvent(Object source, GameSession session) {
        super(source);
        this.session = session;
        this.timestamp = LocalDateTime.now();
    }

    public GameSession getSession() {
        return session;
    }

    public LocalDateTime getEventDateTime() {
        return timestamp;
    }
}
