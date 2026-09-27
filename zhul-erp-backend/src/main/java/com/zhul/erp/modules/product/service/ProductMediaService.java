package com.zhul.erp.modules.product.service;

import com.zhul.erp.modules.product.dto.MediaVO;
import com.zhul.erp.modules.product.dto.RegisterMediaRequest;
import com.zhul.erp.modules.product.dto.UpdateMediaRequest;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ProductMediaService {

    /** 主图排最前，其余按 sort_order、id */
    List<MediaVO> list(Long productId);

    /** 登记外部链接，不下载文件 */
    MediaVO register(Long productId, RegisterMediaRequest req);

    /** 上传一个文件并新建媒体记录；加限流；租户账号被拒绝且不保存文件 */
    MediaVO upload(Long productId, MultipartFile file, Integer mediaType, String title, Boolean setMain);

    MediaVO update(Long productId, Long itemId, UpdateMediaRequest req);

    void delete(Long productId, Long itemId);

    /** 设为主图：仅图片；同一事务内取消原主图，商品始终最多一张主图 */
    MediaVO setMain(Long productId, Long itemId);
}
