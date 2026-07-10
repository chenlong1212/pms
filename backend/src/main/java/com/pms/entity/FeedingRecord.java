package com.pms.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class FeedingRecord {

    private Long id;
    private Integer pondId;
    private LocalDate feedDate;
    private BigDecimal feedTotalKg;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
