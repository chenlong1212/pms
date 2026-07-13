package com.pms.dto;

import com.pms.entity.FeedingRecord;
import lombok.Data;

import java.time.format.DateTimeFormatter;

@Data
public class FeedingRecordVO {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private Long id;
    private Integer pondId;
    private String feedDate;
    private String feedTotalKg;
    private String remark;

    public static FeedingRecordVO from(FeedingRecord record) {
        FeedingRecordVO vo = new FeedingRecordVO();
        vo.setId(record.getId());
        vo.setPondId(record.getPondId());
        vo.setFeedDate(record.getFeedDate().format(DATE_FORMATTER));
        vo.setFeedTotalKg(record.getFeedTotalKg().stripTrailingZeros().toPlainString());
        vo.setRemark(record.getRemark());
        return vo;
    }
}
