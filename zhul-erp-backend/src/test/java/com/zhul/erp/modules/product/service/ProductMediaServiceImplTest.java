package com.zhul.erp.modules.product.service;

import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.product.constants.ProductErrorCodes;
import com.zhul.erp.modules.product.entity.ProductMediaDO;
import com.zhul.erp.modules.product.repository.ProductMediaMapper;
import com.zhul.erp.modules.product.service.impl.ProductMediaServiceImpl;
import com.zhul.erp.modules.product.support.PlatformScopeGuard;
import com.zhul.erp.modules.product.support.ProductFinder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductMediaServiceImplTest {

    @Mock
    private ProductMediaMapper mediaMapper;
    @Mock
    private ProductMediaStorageService storage;
    @Mock
    private ProductFinder productFinder;
    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ValueOperations<String, String> ops;

    private ProductMediaServiceImpl service;
    private final MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", new byte[]{1});

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(0);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.increment(any())).thenReturn(1L);
        when(storage.store(any(), anyInt())).thenReturn(new ProductMediaStorageService.StoredMedia("/uploads/product/x.png", 1));
        service = new ProductMediaServiceImpl(mediaMapper, storage, productFinder, new PlatformScopeGuard(), redis);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void uploadStillWorksWhenRedisRateLimiterIsDown() {
        when(ops.increment(any())).thenThrow(new RedisConnectionFailureException("down"));

        service.upload(1L, file, 1, null, null);

        verify(mediaMapper).insert(any(ProductMediaDO.class));
    }

    @Test
    void firstUploadInAWindowStartsTheExpiry() {
        service.upload(1L, file, 1, null, null);

        verify(redis).expire(any(), any(java.time.Duration.class));
    }

    @Test
    void thirtyFirstUploadInTheWindowIsRefused() {
        when(ops.increment(any())).thenReturn(31L);

        BizException e = assertThrows(BizException.class, () -> service.upload(1L, file, 1, null, null));

        assertEquals(ProductErrorCodes.RATE_LIMITED, e.getErrorCode());
        verifyNoInteractions(storage);
    }

    @Test
    void savedFileIsRemovedWhenTheDatabaseInsertFails() {
        when(mediaMapper.insert(any(ProductMediaDO.class))).thenThrow(new IllegalStateException("db down"));

        assertThrows(IllegalStateException.class, () -> service.upload(1L, file, 1, null, null));

        verify(storage).deleteQuietly("/uploads/product/x.png");
    }

    @Test
    void savedFileIsRemovedWhenTheProductIsDeletedMeanwhile() {
        when(productFinder.lockActive(1L)).thenThrow(BizException.of(ProductErrorCodes.PRODUCT_NOT_FOUND, "商品不存在"));

        assertThrows(BizException.class, () -> service.upload(1L, file, 1, null, null));

        verify(storage).deleteQuietly("/uploads/product/x.png");
        verify(mediaMapper, never()).insert(any(ProductMediaDO.class));
    }

    @Test
    void missingProductStopsBeforeAnythingIsWritten() {
        when(productFinder.active(1L)).thenThrow(BizException.of(ProductErrorCodes.PRODUCT_NOT_FOUND, "商品不存在"));

        assertThrows(BizException.class, () -> service.upload(1L, file, 1, null, null));

        verifyNoInteractions(storage);
    }

    @Test
    void invalidMediaTypeStopsBeforeStoring() {
        BizException e = assertThrows(BizException.class, () -> service.upload(1L, file, null, null, null));

        assertEquals(ProductErrorCodes.PARAM_INVALID, e.getErrorCode());
        verify(storage, never()).store(any(), eq(1));
    }
}
