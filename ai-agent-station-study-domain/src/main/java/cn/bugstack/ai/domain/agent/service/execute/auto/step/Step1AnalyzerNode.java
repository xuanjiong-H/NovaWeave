package cn.bugstack.ai.domain.agent.service.execute.auto.step;

import cn.bugstack.ai.domain.agent.model.entity.AutoAgentExecuteResultEntity;
import cn.bugstack.ai.domain.agent.model.entity.ExecuteCommandEntity;
import cn.bugstack.ai.domain.agent.model.valobj.AiAgentClientFlowConfigVO;
import cn.bugstack.ai.domain.agent.model.valobj.enums.AiClientTypeEnumVO;
import cn.bugstack.ai.domain.agent.service.execute.auto.parser.StageOutputParser;
import cn.bugstack.ai.domain.agent.service.execute.auto.policy.AutoAgentMonitoringPolicy;
import cn.bugstack.ai.domain.agent.service.execute.auto.step.factory.DefaultAutoAgentExecuteStrategyFactory;
import cn.bugstack.ai.domain.agent.service.tool.McpToolCatalogService;
import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 任务分析节点
 *
 * @author xiaofuge bugstack.cn @小傅哥
 * 2025/7/27 16:36
 */
@Slf4j
@Service
public class Step1AnalyzerNode extends AbstractExecuteSupport {

    @Resource
    private McpToolCatalogService mcpToolCatalogService;

    private static final Map<String, String> SECTION_MAPPINGS = StageOutputParser.mappings(
            "任务状态分析", "analysis_status",
            "当前状况分析", "analysis_status",
            "需求理解", "analysis_status",
            "执行历史评估", "analysis_history",
            "MCP验证记录", "analysis_history",
            "下一步策略", "analysis_strategy",
            "执行计划", "analysis_strategy",
            "策略调整", "analysis_strategy",
            "完成度评估", "analysis_progress",
            "任务状态", "analysis_task_status"
    );

    @Override
    protected String doApply(ExecuteCommandEntity requestParameter, DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws Exception {
        log.info("\n🎯 === 执行第 {} 轮 ===", dynamicContext.getStep());

        // 获取配置信息
        AiAgentClientFlowConfigVO aiAgentClientFlowConfigVO = dynamicContext.getAiAgentClientFlowConfigVOMap().get(AiClientTypeEnumVO.TASK_ANALYZER_CLIENT.getCode());

        // 第一阶段：任务分析
        log.info("\n📊 阶段1: 任务状态分析");
        String analysisPrompt = String.format(aiAgentClientFlowConfigVO.getStepPrompt(),
                requestParameter.getMessage(),
                dynamicContext.getStep(),
                dynamicContext.getMaxStep(),
                !dynamicContext.getExecutionHistory().isEmpty() ? dynamicContext.getExecutionHistory().toString() : "[首次执行]",
                dynamicContext.getCurrentTask()
        );

        AiAgentClientFlowConfigVO executorConfig = dynamicContext.getAiAgentClientFlowConfigVOMap()
                .get(AiClientTypeEnumVO.PRECISION_EXECUTOR_CLIENT.getCode());
        String toolCatalog = executorConfig == null
                ? "未找到精准执行器配置，无法读取可用工具。"
                : mcpToolCatalogService.describeTools(executorConfig.getClientId());
        analysisPrompt += """

                # 执行器真实可用 MCP 工具
                以下内容仅用于制定计划，不代表工具已经执行。只能规划清单中真实存在的工具和参数。
                %s
                """.formatted(toolCatalog);
        analysisPrompt = AutoAgentMonitoringPolicy.append(
                requestParameter.getAiAgentId(),
                AutoAgentMonitoringPolicy.Stage.ANALYZER,
                analysisPrompt
        );

        ChatClient chatClient = getChatClientByClientId(aiAgentClientFlowConfigVO.getClientId());

        String analysisResult = chatClient
                .prompt(analysisPrompt)
                .advisors(a -> a
                        .param(CHAT_MEMORY_CONVERSATION_ID_KEY, requestParameter.getSessionId() + ":analysis")
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 50))
                .call().content();

        assert analysisResult != null;
        parseAnalysisResult(dynamicContext, analysisResult, requestParameter.getSessionId());
        
        // 将分析结果保存到动态上下文中，供下一步使用
        dynamicContext.setValue("analysisResult", analysisResult);

        // 分析阶段无权结束任务，完成状态统一由质量监督节点决定。
        return router(requestParameter, dynamicContext);
    }

    @Override
    public StrategyHandler<ExecuteCommandEntity, DefaultAutoAgentExecuteStrategyFactory.DynamicContext, String> get(ExecuteCommandEntity requestParameter, DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws Exception {
        if (dynamicContext.getStep() > dynamicContext.getMaxStep()) {
            return getBean("step4LogExecutionSummaryNode");
        }
        
        // 否则继续执行下一步
        return getBean("step2PrecisionExecutorNode");
    }

    private void parseAnalysisResult(DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext, String analysisResult, String sessionId) {
        int step = dynamicContext.getStep();
        log.info("\n📊 === 第 {} 轮分析结果 ===", step);
        for (StageOutputParser.Section section : StageOutputParser.parse(
                analysisResult, "analysis_status", SECTION_MAPPINGS)) {
            log.info("   📋 [{}] {}", section.type(), section.content());
            sendAnalysisSubResult(dynamicContext, section.type(), section.content(), sessionId);
        }
    }

    /**
     * 发送分析阶段细分结果到流式输出
     */
    private void sendAnalysisSubResult(DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext, 
                                      String subType, String content, String sessionId) {
        if (!subType.isEmpty() && !content.isEmpty()) {
            AutoAgentExecuteResultEntity result = AutoAgentExecuteResultEntity.createAnalysisSubResult(
                    dynamicContext.getStep(), subType, content, sessionId);
            sendSseResult(dynamicContext, result);
        }
    }

}
