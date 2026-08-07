package com.pms.dto;

import lombok.Data;

@Data
public class ReportRequest {
    private Integer pondId;
    private String reportType = "DAILY";
    private String reportDate;
    private String provider;
}
