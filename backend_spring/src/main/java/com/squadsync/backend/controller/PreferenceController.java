package com.squadsync.backend.controller;

import com.squadsync.backend.dto.PreferenceDto;
import com.squadsync.backend.repository.UserRepository;
import com.squadsync.backend.service.PreferenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/preferences")
@RequiredArgsConstructor
public class PreferenceController {

    private final PreferenceService preferenceService;
    private final UserRepository userRepository;

    @GetMapping
    public ResponseEntity<List<PreferenceDto>> getMyPreferences(@AuthenticationPrincipal UserDetails userDetails) {
        String userId = userRepository.findByEmail(userDetails.getUsername()).orElseThrow().getId();
        return ResponseEntity.ok(preferenceService.getUserPreferences(userId));
    }

    @PostMapping
    public ResponseEntity<PreferenceDto> updatePreference(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody PreferenceDto preferenceDto) {
        String userId = userRepository.findByEmail(userDetails.getUsername()).orElseThrow().getId();
        return ResponseEntity.ok(preferenceService.updatePreference(userId, preferenceDto));
    }
}
