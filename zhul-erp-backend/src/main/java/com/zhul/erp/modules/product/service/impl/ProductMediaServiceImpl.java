package com.zhul.erp.modules.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.framework.security.SecurityUtils;
import com.zhul.erp.modules.product.constants.ProductConstants;
import com.zhul.erp.modules.product.constants.ProductErrorCodes;
import com.zhul.erp.modules.product.dto.MediaVO;
import com.zhul.erp.modules.product.dto.RegisterMediaRequest;
import com.zhul.erp.modules.product.dto.UpdateMediaRequest;
import com.zhul.erp.modules.product.entity.ProductMediaDO;
import com.zhul.erp.modules.product.repository.ProductMediaMapper;
import com.zhul.erp.modules.product.service.ProductMediaService;
import com.zhul.erp.modules.product.service.ProductMediaStorageService;
import com.zhul.erp.modules.product.support.PlatformScopeGuard;
import com.zhul.erp.modules.product.support.ProductFinder;
import com.zhul.erp.modules.product.support.TextRules;
import com.zhul.erp.modules.product.support.UrlRules;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductMediaServiceImpl implements ProductMediaService {

    private static final int STORAGE_UPLOAD = 1;
    private static final int STORAGE_EXTERNAL = 2;
    /** 上传限流：每个账号每分钟最多 30 次 */
    private static final int UPLOAD_LIMIT_PER_MINUTE = 30;
    private static final String UPLOAD_LIMIT_KEY = "zhul:erp:limit:product:upload:";

    private final ProductMediaMapper mediaMapper;
    private final ProductMediaStorageService storageService;
    private final ProductFinder productFinder;
    private final PlatformScopeGuard platformScopeGuard;
    private final StringRedisTemplate redis;

    @Override
    public List<MediaVO> list(Long productId) {
        productFinder.active(productId);
        List<ProductMediaDO> rows = mediaMapper.selectList(new LambdaQueryWrapper<ProductMediaDO>()
                .eq(ProductMediaDO::getTenantId, ProductConstants.PLATFORM_TENANT_ID)
                .eq(ProductMediaDO::getProductId, productId)
                .isNull(ProductMediaDO::getDeletedAt)
                .orderByDesc(ProductMediaDO::getIsMain)
                .orderByAsc(ProductMediaDO::getSortOrder)
                .orderByAsc(ProductMediaDO::getId));
        List<MediaVO> result = new ArrayList<>(rows.size());
        for (ProductMediaDO row : rows) {
            result.add(toVO(row));
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MediaVO register(Long productId, RegisterMediaRequest req) {
        platformScopeGuard.requirePlatform();
        productFinder.lockActive(productId);
        int type = mediaType(req.getMediaType());
        String fileUrl = TextRules.required(req.getFileUrl(), "文件地址", 512);
        requireSafeUrl(fileUrl);
        String coverUrl = TextRules.optional(req.getCoverUrl(), "封面地址", 512);
        if (!coverUrl.isEmpty()) {
            requireSafeUrl(coverUrl);
        }

        ProductMediaDO row = newRow(productId, type, fileUrl, STORAGE_EXTERNAL, 0L,
                TextRules.optional(req.getTitle(), "标题", 128), req.getSortOrder());
        row.setCoverUrl(coverUrl);
        row.setSource(TextRules.optional(req.getSource(), "来源", 128));
        mediaMapper.insert(row);
        if (Boolean.TRUE.equals(req.getSetMain())) {
            applyMain(productId, row.getId(), type);
            row.setIsMain(1);
        }
        return toVO(row);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MediaVO upload(Long productId, MultipartFile file, Integer mediaType, String title, Boolean setMain) {
        // 先校验身份再碰文件：租户账号上传不保存任何内容
        platformScopeGuard.requirePlatform();
        checkUploadRate();
        int type = mediaType(mediaType);
        String cleanTitle = TextRules.optional(title, "标题", 128);
        // 商品不存在就不必落盘；大文件写盘期间不持有商品行锁，写完再加锁入库
        productFinder.active(productId);

        ProductMediaStorageService.StoredMedia stored = storageService.store(file, type);
        try {
            productFinder.lockActive(productId);
            ProductMediaDO row = newRow(productId, type, stored.url(), STORAGE_UPLOAD, stored.size(), cleanTitle, null);
            mediaMapper.insert(row);
            if (Boolean.TRUE.equals(setMain)) {
                applyMain(productId, row.getId(), type);
                row.setIsMain(1);
            }
            return toVO(row);
        } catch (RuntimeException e) {
            // 入库失败（含商品被并发删除）时不留下孤儿文件
            storageService.deleteQuietly(stored.url());
            throw e;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MediaVO update(Long productId, Long itemId, UpdateMediaRequest req) {
        platformScopeGuard.requirePlatform();
        productFinder.lockActive(productId);
        ProductMediaDO current = owned(productId, itemId);
        String coverUrl = req.getCoverUrl() == null ? current.getCoverUrl()
                : TextRules.optional(req.getCoverUrl(), "封面地址", 512);
        if (!coverUrl.isEmpty()) {
            requireSafeUrl(coverUrl);
        }
        ProductMediaDO change = new ProductMediaDO();
        change.setId(itemId);
        change.setTitle(req.getTitle() == null ? current.getTitle() : TextRules.optional(req.getTitle(), "标题", 128));
        change.setCoverUrl(coverUrl);
        change.setSource(req.getSource() == null ? current.getSource() : TextRules.optional(req.getSource(), "来源", 128));
        change.setSortOrder(req.getSortOrder() == null ? current.getSortOrder() : req.getSortOrder());
        mediaMapper.updateById(change);
        return toVO(owned(productId, itemId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long productId, Long itemId) {
        platformScopeGuard.requirePlatform();
        productFinder.lockActive(productId);
        owned(productId, itemId);
        // 软删除，文件保留；被删除的主图同时取消主图标记，避免"已删除的图片仍占着主图"
        mediaMapper.update(new ProductMediaDO(), new LambdaUpdateWrapper<ProductMediaDO>()
                .eq(ProductMediaDO::getId, itemId)
                .set(ProductMediaDO::getDeletedAt, LocalDateTime.now())
                .set(ProductMediaDO::getIsMain, 0));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MediaVO setMain(Long productId, Long itemId) {
        platformScopeGuard.requirePlatform();
        // 锁商品行，两个人同时给同一商品设不同主图时串行执行，最终只有一张主图
        productFinder.lockActive(productId);
        ProductMediaDO row = owned(productId, itemId);
        applyMain(productId, itemId, row.getMediaType());
        return toVO(owned(productId, itemId));
    }

    /** 调用方必须已经锁住商品行。同一事务内先取消原主图再设置新主图 */
    private void applyMain(Long productId, Long itemId, int mediaType) {
        if (mediaType != ProductMediaStorageService.MEDIA_IMAGE) {
            throw BizException.of(ProductErrorCodes.MEDIA_NOT_IMAGE, "只有图片可以设为主图");
        }
        mediaMapper.update(new ProductMediaDO(), new LambdaUpdateWrapper<ProductMediaDO>()
                .eq(ProductMediaDO::getTenantId, ProductConstants.PLATFORM_TENANT_ID)
                .eq(ProductMediaDO::getProductId, productId)
                .eq(ProductMediaDO::getIsMain, 1)
                .ne(ProductMediaDO::getId, itemId)
                .isNull(ProductMediaDO::getDeletedAt)
                .set(ProductMediaDO::getIsMain, 0));
        ProductMediaDO change = new ProductMediaDO();
        change.setId(itemId);
        change.setIsMain(1);
        mediaMapper.updateById(change);
    }

    private void checkUploadRate() {
        String key = UPLOAD_LIMIT_KEY + SecurityUtils.getCurrentUsername();
        try {
            Long count = redis.opsForValue().increment(key);
            if (count != null && count == 1) {
                redis.expire(key, Duration.ofMinutes(1));
            }
            if (count != null && count > UPLOAD_LIMIT_PER_MINUTE) {
                throw BizException.of(ProductErrorCodes.RATE_LIMITED, "上传过于频繁，请稍后再试");
            }
        } catch (BizException e) {
            throw e;
        } catch (RuntimeException e) {
            // Redis 故障时不阻断上传，只是暂时没有限流
            log.warn("上传限流检查失败，已放行，key={}", key, e);
        }
    }

    private static int mediaType(Integer value) {
        if (value == null || (value != ProductMediaStorageService.MEDIA_IMAGE && value != ProductMediaStorageService.MEDIA_VIDEO)) {
            throw BizException.of(ProductErrorCodes.PARAM_INVALID, "媒体类型只能是 1（图片）或 2（视频）");
        }
        return value;
    }

    private static void requireSafeUrl(String url) {
        if (!UrlRules.isSafe(url)) {
            throw BizException.of(ProductErrorCodes.MEDIA_URL_INVALID,
                    "地址格式不合法，只允许 http://、https:// 或以 / 开头的站内路径");
        }
    }

    private static ProductMediaDO newRow(Long productId, int type, String fileUrl, int storageType, long size,
                                         String title, Integer sortOrder) {
        ProductMediaDO row = new ProductMediaDO();
        row.setTenantId(ProductConstants.PLATFORM_TENANT_ID);
        row.setProductId(productId);
        row.setMediaType(type);
        row.setFileUrl(fileUrl);
        row.setStorageType(storageType);
        row.setCoverUrl("");
        row.setTitle(title);
        row.setFileSize(size);
        row.setIsMain(0);
        row.setSource("");
        row.setSortOrder(sortOrder == null ? 0 : sortOrder);
        return row;
    }

    private ProductMediaDO owned(Long productId, Long itemId) {
        ProductMediaDO row = itemId == null ? null : mediaMapper.selectOne(new LambdaQueryWrapper<ProductMediaDO>()
                .eq(ProductMediaDO::getId, itemId)
                .eq(ProductMediaDO::getTenantId, ProductConstants.PLATFORM_TENANT_ID)
                .eq(ProductMediaDO::getProductId, productId)
                .isNull(ProductMediaDO::getDeletedAt));
        if (row == null) {
            throw BizException.of(ProductErrorCodes.CONTENT_NOT_FOUND, "图片或视频不存在");
        }
        return row;
    }

    private static MediaVO toVO(ProductMediaDO row) {
        MediaVO vo = new MediaVO();
        vo.setId(row.getId());
        vo.setProductId(row.getProductId());
        vo.setMediaType(row.getMediaType());
        vo.setFileUrl(row.getFileUrl());
        vo.setStorageType(row.getStorageType());
        vo.setCoverUrl(row.getCoverUrl());
        vo.setTitle(row.getTitle());
        vo.setFileSize(row.getFileSize());
        vo.setIsMain(row.getIsMain());
        vo.setSource(row.getSource());
        vo.setSortOrder(row.getSortOrder());
        return vo;
    }
}
