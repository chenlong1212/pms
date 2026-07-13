package com.pms.service;

import com.pms.dto.FeedingRecordRequest;
import com.pms.dto.FeedingRecordVO;
import com.pms.dto.FeedingStrategyVO;
import com.pms.dto.PageResult;
import com.pms.dto.PondVO;
import com.pms.entity.BiomassRecord;
import com.pms.entity.FeedingRecord;
import com.pms.entity.Pond;
import com.pms.mapper.BiomassRecordMapper;
import com.pms.mapper.FeedingRecordMapper;
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
import java.util.List;

@Service
@RequiredArgsConstructor
public class FeedingService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final int REMARK_MAX_LENGTH = 256;

    private final PondMapper pondMapper;
    private final FeedingRecordMapper feedingRecordMapper;
    private final BiomassRecordMapper biomassRecordMapper;

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
        LocalDate today = LocalDate.now(ZONE);
        String todayStr = today.format(DATE_FORMATTER);
        BiomassRecord biomass = biomassRecordMapper.findByPondAndDate(pondId, todayStr);

        FeedingStrategyVO vo = new FeedingStrategyVO();
        vo.setPondId(pond.getId());
        vo.setPondName(pond.getName());
        vo.setFishSpecies(pond.getFishSpecies());

        if (biomass == null || biomass.getBiomassKg() == null) {
            vo.setAvailable(false);
            vo.setSummaryText("暂无当日生物量数据，无法计算投喂策略。请先确保今日生物量已录入。");
            return vo;
        }

        BigDecimal biomassKg = biomass.getBiomassKg();
        BigDecimal avgWeightKg = biomass.getAvgWeightKg();
        BigDecimal dailyRate = resolveDailyRate(avgWeightKg);
        BigDecimal dailyFeedKg = biomassKg.multiply(dailyRate).setScale(1, RoundingMode.HALF_UP);

        vo.setBiomassKg(biomassKg);
        vo.setAvgWeightKg(avgWeightKg);
        vo.setDailyRate(dailyRate);
        vo.setDailyFeedKg(dailyFeedKg);
        vo.setPlans(buildPlans(dailyFeedKg));
        vo.setAvailable(true);
        vo.setSummaryText(buildSummaryText(pond, biomassKg, avgWeightKg, dailyRate, dailyFeedKg, vo.getPlans()));
        return vo;
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

    private BigDecimal resolveDailyRate(BigDecimal avgWeightKg) {
        if (avgWeightKg == null) {
            return new BigDecimal("0.025");
        }
        if (avgWeightKg.compareTo(new BigDecimal("0.1")) < 0) {
            return new BigDecimal("0.06");
        }
        if (avgWeightKg.compareTo(new BigDecimal("0.5")) < 0) {
            return new BigDecimal("0.04");
        }
        if (avgWeightKg.compareTo(new BigDecimal("1.0")) < 0) {
            return new BigDecimal("0.025");
        }
        return new BigDecimal("0.015");
    }

    private List<FeedingStrategyVO.FeedingPlanVO> buildPlans(BigDecimal dailyFeedKg) {
        List<FeedingStrategyVO.FeedingPlanVO> plans = new ArrayList<>();

        FeedingStrategyVO.FeedingPlanVO oneMeal = new FeedingStrategyVO.FeedingPlanVO();
        oneMeal.setMealsPerDay(1);
        oneMeal.setDescription("1 餐/日");
        oneMeal.setAmountsKg(List.of(dailyFeedKg));
        plans.add(oneMeal);

        BigDecimal morning2 = dailyFeedKg.multiply(new BigDecimal("0.6")).setScale(1, RoundingMode.HALF_UP);
        BigDecimal evening2 = dailyFeedKg.subtract(morning2).setScale(1, RoundingMode.HALF_UP);
        FeedingStrategyVO.FeedingPlanVO twoMeals = new FeedingStrategyVO.FeedingPlanVO();
        twoMeals.setMealsPerDay(2);
        twoMeals.setDescription("2 餐/日");
        twoMeals.setAmountsKg(List.of(morning2, evening2));
        plans.add(twoMeals);

        BigDecimal morning3 = dailyFeedKg.multiply(new BigDecimal("0.4")).setScale(1, RoundingMode.HALF_UP);
        BigDecimal noon3 = dailyFeedKg.multiply(new BigDecimal("0.3")).setScale(1, RoundingMode.HALF_UP);
        BigDecimal evening3 = dailyFeedKg.subtract(morning3).subtract(noon3).setScale(1, RoundingMode.HALF_UP);
        FeedingStrategyVO.FeedingPlanVO threeMeals = new FeedingStrategyVO.FeedingPlanVO();
        threeMeals.setMealsPerDay(3);
        threeMeals.setDescription("3 餐/日");
        threeMeals.setAmountsKg(List.of(morning3, noon3, evening3));
        plans.add(threeMeals);

        return plans;
    }

    private String buildSummaryText(Pond pond, BigDecimal biomassKg, BigDecimal avgWeightKg,
                                    BigDecimal dailyRate, BigDecimal dailyFeedKg,
                                    List<FeedingStrategyVO.FeedingPlanVO> plans) {
        int ratePercent = dailyRate.multiply(new BigDecimal("100")).setScale(1, RoundingMode.HALF_UP).intValue();
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%s（%s）今日生物量 %s kg，平均体重 %s kg，建议日投喂率 %d%%，日总量约 %s kg。%n%n",
                pond.getName(), pond.getFishSpecies(),
                biomassKg.setScale(0, RoundingMode.HALF_UP),
                avgWeightKg.setScale(2, RoundingMode.HALF_UP),
                ratePercent,
                dailyFeedKg));

        FeedingStrategyVO.FeedingPlanVO plan1 = plans.get(0);
        FeedingStrategyVO.FeedingPlanVO plan2 = plans.get(1);
        FeedingStrategyVO.FeedingPlanVO plan3 = plans.get(2);

        sb.append(String.format("• 1 餐/日：每次 %s kg%n", plan1.getAmountsKg().get(0)));
        sb.append(String.format("• 2 餐/日：早 %s kg，晚 %s kg%n",
                plan2.getAmountsKg().get(0), plan2.getAmountsKg().get(1)));
        sb.append(String.format("• 3 餐/日：早 %s kg，中 %s kg，晚 %s kg%n%n",
                plan3.getAmountsKg().get(0), plan3.getAmountsKg().get(1), plan3.getAmountsKg().get(2)));
        sb.append("以上为估算参考，请结合水温和鱼群摄食情况调整。");
        return sb.toString();
    }
}
