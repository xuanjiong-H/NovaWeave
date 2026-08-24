package cn.bugstack.ai.domain.agent.service.execute.flow.step;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class Step4ExecuteStepsNodeTest {

    @Test
    public void shouldAllowPlanAtConfiguredLimit() {
        Step4ExecuteStepsNode.validateStepsBeforeExecution(steps(2), 2);
    }

    @Test
    public void shouldRejectPlanBeforeExecutionWhenLimitIsExceeded() {
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> Step4ExecuteStepsNode.validateStepsBeforeExecution(steps(3), 2)
        );

        assertTrue(exception.getMessage().contains("已在执行任何业务步骤前终止"));
    }

    private Map<String, String> steps(int count) {
        Map<String, String> steps = new LinkedHashMap<>();
        for (int step = 1; step <= count; step++) {
            steps.put("第" + step + "步", "步骤" + step);
        }
        return steps;
    }
}
