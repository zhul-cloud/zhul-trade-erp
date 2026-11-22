package com.zhul.erp.modules.masterdata.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.modules.masterdata.constants.SupplierConstants;
import com.zhul.erp.modules.masterdata.dto.SupplierAttachmentRequest;
import com.zhul.erp.modules.masterdata.dto.SupplierAttachmentVO;
import com.zhul.erp.modules.masterdata.entity.SupplierAttachmentDO;
import com.zhul.erp.modules.masterdata.repository.SupplierAttachmentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.zhul.erp.framework.storage.UploadRules;
import com.zhul.erp.framework.storage.StorageArea;
import com.zhul.erp.framework.storage.FileStorageService;

/**
 * 供应商附件的保存与读取。按 id 合并：带 id 的保留（可改类型），不带 id 的按上传接口返回的 fileKey 新增，
 * 未出现的软删除。文件大小与类型以磁盘上的文件为准，不信任请求。
 */
@Component
@RequiredArgsConstructor
public class SupplierAttachmentSync {

    private final SupplierAttachmentMapper attachmentMapper;
    private final FileStorageService fileStorage;

    public void merge(int tenantId, long supplierId, List<SupplierAttachmentRequest> reqs) {
        if (reqs.size() > SupplierConstants.MAX_ATTACHMENTS) {
            throw new BizException("每个供应商最多 " + SupplierConstants.MAX_ATTACHMENTS + " 个附件");
        }
        Map<Long, SupplierAttachmentDO> existing = new HashMap<>(16);
        // 只管普通附件：收款码单独上传、删除，编辑供应商时不动（否则会被当成删掉的附件）
        for (SupplierAttachmentDO row : activeRows(List.of(supplierId))) {
            if (!isQr(row)) {
                existing.put(row.getId(), row);
            }
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
            String storage = fileStorage.locate(StorageArea.SUPPLIER, req.getFileKey(), tenantId);
            SupplierAttachmentDO row = new SupplierAttachmentDO();
            row.setTenantId(tenantId);
            row.setSupplierId(supplierId);
            row.setCategory(req.getCategory());
            row.setFileKey(req.getFileKey());
            row.setStorage(storage);
            row.setFileName(UploadRules.cleanFileName(req.getFileName(),
                    req.getFileKey().substring(req.getFileKey().lastIndexOf('.') + 1)));
            row.setContentType(UploadRules.contentTypeOf(req.getFileKey()));
            row.setFileSize(fileStorage.storages().of(storage).size(false, req.getFileKey()));
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
                .filter(row -> !isQr(row))
                .sorted(Comparator.comparing(SupplierAttachmentDO::getCategory).thenComparing(SupplierAttachmentDO::getId))
                .forEach(row -> result.computeIfAbsent(row.getSupplierId(), k -> new ArrayList<>(4)).add(toVo(row)));
        return result;
    }

    /** 收款码：每个供应商每种渠道最多一张 */
    public Map<Long, List<SupplierAttachmentVO>> payQrCodes(List<Long> supplierIds) {
        if (supplierIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<SupplierAttachmentVO>> result = new LinkedHashMap<>(16);
        activeRows(supplierIds).stream()
                .filter(SupplierAttachmentSync::isQr)
                .sorted(Comparator.comparing(SupplierAttachmentDO::getCategory).thenComparing(SupplierAttachmentDO::getId))
                .forEach(row -> result.computeIfAbsent(row.getSupplierId(), k -> new ArrayList<>(2)).add(toVo(row)));
        return result;
    }

    /** 收款码的原始记录（取临时链接用） */
    public List<SupplierAttachmentDO> payQrRows(long supplierId) {
        return activeRows(List.of(supplierId)).stream().filter(SupplierAttachmentSync::isQr).toList();
    }

    /** 上传或更换收款码：同一渠道原来那张软删，只支持 JPG、PNG */
    public void replacePayQr(int tenantId, long supplierId, int payChannel, String fileKey, String fileName) {
        Integer category = SupplierConstants.PAY_QR_CATEGORY.get(payChannel);
        if (category == null) {
            throw new BizException("收款码只有支付宝、微信两种");
        }
        if (fileKey == null || !fileKey.toLowerCase().matches(".*\\.(jpe?g|png)$")) {
            throw new BizException("收款码只支持 JPG、PNG 图片");
        }
        removePayQr(supplierId, payChannel);
        String storage = fileStorage.locate(StorageArea.SUPPLIER, fileKey, tenantId);
        SupplierAttachmentDO row = new SupplierAttachmentDO();
        row.setTenantId(tenantId);
        row.setSupplierId(supplierId);
        row.setCategory(category);
        row.setFileKey(fileKey);
        row.setStorage(storage);
        row.setFileName(UploadRules.cleanFileName(fileName, fileKey.substring(fileKey.lastIndexOf('.') + 1)));
        row.setContentType(UploadRules.contentTypeOf(fileKey));
        row.setFileSize(fileStorage.storages().of(storage).size(false, fileKey));
        attachmentMapper.insert(row);
    }

    public void removePayQr(long supplierId, int payChannel) {
        Integer category = SupplierConstants.PAY_QR_CATEGORY.get(payChannel);
        if (category == null) {
            throw new BizException("收款码只有支付宝、微信两种");
        }
        LocalDateTime now = LocalDateTime.now();
        for (SupplierAttachmentDO row : activeRows(List.of(supplierId))) {
            if (category.equals(row.getCategory())) {
                row.setDeletedAt(now);
                attachmentMapper.updateById(row);
            }
        }
    }

    private static boolean isQr(SupplierAttachmentDO row) {
        return SupplierConstants.PAY_QR_CATEGORY.containsValue(row.getCategory());
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

    private SupplierAttachmentVO toVo(SupplierAttachmentDO row) {
        SupplierAttachmentVO vo = new SupplierAttachmentVO();
        vo.setId(row.getId());
        vo.setCategory(row.getCategory());
        SupplierConstants.PAY_QR_CATEGORY.forEach((channel, category) -> {
            if (category.equals(row.getCategory())) {
                vo.setPayChannel(channel);
            }
        });
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
