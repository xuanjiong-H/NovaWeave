package cn.moonice.domain.agent.service.execute.auto.step;

import cn.moonice.domain.agent.model.entity.ExecuteCommandEntity;
import cn.moonice.domain.agent.model.valobj.AiAgentClientFlowConfigVO;
import cn.moonice.domain.agent.model.valobj.enums.AiClientTypeEnumVO;
import cn.moonice.domain.agent.service.execute.auto.step.factory.DefaultAutoAgentExecuteStrategyFactory;
import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/**
 * 任务分析节点
 *
 * @author xiaofuge bugstack.cn @小傅哥
 * 2025/7/27 16:36
 */
@Slf4j
@Service
public class Step1AnalyzerNode extends AbstractExecuteSupport {

    @Override
    protected String doApply(ExecuteCommandEntity requestParameter, DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws Exception {
        log.info("\n🎯 === 执行第 {} 步 ===", dynamicContext.getStep());

        // 第一阶段：任务分析
        log.info("\n📊 阶段1: 任务状态分析");
        String analysisPrompt = String.format("""
                        **原始用户需求:** %s
                        
                        **当前执行步骤:** 第 %d 步 (最大 %d 步)
                        
                        **历史执行记录:**
                        %s
                        
                        **当前任务:** %s
                        
                        请分析当前任务状态，评估执行进度，并制定下一步策略。
                        """,
                requestParameter.getMessage(),
                dynamicContext.getStep(),
                dynamicContext.getMaxStep(),
                !dynamicContext.getExecutionHistory().isEmpty() ? dynamicContext.getExecutionHistory().toString() : "[首次执行]",
                dynamicContext.getCurrentTask()
        );

        // 获取对话客户端
        AiAgentClientFlowConfigVO aiAgentClientFlowConfigVO = dynamicContext.getAiAgentClientFlowConfigVOMap().get(AiClientTypeEnumVO.TASK_ANALYZER_CLIENT.getCode());
        ChatClient chatClient = getChatClientByClientId(aiAgentClientFlowConfigVO.getClientId());

        String analysisResult = chatClient
                .prompt(analysisPrompt)
                .advisors(a -> a
                        .param(CHAT_MEMORY_CONVERSATION_ID_KEY, requestParameter.getSessionId())
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 1024))
                .call().content();

        assert analysisResult != null;
        parseAnalysisResult(dynamicContext.getStep(), analysisResult);
        
        // 将分析结果保存到动态上下文中，供下一步使用
        dynamicContext.setValue("analysisResult", analysisResult);

        // 检查是否已完成
        if (analysisResult.contains("任务状态: COMPLETED") ||
                analysisResult.contains("完成度评估: 100%")) {
            dynamicContext.setCompleted(true);
            log.info("✅ 任务分析显示已完成！");
            return router(requestParameter, dynamicContext);
        }

        return router(requestParameter, dynamicContext);
    }

    @Override
    public StrategyHandler<ExecuteCommandEntity, DefaultAutoAgentExecuteStrategyFactory.DynamicContext, String> get(ExecuteCommandEntity requestParameter, DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws Exception {
        // 如果任务已完成或达到最大步数，进入总结阶段
        if (dynamicContext.isCompleted() || dynamicContext.getStep() > dynamicContext.getMaxStep()) {
            return getBean("step4LogExecutionSummaryNode");
        }
        
        // 否则继续执行下一步
        return getBean("step2PrecisionExecutorNode");
    }

    private void parseAnalysisResult(int step, String analysisResult) {
        log.info("\n📊 === 第 {} 步分析结果 ===", step);

        String[] lines = analysisResult.split("\n");
        String currentSection = "";

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;

            if (line.contains("任务状态分析:")) {
                currentSection = "status";
                log.info("\n🎯 任务状态分析:");
                continue;
            } else if (line.contains("执行历史评估:")) {
                currentSection = "history";
                log.info("\n📈 执行历史评估:");
                continue;
            } else if (line.contains("下一步策略:")) {
                currentSection = "strategy";
                log.info("\n🚀 下一步策略:");
                continue;
            } else if (line.contains("完成度评估:")) {
                currentSection = "progress";
                String progress = line.substring(line.indexOf(":") + 1).trim();
                log.info("\n📊 完成度评估: {}", progress);
                continue;
            } else if (line.contains("任务状态:")) {
                currentSection = "task_status";
                String status = line.substring(line.indexOf(":") + 1).trim();
                if (status.equals("COMPLETED")) {
                    log.info("\n✅ 任务状态: 已完成");
                } else {
                    log.info("\n🔄 任务状态: 继续执行");
                }
                continue;
            }

            switch (currentSection) {
                case "status":
                    log.info("   📋 {}", line);
                    break;
                case "history":
                    log.info("   📊 {}", line);
                    break;
                case "strategy":
                    log.info("   🎯 {}", line);
                    break;
                default:
                    log.info("   📝 {}", line);
                    break;
            }
        }
    }

}
