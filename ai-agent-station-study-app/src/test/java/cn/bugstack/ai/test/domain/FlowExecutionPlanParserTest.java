package cn.bugstack.ai.test.domain;

import cn.bugstack.ai.domain.agent.service.execute.flow.parser.FlowExecutionPlanParser;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class FlowExecutionPlanParserTest {

    @Test
    public void shouldParseOrderedDetailedStepsWithinLimit() {
        FlowExecutionPlanParser.Result result = FlowExecutionPlanParser.parse("""
                # 执行步骤规划

                ### 第1步：生成并发布文章
                - 使用工具：saveArticle

                ### 第2步：发送通知
                - 使用工具：weixinNotice
                """);

        FlowExecutionPlanParser.validateExecutable(result, 2);

        assertEquals(2, result.count());
        assertEquals("第1步", result.steps().keySet().iterator().next());
        assertTrue(result.continuousFromOne());
    }

    @Test
    public void shouldRecognizePlanThatExceedsLimit() {
        FlowExecutionPlanParser.Result result = FlowExecutionPlanParser.parse("""
                [ ] 第1步：生成文章
                [ ] 第2步：发布文章
                [ ] 第3步：发送通知
                """);

        assertTrue(result.exceeds(2));
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> FlowExecutionPlanParser.validateExecutable(result, 2)
        );
        assertTrue(exception.getMessage().contains("超过最大规划步骤数 2"));
    }

    @Test
    public void shouldRejectMissingOrNonContinuousStepNumbers() {
        FlowExecutionPlanParser.Result result = FlowExecutionPlanParser.parse("""
                ### 第1步：生成文章
                - 使用工具：无

                ### 第3步：发送通知
                - 使用工具：weixinNotice
                """);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> FlowExecutionPlanParser.validateExecutable(result, 5)
        );
        assertTrue(exception.getMessage().contains("必须从第1步开始连续"));
    }

    @Test
    public void shouldRejectDuplicateStepNumbers() {
        FlowExecutionPlanParser.Result result = FlowExecutionPlanParser.parse("""
                ### 第1步：生成文章
                - 使用工具：无

                ### 第1步：发布文章
                - 使用工具：saveArticle
                """);

        assertThrows(
                IllegalStateException.class,
                () -> FlowExecutionPlanParser.validateExecutable(result, 5)
        );
    }
}
