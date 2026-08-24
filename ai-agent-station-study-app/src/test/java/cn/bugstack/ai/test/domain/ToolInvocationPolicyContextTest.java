package cn.bugstack.ai.test.domain;

import cn.bugstack.ai.domain.agent.service.tool.ToolInvocationPolicyContext;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ToolInvocationPolicyContextTest {

    @Test
    public void shouldAllowOnlyTwoSupervisorCallsPerRound() {
        try (ToolInvocationPolicyContext.Scope ignored =
                     ToolInvocationPolicyContext.startSupervisorPolicy("5", 2)) {
            assertTrue(ToolInvocationPolicyContext.authorize(
                    "JavaSDKMCPClient_list_datasources").permitted());
            assertTrue(ToolInvocationPolicyContext.authorize(
                    "JavaSDKMCPClient_query_prometheus").permitted());
            assertFalse(ToolInvocationPolicyContext.authorize(
                    "JavaSDKMCPClient_get_dashboard_by_uid").permitted());
        }
    }

    @Test
    public void shouldRejectMutatingSupervisorTool() {
        try (ToolInvocationPolicyContext.Scope ignored =
                     ToolInvocationPolicyContext.startSupervisorPolicy("5", 2)) {
            assertFalse(ToolInvocationPolicyContext.authorize(
                    "JavaSDKMCPClient_update_dashboard").permitted());
        }
    }

    @Test
    public void shouldUseAgentSpecificReadOnlyTools() {
        try (ToolInvocationPolicyContext.Scope ignored =
                     ToolInvocationPolicyContext.startSupervisorPolicy("4", 2)) {
            assertTrue(ToolInvocationPolicyContext.authorize(
                    "JavaSDKMCPClient_list_indices").permitted());
            assertFalse(ToolInvocationPolicyContext.authorize(
                    "JavaSDKMCPClient_query_prometheus").permitted());
        }
    }

    @Test
    public void shouldNotLimitCallsOutsideSupervisorStage() {
        assertTrue(ToolInvocationPolicyContext.authorize(
                "JavaSDKMCPClient_update_dashboard").permitted());
    }
}
