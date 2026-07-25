package cn.bugstack.ai.domain.agent.service.execute.flow.step;

import cn.bugstack.ai.domain.agent.model.entity.AutoAgentExecuteResultEntity;
import cn.bugstack.ai.domain.agent.model.entity.ExecuteCommandEntity;
import cn.bugstack.ai.domain.agent.model.valobj.AiAgentClientFlowConfigVO;
import cn.bugstack.ai.domain.agent.model.valobj.enums.AiClientTypeEnumVO;
import cn.bugstack.ai.domain.agent.service.execute.flow.step.factory.DefaultFlowAgentExecuteStrategyFactory;
import cn.bugstack.ai.domain.agent.service.tool.McpToolCatalogService;
import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/**
 * 步骤2：执行步骤规划节点
 *
 * @author xiaofuge bugstack.cn @小傅哥
 * 2025/8/25 10:30
 */
@Slf4j
@Service
public class Step2PlanningNode extends AbstractExecuteSupport {

     @Resource
     private Step3ParseStepsNode step3ParseStepsNode;

     @Resource
     private McpToolCatalogService mcpToolCatalogService;

    @Override
    protected String doApply(ExecuteCommandEntity requestParameter, DefaultFlowAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws Exception {
        log.info("\n--- 步骤2: 执行步骤规划 ---");

        // 获取配置信息
        AiAgentClientFlowConfigVO aiAgentClientFlowConfigVO = dynamicContext.getAiAgentClientFlowConfigVOMap().get(AiClientTypeEnumVO.PLANNING_CLIENT.getCode());

        // 获取规划客户端
        ChatClient planningChatClient = getChatClientByClientId(aiAgentClientFlowConfigVO.getClientId());

        String userRequest = dynamicContext.getCurrentTask();
//        String mcpToolsAnalysis = dynamicContext.getValue("mcpToolsAnalysis");

        // 规划必须以执行器实际拥有的工具为准，不能使用规划客户端或硬编码工具别名
        AiAgentClientFlowConfigVO executorClientConfig = dynamicContext.getAiAgentClientFlowConfigVOMap()
                .get(AiClientTypeEnumVO.EXECUTOR_CLIENT.getCode());
        String actualMcpToolsInfo = executorClientConfig == null
                ? "未找到执行器 Client 配置，无法获取 MCP 工具清单。\n"
                : mcpToolCatalogService.describeTools(executorClientConfig.getClientId());
        log.info("执行器实际注册的 MCP 工具清单:\n{}", actualMcpToolsInfo);

        String planningPrompt = buildStructuredPlanningPrompt(
                userRequest,
                actualMcpToolsInfo
        );

        String planningResult = planningChatClient.prompt()
                .user(planningPrompt)
                .call()
                .content();

        if (planningResult == null || planningResult.isBlank()) {
            log.error(
                    "规划模型未返回文本，agentId={}, clientId={}, promptLength={}",
                    requestParameter.getAiAgentId(),
                    aiAgentClientFlowConfigVO.getClientId(),
                    planningPrompt.length()
            );
            throw new IllegalStateException(
                    "规划模型未返回文本，请检查规划客户端模型配置和模型响应"
            );
        }


        log.info("执行步骤规划结果: {}", planningResult);
        
        // 保存规划结果到上下文
        dynamicContext.setValue("planningResult", planningResult);
        
        // 发送SSE结果
        AutoAgentExecuteResultEntity result = AutoAgentExecuteResultEntity.createAnalysisSubResult(
                dynamicContext.getStep(), 
                "analysis_strategy", 
                planningResult, 
                requestParameter.getSessionId());
        sendSseResult(dynamicContext, result);
        
        // 更新步骤
        dynamicContext.setStep(dynamicContext.getStep() + 1);
        
        return router(requestParameter, dynamicContext);
    }

    /**
     * 构建结构化的规划提示词
     */
    private String buildStructuredPlanningPrompt(String userRequest, String actualMcpToolsInfo) {
        return """
            # 任务执行计划

            ## 用户请求
            %s

            ## 执行器真实可用 MCP 工具
            %s

            ## 规划要求
            1. 仅使用上方工具清单中存在的准确工具名称。
            2. 工具参数必须符合对应 Schema。
            3. 生成 3 至 5 个有依赖关系的步骤。
            4. 内容生成属于模型工作，不要为内容生成虚构 MCP 工具。
            5. 需要发布和通知时，先发布，再使用发布步骤返回的真实 URL 通知。
            6. 不得声称工具已执行成功；当前只生成计划。
            7. 仅输出如下 Markdown 格式：

            # 执行步骤规划

            [ ] 第1步：步骤描述
            [ ] 第2步：步骤描述
            [ ] 第3步：步骤描述

            ## 步骤详情

            ### 第1步：步骤描述
            - 使用工具：工具名或无
            - 依赖步骤：无
            - 执行方法：具体工作内容
            - 工具参数：符合 Schema 的参数来源或结构
            - 预期输出：本步骤真实产物

            ### 第2步：步骤描述
            - 使用工具：工具名或无
            - 依赖步骤：第1步
            - 执行方法：具体工作内容
            - 工具参数：符合 Schema 的参数来源或结构
            - 预期输出：本步骤真实产物
            """.formatted(userRequest, actualMcpToolsInfo);
    }

    @Override
    public StrategyHandler<ExecuteCommandEntity, DefaultFlowAgentExecuteStrategyFactory.DynamicContext, String> get(ExecuteCommandEntity requestParameter, DefaultFlowAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws Exception {
        return step3ParseStepsNode;
    }

}
