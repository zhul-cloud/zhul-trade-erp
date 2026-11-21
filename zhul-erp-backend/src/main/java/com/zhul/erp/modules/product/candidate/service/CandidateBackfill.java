package com.zhul.erp.modules.product.candidate.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.framework.tenant.TenantScope;
import com.zhul.erp.modules.inquiry.constants.InquiryConstants;
import com.zhul.erp.modules.inquiry.item.entity.InquiryItemDO;
import com.zhul.erp.modules.inquiry.item.repository.InquiryItemMapper;
import com.zhul.erp.modules.product.candidate.constants.CandidateConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 历史补齐：已确认（询价中及之后、未取消）的询盘型号里，建档状态还是「未处理」的按批自动建档。
 * 自动建档后状态不再是未处理，所以重复启动不会重复处理；每批一个事务，单批失败只记日志，下次启动再补。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CandidateBackfill implements ApplicationRunner {

    private static final int BATCH = 200;
    private static final List<Integer> CONFIRMED = List.of(InquiryConstants.STATUS_SOURCING, InquiryConstants.STATUS_READY_TO_QUOTE,
            InquiryConstants.STATUS_QUOTED, InquiryConstants.STATUS_WON, InquiryConstants.STATUS_LOST);

    private final InquiryItemMapper itemMapper;
    private final ProductArchiver archiver;
    private final TransactionTemplate tx;
    private final DocProductLinkService docLinks;

    /** 测试环境关掉：后台线程与用例同时改数据会互相干扰；回填本身由契约测试直接调用 */
    @org.springframework.beans.factory.annotation.Value("${zhul.product.doc-backfill.enabled:true}")
    private boolean docBackfillEnabled;

    /** 单据明细关联商品的历史回填放到后台单线程执行，不拖慢启动（spec product/document-product-link） */
    private final ThreadPoolExecutor pool = new ThreadPoolExecutor(1, 1, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<>(1),
            r -> {
                Thread t = new Thread(r, "doc-product-backfill");
                t.setDaemon(true);
                return t;
            }, new ThreadPoolExecutor.DiscardPolicy());

    @Override
    public void run(ApplicationArguments args) {
        // 跨租户的后台任务：按各行自己的租户处理，不由租户拦截器追加当前租户条件
        TenantScope.platform(() -> {
            long lastId = 0;
            int done = 0;
            while (true) {
                List<Long> ids = itemMapper.selectList(new LambdaQueryWrapper<InquiryItemDO>()
                                .select(InquiryItemDO::getId)
                                .eq(InquiryItemDO::getArchiveStatus, CandidateConstants.ARCHIVE_NONE)
                                .gt(InquiryItemDO::getId, lastId)
                                .isNull(InquiryItemDO::getDeletedAt)
                                .inSql(InquiryItemDO::getCustomerInquiryId, "SELECT id FROM customer_inquiry WHERE deleted_at IS NULL AND status IN ("
                                        + String.join(",", CONFIRMED.stream().map(String::valueOf).toList()) + ")")
                                .orderByAsc(InquiryItemDO::getId)
                                .last("LIMIT " + BATCH))
                        .stream().map(InquiryItemDO::getId).toList();
                if (ids.isEmpty()) {
                    break;
                }
                lastId = ids.get(ids.size() - 1);
                try {
                    tx.executeWithoutResult(s -> archiver.sync(ids));
                    done += ids.size();
                } catch (RuntimeException e) {
                    log.warn("商品候选历史补齐失败，下次启动重试，lastId={}", lastId, e);
                }
            }
            if (done > 0) {
                log.info("商品候选历史补齐完成，询盘型号 {} 个", done);
            }
            try {
                int suggested = archiver.resuggestCategories();
                if (suggested > 0) {
                    log.info("待审核候选补齐或细化建议品类 {} 个", suggested);
                }
            } catch (RuntimeException e) {
                log.warn("待审核候选补齐建议品类失败，下次启动重试", e);
            }
        });
        if (docBackfillEnabled) {
            pool.execute(this::backfillDocuments);
        }
    }

    /** 后台线程入口：自己进入平台范围（线程池里没有登录上下文） */
    void backfillDocuments() {
        try {
            TenantScope.platform(docLinks::backfill);
        } catch (RuntimeException e) {
            log.warn("单据明细关联商品回填失败，下次启动重试", e);
        }
    }
}
