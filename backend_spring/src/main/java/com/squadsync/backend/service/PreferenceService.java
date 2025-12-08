package com.squadsync.backend.service;

import com.squadsync.backend.dto.PreferenceDto;
import com.squadsync.backend.model.Game;
import com.squadsync.backend.model.User;
import com.squadsync.backend.model.UserGamePreference;
import com.squadsync.backend.repository.GameRepository;
import com.squadsync.backend.repository.UserGamePreferenceRepository;
import com.squadsync.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PreferenceService {

    private final UserGamePreferenceRepository preferenceRepository;
    private final UserRepository userRepository;
    private final GameRepository gameRepository;

    public List<PreferenceDto> getUserPreferences(String userId) {
        return preferenceRepository.findByUserId(userId).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    public PreferenceDto updatePreference(String userId, PreferenceDto dto) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        Game game = gameRepository.findById(dto.getGameId())
                .orElseThrow(() -> new RuntimeException("Game not found"));

        UserGamePreference preference = preferenceRepository.findByUserIdAndGameId(userId, dto.getGameId())
                .orElse(new UserGamePreference());

        preference.setUser(user);
        preference.setGame(game);
        preference.setWeight(dto.getWeight());

        preferenceRepository.save(preference);
        return mapToDto(preference);
    }

    private PreferenceDto mapToDto(UserGamePreference pref) {
        PreferenceDto dto = new PreferenceDto();
        dto.setId(pref.getId());
        dto.setUserId(pref.getUser().getId());
        dto.setGameId(pref.getGame().getId());
        dto.setWeight(pref.getWeight());
        return dto;
    }
}
