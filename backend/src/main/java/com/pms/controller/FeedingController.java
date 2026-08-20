package com.pms.controller;

import com.pms.dto.FeedingRecordRequest;
import com.pms.dto.FeedingRecordVO;
import com.pms.dto.FeedingStrategyVO;
import com.pms.dto.FeedingStrategyRequest;
import com.pms.dto.PageResult;
import com.pms.dto.PondVO;
import com.pms.service.FeedingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/feeding")
@RequiredArgsConstructor
public class FeedingController {

    private final FeedingService feedingService;

    @GetMapping("/ponds")
    public ResponseEntity<Map<String, Object>> getPonds() {
        List<PondVO> data = feedingService.getPonds();
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("data", data);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/records")
    public ResponseEntity<Map<String, Object>> getRecords(
            @RequestParam int pondId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageResult<FeedingRecordVO> data = feedingService.getRecords(pondId, page, size);
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("data", data);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/records")
    public ResponseEntity<Map<String, Object>> create(@RequestBody FeedingRecordRequest request) {
        FeedingRecordVO data = feedingService.create(request);
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("data", data);
        return ResponseEntity.ok(result);
    }

    @PutMapping("/records/{id}")
    public ResponseEntity<Map<String, Object>> update(
            @PathVariable long id,
            @RequestBody FeedingRecordRequest request) {
        FeedingRecordVO data = feedingService.update(id, request);
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("data", data);
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/records/{id}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable long id) {
        feedingService.delete(id);
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("data", null);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/strategy")
    public ResponseEntity<Map<String, Object>> getStrategy(@RequestParam int pondId) {
        FeedingStrategyVO data = feedingService.getStrategy(pondId);
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("data", data);
        return ResponseEntity.ok(result);
    }

    @PutMapping("/strategy/{pondId}")
    public ResponseEntity<Map<String, Object>> updateStrategy(
            @PathVariable int pondId,
            @RequestBody FeedingStrategyRequest request) {
        FeedingStrategyVO data = feedingService.updateStrategy(pondId, request);
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("data", data);
        return ResponseEntity.ok(result);
    }
}
