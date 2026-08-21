package com.pms.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class PondSetupVO {

    private Integer pondId;
    private String pondName;
    private String fishSpecies;
    private LocalDate stockDate;
    private Integer initialFishCount;
    private BigDecimal initialWeightKg;
    private LocalDate harvestDate;
    private Integer finalFishCount;
    private BigDecimal finalWeightKg;
    private BigDecimal feedingRatio;
}
