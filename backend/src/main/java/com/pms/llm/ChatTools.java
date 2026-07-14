package com.pms.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pms.dto.DeviceDataVO;
import com.pms.dto.FeedingRecordVO;
import com.pms.dto.FeedingStrategyVO;
import com.pms.dto.PageResult;
import com.pms.service.BiomassService;
import com.pms.service.DeviceDataService;
import com.pms.service.FeedingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatTools {

    private final DeviceDataService deviceDataService;
    private final BiomassService biomassService;
    private final FeedingService feedingService;
    private final ObjectMapper objectMapper;

    public ArrayNode toolDefinitions() {
        ArrayNode tools = objectMapper.createArrayNode();
        tools.add(fn("list_ponds", "列出系统中的所有池塘（ID、名称、鱼种）", emptyProps()));
        tools.add(fn("get_latest_water_quality",
                "获取当前最新水质数据：溶氧 dox、酸碱度 ph、水温 thw、采集时间", emptyProps()));

        ObjectNode trendProps = objectMapper.createObjectNode();
        trendProps.putObject("hours")
                .put("type", "integer")
                .put("description", "回溯小时数，常用 6/12/24/48，默认 24");
        tools.add(fn("get_water_quality_trend", "获取最近若干小时的水质趋势数据点",
                schema(trendProps, null)));

        ObjectNode historyProps = objectMapper.createObjectNode();
        historyProps.putObject("startTime").put("type", "string")
                .put("description", "开始时间，格式 yyyy-MM-dd HH:mm:ss");
        historyProps.putObject("endTime").put("type", "string")
                .put("description", "结束时间，格式 yyyy-MM-dd HH:mm:ss");
        historyProps.putObject("page").put("type", "integer").put("description", "页码，默认 1");
        historyProps.putObject("size").put("type", "integer").put("description", "每页条数，默认 20，最大 50");
        tools.add(fn("get_water_quality_history", "按起止时间查询水质历史记录（分页）",
                schema(historyProps, List.of("startTime", "endTime"))));

        ObjectNode biomassProps = objectMapper.createObjectNode();
        biomassProps.putObject("pondId").put("type", "integer").put("description", "池塘 ID");
        biomassProps.putObject("days").put("type", "integer")
                .put("description", "回溯天数，常用 7/30/90，默认 7");
        tools.add(fn("get_biomass_trend", "获取指定池塘最近若干天的生物量/尾数/均重趋势",
                schema(biomassProps, List.of("pondId"))));

        ObjectNode feedingProps = objectMapper.createObjectNode();
        feedingProps.putObject("pondId").put("type", "integer").put("description", "池塘 ID");
        feedingProps.putObject("page").put("type", "integer").put("description", "页码，默认 1");
        feedingProps.putObject("size").put("type", "integer").put("description", "每页条数，默认 20");
        tools.add(fn("get_feeding_records", "获取指定池塘的投喂记录（按日期倒序分页）",
                schema(feedingProps, List.of("pondId"))));

        ObjectNode strategyProps = objectMapper.createObjectNode();
        strategyProps.putObject("pondId").put("type", "integer").put("description", "池塘 ID");
        tools.add(fn("get_feeding_strategy", "获取指定池塘当日投喂策略建议（基于当日生物量）",
                schema(strategyProps, List.of("pondId"))));

        return tools;
    }

    public String execute(String name, JsonNode args) {
        try {
            Object result = switch (name) {
                case "list_ponds" -> biomassService.getPonds();
                case "get_latest_water_quality" -> {
                    DeviceDataVO latest = deviceDataService.getLatest();
                    yield latest != null ? latest : Map.of("message", "暂无水质数据");
                }
                case "get_water_quality_trend" -> {
                    int hours = Math.max(1, Math.min(intArg(args, "hours", 24), 168));
                    yield summarizeTrend(deviceDataService.getTrend(hours), hours);
                }
                case "get_water_quality_history" -> {
                    String start = textArg(args, "startTime");
                    String end = textArg(args, "endTime");
                    int page = intArg(args, "page", 1);
                    int size = Math.min(intArg(args, "size", 20), 50);
                    yield deviceDataService.getHistory(page, size, start, end);
                }
                case "get_biomass_trend" -> {
                    int pondId = requireInt(args, "pondId");
                    int days = Math.max(1, Math.min(intArg(args, "days", 7), 365));
                    yield biomassService.getTrend(pondId, days);
                }
                case "get_feeding_records" -> {
                    int pondId = requireInt(args, "pondId");
                    int page = intArg(args, "page", 1);
                    int size = Math.min(intArg(args, "size", 20), 50);
                    PageResult<FeedingRecordVO> pageResult = feedingService.getRecords(pondId, page, size);
                    yield pageResult;
                }
                case "get_feeding_strategy" -> {
                    int pondId = requireInt(args, "pondId");
                    FeedingStrategyVO strategy = feedingService.getStrategy(pondId);
                    yield strategy;
                }
                default -> Map.of("error", "未知工具: " + name);
            };
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            log.warn("Tool {} failed: {}", name, e.getMessage());
            try {
                return objectMapper.writeValueAsString(Map.of("error", e.getMessage()));
            } catch (Exception ex) {
                return "{\"error\":\"工具执行失败\"}";
            }
        }
    }

    private Map<String, Object> summarizeTrend(List<DeviceDataVO> trend, int hours) {
        Map<String, Object> summary = new HashMap<>();
        summary.put("hours", hours);
        summary.put("pointCount", trend.size());
        if (trend.isEmpty()) {
            summary.put("message", "该时间范围内暂无水质数据");
            return summary;
        }
        int step = Math.max(1, trend.size() / 24);
        List<DeviceDataVO> sampled = new ArrayList<>();
        for (int i = 0; i < trend.size(); i += step) {
            sampled.add(trend.get(i));
        }
        DeviceDataVO last = trend.get(trend.size() - 1);
        if (sampled.get(sampled.size() - 1) != last) {
            sampled.add(last);
        }
        summary.put("samples", sampled);
        summary.put("latest", last);
        return summary;
    }

    private ObjectNode fn(String name, String description, ObjectNode parameters) {
        ObjectNode tool = objectMapper.createObjectNode();
        tool.put("type", "function");
        ObjectNode function = tool.putObject("function");
        function.put("name", name);
        function.put("description", description);
        function.set("parameters", parameters);
        return tool;
    }

    private ObjectNode emptyProps() {
        return schema(objectMapper.createObjectNode(), null);
    }

    private ObjectNode schema(ObjectNode properties, List<String> required) {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        schema.set("properties", properties);
        if (required != null && !required.isEmpty()) {
            ArrayNode req = schema.putArray("required");
            required.forEach(req::add);
        }
        return schema;
    }

    private static int intArg(JsonNode args, String field, int defaultValue) {
        if (args == null || !args.has(field) || args.get(field).isNull()) {
            return defaultValue;
        }
        return args.get(field).asInt(defaultValue);
    }

    private static int requireInt(JsonNode args, String field) {
        if (args == null || !args.has(field) || args.get(field).isNull()) {
            throw new IllegalArgumentException("缺少参数: " + field);
        }
        return args.get(field).asInt();
    }

    private static String textArg(JsonNode args, String field) {
        if (args == null || !args.has(field) || args.get(field).isNull()) {
            return null;
        }
        String value = args.get(field).asText();
        return value != null && !value.isBlank() ? value.trim() : null;
    }
}
