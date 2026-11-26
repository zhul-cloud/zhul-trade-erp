package com.zhul.erp.modules.video.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 批量加入队列的结果：加入的个数与跳过的任务及原因 */
@Data
public class BatchQueueVO {
    private int queued;
    private List<Skipped> skipped = new ArrayList<>();

    public record Skipped(Long id, String model, String reason) {
    }
}
