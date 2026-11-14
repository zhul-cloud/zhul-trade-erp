package com.zhul.erp.modules.inquiry.sourcing.event;

import java.util.List;

/**
 * 询价记录生效或撤回（供应链联动，OpenSpec supply-chain-phase1-demand-coverage design 决策 16）。
 * 在询价事务里发布；监听方须在事务提交后处理，处理失败不影响询价。
 *
 * @param tenantId  询价任务所属租户
 * @param taskId    询价任务ID
 * @param quoteIds  生效（正式提交、审核通过、保存修改）或被撤回的询价记录ID
 * @param withdrawn true 为撤回回价
 */
public record SourcingQuoteEvent(Integer tenantId, Long taskId, List<Long> quoteIds, boolean withdrawn) {
}
