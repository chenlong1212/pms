package com.pms.service;

import com.pms.dto.ProductionReportVO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductionReportPdfServiceTest {

    @Test
    void rendersChineseReportAsReadablePdf() throws Exception {
        ProductionReportVO report = new ProductionReportVO();
        report.setTitle("一号塘生产日报");
        report.setPondName("一号塘");
        report.setStartDate("2026-07-28");
        report.setEndDate("2026-07-28");
        report.setCreatedAt(LocalDateTime.of(2026, 7, 28, 16, 30));
        report.setContent("""
                ## 一、生产概况
                今日水质、生物量与投喂数据已完成汇总。

                ## 二、水质统计与异常
                溶解氧均值 6.80 mg/L，pH 均值 7.42，水温均值 27.10 ℃。

                ## 三、生物量和投喂分析
                生物量 4320 kg，实际投喂 108 kg。

                ## 四、风险等级与问题清单
                未发现明显数据异常。

                ## 五、下一周期操作建议
                持续监测凌晨溶解氧变化，并完整记录投喂量。
                """);

        byte[] pdf = new ProductionReportPdfService().render(report);
        assertTrue(pdf.length > 1000);
        Path output = Path.of("target", "production-report-preview.pdf");
        Files.createDirectories(output.getParent());
        Files.write(output, pdf);

        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertEquals(1, document.getNumberOfPages());
        }
    }
}
