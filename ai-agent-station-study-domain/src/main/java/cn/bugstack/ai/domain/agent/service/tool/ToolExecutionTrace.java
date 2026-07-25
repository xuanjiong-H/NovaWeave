package cn.bugstack.ai.domain.agent.service.tool;

import java.util.ArrayList;
import java.util.List;

/**
 * Captures MCP responses for the synchronous flow execution currently running
 * on the calling thread. This allows summaries to use tool data rather than
 * model-generated descriptions.
 */
public final class ToolExecutionTrace {

    private static final ThreadLocal<List<ToolExecutionRecord>> CURRENT = new ThreadLocal<>();

    private ToolExecutionTrace() {
    }

    public static void start() {
        CURRENT.set(new ArrayList<>());
    }

    public static void recordSuccess(String toolName, String output, long durationMillis) {
        List<ToolExecutionRecord> records = CURRENT.get();
        if (records != null) {
            records.add(new ToolExecutionRecord(toolName, output, null, durationMillis));
        }
    }

    public static void recordFailure(String toolName, String errorMessage, long durationMillis) {
        List<ToolExecutionRecord> records = CURRENT.get();
        if (records != null) {
            records.add(new ToolExecutionRecord(toolName, null, errorMessage, durationMillis));
        }
    }

    public static List<ToolExecutionRecord> snapshot() {
        List<ToolExecutionRecord> records = CURRENT.get();
        return records == null ? List.of() : List.copyOf(records);
    }

    public static void clear() {
        CURRENT.remove();
    }

    public record ToolExecutionRecord(String toolName, String output, String errorMessage, long durationMillis) {

        public boolean succeeded() {
            return errorMessage == null;
        }
    }
}
