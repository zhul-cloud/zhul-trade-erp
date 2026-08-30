package com.zhul.erp.modules.system.service;

import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.modules.system.dto.AppearanceVO;
import com.zhul.erp.modules.system.dto.ConfigGroupCountVO;
import com.zhul.erp.modules.system.dto.ConfigVO;
import com.zhul.erp.modules.system.dto.SaveConfigRequest;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ConfigService {
    PageResult<ConfigVO> listAll(Integer page, Integer pageSize, String keyword, String group);
    List<ConfigGroupCountVO> groupCounts();
    void create(SaveConfigRequest req);
    void updateValue(Integer id, String value);
    void delete(Integer id);
    String uploadImage(MultipartFile file);
    AppearanceVO getPublicAppearance();
}
