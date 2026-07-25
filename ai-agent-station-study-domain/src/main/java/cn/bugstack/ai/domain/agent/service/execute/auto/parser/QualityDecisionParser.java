package cn.bugstack.ai.domain.agent.service.execute.auto.parser;

import cn.bugstack.ai.domain.agent.model.valobj.enums.QualityDecisionEnumVO;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the machine-readable supervisor control block with legacy text fallback.
 */
public final class QualityDecisionParser {

    private static final Pattern CONTROL_PATTERN = Pattern.compile("<CONTROL>\\s*(\\{.*?})\\s*</CONTROL>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern NUMBER_PATTERN = Pattern.compile("(\\d{1,3})");

    private QualityDecisionParser() {
    }

    public static Result parse(String content) {
        if (content == null || content.isBlank()) {
            return new Result(QualityDecisionEnumVO.UNKNOWN, null, null, null, null, false, false);
        }

        MutableResult result = parseStructuredControl(content);
        boolean structured = result != null;
        if (result == null) {
            result = new MutableResult();
        }
        parseLegacyFields(content, result, !structured);

        QualityDecisionEnumVO originalDecision = result.decision;
        boolean inconsistentPass = originalDecision == QualityDecisionEnumVO.PASS
                && (Boolean.FALSE.equals(result.goalSatisfied)
                || (result.progress != null && result.progress < 100)
                || result.continueStatus
                || containsExplicitIncompleteStatement(content));
        if (inconsistentPass) {
            result.decision = QualityDecisionEnumVO.OPTIMIZE;
        }

        return new Result(
                result.decision,
                result.progress,
                result.goalSatisfied,
                result.score,
                result.nextTask,
                inconsistentPass,
                structured
        );
    }

    private static MutableResult parseStructuredControl(String content) {
        Matcher matcher = CONTROL_PATTERN.matcher(content);
        JSONObject lastControl = null;
        while (matcher.find()) {
            try {
                lastControl = JSON.parseObject(matcher.group(1));
            } catch (Exception ignored) {
                // Legacy parsing below handles malformed model output.
            }
        }
        if (lastControl == null) {
            return null;
        }

        MutableResult result = new MutableResult();
        result.decision = parseDecision(lastControl.getString("decision"));
        result.goalSatisfied = lastControl.getBoolean("goalSatisfied");
        result.progress = getInteger(lastControl, "progress");
        result.score = getInteger(lastControl, "score");
        result.nextTask = trimToNull(lastControl.getString("nextTask"));
        return result;
    }

    private static void parseLegacyFields(String content, MutableResult result, boolean allowDecisionFallback) {
        for (String rawLine : content.split("\\R")) {
            String line = StageOutputParser.normalizeForMatching(rawLine);
            int colonIndex = line.indexOf(':');
            if (colonIndex < 0) {
                continue;
            }

            String key = line.substring(0, colonIndex).trim();
            String value = line.substring(colonIndex + 1).trim();
            if (allowDecisionFallback && isDecisionKey(key)) {
                QualityDecisionEnumVO parsedDecision = parseDecision(value);
                if (parsedDecision != QualityDecisionEnumVO.UNKNOWN) {
                    result.decision = parsedDecision;
                }
            } else if (key.endsWith("完成度评估")) {
                result.progress = parseInteger(value, result.progress);
            } else if (key.endsWith("质量评分")) {
                result.score = parseInteger(value, result.score);
            } else if (key.endsWith("任务状态")) {
                result.continueStatus = value.toUpperCase(Locale.ROOT).contains("CONTINUE") || value.contains("继续");
            } else if ((key.endsWith("下一步重点") || key.endsWith("下一步建议") || key.endsWith("下一步策略")) && !value.isBlank()) {
                result.nextTask = value;
            }
        }
    }

    private static boolean isDecisionKey(String key) {
        return key.endsWith("是否通过") || key.endsWith("评估结果") || key.endsWith("检查结果") || key.endsWith("监督结果");
    }

    private static QualityDecisionEnumVO parseDecision(String value) {
        if (value == null) {
            return QualityDecisionEnumVO.UNKNOWN;
        }
        String normalized = StageOutputParser.normalizeForMatching(value).toUpperCase(Locale.ROOT);
        if (normalized.contains("OPTIMIZE") || normalized.contains("需要优化") || normalized.contains("继续优化")) {
            return QualityDecisionEnumVO.OPTIMIZE;
        }
        if (normalized.contains("FAIL") || normalized.contains("未通过") || normalized.contains("失败")) {
            return QualityDecisionEnumVO.FAIL;
        }
        if (normalized.contains("PASS") || normalized.equals("通过") || normalized.contains("检查通过")) {
            return QualityDecisionEnumVO.PASS;
        }
        return QualityDecisionEnumVO.UNKNOWN;
    }

    private static boolean containsExplicitIncompleteStatement(String content) {
        String normalized = StageOutputParser.normalizeForMatching(content);
        return normalized.contains("任务尚未完成")
                || normalized.contains("任务未完全完成")
                || normalized.contains("尚未完成用户")
                || normalized.contains("尚未完成“")
                || normalized.contains("尚未完成\"")
                || normalized.contains("未完成“所有")
                || normalized.contains("未完成所有接口");
    }

    private static Integer getInteger(JSONObject object, String key) {
        Object value = object.get(key);
        return value == null ? null : parseInteger(String.valueOf(value), null);
    }

    private static Integer parseInteger(String value, Integer fallback) {
        Matcher matcher = NUMBER_PATTERN.matcher(value == null ? "" : value);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : fallback;
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static final class MutableResult {
        private QualityDecisionEnumVO decision = QualityDecisionEnumVO.UNKNOWN;
        private Integer progress;
        private Boolean goalSatisfied;
        private Integer score;
        private String nextTask;
        private boolean continueStatus;
    }

    public record Result(
            QualityDecisionEnumVO decision,
            Integer progress,
            Boolean goalSatisfied,
            Integer score,
            String nextTask,
            boolean adjustedForIncompleteTask,
            boolean structured
    ) {
    }
}
