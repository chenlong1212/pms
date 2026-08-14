package com.pms.entity;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class ProductionReport {
    private Long id;
    private Integer pondId;
    private String reportType;
    private LocalDate startDate;
    private LocalDate endDate;
    private String title;
    private String status;
    private String dataSnapshot;
    private String content;
    private String modelProvider;
    private String modelName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
