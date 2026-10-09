package com.zhul.erp.modules.system;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhul.erp.modules.inquiry.InquiryContractSupport;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * spec system/navigation：按部门分组的侧边栏、调整后权限不变；inquiry/sourcing-quote「兼职采购账号」。
 * 侧边栏口径同前端 toProLayoutMenu：菜单树去掉按钮与隐藏节点，只保留 /api/v1/auth/menus 里的路径。
 */
class NavigationContractTest extends InquiryContractSupport {

    private Map<String, List<String>> sidebar(String token) throws Exception {
        Set<String> allowed = new HashSet<>();
        ok(call(get("/api/v1/auth/menus"), token)).forEach(n -> allowed.add(n.asText()));
        List<JsonNode> roots = new ArrayList<>();
        ok(call(get("/api/v1/system/menus"), token)).forEach(roots::add);
        Map<String, List<String>> out = new LinkedHashMap<>();
        for (JsonNode g : visible(roots, allowed)) {
            List<String> children = new ArrayList<>();
            List<JsonNode> kids = new ArrayList<>();
            g.path("children").forEach(kids::add);
            visible(kids, allowed).forEach(c -> children.add(c.path("name").asText()));
            out.put(g.path("name").asText(), children);
        }
        return out;
    }

    private static List<JsonNode> visible(List<JsonNode> nodes, Set<String> allowed) {
        return nodes.stream()
                .filter(n -> n.path("type").asInt() != 3 && n.path("isHidden").asInt() != 1 && allowed.contains(n.path("path").asText()))
                .sorted((a, b) -> Integer.compare(a.path("sort").asInt(), b.path("sort").asInt()))
                .toList();
    }

    @Test
    void partTimerSeesOnlyPurchaseBoardAndMyQuotes() throws Exception {
        String pt = token("it_wang");
        assertEquals(Map.of("采购管理", List.of("兼职看板", "我的询价")), sidebar(pt));
        assertEquals(403, perform(json(post("/api/v1/inquiry/customer-inquiries/page"), "{}"), pt).getStatus(), "兼职采购不能访问客户询盘");
    }

    @Test
    void salesAndBuyerKeepTheirPagesUnderDepartmentGroups() throws Exception {
        loginWithResources("it_nav_sales", 100001, 100053, 100051, 100073, 100078, 100079, 100056);
        Map<String, List<String>> sales = sidebar(token("it_nav_sales"));
        assertEquals(List.of("工作台", "业务管理", "采购管理"), new ArrayList<>(sales.keySet()));
        assertEquals(List.of("商机", "客户询盘", "报价单", "PI", "销售订单"), sales.get("业务管理"));
        assertEquals(List.of("历史询价"), sales.get("采购管理"));
        assertEquals(200, perform(json(post("/api/v1/inquiry/customer-inquiries/page"), "{}"), token("it_nav_sales")).getStatus(), "调整后仍能访问");

        loginWithResources("it_nav_buyer", 100055, 100056, 100087, 100088, 100062);
        assertEquals(Map.of("采购管理", List.of("我的询价", "历史询价", "采购需求", "采购单", "供应商")), sidebar(token("it_nav_buyer")));
    }

    @Test
    void adminSeesDepartmentGroupsWithoutPlaceholders() throws Exception {
        loginAsAdmin("it_nav_admin");
        Map<String, List<String>> all = sidebar(token("it_nav_admin"));
        assertEquals(List.of("工作台", "业务管理", "采购管理", "财务管理", "商品资料", "业务设置", "系统管理", "租户管理"),
                new ArrayList<>(all.keySet()));
        assertEquals(List.of("商机", "客户", "客户询盘", "报价单", "PI", "销售订单"), all.get("业务管理"), "商机统计从侧边栏隐藏");
        assertEquals(List.of("兼职看板", "询价分配", "我的询价", "历史询价", "采购需求", "采购单", "供应商"), all.get("采购管理"));
        assertEquals(List.of("收款管理"), all.get("财务管理"));
        assertEquals(List.of("定价策略", "汇率", "收款账户", "单据模版", "单据编号"), all.get("业务设置"));
        assertEquals(List.of("用户", "角色", "部门", "岗位", "菜单", "字典", "系统设置", "操作日志", "登录日志"), all.get("系统管理"));
    }
}
