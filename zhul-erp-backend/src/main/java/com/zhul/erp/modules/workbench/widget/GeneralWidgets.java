package com.zhul.erp.modules.workbench.widget;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 通用看板的两张卡片：前端直接调用现有接口（商机统计、PI 列表），这里只登记编码与菜单权限。
 * 它们同时被老板、业务负责人、业务员看板复用。
 */
@Configuration
public class GeneralWidgets {

    @Bean
    public WorkbenchWidget opportunityStatsWidget() {
        return simple("opportunity-stats", "/crm/opportunity-stats");
    }

    @Bean
    public WorkbenchWidget overduePiWidget() {
        return simple("overdue-pi", "/sales/pi");
    }

    private static WorkbenchWidget simple(String key, String menu) {
        return new WorkbenchWidget() {
            @Override
            public String key() {
                return key;
            }

            @Override
            public List<String> menus() {
                return List.of(menu);
            }
        };
    }
}
