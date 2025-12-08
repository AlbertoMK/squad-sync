package com.squadsync.backend.dto;

import lombok.Data;
import java.time.Instant;
import java.util.List;

@Data
public class AvailabilitySlotDto {
    private String id;
    private String userId;
    private Instant startTime;
    private Instant endTime;
    private String gameId;
    private List<PreferenceDto> preferences;
}
