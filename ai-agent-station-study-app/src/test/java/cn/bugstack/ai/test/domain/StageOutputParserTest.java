package cn.bugstack.ai.test.domain;

import cn.bugstack.ai.domain.agent.service.execute.auto.parser.StageOutputParser;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class StageOutputParserTest {

    private static final Map<String, String> ANALYSIS_MAPPINGS = StageOutputParser.mappings(
            "当前状况分析", "analysis_status",
            "下一步策略", "analysis_strategy",
            "完成度评估", "analysis_progress"
    );

    @Test
    public void shouldParseAgentFiveMarkdownHeadings() {
        List<StageOutputParser.Section> sections = StageOutputParser.parse("""
                **当前状况分析:**
                已确认数据源可用。
                **下一步策略：** 按 uri 查询。
                **完成度评估:** 55%
                <CONTROL>{"decision":"OPTIMIZE"}</CONTROL>
                """, "analysis_status", ANALYSIS_MAPPINGS);

        assertEquals(3, sections.size());
        assertEquals("analysis_status", sections.get(0).type());
        assertEquals("analysis_strategy", sections.get(1).type());
        assertEquals("按 uri 查询。", sections.get(1).content());
        assertFalse(sections.stream().anyMatch(section -> section.content().contains("CONTROL")));
    }

    @Test
    public void shouldFallbackToRawStageContent() {
        List<StageOutputParser.Section> sections = StageOutputParser.parse(
                "模型返回了没有标准标题的内容", "execution_result", Map.of());

        assertEquals(1, sections.size());
        assertEquals("execution_result", sections.get(0).type());
        assertEquals("模型返回了没有标准标题的内容", sections.get(0).content());
    }
}
