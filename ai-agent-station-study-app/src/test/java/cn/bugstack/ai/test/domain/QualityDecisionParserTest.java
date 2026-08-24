package cn.bugstack.ai.test.domain;

import cn.bugstack.ai.domain.agent.model.valobj.enums.QualityDecisionEnumVO;
import cn.bugstack.ai.domain.agent.service.execute.auto.parser.QualityDecisionParser;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class QualityDecisionParserTest {

    @Test
    public void shouldParseStructuredOptimizeDecision() {
        QualityDecisionParser.Result result = QualityDecisionParser.parse("""
                质量评估: 接口数据仍不完整
                <CONTROL>{"decision":"OPTIMIZE","goalSatisfied":false,"progress":55,"score":82,"nextTask":"按 uri 聚合查询"}</CONTROL>
                """);

        assertEquals(QualityDecisionEnumVO.OPTIMIZE, result.decision());
        assertEquals(Integer.valueOf(55), result.progress());
        assertEquals("按 uri 聚合查询", result.nextTask());
        assertTrue(result.structured());
    }

    @Test
    public void shouldNormalizeMarkdownAndChineseColon() {
        QualityDecisionParser.Result result = QualityDecisionParser.parse("**评估结果：** `OPTIMIZE`");

        assertEquals(QualityDecisionEnumVO.OPTIMIZE, result.decision());
        assertFalse(result.structured());
    }

    @Test
    public void shouldDowngradeInconsistentPass() {
        QualityDecisionParser.Result result = QualityDecisionParser.parse("""
                **完成度评估:** 55%
                **任务状态:** CONTINUE
                **评估结果:** PASS
                """);

        assertEquals(QualityDecisionEnumVO.OPTIMIZE, result.decision());
        assertTrue(result.adjustedForIncompleteTask());
    }

    @Test
    public void shouldKeepConsistentPass() {
        QualityDecisionParser.Result result = QualityDecisionParser.parse(
                "<CONTROL>{\"decision\":\"PASS\",\"goalSatisfied\":true,\"progress\":100,\"score\":95,\"nextTask\":\"\"}</CONTROL>");

        assertEquals(QualityDecisionEnumVO.PASS, result.decision());
        assertFalse(result.adjustedForIncompleteTask());
    }

    @Test
    public void shouldReturnUnknownForMalformedDecision() {
        QualityDecisionParser.Result result = QualityDecisionParser.parse("质量评估: 格式不完整");

        assertEquals(QualityDecisionEnumVO.UNKNOWN, result.decision());
    }
}
