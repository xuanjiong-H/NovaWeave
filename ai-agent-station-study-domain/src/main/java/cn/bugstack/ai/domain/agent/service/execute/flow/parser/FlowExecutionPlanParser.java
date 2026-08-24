package cn.bugstack.ai.domain.agent.service.execute.flow.parser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the numbered business steps produced by the Flow planning model.
 */
public final class FlowExecutionPlanParser {

    private static final Pattern DETAILED_STEP_PATTERN = Pattern.compile(
            "### (第(\\d+)步：[^\\n]+)([\\s\\S]*?)(?=### 第\\d+步：|$)"
    );
    private static final Pattern SIMPLE_STEP_PATTERN = Pattern.compile(
            "\\[ \\] (第(\\d+)步：[^\\n]+)"
    );

    private FlowExecutionPlanParser() {
    }

    public static Result parse(String planningResult) {
        if (planningResult == null || planningResult.isBlank()) {
            return new Result(Collections.emptyMap(), Collections.emptyList(), false);
        }

        ParseAccumulator detailed = parseDetailedSteps(planningResult);
        ParseAccumulator parsed = detailed.steps.isEmpty()
                ? parseSimpleSteps(planningResult)
                : detailed;

        Map<String, String> orderedSteps = new LinkedHashMap<>();
        parsed.steps.forEach((number, content) -> orderedSteps.put("第" + number + "步", content));

        List<Integer> stepNumbers = new ArrayList<>(parsed.steps.keySet());
        boolean continuous = !parsed.duplicateNumber && isContinuousFromOne(stepNumbers);
        return new Result(
                Collections.unmodifiableMap(orderedSteps),
                Collections.unmodifiableList(stepNumbers),
                continuous
        );
    }

    public static void validateExecutable(Result result, int maxPlanningSteps) {
        if (result.count() == 0) {
            throw new IllegalStateException("规划结果格式不符合要求，必须包含‘### 第N步：步骤描述’");
        }
        if (!result.continuousFromOne()) {
            throw new IllegalStateException("规划步骤编号必须从第1步开始连续且不能重复");
        }
        if (result.exceeds(maxPlanningSteps)) {
            throw new IllegalStateException(
                    "规划包含 " + result.count() + " 个业务步骤，超过最大规划步骤数 " + maxPlanningSteps
            );
        }
    }

    private static ParseAccumulator parseDetailedSteps(String planningResult) {
        ParseAccumulator accumulator = new ParseAccumulator();
        Matcher matcher = DETAILED_STEP_PATTERN.matcher(planningResult);
        while (matcher.find()) {
            int stepNumber = Integer.parseInt(matcher.group(2));
            String stepTitle = matcher.group(1).trim();
            String stepContent = matcher.group(3).trim();
            accumulator.put(stepNumber, stepTitle + "\n" + stepContent);
        }
        return accumulator;
    }

    private static ParseAccumulator parseSimpleSteps(String planningResult) {
        ParseAccumulator accumulator = new ParseAccumulator();
        Matcher matcher = SIMPLE_STEP_PATTERN.matcher(planningResult);
        while (matcher.find()) {
            accumulator.put(Integer.parseInt(matcher.group(2)), matcher.group(1).trim());
        }
        return accumulator;
    }

    private static boolean isContinuousFromOne(List<Integer> stepNumbers) {
        for (int index = 0; index < stepNumbers.size(); index++) {
            if (stepNumbers.get(index) != index + 1) {
                return false;
            }
        }
        return true;
    }

    public record Result(Map<String, String> steps, List<Integer> stepNumbers, boolean continuousFromOne) {

        public int count() {
            return steps.size();
        }

        public boolean exceeds(int maxPlanningSteps) {
            return count() > maxPlanningSteps;
        }
    }

    private static final class ParseAccumulator {

        private final Map<Integer, String> steps = new TreeMap<>();
        private boolean duplicateNumber;

        private void put(int number, String content) {
            if (steps.put(number, content) != null) {
                duplicateNumber = true;
            }
        }
    }
}
