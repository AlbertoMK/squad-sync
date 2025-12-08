package com.squadsync.backend.controller;

import com.squadsync.backend.dto.GameSessionDto;
import com.squadsync.backend.service.MatchmakingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/matchmaking")
@RequiredArgsConstructor
public class MatchmakingController {

    private final MatchmakingService matchmakingService;

    @PostMapping("/run")
    public ResponseEntity<List<GameSessionDto>> runMatchmaking() {
        return ResponseEntity.ok(matchmakingService.runMatchmaking());
    }

    @GetMapping("/sessions")
    public ResponseEntity<List<GameSessionDto>> getSessions() {
        return ResponseEntity.ok(matchmakingService.getUpcomingSessions());
    }
}
