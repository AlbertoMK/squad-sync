package com.squadsync.backend.controller;

import com.squadsync.backend.dto.GameDto;
import com.squadsync.backend.service.GameService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/games")
@RequiredArgsConstructor
public class GameController {

    private final GameService gameService;

    @GetMapping
    public ResponseEntity<List<GameDto>> getAllGames() {
        return ResponseEntity.ok(gameService.getAllGames());
    }

    @GetMapping("/{id}")
    public ResponseEntity<GameDto> getGameById(@PathVariable String id) {
        return ResponseEntity.ok(gameService.getGameById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<GameDto> createGame(@RequestBody GameDto gameDto) {
        return ResponseEntity.ok(gameService.createGame(gameDto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteGame(@PathVariable String id) {
        gameService.deleteGame(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<GameDto> updateGame(@PathVariable String id, @RequestBody GameDto gameDto) {
        return ResponseEntity.ok(gameService.updateGame(id, gameDto));
    }
}
