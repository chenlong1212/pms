package com.pms.mapper;

import com.pms.entity.DeviceDataRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DeviceDataRecordMapper {

    int insert(DeviceDataRecord record);

    int countByDeviceIdAndCollectTimeStr(@Param("deviceId") String deviceId,
                                         @Param("collectTimeStr") String collectTimeStr);

    DeviceDataRecord findLatestByDeviceId(@Param("deviceId") String deviceId);

    List<DeviceDataRecord> findHistory(@Param("deviceId") String deviceId,
                                     @Param("startTime") String startTime,
                                     @Param("endTime") String endTime,
                                     @Param("offset") int offset,
                                     @Param("limit") int limit);

    long countHistory(@Param("deviceId") String deviceId,
                      @Param("startTime") String startTime,
                      @Param("endTime") String endTime);

    List<DeviceDataRecord> findTrendData(@Param("deviceId") String deviceId,
                                         @Param("startTime") String startTime);

    List<DeviceDataRecord> findRange(@Param("deviceId") String deviceId,
                                     @Param("startTime") String startTime,
                                     @Param("endTime") String endTime);
}
