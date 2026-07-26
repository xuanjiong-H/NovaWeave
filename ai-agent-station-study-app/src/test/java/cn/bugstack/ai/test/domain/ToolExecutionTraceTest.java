package cn.bugstack.ai.test.domain;

import cn.bugstack.ai.domain.agent.service.tool.ToolExecutionTrace;
import cn.bugstack.ai.domain.agent.service.tool.ToolExecutionTrace.ToolExecutionRecord;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ToolExecutionTraceTest {

    @Test
    public void shouldCaptureActualToolInputForSupervision() {
        ToolExecutionTrace.start();
        try {
            String input = "{\"queryType\":\"instant\",\"startTime\":\"now\"}";
            ToolExecutionTrace.recordSuccess("query_prometheus", input, "[]", 12);

            List<ToolExecutionRecord> records = ToolExecutionTrace.snapshot();
            assertEquals(1, records.size());
            assertEquals(input, records.get(0).input());
            assertTrue(records.get(0).succeeded());
            assertEquals(12, records.get(0).durationMillis());
        } finally {
            ToolExecutionTrace.clear();
        }
    }

    @Test
    public void shouldCaptureRejectedOrFailedInput() {
        ToolExecutionTrace.start();
        try {
            ToolExecutionTrace.recordFailure("query_prometheus", "{\"startTime\":\"now-24h\"}", "rejected", 0);

            ToolExecutionRecord record = ToolExecutionTrace.snapshot().get(0);
            assertFalse(record.succeeded());
            assertEquals("rejected", record.errorMessage());
        } finally {
            ToolExecutionTrace.clear();
        }
    }
}
