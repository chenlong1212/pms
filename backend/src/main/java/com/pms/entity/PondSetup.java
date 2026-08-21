package com.pms.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class PondSetup {

    private Integer pondId;
    private LocalDate stockDate;
    private Integer initialFishCount;
    private BigDecimal initialWeightKg;
    private LocalDate harvestDate;
    private Integer finalFishCount;
    private BigDecimal finalWeightKg;
    private BigDecimal feedingRatio;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
