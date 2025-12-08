package com.squadsync.backend.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class AvailabilitySlotDto {
    private String id;
    private String userId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String gameId;
    private java.util.List<PreferenceDto> preferences;
}
