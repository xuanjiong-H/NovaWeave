package cn.bugstack.ai.test.domain;

import cn.bugstack.ai.domain.agent.service.execute.flow.FlowAgentExecuteStrategy;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class FlowPlanningLimitTest {

    @Test
    public void shouldUseFiveWhenRequestDoesNotProvideLimit() {
        assertEquals(5, FlowAgentExecuteStrategy.resolveMaxPlanningSteps(null));
    }

    @Test
    public void shouldKeepExplicitLimit() {
        assertEquals(2, FlowAgentExecuteStrategy.resolveMaxPlanningSteps(2));
    }

    @Test
    public void shouldRejectLimitOutsideSupportedRange() {
        assertThrows(
                IllegalArgumentException.class,
                () -> FlowAgentExecuteStrategy.resolveMaxPlanningSteps(0)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> FlowAgentExecuteStrategy.resolveMaxPlanningSteps(11)
        );
    }
}
