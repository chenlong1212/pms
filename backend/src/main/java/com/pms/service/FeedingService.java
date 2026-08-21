package com.pms.service;

import com.pms.dto.FeedingRecordRequest;
import com.pms.dto.FeedingRecordVO;
import com.pms.dto.FeedingStrategyVO;
import com.pms.dto.FeedingStrategyRequest;
import com.pms.dto.PageResult;
import com.pms.dto.PondVO;
import com.pms.entity.BiomassRecord;
import com.pms.entity.FeedingRecord;
import com.pms.entity.FeedingStrategySetting;
import com.pms.entity.Pond;
import com.pms.mapper.BiomassRecordMapper;
import com.pms.mapper.FeedingRecordMapper;
import com.pms.mapper.FeedingStrategyMapper;
import com.pms.mapper.PondMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class FeedingService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final int REMARK_MAX_LENGTH = 256;

    private final PondMapper pondMapper;
    private final FeedingRecordMapper feedingRecordMapper;
    private final BiomassRecordMapper biomassRecordMapper;
    private final FeedingStrategyMapper feedingStrategyMapper;

    public List<PondVO> getPonds() {
        return pondMapper.findAll().stream()
                .map(PondVO::from)
                .toList();
    }

    public PageResult<FeedingRecordVO> getRecords(int pondId, int page, int size) {
        validatePond(pondId);
        int offset = (page - 1) * size;
        long total = feedingRecordMapper.countByPondId(pondId);
        List<FeedingRecordVO> records = feedingRecordMapper.findByPondId(pondId, offset, size).stream()
                .map(FeedingRecordVO::from)
                .toList();
        return new PageResult<>(records, total, page, size);
    }

    public FeedingRecordVO create(FeedingRecordRequest request) {
        validatePond(request.getPondId());
        FeedingRecord record = buildRecordFromRequest(request);
        FeedingRecord existing = feedingRecordMapper.findByPondAndDate(
                request.getPondId(), record.getFeedDate().format(DATE_FORMATTER));
        if (existing != null) {
            throw new IllegalArgumentException("该鱼塘当日已有投喂记录，请编辑原记录");
        }
        LocalDateTime now = LocalDateTime.now(ZONE);
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        feedingRecordMapper.insert(record);
        return FeedingRecordVO.from(record);
    }

    public FeedingRecordVO update(long id, FeedingRecordRequest request) {
        FeedingRecord existing = requireRecord(id);
        if (!existing.getPondId().equals(request.getPondId())) {
            throw new IllegalArgumentException("不能修改记录所属鱼塘");
        }
        validatePond(request.getPondId());
        FeedingRecord record = buildRecordFromRequest(request);
        FeedingRecord duplicate = feedingRecordMapper.findByPondAndDate(
                request.getPondId(), record.getFeedDate().format(DATE_FORMATTER));
        if (duplicate != null && !duplicate.getId().equals(id)) {
            throw new IllegalArgumentException("该鱼塘当日已有投喂记录");
        }
        record.setId(id);
        record.setUpdatedAt(LocalDateTime.now(ZONE));
        feedingRecordMapper.update(record);
        return FeedingRecordVO.from(feedingRecordMapper.findById(id));
    }

    public void delete(long id) {
        requireRecord(id);
        feedingRecordMapper.deleteById(id);
    }

    public FeedingStrategyVO getStrategy(int pondId) {
        Pond pond = validatePond(pondId);
        FeedingStrategySetting setting = getOrCreateStrategySetting(pondId);
        LocalDate today = LocalDate.now(ZONE);
        String todayStr = today.format(DATE_FORMATTER);
        BiomassRecord biomass = biomassRecordMapper.findByPondAndDate(pondId, todayStr);

        FeedingStrategyVO vo = new FeedingStrategyVO();
        vo.setPondId(pond.getId());
        vo.setPondName(pond.getName());
        vo.setFishSpecies(pond.getFishSpecies());
        vo.setDailyRate(setting.getDailyRate());
        vo.setMealsPerDay(setting.getMealsPerDay());
        vo.setFeedTimes(settingTimes(setting));

        if (biomass == null || biomass.getBiomassKg() == null) {
            vo.setAvailable(false);
            vo.setMealAmountsKg(List.of());
            vo.setPlans(List.of());
            vo.setSummaryText("暂无当日生物量数据，无法计算投喂策略。请先确保今日生物量已录入。");
            return vo;
        }

        BigDecimal biomassKg = biomass.getBiomassKg();
        BigDecimal avgWeightKg = biomass.getAvgWeightKg();
        BigDecimal dailyRate = setting.getDailyRate();
        BigDecimal dailyFeedKg = biomassKg.multiply(dailyRate).setScale(1, RoundingMode.HALF_UP);

        vo.setBiomassKg(biomassKg);
        vo.setAvgWeightKg(avgWeightKg);
        vo.setDailyRate(dailyRate);
        vo.setDailyFeedKg(dailyFeedKg);
        vo.setMealAmountsKg(distributeAmounts(dailyFeedKg, setting.getMealsPerDay()));
        vo.setPlans(buildPlans(dailyFeedKg));
        vo.setAvailable(true);
        vo.setSummaryText(buildSummaryText(pond, biomassKg, avgWeightKg, dailyRate, dailyFeedKg,
                vo.getFeedTimes(), vo.getMealAmountsKg()));
        return vo;
    }

    public FeedingStrategyVO updateStrategy(int pondId, FeedingStrategyRequest request) {
        validatePond(pondId);
        if (request == null || request.getDailyRate() == null) {
            throw new IllegalArgumentException("投喂比例不能为空");
        }
        BigDecimal minRate = new BigDecimal("0.0100");
        BigDecimal maxRate = new BigDecimal("0.0500");
        if (request.getDailyRate().compareTo(minRate) < 0 || request.getDailyRate().compareTo(maxRate) > 0) {
            throw new IllegalArgumentException("投喂比例必须在 1%–5% 之间");
        }
        if (request.getMealsPerDay() == null || request.getMealsPerDay() < 1 || request.getMealsPerDay() > 3) {
            throw new IllegalArgumentException("每日投喂次数必须为 1–3 次");
        }
        List<String> times = request.getFeedTimes();
        if (times == null || times.size() != request.getMealsPerDay()) {
            throw new IllegalArgumentException("投喂时间数量必须与每日投喂次数一致");
        }
        List<String> normalizedTimes = times.stream().map(this::normalizeFeedTime).toList();
        Set<String> uniqueTimes = new HashSet<>(normalizedTimes);
        if (uniqueTimes.size() != normalizedTimes.size()) {
            throw new IllegalArgumentException("每次投喂时间不能重复");
        }

        LocalDateTime now = LocalDateTime.now(ZONE);
        FeedingStrategySetting existing = feedingStrategyMapper.findByPondId(pondId);
        FeedingStrategySetting setting = new FeedingStrategySetting();
        setting.setPondId(pondId);
        setting.setDailyRate(request.getDailyRate().setScale(4, RoundingMode.HALF_UP));
        setting.setMealsPerDay(request.getMealsPerDay());
        setting.setFeedTime1(normalizedTimes.get(0));
        setting.setFeedTime2(normalizedTimes.size() > 1 ? normalizedTimes.get(1) : null);
        setting.setFeedTime3(normalizedTimes.size() > 2 ? normalizedTimes.get(2) : null);
        setting.setCreatedAt(existing != null ? existing.getCreatedAt() : now);
        setting.setUpdatedAt(now);
        feedingStrategyMapper.upsert(setting);
        return getStrategy(pondId);
    }

    private Pond validatePond(Integer pondId) {
        if (pondId == null) {
            throw new IllegalArgumentException("鱼塘不能为空");
        }
        Pond pond = pondMapper.findById(pondId);
        if (pond == null) {
            throw new IllegalArgumentException("鱼塘不存在: " + pondId);
        }
        return pond;
    }

    private FeedingRecord requireRecord(long id) {
        FeedingRecord record = feedingRecordMapper.findById(id);
        if (record == null) {
            throw new IllegalArgumentException("投喂记录不存在: " + id);
        }
        return record;
    }

    private FeedingRecord buildRecordFromRequest(FeedingRecordRequest request) {
        if (request.getFeedDate() == null || request.getFeedDate().isBlank()) {
            throw new IllegalArgumentException("投喂日期不能为空");
        }
        if (request.getFeedTotalKg() == null) {
            throw new IllegalArgumentException("投喂总量不能为空");
        }
        if (request.getFeedTotalKg().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("投喂总量必须大于 0");
        }
        if (request.getRemark() != null && request.getRemark().length() > REMARK_MAX_LENGTH) {
            throw new IllegalArgumentException("备注不能超过 " + REMARK_MAX_LENGTH + " 字");
        }

        LocalDate feedDate = LocalDate.parse(request.getFeedDate(), DATE_FORMATTER);
        LocalDate today = LocalDate.now(ZONE);
        if (feedDate.isAfter(today)) {
            throw new IllegalArgumentException("投喂日期不能晚于今天");
        }

        FeedingRecord record = new FeedingRecord();
        record.setPondId(request.getPondId());
        record.setFeedDate(feedDate);
        record.setFeedTotalKg(request.getFeedTotalKg().setScale(4, RoundingMode.HALF_UP));
        record.setRemark(request.getRemark() != null && !request.getRemark().isBlank()
                ? request.getRemark().trim() : null);
        return record;
    }

    private FeedingStrategySetting getOrCreateStrategySetting(int pondId) {
        FeedingStrategySetting setting = feedingStrategyMapper.findByPondId(pondId);
        if (setting != null) return setting;
        LocalDateTime now = LocalDateTime.now(ZONE);
        setting = new FeedingStrategySetting();
        setting.setPondId(pondId);
        setting.setDailyRate(new BigDecimal("0.0250"));
        setting.setMealsPerDay(2);
        setting.setFeedTime1("08:00");
        setting.setFeedTime2("17:00");
        setting.setCreatedAt(now);
        setting.setUpdatedAt(now);
        feedingStrategyMapper.upsert(setting);
        return setting;
    }

    private List<String> settingTimes(FeedingStrategySetting setting) {
        List<String> times = new ArrayList<>();
        if (setting.getFeedTime1() != null) times.add(setting.getFeedTime1());
        if (setting.getMealsPerDay() >= 2 && setting.getFeedTime2() != null) times.add(setting.getFeedTime2());
        if (setting.getMealsPerDay() >= 3 && setting.getFeedTime3() != null) times.add(setting.getFeedTime3());
        return times;
    }

    private String normalizeFeedTime(String value) {
        if (value == null || !value.trim().matches("^(?:[01]\\d|2[0-3]):[0-5]\\d$")) {
            throw new IllegalArgumentException("投喂时间格式必须为 HH:mm");
        }
        return value.trim();
    }

    private List<BigDecimal> distributeAmounts(BigDecimal dailyFeedKg, int mealsPerDay) {
        List<BigDecimal> amounts = new ArrayList<>();
        BigDecimal base = dailyFeedKg.divide(BigDecimal.valueOf(mealsPerDay), 1, RoundingMode.HALF_UP);
        BigDecimal allocated = BigDecimal.ZERO;
        for (int i = 0; i < mealsPerDay - 1; i++) {
            amounts.add(base);
            allocated = allocated.add(base);
        }
        amounts.add(dailyFeedKg.subtract(allocated).setScale(1, RoundingMode.HALF_UP));
        return amounts;
    }

    private List<FeedingStrategyVO.FeedingPlanVO> buildPlans(BigDecimal dailyFeedKg) {
        List<FeedingStrategyVO.FeedingPlanVO> plans = new ArrayList<>();

        FeedingStrategyVO.FeedingPlanVO oneMeal = new FeedingStrategyVO.FeedingPlanVO();
        oneMeal.setMealsPerDay(1);
        oneMeal.setDescription("1 餐/日");
        oneMeal.setAmountsKg(List.of(dailyFeedKg));
        plans.add(oneMeal);

        FeedingStrategyVO.FeedingPlanVO twoMeals = new FeedingStrategyVO.FeedingPlanVO();
        twoMeals.setMealsPerDay(2);
        twoMeals.setDescription("2 餐/日");
        twoMeals.setAmountsKg(distributeAmounts(dailyFeedKg, 2));
        plans.add(twoMeals);

        FeedingStrategyVO.FeedingPlanVO threeMeals = new FeedingStrategyVO.FeedingPlanVO();
        threeMeals.setMealsPerDay(3);
        threeMeals.setDescription("3 餐/日");
        threeMeals.setAmountsKg(distributeAmounts(dailyFeedKg, 3));
        plans.add(threeMeals);

        return plans;
    }

    private String buildSummaryText(Pond pond, BigDecimal biomassKg, BigDecimal avgWeightKg,
                                    BigDecimal dailyRate, BigDecimal dailyFeedKg,
                                    List<String> feedTimes, List<BigDecimal> mealAmounts) {
        BigDecimal ratePercent = dailyRate.multiply(new BigDecimal("100")).setScale(1, RoundingMode.HALF_UP);
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%s（%s）今日生物量 %s kg，平均体重 %s kg，日投喂率 %s%%，日总量约 %s kg。%n%n",
                pond.getName(), pond.getFishSpecies(),
                biomassKg.setScale(0, RoundingMode.HALF_UP),
                avgWeightKg != null ? avgWeightKg.setScale(2, RoundingMode.HALF_UP) : "--",
                ratePercent,
                dailyFeedKg));
        for (int i = 0; i < feedTimes.size(); i++) {
            sb.append(String.format("• %s：%s kg%n", feedTimes.get(i), mealAmounts.get(i)));
        }
        sb.append(System.lineSeparator());
        sb.append("以上为估算参考，请结合水温和鱼群摄食情况调整。");
        return sb.toString();
    }
}
