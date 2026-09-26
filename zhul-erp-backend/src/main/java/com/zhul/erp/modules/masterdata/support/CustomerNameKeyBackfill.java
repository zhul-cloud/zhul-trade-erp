package com.zhul.erp.modules.masterdata.support;

import com.zhul.erp.modules.masterdata.service.CustomerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 启动时补算 name_key 为空的客户（V1.2.9 之前的存量数据）。规范化规则在 Java 里，
 * 迁移脚本不好复刻，所以放在应用启动时做；只处理空值，重复执行无副作用。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomerNameKeyBackfill {

    private final CustomerService customerService;

    @EventListener(ApplicationReadyEvent.class)
    public void backfill() {
        int count = customerService.backfillNameKeys();
        if (count > 0) {
            log.info("客户查重键补算完成，count={}", count);
        }
    }
}
