package com.zhul.erp.modules.document.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.modules.masterdata.entity.CustomerDO;
import com.zhul.erp.modules.system.entity.SysConfigDO;
import com.zhul.erp.modules.system.repository.SysConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 发给客户的单据导出文件名：「公司英文名 - 单据名 - 客户名称 - 单据编号」。
 * 公司英文名取系统参数 document.company.name-en（租户值优先，没配置时省略这一段）；
 * 客户名称简称优先，超过 60 字截断；文件名里不允许的字符换成空格。
 */
@Component
@RequiredArgsConstructor
public class DocFileNames {

    public static final String CONFIG_COMPANY_NAME_EN = "document.company.name-en";
    public static final String QUOTATION = "Quotation";
    public static final String PI = "Invoice";
    public static final String CI = "CI";
    public static final String PL = "PACKING LIST";

    private static final int CUSTOMER_MAX = 60;

    private final SysConfigMapper sysConfigMapper;

    public String of(Integer tenantId, String docLabel, CustomerDO customer, String docNo) {
        return join(companyName(tenantId == null ? 0 : tenantId), docLabel, customerName(customer), docNo);
    }

    /** 各段去掉非法字符后用「 - 」连接，空段省略 */
    static String join(String company, String docLabel, String customer, String docNo) {
        String c = clean(customer);
        if (c.length() > CUSTOMER_MAX) {
            c = c.substring(0, CUSTOMER_MAX).trim();
        }
        List<String> parts = new ArrayList<>(4);
        for (String p : new String[] {clean(company), docLabel, c, clean(docNo)}) {
            if (p != null && !p.isEmpty()) {
                parts.add(p);
            }
        }
        return String.join(" - ", parts);
    }

    static String customerName(CustomerDO c) {
        if (c == null) {
            return "";
        }
        if (c.getShortName() != null && !c.getShortName().isBlank()) {
            return c.getShortName();
        }
        return c.getName() == null || c.getName().isBlank() ? c.getContactName() : c.getName();
    }

    private String companyName(int tenantId) {
        return sysConfigMapper.selectList(new LambdaQueryWrapper<SysConfigDO>()
                        .eq(SysConfigDO::getConfigKey, CONFIG_COMPANY_NAME_EN)
                        .in(SysConfigDO::getTenantId, List.of(0, tenantId))
                        .isNull(SysConfigDO::getDeletedAt))
                .stream().max(Comparator.comparing(SysConfigDO::getTenantId))
                .map(SysConfigDO::getConfigValue).orElse("");
    }

    private static String clean(String s) {
        return s == null ? "" : s.replaceAll("[\\\\/:*?\"<>|\\r\\n]+", " ").replaceAll("\\s+", " ").trim();
    }
}
