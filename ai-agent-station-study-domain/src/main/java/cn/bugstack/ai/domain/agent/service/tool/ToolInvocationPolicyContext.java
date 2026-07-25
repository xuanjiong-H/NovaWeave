package cn.bugstack.ai.domain.agent.service.tool;

import java.util.Locale;
import java.util.Set;

/**
 * Thread-bound MCP policy used to keep quality supervision read-only and small.
 */
public final class ToolInvocationPolicyContext {

    private static final Set<String> SUPPORTED_AGENT_IDS = Set.of("3", "4", "5");
    private static final ThreadLocal<Policy> CURRENT = new ThreadLocal<>();

    private ToolInvocationPolicyContext() {
    }

    public static Scope startSupervisorPolicy(String agentId, int maxCalls) {
        if (!SUPPORTED_AGENT_IDS.contains(agentId)) {
            return () -> { };
        }
        CURRENT.set(new Policy(agentId, maxCalls));
        return CURRENT::remove;
    }

    public static Decision authorize(String toolName) {
        Policy policy = CURRENT.get();
        if (policy == null) {
            return Decision.allowed();
        }

        int attempt = ++policy.attempts;
        if (attempt > policy.maxCalls) {
            return Decision.rejected("质量监督每轮最多允许 " + policy.maxCalls + " 次 MCP 抽样验证");
        }
        if (!isReadOnlyTool(policy.agentId, toolName)) {
            return Decision.rejected("质量监督只允许调用当前智能体的只读查询工具");
        }
        return Decision.allowed();
    }

    static boolean isReadOnlyTool(String agentId, String toolName) {
        String normalized = toolName == null ? "" : toolName.toLowerCase(Locale.ROOT);
        return switch (agentId) {
            case "3" -> normalized.contains("search");
            case "4" -> normalized.contains("list_indices")
                    || normalized.contains("get_mappings")
                    || normalized.contains("search");
            case "5" -> normalized.contains("list_")
                    || normalized.contains("get_")
                    || normalized.contains("query_")
                    || normalized.contains("search_")
                    || normalized.contains("read_");
            default -> false;
        };
    }

    private static final class Policy {
        private final String agentId;
        private final int maxCalls;
        private int attempts;

        private Policy(String agentId, int maxCalls) {
            this.agentId = agentId;
            this.maxCalls = maxCalls;
        }
    }

    public record Decision(boolean permitted, String rejectionReason) {

        private static Decision allowed() {
            return new Decision(true, null);
        }

        private static Decision rejected(String reason) {
            return new Decision(false, reason);
        }
    }

    @FunctionalInterface
    public interface Scope extends AutoCloseable {

        @Override
        void close();
    }
}
