package com.pms.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class DeviceDataRecord {

    private Long id;
    private String deviceId;
    private BigDecimal dox;
    private BigDecimal ph;
    private BigDecimal thw;
    private String collectTimeStr;
    private LocalDateTime createdAt;
}
