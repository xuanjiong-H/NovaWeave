package cn.bugstack.ai.domain.agent.service.execute.auto.step;

import cn.bugstack.ai.domain.agent.model.entity.AutoAgentExecuteResultEntity;
import cn.bugstack.ai.domain.agent.model.entity.ExecuteCommandEntity;
import cn.bugstack.ai.domain.agent.model.valobj.AiAgentClientFlowConfigVO;
import cn.bugstack.ai.domain.agent.model.valobj.enums.AiClientTypeEnumVO;
import cn.bugstack.ai.domain.agent.service.execute.auto.parser.FinalSummaryFormatter;
import cn.bugstack.ai.domain.agent.service.execute.auto.policy.AutoAgentMonitoringPolicy;
import cn.bugstack.ai.domain.agent.service.execute.auto.step.factory.DefaultAutoAgentExecuteStrategyFactory;
import cn.bugstack.ai.domain.agent.service.sse.SseConnectionClosedException;
import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/**
 * 执行总结节点
 *
 * @author xiaofuge bugstack.cn @小傅哥
 * 2025/7/27 16:45
 */
@Slf4j
@Service
public class Step4LogExecutionSummaryNode extends AbstractExecuteSupport {

    @Override
    protected String doApply(ExecuteCommandEntity requestParameter, DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws Exception {
        log.info("\n📊 === 生成执行总结 ===");
        
        // 记录执行总结
        logExecutionSummary(dynamicContext.getMaxStep(), dynamicContext.getCompletedRounds(), dynamicContext.isCompleted());
        
        // 生成最终总结报告（无论任务是否完成都需要生成）
        generateFinalReport(requestParameter, dynamicContext);
        
        log.info("\n🏁 === 动态多轮执行结束 ====");
        
        return "ai agent execution summary completed!";
    }

    @Override
    public StrategyHandler<ExecuteCommandEntity, DefaultAutoAgentExecuteStrategyFactory.DynamicContext, String> get(ExecuteCommandEntity requestParameter, DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws Exception {
        // 总结节点是最后一个节点，返回null表示执行结束
        return defaultStrategyHandler;
    }
    
    /**
     * 记录执行总结
     */
    private void logExecutionSummary(int maxRounds, int completedRounds, boolean isCompleted) {
        log.info("\n📊 === 动态多轮执行总结 ====");

        log.info("📈 实际执行轮数: {} 轮", completedRounds);
        
        if (isCompleted) {
            log.info("✅ 任务完成状态: 已完成");
        } else {
            log.info("⏸️ 任务完成状态: 未完成（达到最大执行轮数）");
        }

        double roundUsage = maxRounds > 0 ? (double) completedRounds / maxRounds * 100 : 0;
        log.info("📊 执行轮次使用率: {}%", Math.min(roundUsage, 100.0));
    }
    
    /**
     * 生成最终总结报告
     */
    private void generateFinalReport(ExecuteCommandEntity requestParameter, DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext) {
        try {
            boolean isCompleted = dynamicContext.isCompleted();
            log.info("\n--- 生成{}任务的最终答案 ---", isCompleted ? "已完成" : "未完成");

            AiAgentClientFlowConfigVO aiAgentClientFlowConfigVO = dynamicContext.getAiAgentClientFlowConfigVOMap().get(AiClientTypeEnumVO.RESPONSE_ASSISTANT.getCode());

            String summaryPrompt = getSummaryPrompt(aiAgentClientFlowConfigVO, requestParameter, dynamicContext, isCompleted);

            // 获取对话客户端 - 使用任务分析客户端进行总结
            ChatClient chatClient = getChatClientByClientId(aiAgentClientFlowConfigVO.getClientId());
            
            String summaryResult = chatClient
                    .prompt(summaryPrompt)
                    .advisors(a -> a
                            .param(CHAT_MEMORY_CONVERSATION_ID_KEY, requestParameter.getSessionId() + "-summary")
                            .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 50))
                    .call().content();

            assert summaryResult != null;
            String finalSummary = FinalSummaryFormatter.appendMaxRoundsNotice(
                    summaryResult,
                    dynamicContext.getMaxStep(),
                    dynamicContext.isCompleted(),
                    dynamicContext.isMaxRoundsReached(),
                    dynamicContext.getCurrentTask());
            logFinalReport(dynamicContext, finalSummary, requestParameter.getSessionId());
            
            // 将总结结果保存到动态上下文中
            dynamicContext.setValue("finalSummary", finalSummary);
            
        } catch (SseConnectionClosedException e) {
            throw e;
        } catch (Exception e) {
            log.error("生成最终总结报告时出现异常: {}", e.getMessage(), e);
        }
    }

    private static String getSummaryPrompt(AiAgentClientFlowConfigVO aiAgentClientFlowConfigVO, ExecuteCommandEntity requestParameter, DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext, boolean isCompleted) {
        String executionContext = dynamicContext.getExecutionHistory().toString();
        if (executionContext.isBlank()) {
            String analysisResult = dynamicContext.getValue("analysisResult");
            if (analysisResult != null && !analysisResult.isBlank()) {
                executionContext = "【任务分析及工具查询结果】\n" + analysisResult;
            }
        }

        String summaryPrompt = String.format(aiAgentClientFlowConfigVO.getStepPrompt(),
                requestParameter.getMessage(),
                executionContext);
        summaryPrompt = AutoAgentMonitoringPolicy.append(
                requestParameter.getAiAgentId(),
                AutoAgentMonitoringPolicy.Stage.SUMMARY,
                summaryPrompt
        );
        if (isCompleted) {
            return summaryPrompt;
        }
        return summaryPrompt + """

                # 未完成任务总结约束
                当前已达到最大执行轮数，但任务尚未完全完成。请在专业报告中：
                1. 先给出已有数据能够支持的部分答案。
                2. 明确区分已确认结果与未获取结果，不得补写或估算缺失数据。
                3. 说明剩余事项及其未完成原因。
                4. 不得将任务描述为已经完成。
                """;
    }

    /**
     * 输出最终总结报告
     */
    private void logFinalReport(DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext, String summaryResult, String sessionId) {
        boolean isCompleted = dynamicContext.isCompleted();
        log.info("\n📋 === {}任务最终总结报告 ===", isCompleted ? "已完成" : "未完成");

        String[] lines = summaryResult.split("\n");
        
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;
            
            // 根据内容类型添加不同图标
            if (line.contains("已完成") || line.contains("完成的工作")) {
                log.info("✅ {}", line);
            } else if (line.contains("未完成") || line.contains("原因")) {
                log.info("❌ {}", line);
            } else if (line.contains("建议") || line.contains("推荐")) {
                log.info("💡 {}", line);
            } else if (line.contains("评估") || line.contains("效果")) {
                log.info("📊 {}", line);
            } else {
                log.info("📝 {}", line);
            }
        }

        // 发送完整的总结结果
        sendSummaryResult(dynamicContext, summaryResult, sessionId);
        
    }
    
    /**
     * 发送总结结果到流式输出
     */
    private void sendSummaryResult(DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext, 
                                 String summaryResult, String sessionId) {
        AutoAgentExecuteResultEntity result = AutoAgentExecuteResultEntity.createSummaryResult(
                 summaryResult, sessionId);
        sendSseResult(dynamicContext, result);
    }
    
}
