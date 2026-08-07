package com.pms.mapper;

import com.pms.entity.ProductionReport;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ProductionReportMapper {
    int insert(ProductionReport report);
    ProductionReport findById(@Param("id") long id);
    List<ProductionReport> findPage(@Param("pondId") Integer pondId,
                                    @Param("offset") int offset,
                                    @Param("limit") int limit);
    long count(@Param("pondId") Integer pondId);
}
