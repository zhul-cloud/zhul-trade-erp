package com.zhul.erp.modules.video;

import com.zhul.erp.modules.video.dto.VideoScript;
import com.zhul.erp.modules.video.support.ScriptRules;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** spec「视频脚本」：按每秒约 2.5 个词估算时长，镜头 4–8 个，总时长 15–20 秒 */
class ScriptRulesTest {

    private static final List<Long> USED = List.of(1L, 2L, 3L, 4L, 5L, 6L);

    @Test
    void estimate_byWords() {
        assertEquals(0, ScriptRules.words(null));
        assertEquals(0, ScriptRules.words("   "));
        assertEquals(6, ScriptRules.words("  Genuine  Siemens S7-1200 CPU in stock "));
        assertEquals(2.0, ScriptRules.seconds("one two three four five"));
        assertEquals(24.0, ScriptRules.totalSeconds(script(4, 15)), "60 个词估算 24 秒");
    }

    @Test
    void valid_fourShotsSixteenSeconds() {
        assertEquals(Optional.empty(), ScriptRules.problem(script(4, 10), USED));
        assertEquals(Optional.empty(), ScriptRules.problem(script(8, 5), List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L)));
    }

    @Test
    void problems() {
        assertEquals("还没有脚本", ScriptRules.problem(null, USED).orElseThrow());
        assertEquals("估算时长 24 秒，需要 15–20 秒，请精简字幕", ScriptRules.problem(script(4, 15), USED).orElseThrow());
        assertEquals("估算时长 12.8 秒，需要 15–20 秒，请补充字幕", ScriptRules.problem(script(4, 8), USED).orElseThrow());
        assertEquals("镜头 3 个，需要 4–8 个", ScriptRules.problem(script(3, 14), USED).orElseThrow());
        VideoScript s = script(4, 10);
        s.getShots().get(1).setAttachmentId(99L);
        assertEquals("镜头 2 的图片不在选用的图片里", ScriptRules.problem(s, USED).orElseThrow());
        s = script(4, 10);
        s.getShots().get(3).setCaption(" ");
        assertEquals("镜头 4 没有字幕", ScriptRules.problem(s, USED).orElseThrow());
        s = script(4, 10);
        s.getShots().get(0).setCaption("x".repeat(121));
        assertTrue(ScriptRules.problem(s, USED).orElseThrow().contains("超过 120 个字符"));
        s = script(4, 10);
        s.setTitle("");
        assertEquals("请填写视频标题", ScriptRules.problem(s, USED).orElseThrow());
    }

    /** n 个镜头，每个字幕 words 个词，图片依次为 1..n */
    static VideoScript script(int n, int words) {
        VideoScript s = new VideoScript();
        s.setTitle("Siemens 6ES7214-1AG40-0XB0 CPU");
        for (int i = 1; i <= n; i++) {
            VideoScript.Shot shot = new VideoScript.Shot();
            shot.setAttachmentId((long) i);
            shot.setCaption(String.join(" ", java.util.Collections.nCopies(words, "word")));
            s.getShots().add(shot);
        }
        return s;
    }
}
