package com.pms.mapper;

import com.pms.entity.BiomassRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Mapper
public interface BiomassRecordMapper {

    List<BiomassRecord> findTrendData(@Param("pondId") int pondId,
                                      @Param("startDate") String startDate,
                                      @Param("endDate") String endDate);

    BiomassRecord findByPondAndDate(@Param("pondId") int pondId,
                                    @Param("recordDate") String recordDate);

    BiomassRecord findLatestBefore(@Param("pondId") int pondId,
                                   @Param("recordDate") String recordDate);

    BiomassRecord findLatestByPondId(@Param("pondId") int pondId);

    int upsertDailyMetric(@Param("pondId") int pondId,
                          @Param("recordDate") String recordDate,
                          @Param("fishCount") int fishCount,
                          @Param("avgWeightKg") BigDecimal avgWeightKg,
                          @Param("now") LocalDateTime now);
}
