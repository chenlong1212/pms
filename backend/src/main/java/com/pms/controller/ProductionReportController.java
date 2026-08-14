package com.pms.controller;

import com.pms.dto.PageResult;
import com.pms.dto.ProductionReportVO;
import com.pms.dto.ReportPreviewVO;
import com.pms.dto.ReportRequest;
import com.pms.service.ProductionReportPdfService;
import com.pms.service.ProductionReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ProductionReportController {
    private final ProductionReportService reportService;
    private final ProductionReportPdfService pdfService;

    @PostMapping("/preview")
    public ResponseEntity<Map<String, Object>> preview(@RequestBody ReportRequest request) {
        return ok(reportService.preview(request));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@RequestBody ReportRequest request) {
        return ok(reportService.create(request));
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @RequestParam(required = false) Integer pondId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        PageResult<ProductionReportVO> data = reportService.list(pondId, page, size);
        return ok(data);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> detail(@PathVariable long id) {
        return ok(reportService.get(id));
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable long id) {
        ProductionReportVO report = reportService.get(id);
        byte[] body = pdfService.render(report);
        String filename = report.getTitle() + "-" + report.getEndDate() + ".pdf";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(filename, StandardCharsets.UTF_8).build().toString())
                .body(body);
    }

    private ResponseEntity<Map<String, Object>> ok(Object data) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", 200);
        body.put("data", data);
        body.put("message", "success");
        return ResponseEntity.ok(body);
    }
}
