package com.zhul.erp.modules.aitask.translation;

import com.zhul.erp.common.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 生成英文描述：报价单、PI 编辑页使用 */
@RestController
@RequestMapping("/api/v1/translations/item-descriptions")
@RequiredArgsConstructor
@PreAuthorize("@perm.canAccessMenu('/quotation/quotations') or @perm.canAccessMenu('/sales/pi')")
public class TranslationController {

    private final DescriptionTranslations translations;

    @PostMapping
    public Result<TranslationVO> submit(@Valid @RequestBody TranslateItemsRequest req) {
        return Result.ok(translations.submit(req));
    }

    @GetMapping("/{taskId}")
    public Result<TranslationVO> status(@PathVariable Long taskId) {
        return Result.ok(translations.status(taskId));
    }
}
