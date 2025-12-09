package com.squadsync.backend.service;

import com.squadsync.backend.dto.AvailabilitySlotDto;
import com.squadsync.backend.model.AvailabilitySlot;
import com.squadsync.backend.model.User;
import com.squadsync.backend.repository.AvailabilitySlotRepository;
import com.squadsync.backend.repository.GameRepository;
import com.squadsync.backend.repository.UserRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AvailabilityServiceTest {

    @Mock
    private AvailabilitySlotRepository slotRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private GameRepository gameRepository;

    @Mock
    private MatchmakingService matchmakingService;

    @InjectMocks
    private AvailabilityService availabilityService;

    @Test
    public void testCreateSlot_Overlapping_ThrowsException() {
        // Given
        String userId = "user1";
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime existingStart = now.plusHours(1);
        LocalDateTime existingEnd = now.plusHours(3); // 1h - 3h (2 hours)

        User user = new User();
        user.setId(userId);

        AvailabilitySlot existingSlot = new AvailabilitySlot();
        existingSlot.setId("existing-1");
        existingSlot.setUser(user);
        existingSlot.setStartTime(existingStart);
        existingSlot.setEndTime(existingEnd);

        // Mock existing slot returning when queried
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(slotRepository.findByUserId(userId)).thenReturn(List.of(existingSlot));

        // When: Trying to create a slot that overlaps (e.g. 2h - 4h)
        // Overlap is 2h-3h
        AvailabilitySlotDto newSlotDto = new AvailabilitySlotDto();
        newSlotDto.setStartTime(now.plusHours(2));
        newSlotDto.setEndTime(now.plusHours(4));
        // Truncation happens in service, so input assumes seconds precision or service
        // handles it.

        // Then
        IllegalArgumentException exception = Assertions.assertThrows(IllegalArgumentException.class, () -> {
            availabilityService.createSlot(userId, newSlotDto);
        });

        Assertions.assertEquals("Overlapping availability slot exists", exception.getMessage());
    }

    @Test
    public void testCreateSlot_NoOverlap_Success() {
        // Given
        String userId = "user1";
        LocalDateTime now = LocalDateTime.now();
        User user = new User();
        user.setId(userId);

        AvailabilitySlot existingSlot = new AvailabilitySlot();
        existingSlot.setStartTime(now.plusHours(1));
        existingSlot.setEndTime(now.plusHours(2));

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(slotRepository.findByUserId(userId)).thenReturn(List.of(existingSlot));
        when(slotRepository.save(any(AvailabilitySlot.class))).thenAnswer(i -> {
            AvailabilitySlot s = i.getArgument(0);
            s.setId("new-1");
            return s;
        });

        // When: New slot completely after (3h - 4h)
        AvailabilitySlotDto newSlotDto = new AvailabilitySlotDto();
        newSlotDto.setStartTime(now.plusHours(3));
        newSlotDto.setEndTime(now.plusHours(4));

        // Then
        Assertions.assertDoesNotThrow(() -> {
            availabilityService.createSlot(userId, newSlotDto);
        });
    }
}
