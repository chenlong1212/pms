package com.pms.service;

import com.pms.config.DeviceProperties;
import com.pms.dto.DeviceDataVO;
import com.pms.dto.PageResult;
import com.pms.entity.DeviceDataRecord;
import com.pms.mapper.DeviceDataRecordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DeviceDataService {

    private final DeviceDataRecordMapper mapper;
    private final DeviceProperties deviceProperties;
    private final DataCollectService dataCollectService;

    public DeviceDataVO getLatest() {
        String deviceId = deviceProperties.getApi().getDeviceId();
        DeviceDataRecord record = mapper.findLatestByDeviceId(deviceId);
        return record != null ? DeviceDataVO.from(record) : null;
    }

    public PageResult<DeviceDataVO> getHistory(int page, int size, String startTime, String endTime) {
        String deviceId = deviceProperties.getApi().getDeviceId();
        int offset = (page - 1) * size;
        long total = mapper.countHistory(deviceId, startTime, endTime);
        List<DeviceDataVO> records = mapper.findHistory(deviceId, startTime, endTime, offset, size)
                .stream()
                .map(DeviceDataVO::from)
                .toList();
        return new PageResult<>(records, total, page, size);
    }

    public List<DeviceDataVO> getTrend(int hours) {
        String deviceId = deviceProperties.getApi().getDeviceId();
        String startTime = dataCollectService.getStartTimeForHours(hours);
        return mapper.findTrendData(deviceId, startTime).stream()
                .map(DeviceDataVO::from)
                .toList();
    }
}
