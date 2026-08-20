package com.pms.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class BiomassCorrectionRequest {
    private Integer pondId;
    private String recordDate;
    private Integer fishCount;
    private BigDecimal avgWeightKg;
}
