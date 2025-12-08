package com.squadsync.backend.service;

import com.squadsync.backend.dto.AvailabilitySlotDto;
import com.squadsync.backend.model.AvailabilitySlot;
import com.squadsync.backend.model.Game;
import com.squadsync.backend.model.User;
import com.squadsync.backend.repository.AvailabilitySlotRepository;
import com.squadsync.backend.repository.GameRepository;
import com.squadsync.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AvailabilityService {

    private final AvailabilitySlotRepository slotRepository;
    private final UserRepository userRepository;
    private final GameRepository gameRepository;
    private final MatchmakingService matchmakingService;

    public List<AvailabilitySlotDto> getUserSlots(String userId) {
        return slotRepository.findByUserId(userId).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    public AvailabilitySlotDto createSlot(String userId, AvailabilitySlotDto dto) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        AvailabilitySlot slot = new AvailabilitySlot();
        slot.setUser(user);
        // Convert Instant (DTO) to LocalDateTime (Entity) using UTC
        slot.setStartTime(LocalDateTime.ofInstant(dto.getStartTime(), ZoneOffset.UTC));
        slot.setEndTime(LocalDateTime.ofInstant(dto.getEndTime(), ZoneOffset.UTC));

        if (dto.getGameId() != null) {
            Game game = gameRepository.findById(dto.getGameId())
                    .orElse(null);
            slot.setGame(game);
        }

        slotRepository.save(slot);

        if (dto.getPreferences() != null && !dto.getPreferences().isEmpty()) {
            for (com.squadsync.backend.dto.PreferenceDto prefDto : dto.getPreferences()) {
                com.squadsync.backend.model.AvailabilityGamePreference pref = new com.squadsync.backend.model.AvailabilityGamePreference();
                pref.setAvailabilitySlot(slot);
                pref.setGame(gameRepository.findById(prefDto.getGameId())
                        .orElseThrow(() -> new RuntimeException("Game not found")));
                pref.setWeight(prefDto.getWeight());
                slot.getPreferences().add(pref);
            }
            slotRepository.save(slot);
        }

        matchmakingService.runMatchmaking(); // Trigger matchmaking
        return mapToDto(slot);
    }

    public void deleteSlot(String slotId, String userId) {
        AvailabilitySlot slot = slotRepository.findById(slotId)
                .orElseThrow(() -> new RuntimeException("Slot not found"));

        if (!slot.getUser().getId().equals(userId)) {
            throw new RuntimeException("Unauthorized");
        }

        // Find overlapping sessions for this user
        List<com.squadsync.backend.model.GameSession> sessions = matchmakingService.findSessionsForUser(userId);

        for (com.squadsync.backend.model.GameSession session : sessions) {
            // Check if session overlaps with the slot being deleted
            if (session.getStartTime().isBefore(slot.getEndTime()) &&
                    slot.getStartTime().isBefore(session.getEndTime())) {

                // Remove player from session
                matchmakingService.removePlayerFromSession(session.getId(), userId);
            }
        }

        slotRepository.delete(slot);
        matchmakingService.runMatchmaking(); // Trigger matchmaking
    }

    private AvailabilitySlotDto mapToDto(AvailabilitySlot slot) {
        AvailabilitySlotDto dto = new AvailabilitySlotDto();
        dto.setId(slot.getId());
        dto.setUserId(slot.getUser().getId());
        // Convert LocalDateTime (Entity) to Instant (DTO) using UTC
        dto.setStartTime(slot.getStartTime().toInstant(ZoneOffset.UTC));
        dto.setEndTime(slot.getEndTime().toInstant(ZoneOffset.UTC));
        if (slot.getGame() != null) {
            dto.setGameId(slot.getGame().getId());
        }
        return dto;
    }
}
