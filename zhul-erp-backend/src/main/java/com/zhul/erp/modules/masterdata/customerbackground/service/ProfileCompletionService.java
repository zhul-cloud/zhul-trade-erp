package com.zhul.erp.modules.masterdata.customerbackground.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhul.erp.framework.security.DataScopeResolver;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.inquiry.support.CurrentUserResolver;
import com.zhul.erp.modules.masterdata.customerbackground.dto.BackgroundReportDetailVO;
import com.zhul.erp.modules.masterdata.customerbackground.dto.CompletionQueueVO;
import com.zhul.erp.modules.masterdata.customerbackground.dto.CompletionWizardVO;
import com.zhul.erp.modules.masterdata.customerbackground.entity.CustomerBackgroundReportDO;
import com.zhul.erp.modules.masterdata.customerbackground.repository.CustomerBackgroundReportMapper;
import com.zhul.erp.modules.masterdata.customerprofile.dto.CustomerProfileVO;
import com.zhul.erp.modules.masterdata.customerprofile.entity.CountryProfileDO;
import com.zhul.erp.modules.masterdata.customerprofile.entity.CustomerContactDO;
import com.zhul.erp.modules.masterdata.customerprofile.entity.CustomerFieldSourceDO;
import com.zhul.erp.modules.masterdata.customerprofile.repository.CustomerFieldSourceMapper;
import com.zhul.erp.modules.masterdata.customerprofile.support.JsonArrays;
import com.zhul.erp.modules.masterdata.customerprofile.support.ProfileCompleteness;
import com.zhul.erp.modules.masterdata.customerprofile.support.ProfileFacts;
import com.zhul.erp.modules.masterdata.customerprofile.support.ProfileField;
import com.zhul.erp.modules.masterdata.entity.CustomerDO;
import com.zhul.erp.modules.masterdata.repository.CustomerMapper;
import com.zhul.erp.modules.masterdata.service.CustomerService;
import com.zhul.erp.modules.system.entity.SysConfigDO;
import com.zhul.erp.modules.system.entity.UserBasicDO;
import com.zhul.erp.modules.system.repository.SysConfigMapper;
import com.zhul.erp.modules.system.repository.UserBasicMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static com.zhul.erp.modules.masterdata.customerbackground.constants.BackgroundConstants.STATUS_FAILED;

/**
 * 档案补全（spec crm/customer-background「档案补全」）：左侧待补全的客户、右侧四步补全向导。
 * 字段到四步的归类用 {@link ProfileField#category()}；保存、「已经问了」、AI 背调与采纳沿用已有接口。
 */
@Service
@RequiredArgsConstructor
public class ProfileCompletionService {

    /** 180 天内背调过的，第②步显示报告结论，不再提示背调 */
    static final int REPORT_FRESH_DAYS = 180;
    static final String ASK_PREFIX = "customer.ask.";
    static final List<String> STEP_NAMES = List.of("系统已经知道", "AI 背调能查到", "你自己就能判断", "只能问客户");

    /**
     * 最近活动：最近询盘、订单或商机的更新时间（没有记录的按 1970 年，排在最后）。
     * 只拼接固定 SQL 片段，不含任何外部输入。
     */
    private static final String LAST_ACTIVITY = "CAST(GREATEST("
            + "COALESCE((SELECT MAX(i.update_time) FROM customer_inquiry i WHERE i.customer_id = customer.id AND i.deleted_at IS NULL), '1970-01-01'),"
            + "COALESCE((SELECT MAX(o.update_time) FROM sales_order o WHERE o.customer_id = customer.id AND o.deleted_at IS NULL), '1970-01-01'),"
            + "COALESCE((SELECT MAX(p.update_time) FROM opportunity p WHERE p.customer_id = customer.id AND p.deleted_at IS NULL), '1970-01-01')) AS DATETIME)";

    private final CustomerService customerService;
    private final CustomerMapper customerMapper;
    private final CustomerFieldSourceMapper sourceMapper;
    private final CustomerBackgroundReportMapper reportMapper;
    private final CustomerBackgroundService backgroundService;
    private final ProfileFacts facts;
    private final SysConfigMapper sysConfigMapper;
    private final UserBasicMapper userBasicMapper;
    private final DataScopeResolver dataScopeResolver;
    private final CurrentUserResolver currentUser;

    // ---------------------------------------------------------------- 左侧：待补全的客户

    /**
     * 范围：数据范围内启用、还没建档（完整度不足 5/5）的客户。
     * 排序：成交客户在前，按经营等级 S、A、B、C，同级按综合分数；未成交的按潜力评级，同星数按最近活动。
     */
    public CompletionQueueVO queue(int page, int pageSize) {
        int tenantId = tenantId();
        LambdaQueryWrapper<CustomerDO> w = new LambdaQueryWrapper<CustomerDO>()
                .eq(CustomerDO::getTenantId, tenantId)
                .eq(CustomerDO::getStatus, 1)
                .isNull(CustomerDO::getDeletedAt)
                .and(x -> x.ne(CustomerDO::getProfileMask, ProfileField.ALL_DONE).or().isNull(CustomerDO::getProfileMask));
        dataScopeResolver.current().apply(w, CustomerDO::getOwnerId);
        w.last("ORDER BY CASE WHEN biz_grade > 0 THEN 0 ELSE 1 END, biz_grade, biz_grade_score DESC, potential_rating DESC, "
                + LAST_ACTIVITY + " DESC, id DESC");
        Page<CustomerDO> result = customerMapper.selectPage(new Page<>(Math.max(page, 1), Math.min(Math.max(pageSize, 1), 100)), w);

        List<CustomerDO> rows = result.getRecords();
        Map<Long, LocalDateTime> asked = earliestAsked(rows.stream().map(CustomerDO::getId).toList());
        Map<Long, LocalDateTime> activity = lastActivity(rows.stream().map(CustomerDO::getId).toList());
        List<CompletionQueueVO.Row> out = new ArrayList<>(rows.size());
        for (CustomerDO c : rows) {
            ProfileCompleteness.Result done = facts.evaluate(c);
            CompletionQueueVO.Row r = new CompletionQueueVO.Row();
            r.setCustomerId(c.getId());
            r.setCustomerCode(c.getCustomerCode());
            r.setName(c.getName());
            r.setCountry(c.getCountry());
            r.setBizGrade(c.getBizGrade() == null ? 0 : c.getBizGrade());
            r.setPotentialRating(c.getPotentialRating() == null ? 0 : c.getPotentialRating());
            r.setDone(done.doneCount());
            r.setMissingCount(done.missing().values().stream().mapToInt(List::size).sum());
            LocalDateTime at = asked.get(c.getId());
            r.setAskedDays(at == null ? null : days(at));
            r.setLastActivity(activity.get(c.getId()));
            out.add(r);
        }
        CompletionQueueVO vo = new CompletionQueueVO();
        vo.setTotal(result.getTotal());
        vo.setRecords(out);
        vo.setTodayDone(todayDone(tenantId));
        return vo;
    }

    /** 本人今天把完整度提到 5/5 的客户数（降回 5 以下的不算） */
    private long todayDone(int tenantId) {
        Long me = currentUser.resolve();
        if (me == null) {
            return 0;
        }
        return customerMapper.selectCount(new LambdaQueryWrapper<CustomerDO>()
                .eq(CustomerDO::getTenantId, tenantId)
                .eq(CustomerDO::getProfileDoneBy, me)
                .ge(CustomerDO::getProfileDoneAt, LocalDate.now().atStartOfDay())
                .isNull(CustomerDO::getDeletedAt));
    }

    private Map<Long, LocalDateTime> earliestAsked(List<Long> customerIds) {
        Map<Long, LocalDateTime> out = new HashMap<>();
        if (customerIds.isEmpty()) {
            return out;
        }
        for (CustomerFieldSourceDO s : sourceMapper.selectList(new LambdaQueryWrapper<CustomerFieldSourceDO>()
                .select(CustomerFieldSourceDO::getCustomerId, CustomerFieldSourceDO::getAskedAt)
                .in(CustomerFieldSourceDO::getCustomerId, customerIds)
                .gt(CustomerFieldSourceDO::getAskedBy, 0)
                .isNotNull(CustomerFieldSourceDO::getAskedAt))) {
            out.merge(s.getCustomerId(), s.getAskedAt(), (a, b) -> a.isBefore(b) ? a : b);
        }
        return out;
    }

    private Map<Long, LocalDateTime> lastActivity(List<Long> customerIds) {
        Map<Long, LocalDateTime> out = new HashMap<>();
        if (customerIds.isEmpty()) {
            return out;
        }
        for (Map<String, Object> m : customerMapper.selectMaps(new QueryWrapper<CustomerDO>()
                .select("id", LAST_ACTIVITY + " AS last_activity")
                .in("id", customerIds))) {
            Object v = m.get("last_activity");
            LocalDateTime t = v instanceof LocalDateTime ldt ? ldt
                    : v instanceof java.sql.Timestamp ts ? ts.toLocalDateTime() : null;
            if (t != null && t.getYear() > 1970) {
                out.put(((Number) m.get("id")).longValue(), t);
            }
        }
        return out;
    }

    // ---------------------------------------------------------------- 右侧：四步补全向导

    public CompletionWizardVO wizard(Long customerId) {
        CustomerDO c = customerService.requireInScope(customerId);
        List<CustomerContactDO> contacts = facts.contacts(c.getId());
        CountryProfileDO country = facts.country(c.getTenantId(), c.getCountry());
        boolean derivedBrands = facts.hasDerivedBrands(c.getId());
        ProfileCompleteness.Result done = ProfileCompleteness.evaluate(c, contacts, country, derivedBrands);
        CustomerContactDO contact = contacts.stream()
                .filter(x -> Integer.valueOf(1).equals(x.getIsPrimary())).findFirst()
                .orElse(contacts.isEmpty() ? null : contacts.get(0));

        CompletionWizardVO vo = new CompletionWizardVO();
        vo.setCustomerId(c.getId());
        vo.setCustomerCode(c.getCustomerCode());
        vo.setName(c.getName());
        vo.setCountry(c.getCountry());
        vo.setDone(done.doneCount());
        vo.setContactId(contact == null ? null : contact.getId());
        vo.setContactName(contact == null ? null : contact.getName());
        for (int i = 0; i < STEP_NAMES.size(); i++) {
            CompletionWizardVO.Step s = new CompletionWizardVO.Step();
            s.setStep(i + 1);
            s.setName(STEP_NAMES.get(i));
            vo.getSteps().add(s);
        }

        Map<String, CustomerFieldSourceDO> sources = sourceMapper.selectList(new LambdaQueryWrapper<CustomerFieldSourceDO>()
                        .eq(CustomerFieldSourceDO::getCustomerId, c.getId()))
                .stream().collect(Collectors.toMap(CustomerFieldSourceDO::getFieldKey, x -> x, (a, b) -> a));
        Map<Long, String> askers = names(sources.values().stream().map(CustomerFieldSourceDO::getAskedBy).collect(Collectors.toSet()));
        Map<String, String> questions = questions(c.getTenantId());

        for (Map.Entry<Integer, List<ProfileField>> e : done.missing().entrySet()) {
            boolean inModule4 = e.getKey() == 4;
            for (ProfileField f : e.getValue()) {
                if (f == ProfileField.COUNTRY_PROFILE) {
                    vo.getSteps().get(0).setCountryProfileMissing(true);
                    continue;
                }
                CompletionWizardVO.Item item = new CompletionWizardVO.Item();
                item.setKey(f.key());
                item.setModule(e.getKey());
                item.setContact(inModule4);
                item.setLabel(inModule4 && f == ProfileField.NAME ? "联系人姓名" : f.label());
                String sourceKey = inModule4 && contact != null ? "contact." + contact.getId() + "." + f.key() : f.key();
                item.setSourceKey(sourceKey);
                int step = stepOf(f, inModule4);
                if (step == 4) {
                    item.setQuestion(questions.get(f.key()));
                    CustomerFieldSourceDO src = sources.get(sourceKey);
                    if (src != null && src.getAskedBy() != null && src.getAskedBy() > 0 && src.getAskedAt() != null) {
                        item.setAskedAt(src.getAskedAt());
                        item.setAskedDays(days(src.getAskedAt()));
                        item.setAskedByName(askers.get(src.getAskedBy()));
                    }
                }
                vo.getSteps().get(step - 1).getItems().add(item);
            }
        }
        system(vo.getSteps().get(0), c, country, derivedBrands);
        vo.getSteps().get(1).setReport(freshReport(c.getId()));
        List<String> asks = vo.getSteps().get(3).getItems().stream()
                .map(CompletionWizardVO.Item::getQuestion).filter(StringUtils::hasText).toList();
        vo.getSteps().get(3).setCopyAll(asks.isEmpty() ? null : String.join("\n", asks));
        return vo;
    }

    /** 联系人姓名缺失（还没有联系人）放第③步：业务员自己就能补上联系人 */
    private static int stepOf(ProfileField f, boolean inModule4) {
        if (inModule4 && f == ProfileField.NAME) {
            return 3;
        }
        return switch (f.category()) {
            case SYSTEM -> 1;
            case RESEARCH -> 2;
            case JUDGE -> 3;
            case ASK -> 4;
        };
    }

    /** 第①步：系统推导与国家资料已自动补上的项 */
    private void system(CompletionWizardVO.Step s, CustomerDO c, CountryProfileDO country, boolean derivedBrands) {
        List<String> auto = new ArrayList<>();
        if (!JsonArrays.nonEmpty(c.getCurrentBrands()) && derivedBrands) {
            List<String> top = facts.topBrands(c.getId(), 3).stream().map(CustomerProfileVO.BrandCount::getName).toList();
            auto.add("常用品牌（询盘推导：" + String.join("、", top) + "）");
        }
        if (country != null && !JsonArrays.nonEmpty(c.getVoltages()) && JsonArrays.nonEmpty(country.getVoltages())) {
            auto.add("电源电压（国家资料）");
        }
        if (country != null && !JsonArrays.nonEmpty(c.getCertifications()) && JsonArrays.nonEmpty(country.getCertifications())) {
            auto.add("认证要求（国家资料）");
        }
        s.setAutoFilled(auto);
        if (s.getCountryProfileMissing() == null) {
            s.setCountryProfileMissing(false);
        }
    }

    /** 180 天内最近一份背调报告（失败的不算），不含正文 */
    private BackgroundReportDetailVO freshReport(Long customerId) {
        CustomerBackgroundReportDO r = reportMapper.selectOne(new LambdaQueryWrapper<CustomerBackgroundReportDO>()
                .select(CustomerBackgroundReportDO::getId)
                .eq(CustomerBackgroundReportDO::getCustomerId, customerId)
                .isNull(CustomerBackgroundReportDO::getDeletedAt)
                .ne(CustomerBackgroundReportDO::getStatus, STATUS_FAILED)
                .ge(CustomerBackgroundReportDO::getCreateTime, LocalDateTime.now().minusDays(REPORT_FRESH_DAYS))
                .orderByDesc(CustomerBackgroundReportDO::getCreateTime)
                .orderByDesc(CustomerBackgroundReportDO::getId)
                .last("LIMIT 1"));
        if (r == null) {
            return null;
        }
        BackgroundReportDetailVO vo = backgroundService.detail(customerId, r.getId());
        vo.setReportMarkdown(null);
        vo.setReportHtml(null);
        return vo;
    }

    /** 「只能问客户」各字段的英文问法：平台模板，租户可在系统设置里覆盖 */
    private Map<String, String> questions(int tenantId) {
        Map<String, String> out = new HashMap<>();
        sysConfigMapper.selectList(new LambdaQueryWrapper<SysConfigDO>()
                        .likeRight(SysConfigDO::getConfigKey, ASK_PREFIX)
                        .in(SysConfigDO::getTenantId, List.of(0, tenantId))
                        .isNull(SysConfigDO::getDeletedAt))
                .stream()
                .sorted(Comparator.comparing(SysConfigDO::getTenantId))
                .forEach(x -> {
                    if (StringUtils.hasText(x.getConfigValue())) {
                        out.put(x.getConfigKey().substring(ASK_PREFIX.length()), x.getConfigValue().trim());
                    }
                });
        return out;
    }

    private Map<Long, String> names(Set<Long> ids) {
        Set<Long> real = new HashSet<>(ids);
        real.removeIf(id -> id == null || id == 0);
        if (real.isEmpty()) {
            return Map.of();
        }
        return userBasicMapper.selectBatchIds(real).stream()
                .collect(Collectors.toMap(u -> Long.valueOf(u.getId()), UserBasicDO::getName, (a, b) -> a));
    }

    private static int days(LocalDateTime at) {
        return (int) Math.max(0, ChronoUnit.DAYS.between(at.toLocalDate(), LocalDate.now()));
    }

    private static int tenantId() {
        return Objects.requireNonNullElse(TenantContext.getTenantId(), 0);
    }
}
