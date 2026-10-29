package com.zhul.erp.modules.system.migration.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhul.erp.modules.inquiry.constants.InquiryConstants;
import com.zhul.erp.modules.inquiry.customerinquiry.entity.CustomerInquiryDO;
import com.zhul.erp.modules.inquiry.customerinquiry.repository.CustomerInquiryMapper;
import com.zhul.erp.modules.inquiry.item.entity.InquiryItemDO;
import com.zhul.erp.modules.inquiry.item.repository.InquiryItemMapper;
import com.zhul.erp.modules.inquiry.sourcing.entity.SourcingQuoteDO;
import com.zhul.erp.modules.inquiry.sourcing.entity.SourcingTaskAssigneeDO;
import com.zhul.erp.modules.inquiry.sourcing.entity.SourcingTaskDO;
import com.zhul.erp.modules.inquiry.sourcing.repository.SourcingQuoteMapper;
import com.zhul.erp.modules.inquiry.sourcing.repository.SourcingTaskAssigneeMapper;
import com.zhul.erp.modules.inquiry.sourcing.repository.SourcingTaskMapper;
import com.zhul.erp.modules.product.support.BrandResolver;
import com.zhul.erp.modules.product.support.MpnNormalizer;
import com.zhul.erp.modules.system.constants.DocumentType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static com.zhul.erp.modules.system.migration.service.MigrationRun.cut;
import static com.zhul.erp.modules.system.migration.service.MigrationRun.date;
import static com.zhul.erp.modules.system.migration.service.MigrationRun.integer;
import static com.zhul.erp.modules.system.migration.service.MigrationRun.money;
import static com.zhul.erp.modules.system.migration.service.MigrationRun.text;
import static com.zhul.erp.modules.system.migration.service.MigrationRun.time;

/** 询盘与询价阶段：询盘单、型号行、询价任务（已回价）、任务分配、询价记录（已提交，录入方式为导入） */
@Component
@RequiredArgsConstructor
public class InquiryImporter {

    static final String STAGE = "询盘与询价";
    private static final BigDecimal DEFAULT_TAX_RATE = new BigDecimal("13.00");

    private final CustomerInquiryMapper inquiryMapper;
    private final InquiryItemMapper itemMapper;
    private final SourcingTaskMapper taskMapper;
    private final SourcingTaskAssigneeMapper assigneeMapper;
    private final SourcingQuoteMapper quoteMapper;
    private final BrandResolver brandResolver;

    void run(MigrationRun r, Integer businessUnit) {
        Map<String, String> oppOfCustomer = new HashMap<>();
        for (JsonNode o : r.list("opportunities")) {
            oppOfCustomer.put(text(o, "customerKey"), text(o, "key"));
        }
        Map<String, Integer> sourceOfCustomer = new HashMap<>();
        for (JsonNode c : r.list("customers")) {
            sourceOfCustomer.put(text(c, "key"), integer(c, "sourceChannel", 0));
        }
        Map<String, Long> brandCache = new HashMap<>();
        for (JsonNode q : r.list("inquiries")) {
            inquiry(r, q, businessUnit, oppOfCustomer, sourceOfCustomer, brandCache);
        }
        Map<String, Integer> letters = new HashMap<>();
        for (JsonNode t : r.list("sourcing")) {
            task(r, t, letters);
        }
    }

    private Long brandId(Map<String, Long> cache, String brand) {
        String key = BrandResolver.key(brand);
        if (key.isEmpty()) {
            return null;
        }
        if (!cache.containsKey(key)) {
            cache.put(key, brandResolver.resolve(brand));
        }
        return cache.get(key);
    }

    private void inquiry(MigrationRun r, JsonNode q, Integer bu, Map<String, String> oppOfCustomer,
                         Map<String, Integer> sourceOfCustomer, Map<String, Long> brandCache) {
        String key = text(q, "key");
        Long existing = r.mapped("inquiries", key, "customer_inquiry");
        if (existing != null) {
            for (JsonNode l : q.path("lines")) {
                Long item = r.mapped("inquiryLines", key + "#" + l.path("lineNo").asInt(), "inquiry_item");
                if (item != null) {
                    r.inquiryItemIds.add(item);
                }
            }
            r.skipped(STAGE, "询盘");
            return;
        }
        String customerKey = text(q, "customerKey");
        Long customerId = r.mapped("customers", customerKey, "customer");
        if (customerId == null) {
            r.failed(STAGE, "询盘（客户没迁移）");
            return;
        }
        LocalDateTime created = time(q, "createTime");
        LocalDate day = date(q, "inquiryDate");
        if (day == null) {
            day = created == null ? LocalDate.now() : created.toLocalDate();
        }
        Long opportunityId = null;
        if (!text(q, "opportunityKey").isEmpty() && oppOfCustomer.containsKey(customerKey)) {
            opportunityId = r.mapped("opportunities", oppOfCustomer.get(customerKey), "opportunity");
        }
        CustomerInquiryDO d = new CustomerInquiryDO();
        d.setTenantId(r.tenantId);
        d.setBusinessUnitId(bu);
        d.setInquiryCode(r.nextNo(DocumentType.IQ, day));
        d.setCustomerId(customerId);
        d.setOpportunityId(opportunityId);
        int source = sourceOfCustomer.getOrDefault(customerKey, 0);
        d.setSource(source == 0 ? 1 : source);
        d.setRawContent(cut(text(q, "rawContent"), 300));
        d.setInquiryDate(day);
        d.setQuoteDeadline(day);
        d.setUrgent(integer(q, "urgent", 0));
        d.setLevel(integer(q, "level", 3));
        d.setCustomerType(integer(q, "customerType", 1));
        d.setParseMode(InquiryConstants.PARSE_MODE_AI);
        // 切换日之前的询盘一律视为已完结：没有成交、取消的记为未成交
        int status = integer(q, "status", InquiryConstants.STATUS_QUOTED);
        d.setStatus(status == InquiryConstants.STATUS_WON || status == InquiryConstants.STATUS_CANCELLED ? status : InquiryConstants.STATUS_LOST);
        Long owner = r.user(text(q, "owner"));
        d.setOwnerId(owner == null ? 0L : owner);
        d.setRemark(cut(text(q, "remark"), 500));
        JsonNode lines = q.path("lines");
        int qty = 0;
        int pending = 0;
        for (JsonNode l : lines) {
            qty += Math.max(0, l.path("quantity").asInt(0));
            if (l.path("confidence").asInt() >= InquiryConstants.CONFIDENCE_PENDING_VERIFY) {
                pending++;
            }
        }
        d.setTotalItemCount(lines.size());
        d.setTotalQuantity(qty);
        d.setPendingVerifyCount(pending);
        d.setMigrated(1);
        d.setCreateTime(created);
        d.setCreateBy(r.username(text(q, "createdBy")));
        inquiryMapper.insert(d);
        r.record("inquiries", key, "customer_inquiry", d.getId());
        r.created(STAGE, "询盘");

        String category = cut(text(q, "category"), 32);
        for (JsonNode l : lines) {
            InquiryItemDO i = new InquiryItemDO();
            i.setTenantId(r.tenantId);
            i.setCustomerInquiryId(d.getId());
            i.setLineNo(l.path("lineNo").asInt());
            String brand = cut(text(l, "brand"), 64);
            Long brandId = brandId(brandCache, brand);
            i.setBrand(brand);
            i.setBrandId(brandId);
            i.setBrandKey(brandId != null ? "#" + brandId : cut(BrandResolver.key(brand), 80));
            i.setCategory(category);
            String model = cut(text(l, "model"), 128);
            i.setOriginalModel(model);
            i.setConfirmedModel(model);
            i.setModelKey(cut(MpnNormalizer.normalize(model), 128));
            i.setConfidence(integer(l, "confidence", InquiryConstants.CONFIDENCE_UNRECOGNIZED));
            i.setQuantity(Math.max(0, l.path("quantity").asInt(0)));
            i.setDescription(cut(text(l, "description"), 300));
            i.setQuoteStatus(InquiryConstants.ITEM_PENDING);
            i.setPriceSource(InquiryConstants.PRICE_SOURCE_SOURCING);
            String condition = text(l, "condition");
            i.setRemark(condition.isEmpty() ? "" : cut("成色：" + condition, 300));
            i.setCreateTime(created);
            i.setCreateBy(d.getCreateBy());
            itemMapper.insert(i);
            r.record("inquiryLines", key + "#" + i.getLineNo(), "inquiry_item", i.getId());
            r.inquiryItemIds.add(i.getId());
            r.created(STAGE, "询盘型号行");
        }
    }

    private void task(MigrationRun r, JsonNode t, Map<String, Integer> letters) {
        String key = text(t, "key");
        if (r.mapped("sourcing", key, "sourcing_task") != null) {
            r.skipped(STAGE, "询价任务");
            return;
        }
        String inquiryKey = text(t, "inquiryKey");
        Long inquiryId = r.mapped("inquiries", inquiryKey, "customer_inquiry");
        if (inquiryId == null) {
            r.failed(STAGE, "询价任务（询盘没迁移）");
            return;
        }
        CustomerInquiryDO inquiry = inquiryMapper.selectById(inquiryId);
        LocalDateTime created = time(t, "createTime");
        Long purchaser = r.user(text(t, "purchaser"));
        int n = letters.merge(inquiryKey, 1, Integer::sum);
        SourcingTaskDO task = new SourcingTaskDO();
        task.setTenantId(r.tenantId);
        task.setTaskCode(inquiry.getInquiryCode() + taskLetter(n));
        task.setCustomerInquiryId(inquiryId);
        task.setUrgent(inquiry.getUrgent());
        task.setStatus(InquiryConstants.TASK_DONE);
        task.setFirstAssignedAt(created);
        task.setCompletedAt(created);
        task.setMigrated(1);
        task.setCreateTime(created);
        task.setCreateBy(r.username(text(t, "purchaser")));
        taskMapper.insert(task);
        r.record("sourcing", key, "sourcing_task", task.getId());
        r.created(STAGE, "询价任务");

        if (purchaser != null) {
            SourcingTaskAssigneeDO a = new SourcingTaskAssigneeDO();
            a.setTenantId(r.tenantId);
            a.setTaskId(task.getId());
            a.setAssigneeId(purchaser);
            a.setAssignedBy(0L);
            a.setAssignedAt(created);
            a.setAssignMode(InquiryConstants.ASSIGN_MANUAL);
            a.setActive(1);
            a.setSubmittedAt(created);
            a.setCreateTime(created);
            a.setCreateBy(task.getCreateBy());
            assigneeMapper.insert(a);
        }

        Set<Long> items = new HashSet<>();
        for (JsonNode q : t.path("quotes")) {
            String qkey = text(q, "key");
            if (r.mapped("quotes", qkey, "sourcing_quote") != null) {
                r.skipped(STAGE, "询价记录");
                continue;
            }
            Long itemId = q.path("lineNo").isNull() || q.path("lineNo").isMissingNode() ? null
                    : r.mapped("inquiryLines", inquiryKey + "#" + q.path("lineNo").asInt(), "inquiry_item");
            if (itemId == null) {
                r.failed(STAGE, "询价记录（没有对应型号行）");
                continue;
            }
            InquiryItemDO item = itemMapper.selectById(itemId);
            boolean noStock = q.path("noStock").asBoolean();
            BigDecimal price = noStock ? null : money(q, "unitPrice");
            boolean taxed = q.path("taxIncluded").asInt() == 1;
            SourcingQuoteDO s = new SourcingQuoteDO();
            s.setTenantId(r.tenantId);
            s.setInquiryItemId(itemId);
            s.setTaskId(task.getId());
            s.setCustomerInquiryId(inquiryId);
            s.setBrand(item.getBrand());
            s.setBrandKey(item.getBrandKey());
            String model = cut(text(q, "model"), 128);
            s.setModel(model.isEmpty() ? item.getConfirmedModel() : model);
            s.setModelKey(cut(MpnNormalizer.normalize(s.getModel()), 128));
            s.setChannel(integer(q, "channel", InquiryConstants.CHANNEL_OTHER));
            s.setShopName(cut(text(q, "shopName"), 128));
            String supplierKey = text(q, "supplierKey");
            s.setSupplierId(supplierKey.isEmpty() ? null : r.mapped("suppliers", supplierKey, "supplier"));
            s.setNoStock(price == null ? 1 : 0);
            s.setCurrencyCode("CNY");
            s.setUnitPrice(price);
            s.setExchangeRate(price == null ? null : BigDecimal.ONE);
            s.setTaxIncluded(taxed ? 1 : 0);
            s.setTaxRate(taxed ? DEFAULT_TAX_RATE : BigDecimal.ZERO);
            s.setUnitPriceCny(price == null ? null : taxed
                    ? price.divide(BigDecimal.ONE.add(DEFAULT_TAX_RATE.movePointLeft(2)), 2, RoundingMode.HALF_UP) : price);
            s.setItemCondition(integer(q, "itemCondition", 0));
            s.setLeadTime(integer(q, "leadTime", 0));
            s.setNote(cut(text(q, "note"), 300));
            s.setRecommended(0);
            s.setQuotedBy(purchaser == null ? 0L : purchaser);
            s.setQuotedAt(time(q, "quotedAt"));
            s.setStatus(InquiryConstants.QUOTE_SUBMITTED);
            s.setEntryMode(InquiryConstants.ENTRY_IMPORT);
            s.setSubmitBatch("MIG-" + r.batchId);
            s.setMigrated(1);
            s.setCreateTime(s.getQuotedAt());
            s.setCreateBy(task.getCreateBy());
            quoteMapper.insert(s);
            r.record("quotes", qkey, "sourcing_quote", s.getId());
            r.created(STAGE, "询价记录");
            items.add(itemId);

            // 型号行的回价状态与询价任务：有价格为已有价格，只有无货为无货
            if (price != null) {
                item.setQuoteStatus(InquiryConstants.ITEM_PRICED);
            } else if (item.getQuoteStatus() == InquiryConstants.ITEM_PENDING) {
                item.setQuoteStatus(InquiryConstants.ITEM_NO_STOCK);
            }
            if (item.getSourcingTaskId() == null) {
                item.setSourcingTaskId(task.getId());
            }
            itemMapper.updateById(item);
        }
        task.setItemCount(items.size());
        taskMapper.updateById(task);
        inquiry.setTotalOrderCount((inquiry.getTotalOrderCount() == null ? 0 : inquiry.getTotalOrderCount()) + 1);
        inquiry.setPricedItemCount(Math.toIntExact(itemMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<InquiryItemDO>()
                        .eq(InquiryItemDO::getCustomerInquiryId, inquiryId)
                        .ne(InquiryItemDO::getQuoteStatus, InquiryConstants.ITEM_PENDING))));
        inquiryMapper.updateById(inquiry);
    }

    /** 1 → A，26 → Z，27 → AA */
    private static String taskLetter(int n) {
        StringBuilder sb = new StringBuilder();
        for (int x = n; x > 0; x = (x - 1) / 26) {
            sb.insert(0, (char) ('A' + (x - 1) % 26));
        }
        return sb.toString();
    }
}
