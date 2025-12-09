package com.squadsync.backend.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class AvailabilitySlotDto {
    private String id;
    private String userId;
    private java.time.LocalDateTime startTime;
    private java.time.LocalDateTime endTime;
    private String gameId;
    private List<PreferenceDto> preferences;
}
