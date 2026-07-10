package com.pms.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class BiomassRecord {

    private Long id;
    private Integer pondId;
    private LocalDate recordDate;
    private BigDecimal biomassKg;
    private Integer fishCount;
    private BigDecimal avgWeightKg;
    private LocalDateTime createdAt;
}
