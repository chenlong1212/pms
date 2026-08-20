package com.pms.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class FeedingStrategySetting {
    private Integer pondId;
    private BigDecimal dailyRate;
    private Integer mealsPerDay;
    private String feedTime1;
    private String feedTime2;
    private String feedTime3;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
