package com.zhul.erp.framework.security;

import com.zhul.erp.modules.system.entity.ResourceDO;
import com.zhul.erp.modules.system.repository.ResourceMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PermissionCheckerTest {

    private static final String PERM_CODE = "product:brand:add";
    private static final int RESOURCE_ID = 110101;

    @Mock
    private ResourceMapper resourceMapper;
    @Mock
    private EffectivePermissionResolver permissionResolver;

    private PermissionChecker checker;

    @BeforeEach
    void setUp() {
        checker = new PermissionChecker(resourceMapper, permissionResolver);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static ResourceDO resource() {
        ResourceDO r = new ResourceDO();
        r.setId(RESOURCE_ID);
        r.setPermission(PERM_CODE);
        r.setType(3);
        return r;
    }

    @Test
    void unauthenticatedIsDenied() {
        SecurityContextHolder.clearContext();
        assertFalse(checker.has(PERM_CODE));
    }

    @Test
    void unknownPermissionCodeIsDenied() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("someone", null, List.of()));
        lenient().when(resourceMapper.selectOne(any())).thenReturn(null);
        assertFalse(checker.has("no:such:code"));
    }

    @Test
    void unrestrictedAccountIsGranted() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("platform", null, List.of()));
        when(resourceMapper.selectOne(any())).thenReturn(resource());
        when(permissionResolver.resolveAllowedResourceIds("platform")).thenReturn(null);

        assertTrue(checker.has(PERM_CODE));
    }

    @Test
    void grantedWhenResourceInAllowedSet() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("tenant_admin", null, List.of()));
        when(resourceMapper.selectOne(any())).thenReturn(resource());
        when(permissionResolver.resolveAllowedResourceIds("tenant_admin")).thenReturn(Set.of(RESOURCE_ID, 100001));

        assertTrue(checker.has(PERM_CODE));
    }

    @Test
    void deniedWhenResourceNotInAllowedSet() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("tenant_staff", null, List.of()));
        when(resourceMapper.selectOne(any())).thenReturn(resource());
        when(permissionResolver.resolveAllowedResourceIds("tenant_staff")).thenReturn(Set.of(100001));

        assertFalse(checker.has(PERM_CODE));
    }

    @Test
    void deniedWhenAllowedSetIsEmpty() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("nobody_role", null, List.of()));
        when(resourceMapper.selectOne(any())).thenReturn(resource());
        when(permissionResolver.resolveAllowedResourceIds("nobody_role")).thenReturn(Set.of());

        assertFalse(checker.has(PERM_CODE));
    }
}
