package cn.bugstack.ai.test.domain;

import cn.bugstack.ai.domain.agent.service.execute.auto.policy.AutoAgentMonitoringPolicy;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AutoAgentMonitoringPolicyTest {

    @Test
    public void shouldLeaveOtherAgentsUnchanged() {
        String prompt = "base prompt";

        assertEquals(prompt, AutoAgentMonitoringPolicy.append(
                "4", AutoAgentMonitoringPolicy.Stage.ANALYZER, prompt));
    }

    @Test
    public void shouldApplyWindowExpansionAndBusinessFilters() {
        String prompt = AutoAgentMonitoringPolicy.append(
                "5", AutoAgentMonitoringPolicy.Stage.ANALYZER, "base prompt");

        assertTrue(prompt.contains("latest 24 hours"));
        assertTrue(prompt.contains("48h, then 72h"));
        assertTrue(prompt.contains("startTime=\"now\""));
        assertTrue(prompt.contains("method!=\"OPTIONS\""));
        assertTrue(prompt.contains("status!~\"404|405\""));
        assertTrue(prompt.contains("/actuator.*|root|/\\\\*\\\\*"));
    }

    @Test
    public void shouldKeepQpsAndTpsSemanticsSeparate() {
        String prompt = AutoAgentMonitoringPolicy.append(
                "5", AutoAgentMonitoringPolicy.Stage.EXECUTOR, "base prompt");

        assertTrue(prompt.contains("HTTP request rate is QPS/RPS, not business TPS"));
        assertTrue(prompt.contains("raffle_draw, credit_exchange, and calendar_sign"));
        assertTrue(prompt.contains("mark TPS unavailable"));
        assertFalse(prompt.contains("HTTP request rate is TPS"));
    }

    @Test
    public void shouldAllowCompletionAfterEmpty72HourProbe() {
        String prompt = AutoAgentMonitoringPolicy.append(
                "5", AutoAgentMonitoringPolicy.Stage.SUPERVISOR, "base prompt");

        assertTrue(prompt.contains("72h business probe is empty"));
        assertTrue(prompt.contains("Do not make unrequested P95/P99"));
    }

    @Test
    public void shouldRequireSeparatedFinalReportSections() {
        String prompt = AutoAgentMonitoringPolicy.append(
                "5", AutoAgentMonitoringPolicy.Stage.SUMMARY, "base prompt");

        assertTrue(prompt.contains("business HTTP QPS/RPS"));
        assertTrue(prompt.contains("real business TPS"));
        assertTrue(prompt.contains("excluded/non-business traffic"));
    }
}
