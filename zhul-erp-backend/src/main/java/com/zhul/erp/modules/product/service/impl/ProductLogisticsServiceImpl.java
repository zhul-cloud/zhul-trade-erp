package com.zhul.erp.modules.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.modules.product.constants.ProductConstants;
import com.zhul.erp.modules.product.constants.ProductErrorCodes;
import com.zhul.erp.modules.product.dto.LogisticsVO;
import com.zhul.erp.modules.product.dto.SaveLogisticsRequest;
import com.zhul.erp.modules.product.entity.ProductLogisticsDO;
import com.zhul.erp.modules.product.repository.ProductLogisticsMapper;
import com.zhul.erp.modules.product.service.ProductLogisticsService;
import com.zhul.erp.modules.product.support.PlatformScopeGuard;
import com.zhul.erp.modules.product.support.ProductFinder;
import com.zhul.erp.modules.product.support.TextRules;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 精度规则：重量 3 位小数、尺寸 1 位小数，均按 HALF_UP 舍入；舍入后必须仍大于 0。
 */
@Service
@RequiredArgsConstructor
public class ProductLogisticsServiceImpl implements ProductLogisticsService {

    private static final int WEIGHT_SCALE = 3;
    private static final int DIMENSION_SCALE = 1;
    /** decimal(10,3) 与 decimal(10,1) 的整数部分最多 7 位 */
    private static final BigDecimal WEIGHT_LIMIT = new BigDecimal("10000000");
    /** 整数部分最多 9 位 */
    private static final BigDecimal DIMENSION_LIMIT = new BigDecimal("100000000");

    private final ProductLogisticsMapper logisticsMapper;
    private final ProductFinder productFinder;
    private final PlatformScopeGuard platformScopeGuard;

    @Override
    public LogisticsVO get(Long productId) {
        productFinder.active(productId);
        ProductLogisticsDO row = logisticsMapper.selectOne(new LambdaQueryWrapper<ProductLogisticsDO>()
                .eq(ProductLogisticsDO::getTenantId, ProductConstants.PLATFORM_TENANT_ID)
                .eq(ProductLogisticsDO::getProductId, productId)
                .isNull(ProductLogisticsDO::getDeletedAt));
        return row == null ? empty(productId) : toVO(row);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LogisticsVO save(Long productId, SaveLogisticsRequest req) {
        platformScopeGuard.requirePlatform();
        productFinder.lockActive(productId);

        BigDecimal net = amount(req.getNetWeightKg(), "净重", WEIGHT_SCALE, WEIGHT_LIMIT);
        BigDecimal gross = amount(req.getGrossWeightKg(), "毛重", WEIGHT_SCALE, WEIGHT_LIMIT);
        if (net != null && gross != null && gross.compareTo(net) < 0) {
            throw BizException.of(ProductErrorCodes.LOGISTICS_INVALID, "毛重不能小于净重");
        }
        BigDecimal length = amount(req.getLengthMm(), "单品长", DIMENSION_SCALE, DIMENSION_LIMIT);
        BigDecimal width = amount(req.getWidthMm(), "单品宽", DIMENSION_SCALE, DIMENSION_LIMIT);
        BigDecimal height = amount(req.getHeightMm(), "单品高", DIMENSION_SCALE, DIMENSION_LIMIT);
        BigDecimal packageLength = amount(req.getPackageLengthMm(), "包装长", DIMENSION_SCALE, DIMENSION_LIMIT);
        BigDecimal packageWidth = amount(req.getPackageWidthMm(), "包装宽", DIMENSION_SCALE, DIMENSION_LIMIT);
        BigDecimal packageHeight = amount(req.getPackageHeightMm(), "包装高", DIMENSION_SCALE, DIMENSION_LIMIT);
        Integer quantity = req.getPackageQuantity();
        if (quantity != null && quantity <= 0) {
            throw BizException.of(ProductErrorCodes.LOGISTICS_INVALID, "每包装件数必须大于 0");
        }
        int dangerous = req.getIsDangerous() == null ? 0 : req.getIsDangerous();
        if (dangerous != 0 && dangerous != 1) {
            throw BizException.of(ProductErrorCodes.PARAM_INVALID, "是否危险品只能是 0 或 1");
        }
        String packageType = TextRules.optional(req.getPackageType(), "包装类型", 32);
        String note = TextRules.optional(req.getShippingNote(), "运输备注", 255);

        // 唯一键含已软删除的行，所以先找（含已删除的），有就整体覆盖，没有才新建
        ProductLogisticsDO existing = logisticsMapper.selectOne(new LambdaQueryWrapper<ProductLogisticsDO>()
                .eq(ProductLogisticsDO::getTenantId, ProductConstants.PLATFORM_TENANT_ID)
                .eq(ProductLogisticsDO::getProductId, productId));
        if (existing == null) {
            ProductLogisticsDO row = new ProductLogisticsDO();
            row.setTenantId(ProductConstants.PLATFORM_TENANT_ID);
            row.setProductId(productId);
            row.setNetWeightKg(net);
            row.setGrossWeightKg(gross);
            row.setLengthMm(length);
            row.setWidthMm(width);
            row.setHeightMm(height);
            row.setPackageType(packageType);
            row.setPackageLengthMm(packageLength);
            row.setPackageWidthMm(packageWidth);
            row.setPackageHeightMm(packageHeight);
            row.setPackageQuantity(quantity);
            row.setIsDangerous(dangerous);
            row.setShippingNote(note);
            logisticsMapper.insert(row);
        } else {
            // 数值可能要写回 NULL，实体更新会跳过 null，所以全部字段用 set；空实体用来触发审计字段填充
            logisticsMapper.update(new ProductLogisticsDO(), new LambdaUpdateWrapper<ProductLogisticsDO>()
                    .eq(ProductLogisticsDO::getId, existing.getId())
                    .set(ProductLogisticsDO::getNetWeightKg, net)
                    .set(ProductLogisticsDO::getGrossWeightKg, gross)
                    .set(ProductLogisticsDO::getLengthMm, length)
                    .set(ProductLogisticsDO::getWidthMm, width)
                    .set(ProductLogisticsDO::getHeightMm, height)
                    .set(ProductLogisticsDO::getPackageType, packageType)
                    .set(ProductLogisticsDO::getPackageLengthMm, packageLength)
                    .set(ProductLogisticsDO::getPackageWidthMm, packageWidth)
                    .set(ProductLogisticsDO::getPackageHeightMm, packageHeight)
                    .set(ProductLogisticsDO::getPackageQuantity, quantity)
                    .set(ProductLogisticsDO::getIsDangerous, dangerous)
                    .set(ProductLogisticsDO::getShippingNote, note)
                    .set(ProductLogisticsDO::getDeletedAt, null));
        }
        return get(productId);
    }

    /** 未填写返回 null；填写的必须大于 0，按 scale 位 HALF_UP 舍入后仍要大于 0，且不能超出列的容量 */
    private static BigDecimal amount(BigDecimal value, String label, int scale, BigDecimal limit) {
        if (value == null) {
            return null;
        }
        BigDecimal rounded = value.setScale(scale, RoundingMode.HALF_UP);
        if (value.signum() <= 0 || rounded.signum() <= 0) {
            throw BizException.of(ProductErrorCodes.LOGISTICS_INVALID, label + "必须大于 0");
        }
        if (rounded.compareTo(limit) >= 0) {
            throw BizException.of(ProductErrorCodes.LOGISTICS_INVALID, label + "数值过大");
        }
        return rounded;
    }

    private static LogisticsVO empty(Long productId) {
        LogisticsVO vo = new LogisticsVO();
        vo.setProductId(productId);
        vo.setIsDangerous(0);
        vo.setPackageType("");
        vo.setShippingNote("");
        return vo;
    }

    private static LogisticsVO toVO(ProductLogisticsDO row) {
        LogisticsVO vo = new LogisticsVO();
        vo.setId(row.getId());
        vo.setProductId(row.getProductId());
        vo.setNetWeightKg(row.getNetWeightKg());
        vo.setGrossWeightKg(row.getGrossWeightKg());
        vo.setLengthMm(row.getLengthMm());
        vo.setWidthMm(row.getWidthMm());
        vo.setHeightMm(row.getHeightMm());
        vo.setPackageType(row.getPackageType());
        vo.setPackageLengthMm(row.getPackageLengthMm());
        vo.setPackageWidthMm(row.getPackageWidthMm());
        vo.setPackageHeightMm(row.getPackageHeightMm());
        vo.setPackageQuantity(row.getPackageQuantity());
        vo.setIsDangerous(row.getIsDangerous());
        vo.setShippingNote(row.getShippingNote());
        return vo;
    }
}
