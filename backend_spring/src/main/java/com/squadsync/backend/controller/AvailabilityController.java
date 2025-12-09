package com.squadsync.backend.controller;

import com.squadsync.backend.dto.AvailabilitySlotDto;
import com.squadsync.backend.repository.UserRepository;
import com.squadsync.backend.service.AvailabilityService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/availability")
@RequiredArgsConstructor
public class AvailabilityController {

    private final AvailabilityService availabilityService;
    private final UserRepository userRepository;

    @GetMapping
    public ResponseEntity<List<AvailabilitySlotDto>> getMySlots(@AuthenticationPrincipal UserDetails userDetails) {
        String userId = userRepository.findByEmail(userDetails.getUsername()).orElseThrow().getId();
        return ResponseEntity.ok(availabilityService.getUserSlots(userId));
    }

    @PostMapping
    public ResponseEntity<AvailabilitySlotDto> createSlot(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody AvailabilitySlotDto slotDto) {
        String userId = userRepository.findByEmail(userDetails.getUsername()).orElseThrow().getId();
        return ResponseEntity.ok(availabilityService.createSlot(userId, slotDto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSlot(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String id) {
        String userId = userRepository.findByEmail(userDetails.getUsername()).orElseThrow().getId();
        availabilityService.deleteSlot(id, userId);
        return ResponseEntity.ok().build();
    }
}
