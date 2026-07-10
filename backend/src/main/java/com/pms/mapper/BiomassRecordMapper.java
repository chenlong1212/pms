package com.pms.mapper;

import com.pms.entity.BiomassRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface BiomassRecordMapper {

    List<BiomassRecord> findTrendData(@Param("pondId") int pondId,
                                      @Param("startDate") String startDate,
                                      @Param("endDate") String endDate);
}
