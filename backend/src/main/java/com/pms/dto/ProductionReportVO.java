package com.pms.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.pms.entity.ProductionReport;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ProductionReportVO {
    private Long id;
    private Integer pondId;
    private String pondName;
    private String reportType;
    private String startDate;
    private String endDate;
    private String title;
    private String status;
    private JsonNode dataSnapshot;
    private String content;
    private String modelProvider;
    private String modelName;
    private LocalDateTime createdAt;

    public static ProductionReportVO from(ProductionReport report, String pondName, JsonNode snapshot) {
        ProductionReportVO vo = new ProductionReportVO();
        vo.setId(report.getId());
        vo.setPondId(report.getPondId());
        vo.setPondName(pondName);
        vo.setReportType(report.getReportType());
        vo.setStartDate(report.getStartDate().toString());
        vo.setEndDate(report.getEndDate().toString());
        vo.setTitle(report.getTitle());
        vo.setStatus(report.getStatus());
        vo.setDataSnapshot(snapshot);
        vo.setContent(report.getContent());
        vo.setModelProvider(report.getModelProvider());
        vo.setModelName(report.getModelName());
        vo.setCreatedAt(report.getCreatedAt());
        return vo;
    }
}
