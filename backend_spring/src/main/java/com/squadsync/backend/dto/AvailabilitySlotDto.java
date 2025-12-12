package com.squadsync.backend.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class AvailabilitySlotDto {
    private String id;
    private String userId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String gameId;
    private List<PreferenceDto> preferences;
}
