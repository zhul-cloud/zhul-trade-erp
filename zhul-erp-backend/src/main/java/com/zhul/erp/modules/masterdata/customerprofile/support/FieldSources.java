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
    /** 只在返回给前端时使用：系统推导或国家资料 */
    public static final int SYSTEM = 9;

    private final CustomerFieldSourceMapper mapper;

    /** 记录这些字段的来源；填写了字段同时清除「已问、等回复」 */
    public void record(int tenantId, Long customerId, Collection<String> keys, int source, long reportId, Long operatorId) {
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

    public List<CustomerFieldSourceDO> list(Long customerId) {
        return mapper.selectList(new LambdaQueryWrapper<CustomerFieldSourceDO>().eq(CustomerFieldSourceDO::getCustomerId, customerId));
    }

    /** 合并客户时把来源搬到保留客户（只搬保留客户没有的字段） */
    public void moveMissing(Long fromCustomerId, Long toCustomerId, Collection<String> keys) {
        if (keys.isEmpty()) {
            return;
        }
        mapper.update(null, new LambdaUpdateWrapper<CustomerFieldSourceDO>()
                .eq(CustomerFieldSourceDO::getCustomerId, fromCustomerId)
                .in(CustomerFieldSourceDO::getFieldKey, keys)
                .set(CustomerFieldSourceDO::getCustomerId, toCustomerId));
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
