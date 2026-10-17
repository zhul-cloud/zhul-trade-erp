package com.zhul.erp.modules.warehouse.support;

import com.zhul.erp.modules.attachment.service.AttachmentService;
import com.zhul.erp.modules.attachment.support.ModelFolderResolver;
import com.zhul.erp.modules.warehouse.entity.ShootTaskDO;
import com.zhul.erp.modules.warehouse.repository.ShootTaskMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;

/** 实拍任务的素材按任务的品牌与型号归档（存储目录 media/{品牌}/{型号}/） */
@Component
@RequiredArgsConstructor
public class ShootModelFolder implements ModelFolderResolver {

    private final ShootTaskMapper taskMapper;

    @Override
    public String ownerType() {
        return AttachmentService.SHOOT;
    }

    @Override
    public Optional<Model> modelOf(Long ownerId, int tenantId) {
        ShootTaskDO t = taskMapper.selectById(ownerId);
        if (t == null || t.getDeletedAt() != null || !Objects.equals(t.getTenantId(), tenantId)) {
            return Optional.empty();
        }
        return Optional.of(new Model(t.getBrand(), t.getModel()));
    }
}
