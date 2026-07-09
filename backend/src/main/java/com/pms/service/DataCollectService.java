package com.pms.service;

import com.pms.config.DeviceProperties;
import com.pms.dto.ExternalDeviceResponse;
import com.pms.entity.DeviceDataRecord;
import com.pms.mapper.DeviceDataRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
public class DataCollectService {

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final RestTemplate restTemplate;
    private final DeviceProperties deviceProperties;
    private final DeviceDataRecordMapper mapper;

    public synchronized void collectData() {
        String deviceId = deviceProperties.getApi().getDeviceId();
        String url = deviceProperties.getApi().getBaseUrl()
                + "/getDeviceLatestData?deviceId=" + deviceId;

        try {
            log.info("开始采集设备数据, deviceId={}, url={}", deviceId, url);
            ExternalDeviceResponse response = restTemplate.getForObject(url, ExternalDeviceResponse.class);

            if (response == null) {
                log.warn("外部接口返回空数据, deviceId={}", deviceId);
                return;
            }

            String collectTimeStr = response.getCollectTimeStr();
            if (collectTimeStr != null) {
                collectTimeStr = collectTimeStr.trim();
            }

            if (response.getDox() == null || response.getPh() == null
                    || response.getThw() == null || collectTimeStr == null || collectTimeStr.isEmpty()) {
                log.warn("外部接口返回数据不完整: {}", response);
                return;
            }

            DeviceDataRecord latest = mapper.findLatestByDeviceId(deviceId);
            if (latest != null && collectTimeStr.equals(latest.getCollectTimeStr())) {
                log.info("数据未更新, 跳过入库, collectTimeStr={}", collectTimeStr);
                return;
            }

            if (mapper.countByDeviceIdAndCollectTimeStr(deviceId, collectTimeStr) > 0) {
                log.info("数据已存在, 跳过入库, collectTimeStr={}", collectTimeStr);
                return;
            }

            DeviceDataRecord record = new DeviceDataRecord();
            record.setDeviceId(deviceId);
            record.setDox(response.getDox());
            record.setPh(response.getPh());
            record.setThw(response.getThw());
            record.setCollectTimeStr(collectTimeStr);
            record.setCreatedAt(LocalDateTime.now());

            mapper.insert(record);
            log.info("数据采集成功, dox={}, ph={}, thw={}, collectTimeStr={}",
                    response.getDox(), response.getPh(), response.getThw(), collectTimeStr);

        } catch (DuplicateKeyException e) {
            log.info("数据已存在(唯一约束), 跳过入库");
        } catch (RestClientException e) {
            log.error("采集设备数据失败, deviceId={}, error={}", deviceId, e.getMessage(), e);
        } catch (Exception e) {
            log.error("处理采集数据异常, deviceId={}", deviceId, e);
        }
    }

    public String getStartTimeForHours(int hours) {
        return LocalDateTime.now().minusHours(hours).format(TIME_FORMATTER);
    }
}
