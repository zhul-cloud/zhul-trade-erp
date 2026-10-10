package com.zhul.erp.modules.product.service;

import com.zhul.erp.modules.product.dto.RelationshipVO;
import com.zhul.erp.modules.product.dto.SaveRelationshipRequest;

import java.util.List;

public interface ProductRelationshipService {

    /** 英文说明（兼容现有调用方） */
    default List<RelationshipVO> list(Long productId) {
        return list(productId, null);
    }

    /** 说明按语言返回：英文取关系本身的说明，中文、俄文取多语言说明，没有时为空 */
    List<RelationshipVO> list(Long productId, String lang);

    /** 创建关系；对称类型且 createReverse=true 时同一事务再创建反向关系 */
    RelationshipVO create(Long productId, SaveRelationshipRequest req);

    RelationshipVO update(Long productId, Long relationshipId, SaveRelationshipRequest req);

    /** 软删除这一条关系，已创建的反向关系不受影响 */
    void delete(Long productId, Long relationshipId);
}
