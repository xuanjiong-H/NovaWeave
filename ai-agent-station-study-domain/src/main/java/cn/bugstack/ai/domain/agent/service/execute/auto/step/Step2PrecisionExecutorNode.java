package cn.bugstack.ai.domain.agent.service.execute.auto.step;

import cn.bugstack.ai.domain.agent.model.entity.AutoAgentExecuteResultEntity;
import cn.bugstack.ai.domain.agent.model.entity.ExecuteCommandEntity;
import cn.bugstack.ai.domain.agent.model.valobj.AiAgentClientFlowConfigVO;
import cn.bugstack.ai.domain.agent.model.valobj.AiClientVO;
import cn.bugstack.ai.domain.agent.model.valobj.enums.AiClientTypeEnumVO;
import cn.bugstack.ai.domain.agent.service.execute.auto.parser.StageOutputParser;
import cn.bugstack.ai.domain.agent.service.execute.auto.policy.AutoAgentMonitoringPolicy;
import cn.bugstack.ai.domain.agent.service.execute.auto.step.factory.DefaultAutoAgentExecuteStrategyFactory;
import cn.bugstack.ai.domain.agent.service.tool.ToolExecutionTrace;
import cn.bugstack.ai.domain.agent.service.tool.ToolExecutionTrace.ToolExecutionRecord;
import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 精准执行节点
 *
 * @author xiaofuge bugstack.cn @小傅哥
 * 2025/7/27 16:42
 */
@Slf4j
@Service
public class Step2PrecisionExecutorNode extends AbstractExecuteSupport{

    private static final String ZERO_TOOL_RETRY_INSTRUCTION = """

            # MCP 工具调用重试要求
            前一次执行没有触发任何 MCP 工具调用，但当前精准执行器已经绑定 MCP 工具。请立即选择并调用至少一个与本轮目标匹配的工具获取真实数据，再基于工具返回结果作答。不得再次仅描述计划、要求用户提供数据或直接表示无法查询。
            """;

    private static final Map<String, String> SECTION_MAPPINGS = StageOutputParser.mappings(
            "执行目标", "execution_target",
            "执行过程", "execution_process",
            "MCP工具调用记录", "execution_process",
            "执行结果", "execution_result",
            "质量检查", "execution_quality",
            "数据验证", "execution_quality",
            "下一步建议", "execution_quality"
    );

    @Override
    protected String doApply(ExecuteCommandEntity requestParameter, DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws Exception {
        log.info("\n⚡ 阶段2: 精准任务执行");
        
        // 从动态上下文中获取分析结果
        String analysisResult = dynamicContext.getValue("analysisResult");
        if (analysisResult == null || analysisResult.trim().isEmpty()) {
            log.warn("⚠️ 分析结果为空，使用默认执行策略");
            analysisResult = "执行当前任务步骤";
        }

        AiAgentClientFlowConfigVO aiAgentClientFlowConfigVO = dynamicContext.getAiAgentClientFlowConfigVOMap().get(AiClientTypeEnumVO.PRECISION_EXECUTOR_CLIENT.getCode());

        String executionPrompt = String.format(aiAgentClientFlowConfigVO.getStepPrompt(), requestParameter.getMessage(), analysisResult);
        executionPrompt = AutoAgentMonitoringPolicy.append(
                requestParameter.getAiAgentId(),
                AutoAgentMonitoringPolicy.Stage.EXECUTOR,
                executionPrompt
        );

        // 获取对话客户端
        ChatClient chatClient = getChatClientByClientId(aiAgentClientFlowConfigVO.getClientId());

        ExecutionAttempt executionAttempt = executeWithTrace(chatClient, executionPrompt, requestParameter.getSessionId());
        boolean hasBoundMcpTools = hasBoundMcpTools(aiAgentClientFlowConfigVO.getClientId());

        if (hasBoundMcpTools && executionAttempt.toolRecords().isEmpty()) {
            log.warn("精准执行器本轮未调用 MCP 工具，将进行一次定向重试: clientId={}, step={}",
                    aiAgentClientFlowConfigVO.getClientId(), dynamicContext.getStep());
            executionAttempt = executeWithTrace(
                    chatClient,
                    executionPrompt + ZERO_TOOL_RETRY_INSTRUCTION,
                    requestParameter.getSessionId()
            );
        }

        String executionResult = executionAttempt.content();
        if (hasBoundMcpTools && executionAttempt.toolRecords().isEmpty()) {
            log.error("精准执行器重试后仍未调用 MCP 工具: clientId={}, step={}",
                    aiAgentClientFlowConfigVO.getClientId(), dynamicContext.getStep());
            executionResult += "\n\n质量检查: 当前精准执行器已绑定 MCP 工具，但本轮重试后仍未发生工具调用；以上内容不应视为真实外部数据查询结果。";
        }

        logToolExecutionSummary(aiAgentClientFlowConfigVO.getClientId(), dynamicContext.getStep(), executionAttempt.toolRecords());
        dynamicContext.setValue("executionToolRecords", executionAttempt.toolRecords());

        assert executionResult != null;
        parseExecutionResult(dynamicContext, executionResult, requestParameter.getSessionId());
        
        // 将执行结果保存到动态上下文中，供下一步使用
        dynamicContext.setValue("executionResult", executionResult);
        
        return router(requestParameter, dynamicContext);
    }

    private ExecutionAttempt executeWithTrace(ChatClient chatClient, String prompt, String sessionId) {
        ToolExecutionTrace.start();
        try {
            String content = chatClient
                    .prompt(prompt)
                    .advisors(a -> a
                            .param(CHAT_MEMORY_CONVERSATION_ID_KEY, sessionId + ":execution")
                            .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 50))
                    .call().content();
            return new ExecutionAttempt(content == null ? "" : content, ToolExecutionTrace.snapshot());
        } finally {
            ToolExecutionTrace.clear();
        }
    }

    private boolean hasBoundMcpTools(String clientId) {
        List<AiClientVO> clientList = repository.AiClientVOByClientIds(List.of(clientId));
        return clientList != null
                && !clientList.isEmpty()
                && clientList.get(0).getMcpIdList() != null
                && !clientList.get(0).getMcpIdList().isEmpty();
    }

    private void logToolExecutionSummary(String clientId, int step, List<ToolExecutionRecord> records) {
        long successCount = records.stream().filter(ToolExecutionRecord::succeeded).count();
        log.info("精准执行器 MCP 调用汇总: clientId={}, step={}, total={}, success={}, failure={}, tools={}",
                clientId,
                step,
                records.size(),
                successCount,
                records.size() - successCount,
                records.stream().map(ToolExecutionRecord::toolName).toList());
    }

    private record ExecutionAttempt(String content, List<ToolExecutionRecord> toolRecords) {
    }

    @Override
    public StrategyHandler<ExecuteCommandEntity, DefaultAutoAgentExecuteStrategyFactory.DynamicContext, String> get(ExecuteCommandEntity requestParameter, DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws Exception {
        return getBean("step3QualitySupervisorNode");
    }
    
    /**
     * 解析执行结果
     */
    private void parseExecutionResult(DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext, String executionResult, String sessionId) {
        int step = dynamicContext.getStep();
        log.info("\n⚡ === 第 {} 轮执行结果 ===", step);
        for (StageOutputParser.Section section : StageOutputParser.parse(
                executionResult, "execution_result", SECTION_MAPPINGS)) {
            log.info("   📊 [{}] {}", section.type(), section.content());
            sendExecutionSubResult(dynamicContext, section.type(), section.content(), sessionId);
        }
    }
    
    /**
     * 发送执行阶段细分结果到流式输出
     */
    private void sendExecutionSubResult(DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext, 
                                       String subType, String content, String sessionId) {
        // 抽取的通用判断逻辑
        if (!subType.isEmpty() && !content.isEmpty()) {
            AutoAgentExecuteResultEntity result = AutoAgentExecuteResultEntity.createExecutionSubResult(
                    dynamicContext.getStep(), subType, content, sessionId);
            sendSseResult(dynamicContext, result);
        }
    }
    
}
