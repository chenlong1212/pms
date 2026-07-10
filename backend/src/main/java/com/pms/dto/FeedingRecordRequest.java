package com.pms.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class FeedingRecordRequest {

    private Integer pondId;
    private String feedDate;
    private BigDecimal feedTotalKg;
    private String remark;
}
