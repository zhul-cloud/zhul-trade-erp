package com.zhul.erp.modules.crm.followup.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** SOP 作战卡：阶段目标、清单、推进条件、示例对话，以及卡点应对 */
@Data
public class SopCardVO {
    private Integer board;
    private String stageCode;
    private String name;
    private String goal;
    private List<String> checklist = new ArrayList<>();
    private String gateText;
    private String autoHint;
    private String exampleDialog;
    private List<Blocker> blockers = new ArrayList<>();

    @Data
    public static class Blocker {
        private String code;
        private String name;
        private Integer lostReason;
        private List<String> playbook = new ArrayList<>();
    }
}
