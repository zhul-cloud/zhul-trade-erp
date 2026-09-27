package com.zhul.erp.modules.product.service;

import com.zhul.erp.modules.product.dto.RelationshipVO;
import com.zhul.erp.modules.product.dto.SaveRelationshipRequest;

import java.util.List;

public interface ProductRelationshipService {

    List<RelationshipVO> list(Long productId);

    /** 创建关系；对称类型且 createReverse=true 时同一事务再创建反向关系 */
    RelationshipVO create(Long productId, SaveRelationshipRequest req);

    RelationshipVO update(Long productId, Long relationshipId, SaveRelationshipRequest req);

    /** 软删除这一条关系，已创建的反向关系不受影响 */
    void delete(Long productId, Long relationshipId);
}
