package com.pms.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pms.config.DeviceProperties;
import com.pms.config.LlmProperties;
import com.pms.dto.PageResult;
import com.pms.dto.ProductionReportVO;
import com.pms.dto.ReportPreviewVO;
import com.pms.dto.ReportRequest;
import com.pms.entity.BiomassRecord;
import com.pms.entity.DeviceDataRecord;
import com.pms.entity.FeedingRecord;
import com.pms.entity.Pond;
import com.pms.entity.ProductionReport;
import com.pms.llm.LlmClient;
import com.pms.mapper.BiomassRecordMapper;
import com.pms.mapper.DeviceDataRecordMapper;
import com.pms.mapper.FeedingRecordMapper;
import com.pms.mapper.PondMapper;
import com.pms.mapper.ProductionReportMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class ProductionReportService {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final PondMapper pondMapper;
    private final DeviceDataRecordMapper deviceDataRecordMapper;
    private final BiomassRecordMapper biomassRecordMapper;
    private final FeedingRecordMapper feedingRecordMapper;
    private final ProductionReportMapper reportMapper;
    private final DeviceProperties deviceProperties;
    private final LlmClient llmClient;
    private final LlmProperties llmProperties;
    private final ObjectMapper objectMapper;
    private final BiomassService biomassService;

    private String prompt;

    @PostConstruct
    void loadPrompt() throws Exception {
        prompt = StreamUtils.copyToString(
                new ClassPathResource("prompts/production-report.txt").getInputStream(),
                StandardCharsets.UTF_8);
    }

    public ReportPreviewVO preview(ReportRequest request) {
        validateRequest(request);
        Pond pond = requirePond(request.getPondId());
        LocalDate endDate = parseDate(request.getReportDate());
        String reportType = normalizeType(request.getReportType());
        LocalDate startDate = "WEEKLY".equals(reportType) ? endDate.minusDays(6) : endDate;

        ReportPreviewVO vo = new ReportPreviewVO();
        vo.setPondId(pond.getId());
        vo.setPondName(pond.getName());
        vo.setFishSpecies(pond.getFishSpecies());
        vo.setReportType(reportType);
        vo.setStartDate(startDate.toString());
        vo.setEndDate(endDate.toString());

        String startTime = LocalDateTime.of(startDate, LocalTime.MIN).format(DATE_TIME);
        String endTime = LocalDateTime.of(endDate, LocalTime.MAX).format(DATE_TIME);
        List<DeviceDataRecord> water = deviceDataRecordMapper.findRange(
                deviceProperties.getApi().getDeviceId(), startTime, endTime);
        vo.getWaterQuality().setSampleCount(water.size());
        vo.getWaterQuality().setDox(metric(water, DeviceDataRecord::getDox));
        vo.getWaterQuality().setPh(metric(water, DeviceDataRecord::getPh));
        vo.getWaterQuality().setTemperature(metric(water, DeviceDataRecord::getThw));

        // 报告周期内逐日取有效生物量：有手动校正记录用记录，否则用放养模型模拟值兜底
        List<BiomassRecord> biomassRecords = new ArrayList<>();
        for (LocalDate d = startDate; !d.isAfter(endDate); d = d.plusDays(1)) {
            BiomassRecord r = biomassService.getEffectiveBiomass(pond.getId(), d);
            if (r != null) biomassRecords.add(r);
        }
        BiomassRecord biomass = biomassRecords.isEmpty() ? null : biomassRecords.get(biomassRecords.size() - 1);
        if (biomass != null) {
            ReportPreviewVO.Biomass value = new ReportPreviewVO.Biomass();
            value.setFishCount(biomass.getFishCount());
            value.setAvgWeightKg(biomass.getAvgWeightKg());
            value.setBiomassKg(biomass.getBiomassKg());
            vo.setBiomass(value);
            BigDecimal recommendedTotal = biomassRecords.stream()
                    .map(this::recommendedFeed)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(1, RoundingMode.HALF_UP);
            vo.getFeeding().setRecommendedKg(recommendedTotal);
        }

        List<FeedingRecord> feeding = feedingRecordMapper.findRange(
                pond.getId(), startDate.format(DATE), endDate.format(DATE));
        BigDecimal actual = feeding.stream().map(FeedingRecord::getFeedTotalKg)
                .filter(v -> v != null).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (!feeding.isEmpty()) vo.getFeeding().setActualKg(actual.setScale(1, RoundingMode.HALF_UP));
        BigDecimal recommended = vo.getFeeding().getRecommendedKg();
        if (recommended != null && actual.signum() > 0) {
            vo.getFeeding().setDeviationPercent(actual.subtract(recommended)
                    .divide(recommended, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP));
        }

        assessQuality(vo, water);
        return vo;
    }

    public ProductionReportVO create(ReportRequest request) {
        ReportPreviewVO preview = preview(request);
        if (!preview.getDataQuality().isReady()) {
            throw new IllegalArgumentException("核心数据不完整或异常，请先根据数据质量提示处理后再生成");
        }
        String providerName = request.getProvider() == null || request.getProvider().isBlank()
                ? llmProperties.getDefaultProvider() : request.getProvider().trim().toLowerCase();
        LlmProperties.Provider provider = llmProperties.requireProvider(providerName);
        JsonNode snapshot = objectMapper.valueToTree(preview);
        String content = generateContent(providerName, snapshot);

        ProductionReport report = new ProductionReport();
        report.setPondId(preview.getPondId());
        report.setReportType(preview.getReportType());
        report.setStartDate(LocalDate.parse(preview.getStartDate()));
        report.setEndDate(LocalDate.parse(preview.getEndDate()));
        report.setTitle(preview.getPondName() + ("WEEKLY".equals(preview.getReportType()) ? "生产周报" : "生产日报"));
        report.setStatus("GENERATED");
        report.setDataSnapshot(snapshot.toString());
        report.setContent(content);
        report.setModelProvider(providerName);
        report.setModelName(provider.getModel());
        report.setCreatedAt(LocalDateTime.now(ZONE));
        report.setUpdatedAt(report.getCreatedAt());
        reportMapper.insert(report);
        return toVO(report);
    }

    public PageResult<ProductionReportVO> list(Integer pondId, int page, int size) {
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, Math.min(size, 50));
        List<ProductionReportVO> records = reportMapper
                .findPage(pondId, (safePage - 1) * safeSize, safeSize)
                .stream().map(this::toVO).toList();
        return new PageResult<>(records, reportMapper.count(pondId), safePage, safeSize);
    }

    public ProductionReportVO get(long id) {
        ProductionReport report = reportMapper.findById(id);
        if (report == null) throw new IllegalArgumentException("报告不存在: " + id);
        return toVO(report);
    }

    private String generateContent(String providerName, JsonNode snapshot) {
        String lastContent = "";
        String lastFinishReason = "";
        for (int attempt = 1; attempt <= 2; attempt++) {
            ArrayNode messages = objectMapper.createArrayNode();
            messages.add(objectMapper.createObjectNode().put("role", "system").put("content", prompt));
            String instruction = attempt == 1
                    ? "请根据以下数据快照一次性生成完整报告：\n"
                    : "上一次返回的报告不完整。请重新生成，不要只输出标题，必须一次性写完五个章节：\n";
            messages.add(objectMapper.createObjectNode().put("role", "user")
                    .put("content", instruction + snapshot.toPrettyString()));
            JsonNode response = llmClient.chat(providerName, messages, objectMapper.createArrayNode());
            lastContent = response.path("choices").path(0).path("message").path("content").asText("").trim();
            lastFinishReason = response.path("choices").path(0).path("finish_reason").asText("");
            if (isCompleteReport(lastContent)) {
                return lastContent;
            }
        }
        if ("length".equals(lastFinishReason)) {
            throw new IllegalStateException("大模型连续两次输出被长度限制截断，未生成完整报告");
        }
        if (lastContent.isBlank()) {
            throw new IllegalStateException("大模型连续两次未返回报告正文");
        }
        throw new IllegalStateException("大模型连续两次返回不完整报告，已停止保存，请重试");
    }

    private boolean isCompleteReport(String content) {
        if (content == null || content.length() < 200) {
            return false;
        }
        return content.contains("一、生产概况")
                && content.contains("二、水质统计与异常")
                && content.contains("三、生物量和投喂分析")
                && content.contains("四、风险等级与问题清单")
                && content.contains("五、下一周期操作建议");
    }

    private void assessQuality(ReportPreviewVO vo, List<DeviceDataRecord> water) {
        var quality = vo.getDataQuality();
        if (water.isEmpty()) quality.getMissingFields().add("水质数据");
        if (vo.getBiomass() == null) quality.getMissingFields().add("生物量数据");
        if (vo.getFeeding().getActualKg() == null) {
            quality.getMissingFields().add("投喂记录");
            quality.getWarnings().add("投喂记录缺失，无法评价实际投喂偏差");
        }
        ReportPreviewVO.Metric dox = vo.getWaterQuality().getDox();
        ReportPreviewVO.Metric ph = vo.getWaterQuality().getPh();
        ReportPreviewVO.Metric temperature = vo.getWaterQuality().getTemperature();
        if (dox != null && dox.getMin().compareTo(BigDecimal.ZERO) < 0)
            quality.getWarnings().add("溶解氧存在小于 0 mg/L 的无效数据");
        if (ph != null && (ph.getMin().compareTo(BigDecimal.ZERO) < 0 || ph.getMax().compareTo(new BigDecimal("14")) > 0))
            quality.getWarnings().add("pH 存在超出 0–14 合理范围的数据");
        if (temperature != null && (temperature.getMin().compareTo(new BigDecimal("-5")) < 0 || temperature.getMax().compareTo(new BigDecimal("50")) > 0))
            quality.getWarnings().add("水温存在超出 -5–50 ℃ 合理范围的数据");
        if (dox != null && dox.getMin().compareTo(new BigDecimal("5")) < 0)
            quality.getWarnings().add("统计周期内溶解氧最低值低于 5 mg/L，请关注低氧时段");
        if (ph != null && (ph.getMin().compareTo(new BigDecimal("6.5")) < 0 || ph.getMax().compareTo(new BigDecimal("9")) > 0))
            quality.getWarnings().add("统计周期内 pH 超出 6.5–9.0 关注范围");
        boolean invalid = quality.getWarnings().stream()
                .anyMatch(v -> v.contains("合理范围") || v.contains("无效数据"));
        quality.setReady(water.size() > 0 && vo.getBiomass() != null && !invalid);
    }

    private ReportPreviewVO.Metric metric(List<DeviceDataRecord> records,
                                          Function<DeviceDataRecord, BigDecimal> extractor) {
        List<BigDecimal> values = records.stream().map(extractor).filter(v -> v != null).toList();
        if (values.isEmpty()) return null;
        ReportPreviewVO.Metric metric = new ReportPreviewVO.Metric();
        metric.setMin(values.stream().min(BigDecimal::compareTo).orElseThrow());
        metric.setMax(values.stream().max(BigDecimal::compareTo).orElseThrow());
        metric.setAvg(values.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(values.size()), 2, RoundingMode.HALF_UP));
        return metric;
    }

    private BigDecimal recommendedFeed(BiomassRecord biomass) {
        BigDecimal weight = biomass.getAvgWeightKg();
        BigDecimal rate = weight == null ? new BigDecimal("0.025")
                : weight.compareTo(new BigDecimal("0.1")) < 0 ? new BigDecimal("0.06")
                : weight.compareTo(new BigDecimal("0.5")) < 0 ? new BigDecimal("0.04")
                : weight.compareTo(BigDecimal.ONE) < 0 ? new BigDecimal("0.025")
                : new BigDecimal("0.015");
        return biomass.getBiomassKg().multiply(rate).setScale(1, RoundingMode.HALF_UP);
    }

    private ProductionReportVO toVO(ProductionReport report) {
        Pond pond = requirePond(report.getPondId());
        try {
            return ProductionReportVO.from(report, pond.getName(), objectMapper.readTree(report.getDataSnapshot()));
        } catch (Exception e) {
            throw new IllegalStateException("报告快照解析失败: " + report.getId(), e);
        }
    }

    private void validateRequest(ReportRequest request) {
        if (request == null || request.getPondId() == null) throw new IllegalArgumentException("请选择池塘");
        parseDate(request.getReportDate());
        normalizeType(request.getReportType());
    }

    private Pond requirePond(int pondId) {
        Pond pond = pondMapper.findById(pondId);
        if (pond == null) throw new IllegalArgumentException("鱼塘不存在: " + pondId);
        return pond;
    }

    private LocalDate parseDate(String value) {
        LocalDate date = value == null || value.isBlank() ? LocalDate.now(ZONE) : LocalDate.parse(value, DATE);
        if (date.isAfter(LocalDate.now(ZONE))) throw new IllegalArgumentException("报告日期不能晚于今天");
        return date;
    }

    private String normalizeType(String type) {
        String value = type == null ? "DAILY" : type.trim().toUpperCase();
        if (!List.of("DAILY", "WEEKLY").contains(value)) throw new IllegalArgumentException("仅支持日报或周报");
        return value;
    }
}
