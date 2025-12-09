package com.squadsync.backend.service;

import com.squadsync.backend.dto.AuthDto;
import com.squadsync.backend.dto.UserDto;
import com.squadsync.backend.model.User;
import com.squadsync.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    public AuthDto.AuthResponse register(AuthDto.RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already exists");
        }
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new RuntimeException("Username already exists");
        }

        var user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        if (request.getDiscordId() != null && !request.getDiscordId().isBlank()) {
            if (!request.getDiscordId().matches("\\d+")) {
                throw new RuntimeException("Discord ID must be numeric");
            }
            user.setDiscordId(request.getDiscordId());
        }
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setAvatarColor(request.getAvatarColor() != null ? request.getAvatarColor() : "#3b82f6");
        user.setRole("NORMAL");

        userRepository.save(user);

        var userDetails = org.springframework.security.core.userdetails.User.builder()
                .username(user.getEmail())
                .password(user.getPasswordHash())
                .roles("USER")
                .build();

        var jwtToken = jwtService.generateToken(userDetails);
        return new AuthDto.AuthResponse(jwtToken, mapToDto(user));
    }

    public AuthDto.AuthResponse login(AuthDto.LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()));

        var user = userRepository.findByEmail(request.getEmail())
                .orElseThrow();

        var userDetails = org.springframework.security.core.userdetails.User.builder()
                .username(user.getEmail())
                .password(user.getPasswordHash())
                .roles("USER")
                .build();

        var jwtToken = jwtService.generateToken(userDetails);
        return new AuthDto.AuthResponse(jwtToken, mapToDto(user));
    }

    public UserDto updateProfile(String email, UserDto updateDto) {
        var user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (updateDto.getDiscordId() != null) {
            String discordId = updateDto.getDiscordId().trim();
            if (!discordId.isEmpty() && !discordId.matches("\\d+")) {
                throw new RuntimeException("Discord ID must be numeric");
            }
            user.setDiscordId(discordId);
        }
        // Add other updatable fields here if needed in future

        userRepository.save(user);
        return mapToDto(user);
    }

    public UserDto getCurrentUser(String email) {
        var user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return mapToDto(user);
    }

    private UserDto mapToDto(User user) {
        UserDto dto = new UserDto();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setEmail(user.getEmail());
        dto.setDiscordId(user.getDiscordId());
        dto.setRole(user.getRole());
        dto.setAvatarColor(user.getAvatarColor());
        dto.setCreatedAt(user.getCreatedAt());
        return dto;
    }
}
