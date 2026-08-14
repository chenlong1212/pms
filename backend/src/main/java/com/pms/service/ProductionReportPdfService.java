package com.pms.service;

import com.pms.dto.ProductionReportVO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
public class ProductionReportPdfService {
    private static final float MARGIN = 54;
    private static final float BODY_SIZE = 10.5f;
    private static final float LINE_HEIGHT = 17f;

    public byte[] render(ProductionReportVO report) {
        try (PDDocument document = new PDDocument()) {
            PDFont font = loadChineseFont(document);
            List<String> lines = new ArrayList<>();
            lines.add(report.getTitle());
            lines.add("池塘：" + report.getPondName());
            lines.add("统计周期：" + report.getStartDate() + " 至 " + report.getEndDate());
            lines.add("生成时间：" + report.getCreatedAt());
            lines.add("");
            for (String raw : report.getContent().replace("\r", "").split("\n")) {
                String clean = raw.replaceFirst("^#{1,6}\\s*", "")
                        .replace("**", "").replace("`", "").replace("> ", "");
                lines.addAll(wrap(clean, font, BODY_SIZE, PDRectangle.A4.getWidth() - MARGIN * 2));
            }

            PDPage page = null;
            PDPageContentStream stream = null;
            float y = 0;
            try {
                for (int i = 0; i < lines.size(); i++) {
                    if (stream == null || y < MARGIN + LINE_HEIGHT) {
                        if (stream != null) stream.close();
                        page = new PDPage(PDRectangle.A4);
                        document.addPage(page);
                        stream = new PDPageContentStream(document, page);
                        y = page.getMediaBox().getHeight() - MARGIN;
                    }
                    String line = lines.get(i);
                    float size = i == 0 ? 18f : (line.matches("^[一二三四五]、.*") ? 13f : BODY_SIZE);
                    stream.beginText();
                    stream.setFont(font, size);
                    stream.newLineAtOffset(MARGIN, y);
                    stream.showText(line);
                    stream.endText();
                    y -= i == 0 ? 28f : LINE_HEIGHT;
                }
            } finally {
                if (stream != null) stream.close();
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            document.save(output);
            return output.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("生成 PDF 失败: " + e.getMessage(), e);
        }
    }

    private PDFont loadChineseFont(PDDocument document) throws IOException {
        String[] candidates = {
                "/System/Library/Fonts/Supplemental/Arial Unicode.ttf",
                "C:/Windows/Fonts/msyh.ttf",
                "C:/Windows/Fonts/simhei.ttf",
                "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc",
                "/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc"
        };
        for (String path : candidates) {
            File file = new File(path);
            if (file.isFile() && path.toLowerCase().endsWith(".ttf")) {
                return PDType0Font.load(document, file);
            }
        }
        throw new IllegalStateException("未找到可用中文字体，请安装微软雅黑或 Noto Sans CJK");
    }

    private List<String> wrap(String text, PDFont font, float size, float width) throws IOException {
        List<String> result = new ArrayList<>();
        if (text.isBlank()) {
            result.add("");
            return result;
        }
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            String candidate = line.toString() + ch;
            if (font.getStringWidth(candidate) / 1000f * size > width && !line.isEmpty()) {
                result.add(line.toString());
                line.setLength(0);
            }
            line.append(ch);
        }
        if (!line.isEmpty()) result.add(line.toString());
        return result;
    }
}
