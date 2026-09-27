package com.zhul.erp.modules.masterdata.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.modules.masterdata.constants.SupplierConstants;
import com.zhul.erp.modules.masterdata.dto.SupplierAttachmentRequest;
import com.zhul.erp.modules.masterdata.dto.SupplierAttachmentVO;
import com.zhul.erp.modules.masterdata.entity.SupplierAttachmentDO;
import com.zhul.erp.modules.masterdata.repository.SupplierAttachmentMapper;
import com.zhul.erp.modules.masterdata.support.SupplierAttachmentStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 供应商附件的保存与读取。按 id 合并：带 id 的保留（可改类型），不带 id 的按上传接口返回的 fileKey 新增，
 * 未出现的软删除。文件大小与类型以磁盘上的文件为准，不信任请求。
 */
@Component
@RequiredArgsConstructor
public class SupplierAttachmentSync {

    private final SupplierAttachmentMapper attachmentMapper;
    private final SupplierAttachmentStorage storage;

    public void merge(int tenantId, long supplierId, List<SupplierAttachmentRequest> reqs) {
        if (reqs.size() > SupplierConstants.MAX_ATTACHMENTS) {
            throw new BizException("每个供应商最多 " + SupplierConstants.MAX_ATTACHMENTS + " 个附件");
        }
        Map<Long, SupplierAttachmentDO> existing = new HashMap<>(16);
        for (SupplierAttachmentDO row : activeRows(List.of(supplierId))) {
            existing.put(row.getId(), row);
        }
        Set<Long> kept = new HashSet<>(16);
        for (SupplierAttachmentRequest req : reqs) {
            if (req.getId() != null) {
                SupplierAttachmentDO row = existing.get(req.getId());
                if (row == null || !kept.add(req.getId())) {
                    throw new BizException("附件不存在或已被删除，请刷新后重试");
                }
                if (!row.getCategory().equals(req.getCategory())) {
                    row.setCategory(req.getCategory());
                    attachmentMapper.updateById(row);
                }
                continue;
            }
            Path file = storage.resolveOwned(req.getFileKey(), tenantId);
            SupplierAttachmentDO row = new SupplierAttachmentDO();
            row.setTenantId(tenantId);
            row.setSupplierId(supplierId);
            row.setCategory(req.getCategory());
            row.setFileKey(req.getFileKey());
            row.setFileName(SupplierAttachmentStorage.cleanFileName(req.getFileName(),
                    req.getFileKey().substring(req.getFileKey().lastIndexOf('.') + 1)));
            row.setContentType(SupplierAttachmentStorage.contentTypeOf(req.getFileKey()));
            try {
                row.setFileSize(Files.size(file));
            } catch (IOException e) {
                throw new UncheckedIOException("读取附件失败", e);
            }
            attachmentMapper.insert(row);
        }
        LocalDateTime now = LocalDateTime.now();
        for (SupplierAttachmentDO row : existing.values()) {
            if (!kept.contains(row.getId())) {
                row.setDeletedAt(now);
                attachmentMapper.updateById(row);
            }
        }
    }

    public Map<Long, List<SupplierAttachmentVO>> load(List<Long> supplierIds) {
        if (supplierIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<SupplierAttachmentVO>> result = new LinkedHashMap<>(16);
        activeRows(supplierIds).stream()
                .sorted(Comparator.comparing(SupplierAttachmentDO::getCategory).thenComparing(SupplierAttachmentDO::getId))
                .forEach(row -> result.computeIfAbsent(row.getSupplierId(), k -> new ArrayList<>(4)).add(toVo(row)));
        return result;
    }

    /** 下载用：附件必须属于该租户的该供应商且未删除 */
    public SupplierAttachmentDO getForDownload(int tenantId, long supplierId, long attachmentId) {
        SupplierAttachmentDO row = attachmentMapper.selectById(attachmentId);
        if (row == null || row.getDeletedAt() != null || row.getTenantId() != tenantId
                || row.getSupplierId() != supplierId) {
            throw new BizException("附件不存在");
        }
        return row;
    }

    public Path fileOf(SupplierAttachmentDO row) {
        return storage.resolveOwned(row.getFileKey(), row.getTenantId());
    }

    private SupplierAttachmentVO toVo(SupplierAttachmentDO row) {
        SupplierAttachmentVO vo = new SupplierAttachmentVO();
        vo.setId(row.getId());
        vo.setCategory(row.getCategory());
        vo.setFileName(row.getFileName());
        vo.setFileSize(row.getFileSize());
        vo.setContentType(row.getContentType());
        vo.setCreateBy(row.getCreateBy());
        vo.setCreateTime(row.getCreateTime());
        return vo;
    }

    private List<SupplierAttachmentDO> activeRows(List<Long> supplierIds) {
        return attachmentMapper.selectList(new LambdaQueryWrapper<SupplierAttachmentDO>()
                .in(SupplierAttachmentDO::getSupplierId, supplierIds)
                .isNull(SupplierAttachmentDO::getDeletedAt));
    }
}
