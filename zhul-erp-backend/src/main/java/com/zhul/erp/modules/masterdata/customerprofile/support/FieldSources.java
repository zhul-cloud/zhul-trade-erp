package com.zhul.erp.modules.masterdata.customerprofile.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhul.erp.modules.masterdata.customerprofile.entity.CustomerFieldSourceDO;
import com.zhul.erp.modules.masterdata.customerprofile.repository.CustomerFieldSourceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 画像字段来源（spec master-data/customer-profile「画像字段来源」）与「已问客户、等回复」 */
@Component
@RequiredArgsConstructor
public class FieldSources {

    public static final int MANUAL = 1;
    public static final int REPORT = 2;
    public static final int MIGRATED = 3;
    /** 记一次跟进时勾选写入档案（openspec customer-followup） */
    public static final int FOLLOWUP = 4;
    /** 客户中文名称由 AI 按英文名称翻译（openspec feedback-1116-batch1） */
    public static final int AI_TRANSLATE = 5;
    /** 客户中文名称的字段键 */
    public static final String NAME_CN = "nameCn";
    /** 只在返回给前端时使用：系统推导或国家资料 */
    public static final int SYSTEM = 9;

    private final CustomerFieldSourceMapper mapper;

    /** 记录这些字段的来源；填写了字段同时清除「已问、等回复」 */
    public void record(int tenantId, Long customerId, Collection<String> keys, int source, long reportId, Long operatorId) {
        record(tenantId, customerId, keys, source, reportId, 0L, operatorId);
    }

    /** 同上，来源为跟进记录时带跟进记录 ID */
    public void record(int tenantId, Long customerId, Collection<String> keys, int source, long reportId, long followupId,
                       Long operatorId) {
        if (keys.isEmpty()) {
            return;
        }
        Map<String, CustomerFieldSourceDO> existing = byKey(customerId, keys);
        for (String key : keys) {
            CustomerFieldSourceDO row = existing.get(key);
            if (row == null) {
                row = new CustomerFieldSourceDO();
                row.setTenantId(tenantId);
                row.setCustomerId(customerId);
                row.setFieldKey(key);
            }
            row.setSource(source);
            row.setReportId(reportId);
            row.setFollowupId(followupId);
            row.setOperatorId(operatorId == null ? 0L : operatorId);
            row.setAskedBy(0L);
            row.setAskedAt(null);
            if (row.getId() == null) {
                mapper.insert(row);
            } else {
                mapper.updateById(row);
            }
        }
    }

    /** 标记或取消「已经问了，等回复」 */
    public void markAsked(int tenantId, Long customerId, Collection<String> keys, boolean asked, Long operatorId) {
        Map<String, CustomerFieldSourceDO> existing = byKey(customerId, keys);
        LocalDateTime now = LocalDateTime.now();
        for (String key : keys) {
            CustomerFieldSourceDO row = existing.get(key);
            if (row == null) {
                if (!asked) {
                    continue;
                }
                row = new CustomerFieldSourceDO();
                row.setTenantId(tenantId);
                row.setCustomerId(customerId);
                row.setFieldKey(key);
                row.setSource(MANUAL);
                row.setReportId(0L);
                row.setFollowupId(0L);
                row.setOperatorId(0L);
            }
            row.setAskedBy(asked ? operatorId : 0L);
            row.setAskedAt(asked ? now : null);
            if (row.getId() == null) {
                mapper.insert(row);
            } else {
                mapper.updateById(row);
            }
        }
    }

    /** 某个字段的来源记录，没有时为空 */
    public CustomerFieldSourceDO get(Long customerId, String key) {
        return byKey(customerId, List.of(key)).get(key);
    }

    public List<CustomerFieldSourceDO> list(Long customerId) {
        return mapper.selectList(new LambdaQueryWrapper<CustomerFieldSourceDO>().eq(CustomerFieldSourceDO::getCustomerId, customerId));
    }

    /**
     * 合并客户时把来源搬到保留客户（keys 是保留客户为空、用被合并客户的值补上的字段）。
     * 保留客户已有同字段的来源行（如「已问、等回复」、清空过字段）时不整行搬，改为照搬来源到这一行，
     * 避免撞 (customer_id, field_key) 唯一键（BUS-047）；字段已补上值，「已问、等回复」随之清除。
     */
    public void moveMissing(Long fromCustomerId, Long toCustomerId, Collection<String> keys) {
        if (keys.isEmpty()) {
            return;
        }
        Map<String, CustomerFieldSourceDO> from = byKey(fromCustomerId, keys);
        Map<String, CustomerFieldSourceDO> to = byKey(toCustomerId, keys);
        for (String key : keys) {
            CustomerFieldSourceDO s = from.get(key);
            CustomerFieldSourceDO t = to.get(key);
            if (t == null) {
                if (s != null) {
                    mapper.update(null, new LambdaUpdateWrapper<CustomerFieldSourceDO>()
                            .eq(CustomerFieldSourceDO::getId, s.getId())
                            .set(CustomerFieldSourceDO::getCustomerId, toCustomerId));
                }
                continue;
            }
            if (s != null) {
                t.setSource(s.getSource());
                t.setReportId(s.getReportId());
                t.setFollowupId(s.getFollowupId());
                t.setOperatorId(s.getOperatorId());
            }
            t.setAskedBy(0L);
            t.setAskedAt(null);
            mapper.updateById(t);
        }
    }

    /** 合并客户时联系人字段键里的联系人ID不变，整体搬到保留客户 */
    public void moveContactKeys(Long fromCustomerId, Long toCustomerId) {
        mapper.update(null, new LambdaUpdateWrapper<CustomerFieldSourceDO>()
                .eq(CustomerFieldSourceDO::getCustomerId, fromCustomerId)
                .likeRight(CustomerFieldSourceDO::getFieldKey, "contact.")
                .set(CustomerFieldSourceDO::getCustomerId, toCustomerId));
    }

    private Map<String, CustomerFieldSourceDO> byKey(Long customerId, Collection<String> keys) {
        if (keys.isEmpty()) {
            return Map.of();
        }
        return mapper.selectList(new LambdaQueryWrapper<CustomerFieldSourceDO>()
                        .eq(CustomerFieldSourceDO::getCustomerId, customerId)
                        .in(CustomerFieldSourceDO::getFieldKey, keys))
                .stream().collect(Collectors.toMap(CustomerFieldSourceDO::getFieldKey, Function.identity(), (a, b) -> a));
    }
}
