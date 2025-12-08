package com.squadsync.backend.dto;

import lombok.Data;

@Data
public class PreferenceDto {
    private String id;
    private String userId;
    private String gameId;
    private int weight;
}
