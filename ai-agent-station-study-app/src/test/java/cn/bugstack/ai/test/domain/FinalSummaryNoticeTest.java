package cn.bugstack.ai.test.domain;

import cn.bugstack.ai.domain.agent.service.execute.auto.parser.FinalSummaryFormatter;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class FinalSummaryNoticeTest {

    @Test
    public void shouldAppendNoticeWhenMaxRoundsReached() {
        String result = FinalSummaryFormatter.appendMaxRoundsNotice(
                "阶段性回答", 2, false, true, "补充按 URI 聚合的接口排行");

        assertTrue(result.contains("已达到最大执行轮数 2"));
        assertTrue(result.contains("任务尚未完全完成"));
        assertTrue(result.contains("补充按 URI 聚合的接口排行"));
    }

    @Test
    public void shouldNotAppendNoticeForCompletedTask() {
        assertEquals("最终回答", FinalSummaryFormatter.appendMaxRoundsNotice(
                "最终回答", 2, true, false, null));
    }
}
