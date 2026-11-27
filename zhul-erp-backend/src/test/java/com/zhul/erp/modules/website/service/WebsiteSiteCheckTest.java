package com.zhul.erp.modules.website.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.modules.product.repository.ProductCategoryMapper;
import com.zhul.erp.modules.website.constants.WebsiteConstants;
import com.zhul.erp.modules.website.dto.CheckItemVO;
import com.zhul.erp.modules.website.entity.WebsiteSiteDO;
import com.zhul.erp.modules.website.repository.WebsiteCategoryMapMapper;
import com.zhul.erp.modules.website.repository.WebsiteSiteMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** spec website-site「检测连接」：四项按固定顺序显示，部署私钥不对时网址照常检测；发布服务出错给出原因 */
class WebsiteSiteCheckTest {

    private final ObjectMapper om = new ObjectMapper();
    private final WebsiteSiteMapper siteMapper = mock(WebsiteSiteMapper.class);
    private final SiteSecrets secrets = mock(SiteSecrets.class);
    private final SitePublisherClient publisher = mock(SitePublisherClient.class);
    private final WebsiteSiteService service = new WebsiteSiteService(siteMapper, mock(WebsiteCategoryMapMapper.class),
            mock(ProductCategoryMapper.class), secrets, publisher, om);

    private WebsiteSiteDO site() {
        WebsiteSiteDO s = new WebsiteSiteDO();
        s.setId(7L);
        s.setTenantId(1);
        s.setUrl("https://fouwell.com");
        s.setPublishMode(WebsiteConstants.MODE_AUTO);
        s.setRepoUrl("https://github.com/example/site.git");
        s.setRepoBranch("main");
        s.setDeployHost("gcam.example.com");
        s.setDeployPort(18765);
        s.setDeployUser("u1");
        s.setDeployPath("www/x/public_html");
        when(siteMapper.selectById(7L)).thenReturn(s);
        when(secrets.plain(7L)).thenReturn(Map.of(WebsiteConstants.SECRET_REPO_TOKEN, "tok", WebsiteConstants.SECRET_DEPLOY_KEY, "key"));
        return s;
    }

    @SuppressWarnings("unchecked")
    @Test
    void deployKeyRejected_otherItemsStillShown_inFixedOrder() throws Exception {
        site();
        when(publisher.check(any())).thenReturn(new SitePublisherClient.Reply(200, om.readTree(
                "{\"items\":[{\"key\":\"url\",\"ok\":true,\"message\":\"通过\"},{\"key\":\"repo\",\"ok\":true,\"message\":\"通过\"},"
                        + "{\"key\":\"deploy\",\"ok\":false,\"message\":\"登录失败：密钥被拒绝\"},{\"key\":\"adapter\",\"ok\":true,\"message\":\"通过\"}]}")));
        List<CheckItemVO> items = service.check(7L);
        assertEquals(List.of("代码仓库", "适配脚本", "部署服务器", "网址"), items.stream().map(CheckItemVO::name).toList());
        assertEquals("登录失败：密钥被拒绝", items.get(2).message());
        assertEquals(List.of(true, true, false, true), items.stream().map(CheckItemVO::ok).toList());

        ArgumentCaptor<Map<String, Object>> body = ArgumentCaptor.forClass(Map.class);
        verify(publisher).check(body.capture());
        Map<String, Object> sent = body.getValue();
        assertEquals("fouwell.com", ((Map<String, Object>) sent.get("site")).get("code"));
        assertEquals(Map.of("repoToken", "tok", "deployKey", "key"), sent.get("secrets"));
    }

    @Test
    void missingItem_andPublisherError() throws Exception {
        site();
        when(publisher.check(any())).thenReturn(new SitePublisherClient.Reply(200, om.readTree("{\"items\":[]}")));
        assertEquals("没有检测结果", service.check(7L).get(0).message());

        when(publisher.check(any())).thenReturn(new SitePublisherClient.Reply(400, om.readTree("{\"detail\":\"部署目录不正确\"}")));
        assertEquals("检测失败：部署目录不正确", assertThrows(BizException.class, () -> service.check(7L)).getMessage());
    }
}
