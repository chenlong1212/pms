package com.pms.dto;

import com.pms.entity.DeviceDataRecord;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class DeviceDataVO {

    private Long id;
    private String deviceId;
    private BigDecimal dox;
    private BigDecimal ph;
    private BigDecimal thw;
    private String collectTimeStr;
    private String createdAt;

    public static DeviceDataVO from(DeviceDataRecord record) {
        DeviceDataVO vo = new DeviceDataVO();
        vo.setId(record.getId());
        vo.setDeviceId(record.getDeviceId());
        vo.setDox(record.getDox());
        vo.setPh(record.getPh());
        vo.setThw(record.getThw());
        vo.setCollectTimeStr(record.getCollectTimeStr());
        vo.setCreatedAt(record.getCreatedAt() != null ? record.getCreatedAt().toString() : null);
        return vo;
    }
}
