package cn.bugstack.ai.domain.agent.service.execute.auto.parser;

/**
 * Applies deterministic execution-status information to the model summary.
 */
public final class FinalSummaryFormatter {

    private FinalSummaryFormatter() {
    }

    public static String appendMaxRoundsNotice(String summary,
                                                int maxRounds,
                                                boolean completed,
                                                boolean maxRoundsReached,
                                                String pendingTask) {
        if (completed || !maxRoundsReached) {
            return summary;
        }

        String pendingDescription = pendingTask == null || pendingTask.isBlank()
                ? "请参考最后一轮质量监督给出的改进建议"
                : pendingTask.trim();
        return summary.stripTrailing() + String.format("""


                ---

                > 执行说明：已达到最大执行轮数 %d，任务尚未完全完成。以上回答基于当前已获取的数据生成。待完成事项：%s
                """, maxRounds, pendingDescription);
    }
}
