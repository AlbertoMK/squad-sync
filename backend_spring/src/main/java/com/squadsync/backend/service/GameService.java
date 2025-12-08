package com.squadsync.backend.service;

import com.squadsync.backend.dto.GameDto;
import com.squadsync.backend.model.Game;
import com.squadsync.backend.repository.GameRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GameService {

    private final GameRepository gameRepository;

    public List<GameDto> getAllGames() {
        return gameRepository.findAll().stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    public GameDto getGameById(String id) {
        return gameRepository.findById(id)
                .map(this::mapToDto)
                .orElseThrow(() -> new RuntimeException("Game not found"));
    }

    public GameDto createGame(GameDto gameDto) {
        Game game = new Game();
        game.setTitle(gameDto.getTitle());
        game.setMinPlayers(gameDto.getMinPlayers());
        game.setMaxPlayers(gameDto.getMaxPlayers());
        game.setGenre(gameDto.getGenre());
        game.setCoverImageUrl(gameDto.getCoverImageUrl());

        gameRepository.save(game);
        return mapToDto(game);
    }

    public void deleteGame(String id) {
        gameRepository.deleteById(id);
    }

    public GameDto updateGame(String id, GameDto gameDto) {
        Game game = gameRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Game not found"));

        game.setTitle(gameDto.getTitle());
        game.setMinPlayers(gameDto.getMinPlayers());
        game.setMaxPlayers(gameDto.getMaxPlayers());
        game.setGenre(gameDto.getGenre());
        game.setCoverImageUrl(gameDto.getCoverImageUrl());

        gameRepository.save(game);
        return mapToDto(game);
    }

    private GameDto mapToDto(Game game) {
        GameDto dto = new GameDto();
        dto.setId(game.getId());
        dto.setTitle(game.getTitle());
        dto.setMinPlayers(game.getMinPlayers());
        dto.setMaxPlayers(game.getMaxPlayers());
        dto.setGenre(game.getGenre());
        dto.setCoverImageUrl(game.getCoverImageUrl());
        dto.setCreatedAt(game.getCreatedAt());
        return dto;
    }
}
