package com.zhul.erp.modules.crm.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.modules.crm.constants.OpportunityConstants;
import com.zhul.erp.modules.crm.entity.OpportunityStageDO;
import com.zhul.erp.modules.crm.repository.OpportunityStageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 商机阶段规则，全部按阶段配置（类别、排序、是否计为有效）判断，不写死阶段编码：
 * <ul>
 *   <li>进行中阶段之间可任意前进 / 回退</li>
 *   <li>未计为有效的进行中阶段（S1、S2）可标记无效；计为有效的（S3 起）可标记输单</li>
 *   <li>只有最后一个进行中阶段（S7）可标记赢单</li>
 *   <li>结束状态可重新打开，回到结束前的阶段</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class OpportunityStageRules {

    private final OpportunityStageMapper stageMapper;

    /** 启用的阶段，按排序；配置只有十来行，每次读取即可 */
    public Stages load() {
        List<OpportunityStageDO> rows = stageMapper.selectList(new LambdaQueryWrapper<OpportunityStageDO>()
                .eq(OpportunityStageDO::getTenantId, 0)
                .eq(OpportunityStageDO::getStatus, 1));
        rows.sort(Comparator.comparing(OpportunityStageDO::getSortOrder));
        Map<String, OpportunityStageDO> byCode = new LinkedHashMap<>(16);
        for (OpportunityStageDO row : rows) {
            byCode.put(row.getCode(), row);
        }
        return new Stages(byCode);
    }

    /** 阶段配置快照 */
    public record Stages(Map<String, OpportunityStageDO> byCode) {

        public OpportunityStageDO get(String code) {
            OpportunityStageDO s = code == null ? null : byCode.get(code);
            if (s == null) {
                throw new BizException("阶段不存在");
            }
            return s;
        }

        public String nameOf(String code) {
            OpportunityStageDO s = code == null ? null : byCode.get(code);
            return s == null ? (code == null ? "" : code) : s.getName();
        }

        public boolean isActive(String code) {
            OpportunityStageDO s = byCode.get(code);
            return s != null && s.getCategory() == OpportunityConstants.CATEGORY_ACTIVE;
        }

        public boolean countsAsValid(String code) {
            OpportunityStageDO s = byCode.get(code);
            return s != null && Objects.equals(s.getCountsAsValid(), 1);
        }

        private List<OpportunityStageDO> active() {
            return byCode.values().stream().filter(s -> s.getCategory() == OpportunityConstants.CATEGORY_ACTIVE).toList();
        }

        /** 第一个进行中阶段（登记时的初始阶段） */
        public String first() {
            return active().get(0).getCode();
        }

        private OpportunityStageDO last() {
            List<OpportunityStageDO> a = active();
            return a.get(a.size() - 1);
        }

        /** 第一个计为有效的进行中阶段（默认 S3） */
        public OpportunityStageDO firstValid() {
            return active().stream().filter(s -> Objects.equals(s.getCountsAsValid(), 1)).findFirst().orElse(last());
        }

        private static String label(OpportunityStageDO s) {
            return s.getCode() + " " + s.getName();
        }

        /** 进行中阶段之间变更 */
        public void checkChange(String from, String to) {
            if (!isActive(from)) {
                throw new BizException("商机已结束，请先重新打开");
            }
            if (!isActive(to)) {
                throw new BizException("只能切换到进行中的阶段");
            }
            if (from.equals(to)) {
                throw new BizException("已经是该阶段");
            }
        }

        /** 标记结束：result 为结束状态编码，reason 按类别校验 */
        public void checkClose(String from, String result, Integer reason) {
            if (!isActive(from)) {
                throw new BizException("商机已结束，请先重新打开");
            }
            OpportunityStageDO target = get(result);
            switch (target.getCategory()) {
                case OpportunityConstants.CATEGORY_INVALID -> {
                    if (countsAsValid(from)) {
                        throw new BizException(firstValid().getCode() + " 及以后请标记为输单");
                    }
                    requireReason(reason, OpportunityConstants.INVALID_REASONS, "请选择无效原因");
                }
                case OpportunityConstants.CATEGORY_LOST -> {
                    if (!countsAsValid(from)) {
                        throw new BizException(firstValid().getCode() + " 之前的商机请标记为无效");
                    }
                    requireReason(reason, OpportunityConstants.LOST_REASONS, "请选择输单原因");
                }
                case OpportunityConstants.CATEGORY_WON -> {
                    if (!from.equals(last().getCode())) {
                        throw new BizException("请先推进到 " + label(last()));
                    }
                }
                default -> throw new BizException("只能标记为赢单、输单或无效");
            }
        }

        private static void requireReason(Integer reason, Map<Integer, String> allowed, String message) {
            if (reason == null || !allowed.containsKey(reason)) {
                throw new BizException(message);
            }
        }

        /** 结束原因的文字 */
        public static String reasonLabel(Integer reason) {
            if (reason == null || reason == 0) {
                return "";
            }
            String s = OpportunityConstants.INVALID_REASONS.get(reason);
            return s != null ? s : OpportunityConstants.LOST_REASONS.getOrDefault(reason, "");
        }
    }
}
