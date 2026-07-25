package cn.bugstack.ai.domain.agent.service.execute.auto.step;

import cn.bugstack.ai.domain.agent.model.entity.AutoAgentExecuteResultEntity;
import cn.bugstack.ai.domain.agent.model.entity.ExecuteCommandEntity;
import cn.bugstack.ai.domain.agent.model.valobj.AiAgentClientFlowConfigVO;
import cn.bugstack.ai.domain.agent.model.valobj.enums.AiClientTypeEnumVO;
import cn.bugstack.ai.domain.agent.model.valobj.enums.QualityDecisionEnumVO;
import cn.bugstack.ai.domain.agent.service.execute.auto.parser.QualityDecisionParser;
import cn.bugstack.ai.domain.agent.service.execute.auto.parser.StageOutputParser;
import cn.bugstack.ai.domain.agent.service.execute.auto.step.factory.DefaultAutoAgentExecuteStrategyFactory;
import cn.bugstack.ai.domain.agent.service.tool.ToolInvocationPolicyContext;
import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 质量监督节点
 *
 * @author xiaofuge bugstack.cn @小傅哥
 * 2025/7/27 16:43
 */
@Slf4j
@Service
public class Step3QualitySupervisorNode extends AbstractExecuteSupport {

    private static final Map<String, String> SECTION_MAPPINGS = StageOutputParser.mappings(
            "质量评估", "assessment",
            "需求匹配度", "assessment",
            "内容完整性", "assessment",
            "当前状况分析", "assessment",
            "阶段评价", "assessment",
            "MCP验证记录", "assessment",
            "问题识别", "issues",
            "改进建议", "suggestions",
            "下一步重点", "suggestions",
            "下一步建议", "suggestions",
            "质量评分", "score",
            "是否通过", "pass",
            "评估结果", "pass",
            "检查结果", "pass",
            "监督结果", "pass"
    );

    @Override
    protected String doApply(ExecuteCommandEntity requestParameter, DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws Exception {
        // 第三阶段：质量监督
        log.info("\n🔍 阶段3: 质量监督检查");
        
        // 从动态上下文中获取执行结果
        String executionResult = dynamicContext.getValue("executionResult");
        if (executionResult == null || executionResult.trim().isEmpty()) {
            log.warn("⚠️ 执行结果为空，跳过质量监督");
            return "质量监督跳过";
        }

        AiAgentClientFlowConfigVO aiAgentClientFlowConfigVO = dynamicContext.getAiAgentClientFlowConfigVOMap().get(AiClientTypeEnumVO.QUALITY_SUPERVISOR_CLIENT.getCode());
        
        String supervisionPrompt = String.format(aiAgentClientFlowConfigVO.getStepPrompt(), requestParameter.getMessage(), executionResult);

        // 获取对话客户端
        ChatClient chatClient = getChatClientByClientId(aiAgentClientFlowConfigVO.getClientId());

        String supervisionResult;
        try (ToolInvocationPolicyContext.Scope ignored =
                     ToolInvocationPolicyContext.startSupervisorPolicy(requestParameter.getAiAgentId(), 2)) {
            supervisionResult = chatClient
                    .prompt(supervisionPrompt)
                    .advisors(a -> a
                            .param(CHAT_MEMORY_CONVERSATION_ID_KEY, requestParameter.getSessionId() + ":supervision")
                            .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 50))
                    .call().content();
        }

        assert supervisionResult != null;
        QualityDecisionParser.Result decisionResult = QualityDecisionParser.parse(supervisionResult);
        parseSupervisionResult(dynamicContext, supervisionResult, decisionResult, requestParameter.getSessionId());

        // 将监督结果保存到动态上下文中
        dynamicContext.setValue("supervisionResult", supervisionResult);

        QualityDecisionEnumVO decision = decisionResult.decision();
        dynamicContext.setValue("qualityDecision", decision);
        if (decisionResult.adjustedForIncompleteTask()) {
            log.warn("⚠️ 监督结果声明 PASS，但同时表明任务未完成，已按 OPTIMIZE 继续执行");
        }

        String nextTask = decisionResult.nextTask();
        switch (decision) {
            case PASS -> {
                log.info("✅ 质量检查通过");
                dynamicContext.setCompleted(true);
            }
            case FAIL -> {
                log.info("❌ 质量检查未通过，需要重新执行");
                dynamicContext.setCurrentTask(nextTask != null ? nextTask : "根据质量监督的建议重新执行任务");
            }
            case OPTIMIZE -> {
                log.info("🔧 质量检查建议优化，继续改进");
                dynamicContext.setCurrentTask(nextTask != null ? nextTask : "根据质量监督的建议优化执行结果");
            }
            case UNKNOWN -> {
                log.warn("⚠️ 无法识别质量监督决策，按 OPTIMIZE 继续执行");
                dynamicContext.setCurrentTask(nextTask != null ? nextTask : "质量监督决策格式异常，重新检查并完善执行结果");
            }
        }
        
        // 更新执行历史
        String stepSummary = String.format("""
                === 第 %d 轮完整记录 ===
                【分析阶段】%s
                【执行阶段】%s
                【监督阶段】%s
                """, dynamicContext.getStep(), 
                dynamicContext.getValue("analysisResult"), 
                executionResult, 
                supervisionResult);
        
        dynamicContext.getExecutionHistory().append(stepSummary);
        dynamicContext.setCompletedRounds(dynamicContext.getCompletedRounds() + 1);

        // 完成一轮后增加轮次计数
        dynamicContext.setStep(dynamicContext.getStep() + 1);
        if (!dynamicContext.isCompleted() && dynamicContext.getCompletedRounds() >= dynamicContext.getMaxStep()) {
            dynamicContext.setMaxRoundsReached(true);
            log.info("⏸️ 已达到最大执行轮数 {}，将基于现有结果生成最终回答", dynamicContext.getMaxStep());
        }

        return router(requestParameter, dynamicContext);
    }

    @Override
    public StrategyHandler<ExecuteCommandEntity, DefaultAutoAgentExecuteStrategyFactory.DynamicContext, String> get(ExecuteCommandEntity requestParameter, DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws Exception {
        if (dynamicContext.isCompleted() || dynamicContext.isMaxRoundsReached()) {
            return getBean("step4LogExecutionSummaryNode");
        }
        
        // 否则返回到Step1AnalyzerNode进行下一轮分析
        return getBean("step1AnalyzerNode");
    }
    
    /**
     * 解析监督结果
     */
    private void parseSupervisionResult(DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext,
                                        String supervisionResult,
                                        QualityDecisionParser.Result decisionResult,
                                        String sessionId) {
        int step = dynamicContext.getStep();
        log.info("\n🔍 === 第 {} 轮监督结果 ===", step);
        for (StageOutputParser.Section section : StageOutputParser.parse(
                supervisionResult, "assessment", SECTION_MAPPINGS)) {
            if ("pass".equals(section.type())) {
                continue;
            }
            log.info("   📝 [{}] {}", section.type(), section.content());
            sendSupervisionSubResult(dynamicContext, section.type(), section.content(), sessionId);
        }

        String displayDecision = decisionResult.decision() == QualityDecisionEnumVO.UNKNOWN
                ? "UNKNOWN（按 OPTIMIZE 继续）"
                : decisionResult.decision().name();
        log.info("   📝 [pass] {}", displayDecision);
        sendSupervisionSubResult(dynamicContext, "pass", displayDecision, sessionId);
    }
    
    /**
     * 发送监督子结果到流式输出（细粒度标识）
     */
    private void sendSupervisionSubResult(DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext,
                                        String section, String content, String sessionId) {
        // 抽取的通用判断逻辑
        if (!content.isEmpty() && !section.isEmpty()) {
            AutoAgentExecuteResultEntity result = AutoAgentExecuteResultEntity.createSupervisionSubResult(
                    dynamicContext.getStep(), section, content, sessionId);
            sendSseResult(dynamicContext, result);
        }
    }

}
