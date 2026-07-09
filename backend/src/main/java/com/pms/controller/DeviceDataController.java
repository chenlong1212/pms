package com.pms.controller;

import com.pms.dto.DeviceDataVO;
import com.pms.dto.PageResult;
import com.pms.service.DeviceDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/device")
@RequiredArgsConstructor
public class DeviceDataController {

    private final DeviceDataService deviceDataService;

    @GetMapping("/latest")
    public ResponseEntity<Map<String, Object>> getLatest() {
        DeviceDataVO data = deviceDataService.getLatest();
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("data", data);
        result.put("message", data != null ? "success" : "暂无数据");
        return ResponseEntity.ok(result);
    }

    @GetMapping("/history")
    public ResponseEntity<Map<String, Object>> getHistory(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {
        PageResult<DeviceDataVO> data = deviceDataService.getHistory(page, size, startTime, endTime);
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("data", data);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/trend")
    public ResponseEntity<Map<String, Object>> getTrend(
            @RequestParam(defaultValue = "24") int hours) {
        List<DeviceDataVO> data = deviceDataService.getTrend(hours);
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("data", data);
        return ResponseEntity.ok(result);
    }
}
