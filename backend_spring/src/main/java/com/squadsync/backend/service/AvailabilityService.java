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

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AvailabilityService {

    private final AvailabilitySlotRepository slotRepository;
    private final UserRepository userRepository;
    private final GameRepository gameRepository;

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
        slot.setStartTime(dto.getStartTime());
        slot.setEndTime(dto.getEndTime());

        if (dto.getGameId() != null) {
            Game game = gameRepository.findById(dto.getGameId())
                    .orElse(null);
            slot.setGame(game);
        }

        slotRepository.save(slot);
        return mapToDto(slot);
    }

    public void deleteSlot(String slotId, String userId) {
        AvailabilitySlot slot = slotRepository.findById(slotId)
                .orElseThrow(() -> new RuntimeException("Slot not found"));

        if (!slot.getUser().getId().equals(userId)) {
            throw new RuntimeException("Unauthorized");
        }

        slotRepository.delete(slot);
    }

    private AvailabilitySlotDto mapToDto(AvailabilitySlot slot) {
        AvailabilitySlotDto dto = new AvailabilitySlotDto();
        dto.setId(slot.getId());
        dto.setUserId(slot.getUser().getId());
        dto.setStartTime(slot.getStartTime());
        dto.setEndTime(slot.getEndTime());
        if (slot.getGame() != null) {
            dto.setGameId(slot.getGame().getId());
        }
        return dto;
    }
}
