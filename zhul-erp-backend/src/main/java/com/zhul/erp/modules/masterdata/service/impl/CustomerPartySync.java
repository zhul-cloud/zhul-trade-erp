package com.zhul.erp.modules.masterdata.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.modules.masterdata.dto.CustomerPartyRequest;
import com.zhul.erp.modules.masterdata.dto.CustomerPartyVO;
import com.zhul.erp.modules.masterdata.entity.CustomerPartyDO;
import com.zhul.erp.modules.masterdata.repository.CustomerPartyMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 客户单证主体随客户整体保存：请求是完整的最终状态，按 id 比对更新已有、插入新增、软删除请求里没有的。
 * 默认规则（每类有记录时恰好一条默认）：取该类型请求中最后一条标记为默认的；都没标记时取最早创建的一条
 * （保留下来的已有记录按 id 最小，否则取请求里第一条新增）。调用方负责事务。
 */
@Component
@RequiredArgsConstructor
class CustomerPartySync {

    private final CustomerPartyMapper partyMapper;

    List<CustomerPartyDO> listActive(Long customerId) {
        return partyMapper.selectList(new LambdaQueryWrapper<CustomerPartyDO>()
                .eq(CustomerPartyDO::getCustomerId, customerId)
                .isNull(CustomerPartyDO::getDeletedAt)
                .orderByAsc(CustomerPartyDO::getPartyType)
                .orderByAsc(CustomerPartyDO::getId));
    }

    /** requests 中的文本字段已由调用方规范化（去空格、null 转空串、国家为规范英文名） */
    void sync(Integer tenantId, Long customerId, List<CustomerPartyRequest> requests) {
        Map<Long, CustomerPartyDO> existing = new HashMap<>();
        for (CustomerPartyDO p : listActive(customerId)) {
            existing.put(p.getId(), p);
        }

        // 按请求顺序组装最终的实体列表（已有记录复用原实体，其余视为新增）
        List<CustomerPartyDO> finalList = new ArrayList<>(requests.size());
        // 实体的 equals 比较字段值，两条内容相同的新增记录会被视为相等，所以按对象身份记录"标记为默认"
        Set<CustomerPartyDO> flagged = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<Long> kept = new HashSet<>();
        for (CustomerPartyRequest req : requests) {
            CustomerPartyDO party = req.getId() != null ? existing.get(req.getId()) : null;
            if (party == null || !kept.add(party.getId())) {
                party = new CustomerPartyDO();
                party.setTenantId(tenantId);
                party.setCustomerId(customerId);
            }
            copy(req, party);
            finalList.add(party);
            if (Boolean.TRUE.equals(req.getDefaultParty())) {
                flagged.add(party);
            }
        }

        // 逐类型选出默认
        Map<Integer, CustomerPartyDO> winners = new LinkedHashMap<>();
        for (CustomerPartyDO p : finalList) {
            if (flagged.contains(p)) {
                winners.put(p.getPartyType(), p);
            }
        }
        for (CustomerPartyDO p : finalList) {
            CustomerPartyDO current = winners.get(p.getPartyType());
            if (current == null || (!flagged.contains(current) && earlier(p, current))) {
                winners.put(p.getPartyType(), p);
            }
        }

        for (CustomerPartyDO p : finalList) {
            p.setIsDefault(winners.get(p.getPartyType()) == p ? 1 : 0);
            if (p.getId() == null) {
                partyMapper.insert(p);
            } else {
                partyMapper.updateById(p);
            }
        }

        LocalDateTime now = LocalDateTime.now();
        for (CustomerPartyDO p : existing.values()) {
            if (!kept.contains(p.getId())) {
                p.setDeletedAt(now);
                partyMapper.updateById(p);
            }
        }
    }

    static CustomerPartyVO toVo(CustomerPartyDO p) {
        CustomerPartyVO vo = new CustomerPartyVO();
        vo.setId(p.getId());
        vo.setPartyType(p.getPartyType());
        vo.setCompanyName(p.getCompanyName());
        vo.setCountry(p.getCountry());
        vo.setState(p.getState());
        vo.setCity(p.getCity());
        vo.setPostcode(p.getPostcode());
        vo.setAddress(p.getAddress());
        vo.setContactName(p.getContactName());
        vo.setPhone(p.getPhone());
        vo.setEmail(p.getEmail());
        vo.setTaxId(p.getTaxId());
        vo.setDestinationPort(p.getDestinationPort());
        vo.setDefaultParty(Integer.valueOf(1).equals(p.getIsDefault()));
        vo.setRemark(p.getRemark());
        return vo;
    }

    /** 已有记录早于新增记录；都已有时按 id；都为新增时保持请求顺序（先到先得） */
    private static boolean earlier(CustomerPartyDO a, CustomerPartyDO b) {
        if (a.getId() != null && b.getId() != null) {
            return a.getId() < b.getId();
        }
        return a.getId() != null && b.getId() == null;
    }

    private static void copy(CustomerPartyRequest req, CustomerPartyDO p) {
        p.setPartyType(req.getPartyType());
        p.setCompanyName(req.getCompanyName());
        p.setCountry(req.getCountry());
        p.setState(req.getState());
        p.setCity(req.getCity());
        p.setPostcode(req.getPostcode());
        p.setAddress(req.getAddress());
        p.setContactName(req.getContactName());
        p.setPhone(req.getPhone());
        p.setEmail(req.getEmail());
        p.setTaxId(req.getTaxId());
        p.setDestinationPort(req.getDestinationPort());
        p.setRemark(req.getRemark());
    }
}
