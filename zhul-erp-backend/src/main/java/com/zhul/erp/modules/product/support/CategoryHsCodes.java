package com.zhul.erp.modules.product.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.modules.product.constants.ProductConstants;
import com.zhul.erp.modules.product.constants.ProductErrorCodes;
import com.zhul.erp.modules.product.entity.ProductCategoryDO;
import com.zhul.erp.modules.product.repository.ProductCategoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

/**
 * 品类默认海关编码（openspec content-ai-batch-hs）：品类自己填了用自己的，细分品类没填时沿用上级品类的。
 */
@Component
@RequiredArgsConstructor
public class CategoryHsCodes {

    private static final Pattern HS_CODE = Pattern.compile("^\\d{6,10}$");

    private final ProductCategoryMapper categoryMapper;

    /** 品类所在的默认编码与来源品类 */
    public record Effective(String hsCode, ProductCategoryDO from) {
    }

    /** 去掉点和空格后须为 6~10 位数字，只存数字；空表示未填 */
    public static String normalize(String raw) {
        String digits = raw == null ? "" : raw.replaceAll("[.\\s]", "");
        if (digits.isEmpty()) {
            return "";
        }
        if (!HS_CODE.matcher(digits).matches()) {
            throw BizException.of(ProductErrorCodes.HS_CODE_INVALID, "海关编码格式不合法，去掉点和空格后应为 6 到 10 位数字");
        }
        return digits;
    }

    /** 前 6 位；编码为空时为空 */
    public static String hs6(String hsCode) {
        return StringUtils.hasText(hsCode) && hsCode.length() >= 6 ? hsCode.substring(0, 6) : "";
    }

    /** 品类的默认编码：自己没填时看上级；都没填（或品类不存在）时为空 */
    public Effective of(Long categoryId) {
        ProductCategoryDO c = active(categoryId);
        if (c == null) {
            return new Effective("", null);
        }
        if (StringUtils.hasText(c.getHsCode())) {
            return new Effective(c.getHsCode(), c);
        }
        ProductCategoryDO parent = active(c.getParentId());
        return parent != null && StringUtils.hasText(parent.getHsCode())
                ? new Effective(parent.getHsCode(), parent) : new Effective("", null);
    }

    private ProductCategoryDO active(Long id) {
        if (id == null) {
            return null;
        }
        return categoryMapper.selectOne(new LambdaQueryWrapper<ProductCategoryDO>()
                .eq(ProductCategoryDO::getId, id)
                .eq(ProductCategoryDO::getTenantId, ProductConstants.PLATFORM_TENANT_ID)
                .isNull(ProductCategoryDO::getDeletedAt));
    }
}
