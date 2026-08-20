package com.pms.controller;

import com.pms.dto.BiomassTrendVO;
import com.pms.dto.BiomassCorrectionRequest;
import com.pms.dto.BiomassCorrectionVO;
import com.pms.dto.PondSetupRequest;
import com.pms.dto.PondSetupVO;
import com.pms.dto.PondVO;
import com.pms.service.BiomassService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/biomass")
@RequiredArgsConstructor
public class BiomassController {

    private final BiomassService biomassService;

    @GetMapping("/ponds")
    public ResponseEntity<Map<String, Object>> getPonds() {
        List<PondVO> data = biomassService.getPonds();
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("data", data);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/trend")
    public ResponseEntity<Map<String, Object>> getTrend(
            @RequestParam int pondId,
            @RequestParam(defaultValue = "30") int days) {
        BiomassTrendVO data = biomassService.getTrend(pondId, days);
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("data", data);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/setup")
    public ResponseEntity<Map<String, Object>> getSetup(@RequestParam int pondId) {
        PondSetupVO data = biomassService.getSetup(pondId);
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("data", data);
        return ResponseEntity.ok(result);
    }

    @PutMapping("/setup")
    public ResponseEntity<Map<String, Object>> saveSetup(@RequestBody PondSetupRequest request) {
        PondSetupVO data = biomassService.saveSetup(request);
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("data", data);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/record")
    public ResponseEntity<Map<String, Object>> getRecord(
            @RequestParam int pondId,
            @RequestParam String date) {
        BiomassCorrectionVO data = biomassService.getRecord(pondId, date);
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("data", data);
        return ResponseEntity.ok(result);
    }

    @PutMapping("/record")
    public ResponseEntity<Map<String, Object>> correct(@RequestBody BiomassCorrectionRequest request) {
        BiomassCorrectionVO data = biomassService.correct(request);
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("data", data);
        return ResponseEntity.ok(result);
    }
}
