package com.zhul.erp.modules.crm.followup.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.crm.dto.ChangeStageRequest;
import com.zhul.erp.modules.crm.followup.constants.FollowupConstants;
import com.zhul.erp.modules.crm.followup.dto.AdjustStageRequest;
import com.zhul.erp.modules.crm.followup.entity.CustomerFollowupDO;
import com.zhul.erp.modules.crm.followup.entity.FollowupItemDO;
import com.zhul.erp.modules.crm.followup.repository.CustomerFollowupMapper;
import com.zhul.erp.modules.crm.followup.repository.FollowupItemMapper;
import com.zhul.erp.modules.crm.followup.support.FollowupSync;
import com.zhul.erp.modules.crm.service.OpportunityService;
import com.zhul.erp.modules.inquiry.support.CurrentUserResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 手动调整阶段（spec「手动调整阶段」，design D3）：只能在同一判断段内前进或后退，必须写原因。
 * <ul>
 *   <li>属于进行中商机的：改商机阶段（写商机阶段记录），跟进对象随商机同步；</li>
 *   <li>老客户报价单（S4–S6）：直接改，同步时保留在 S4–S6 之间；</li>
 *   <li>主动开发（D4–D6）：阶段按触达次数、回复推导且只进不退，所以同时改这些事实，再重新推导。</li>
 * </ul>
 * 每次调整写一条「调整阶段」记录（不算一次跟进），不改下一步。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FollowupStageService {

    private static final int NOTE_MAX = 200;

    private final FollowupService followupService;
    private final FollowupItemMapper itemMapper;
    private final CustomerFollowupMapper recordMapper;
    private final OpportunityService opportunityService;
    private final FollowupSync sync;
    private final CurrentUserResolver currentUser;

    @Transactional(rollbackFor = Exception.class)
    public String adjust(Long id, AdjustStageRequest req) {
        FollowupItemDO item = followupService.requireOpen(id);
        String from = item.getStageCode();
        String to = req.getToStage().trim();
        List<String> segment = FollowupConstants.adjustSegment(from);
        if (segment.isEmpty()) {
            throw new BizException(from + " 由单据推进，不能手动调整");
        }
        if (from.equals(to)) {
            throw new BizException("已经是 " + to);
        }
        if (!segment.contains(to)) {
            throw new BizException(from + " 只能调整到 " + String.join("、", segment));
        }
        String reason = req.getReason().trim();
        String reasonName = FollowupConstants.ADJUST_REASONS.get(reason);
        if (reasonName == null) {
            throw new BizException("调整原因不正确");
        }
        String note = req.getNote() == null ? "" : req.getNote().trim();
        if (FollowupConstants.ADJUST_REASON_OTHER.equals(reason) && note.isEmpty()) {
            throw new BizException("请写明调整原因");
        }
        if (note.length() > NOTE_MAX) {
            throw new BizException("说明不能超过200个字");
        }
        String why = FollowupConstants.ADJUST_REASON_OTHER.equals(reason) ? note
                : note.isEmpty() ? reasonName : reasonName + "：" + note;
        LocalDate today = LocalDate.now();
        long opportunityId = item.getOpportunityId() == null ? 0L : item.getOpportunityId();
        if (Objects.equals(item.getBoard(), FollowupConstants.BOARD_SALES) && opportunityId > 0) {
            ChangeStageRequest c = new ChangeStageRequest();
            c.setToStage(to);
            c.setNote("手动调整：" + why);
            // 商机改阶段后会重新推导这个客户的跟进对象
            opportunityService.changeStage(opportunityId, c);
        } else if (Objects.equals(item.getBoard(), FollowupConstants.BOARD_SALES)) {
            if (!Objects.equals(item.getStageGroup(), FollowupConstants.GROUP_AFTER_QUOTE)) {
                throw new BizException("没有进行中的商机，不能调整到 " + to);
            }
            item.setStageCode(to);
            item.setStageSince(LocalDateTime.now());
            itemMapper.updateById(item);
        } else {
            applyDevelopFacts(item, to, today);
            item.setStageCode(to);
            item.setStageSince(LocalDateTime.now());
            item.setStageGroup(FollowupConstants.DEVELOP_GROUPS.get(to));
            itemMapper.updateById(item);
            // updateById 不写空值：触达日期、回复时间要能清空
            itemMapper.update(null, new LambdaUpdateWrapper<FollowupItemDO>()
                    .eq(FollowupItemDO::getId, item.getId())
                    .set(FollowupItemDO::getFirstTouchDate, item.getFirstTouchDate())
                    .set(FollowupItemDO::getRepliedAt, item.getRepliedAt()));
            sync.resync(item.getCustomerId(), today, false);
        }

        Long me = currentUser.resolve();
        CustomerFollowupDO r = new CustomerFollowupDO();
        r.setTenantId(TenantContext.getTenantId());
        r.setRecordType(FollowupConstants.RECORD_STAGE);
        r.setCustomerId(item.getCustomerId());
        r.setFollowupItemId(item.getId());
        r.setTargetType(item.getTargetType());
        r.setTargetId(item.getTargetId());
        r.setOpportunityId(opportunityId);
        r.setTitle("调整阶段：" + from + " → " + to);
        r.setSituation(0);
        r.setBlockerCode("");
        r.setNextAction("");
        r.setChannel(0);
        r.setRawContent("");
        r.setAiSummary(StringUtils.hasText(why) ? why : "");
        r.setAiTaskId(0L);
        r.setStageBefore(from);
        r.setStageAfter(to);
        r.setOperatorId(me == null ? 0L : me);
        recordMapper.insert(r);
        log.info("手动调整阶段，itemId={}, {} → {}, reason={}", item.getId(), from, to, reason);
        return to;
    }

    /**
     * 主动开发的阶段由触达次数、回复推导，取推导结果与当前阶段中靠后的一个。
     * 调到 D4：清掉触达和回复；D5：至少触达过一次、清掉回复；D6：记为客户已回复。
     */
    private static void applyDevelopFacts(FollowupItemDO item, String to, LocalDate today) {
        switch (to) {
            case "D4" -> {
                item.setTouchCount(0);
                item.setFirstTouchDate(null);
                item.setRepliedAt(null);
            }
            case "D5" -> {
                touched(item, today);
                item.setRepliedAt(null);
            }
            case "D6" -> {
                touched(item, today);
                if (item.getRepliedAt() == null) {
                    item.setRepliedAt(LocalDateTime.now());
                }
            }
            default -> throw new BizException("不能调整到 " + to);
        }
    }

    private static void touched(FollowupItemDO item, LocalDate today) {
        if (item.getTouchCount() == null || item.getTouchCount() < 1) {
            item.setTouchCount(1);
        }
        if (item.getFirstTouchDate() == null) {
            item.setFirstTouchDate(today);
        }
    }
}
