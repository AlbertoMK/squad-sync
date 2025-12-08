package com.squadsync.backend.controller;

import com.squadsync.backend.service.GameSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import com.squadsync.backend.repository.UserRepository;

@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
public class GameSessionController {

    private final GameSessionService gameSessionService;
    private final UserRepository userRepository;

    @PostMapping("/{sessionId}/accept")
    public ResponseEntity<Void> acceptSession(
            @PathVariable String sessionId,
            @AuthenticationPrincipal UserDetails userDetails) {

        var user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));

        gameSessionService.acceptSession(sessionId, user.getId());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{sessionId}/reject")
    public ResponseEntity<Void> rejectSession(
            @PathVariable String sessionId,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserDetails userDetails) {

        var user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));

        String reason = body.getOrDefault("reason", "NOT_AVAILABLE");
        gameSessionService.rejectSession(sessionId, user.getId(), reason);

        return ResponseEntity.ok().build();
    }
}
