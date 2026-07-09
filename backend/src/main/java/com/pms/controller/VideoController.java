package com.pms.controller;

import com.pms.config.VideoProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/video")
@RequiredArgsConstructor
public class VideoController {

    private final VideoProperties videoProperties;

    @GetMapping("/stream-url")
    public ResponseEntity<Map<String, Object>> getStreamUrl() {
        Map<String, Object> result = new HashMap<>();
        String url = videoProperties.getStreamUrl();
        if (!StringUtils.hasText(url)) {
            result.put("code", 404);
            result.put("data", null);
            result.put("message", "未配置视频流地址");
            return ResponseEntity.ok(result);
        }
        result.put("code", 200);
        result.put("data", url);
        return ResponseEntity.ok(result);
    }
}
