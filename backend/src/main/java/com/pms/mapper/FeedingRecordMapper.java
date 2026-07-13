package com.pms.mapper;

import com.pms.entity.FeedingRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface FeedingRecordMapper {

    int insert(FeedingRecord record);

    int update(FeedingRecord record);

    int deleteById(@Param("id") long id);

    FeedingRecord findById(@Param("id") long id);

    FeedingRecord findByPondAndDate(@Param("pondId") int pondId,
                                    @Param("feedDate") String feedDate);

    List<FeedingRecord> findByPondId(@Param("pondId") int pondId,
                                     @Param("offset") int offset,
                                     @Param("limit") int limit);

    long countByPondId(@Param("pondId") int pondId);
}
