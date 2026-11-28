package com.zhul.erp.modules.website.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.common.utils.AesUtils;
import com.zhul.erp.modules.website.constants.WebsiteConstants;
import com.zhul.erp.modules.website.dto.SecretVO;
import com.zhul.erp.modules.website.entity.WebsiteSiteSecretDO;
import com.zhul.erp.modules.website.repository.WebsiteSiteSecretMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 站点凭据：AES-256-GCM 加密入库（AesUtils），页面只拿到「已配置」与末四位。
 * 明文只在提交给发布服务时于内存中解密（{@link #plain}），不写日志、不进返回值。
 */
@Component
@RequiredArgsConstructor
public class SiteSecrets {

    private final WebsiteSiteSecretMapper mapper;
    private final AesUtils aes;

    public Map<String, SecretVO> status(Long siteId) {
        Map<String, SecretVO> out = new HashMap<>(4);
        for (WebsiteSiteSecretDO s : list(siteId).values()) {
            out.put(s.getSecretType(), new SecretVO(true, s.getLast4()));
        }
        return out;
    }

    public boolean configured(Long siteId, String type) {
        return list(siteId).containsKey(type);
    }

    /** 保存（新增或覆盖）一项凭据 */
    public void save(int tenantId, Long siteId, String type, String plain) {
        String value = normalize(type, plain);
        WebsiteSiteSecretDO s = list(siteId).get(type);
        if (s == null) {
            s = new WebsiteSiteSecretDO();
            s.setTenantId(tenantId);
            s.setSiteId(siteId);
            s.setSecretType(type);
        }
        s.setCipherText(aes.encrypt(value));
        s.setLast4(last4(type, value));
        if (s.getId() == null) {
            mapper.insert(s);
        } else {
            mapper.updateById(s);
        }
    }

    /** 删除一项凭据（凭据表是基础配置，直接删除，不是业务数据） */
    public void remove(Long siteId, String type) {
        mapper.delete(new LambdaQueryWrapper<WebsiteSiteSecretDO>()
                .eq(WebsiteSiteSecretDO::getSiteId, siteId).eq(WebsiteSiteSecretDO::getSecretType, type));
    }

    /** 解密后的凭据，键为 SECRET_* 类型；只用于提交给发布服务 */
    public Map<String, String> plain(Long siteId) {
        Map<String, String> out = new HashMap<>(4);
        for (WebsiteSiteSecretDO s : list(siteId).values()) {
            out.put(s.getSecretType(), aes.decrypt(s.getCipherText()));
        }
        return out;
    }

    private Map<String, WebsiteSiteSecretDO> list(Long siteId) {
        Map<String, WebsiteSiteSecretDO> out = new HashMap<>(4);
        for (WebsiteSiteSecretDO s : mapper.selectList(new LambdaQueryWrapper<WebsiteSiteSecretDO>()
                .eq(WebsiteSiteSecretDO::getSiteId, siteId))) {
            out.put(s.getSecretType(), s);
        }
        return out;
    }

    /** 私钥统一为 LF 换行并以换行结尾（ssh 要求）；其他凭据去掉首尾空白 */
    static String normalize(String type, String plain) {
        if (WebsiteConstants.SECRET_DEPLOY_KEY.equals(type)) {
            return plain.replace("\r\n", "\n").strip() + "\n";
        }
        return plain.strip();
    }

    /** 末四位：私钥取正文（去掉 BEGIN/END 行与空白）的末四位，其他取明文末四位 */
    static String last4(String type, String value) {
        String body = value;
        if (WebsiteConstants.SECRET_DEPLOY_KEY.equals(type)) {
            StringBuilder b = new StringBuilder();
            for (String line : value.split("\n")) {
                if (!line.startsWith("-----")) {
                    b.append(line.strip());
                }
            }
            body = b.toString();
        }
        return body.length() <= 4 ? "" : body.substring(body.length() - 4);
    }
}
