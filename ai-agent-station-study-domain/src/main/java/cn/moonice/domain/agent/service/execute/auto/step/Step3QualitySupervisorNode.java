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
 * 质量监督节点
 *
 * @author xiaofuge bugstack.cn @小傅哥
 * 2025/7/27 16:43
 */
@Slf4j
@Service
public class Step3QualitySupervisorNode extends AbstractExecuteSupport {

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
        
        String supervisionPrompt = String.format("""
                **用户原始需求:** %s
                
                **执行结果:** %s
                
                **监督要求:** 请评估执行结果的质量，识别问题，并提供改进建议。
                
                **输出格式:**
                质量评估: [对执行结果的整体评估]
                问题识别: [发现的问题和不足]
                改进建议: [具体的改进建议]
                质量评分: [1-10分的质量评分]
                是否通过: [PASS/FAIL/OPTIMIZE]
                """, requestParameter.getMessage(), executionResult);

        // 获取对话客户端
        AiAgentClientFlowConfigVO aiAgentClientFlowConfigVO = dynamicContext.getAiAgentClientFlowConfigVOMap().get(AiClientTypeEnumVO.QUALITY_SUPERVISOR_CLIENT.getCode());
        ChatClient chatClient = getChatClientByClientId(aiAgentClientFlowConfigVO.getClientId());

        String supervisionResult = chatClient
                .prompt(supervisionPrompt)
                .advisors(a -> a
                        .param(CHAT_MEMORY_CONVERSATION_ID_KEY, requestParameter.getSessionId())
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 1024))
                .call().content();

        parseSupervisionResult(dynamicContext.getStep(), supervisionResult);
        
        // 将监督结果保存到动态上下文中
        dynamicContext.setValue("supervisionResult", supervisionResult);
        
        // 根据监督结果决定是否需要重新执行
        if (supervisionResult.contains("是否通过: FAIL")) {
            log.info("❌ 质量检查未通过，需要重新执行");
            dynamicContext.setCurrentTask("根据质量监督的建议重新执行任务");
        } else if (supervisionResult.contains("是否通过: OPTIMIZE")) {
            log.info("🔧 质量检查建议优化，继续改进");
            dynamicContext.setCurrentTask("根据质量监督的建议优化执行结果");
        } else {
            log.info("✅ 质量检查通过");
            dynamicContext.setCompleted(true);
        }
        
        // 更新执行历史
        String stepSummary = String.format("""
                === 第 %d 步完整记录 ===
                【分析阶段】%s
                【执行阶段】%s
                【监督阶段】%s
                """, dynamicContext.getStep(), 
                dynamicContext.getValue("analysisResult"), 
                executionResult, 
                supervisionResult);
        
        dynamicContext.getExecutionHistory().append(stepSummary);
        
        // 增加步骤计数
        dynamicContext.setStep(dynamicContext.getStep() + 1);
        
        // 如果任务已完成或达到最大步数，进入总结阶段
        if (dynamicContext.isCompleted() || dynamicContext.getStep() > dynamicContext.getMaxStep()) {
            return router(requestParameter, dynamicContext);
        }
        
        // 否则继续下一轮执行，返回到Step1AnalyzerNode
        return router(requestParameter, dynamicContext);
    }

    @Override
    public StrategyHandler<ExecuteCommandEntity, DefaultAutoAgentExecuteStrategyFactory.DynamicContext, String> get(ExecuteCommandEntity requestParameter, DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws Exception {
        // 如果任务已完成或达到最大步数，进入总结阶段
        if (dynamicContext.isCompleted() || dynamicContext.getStep() > dynamicContext.getMaxStep()) {
            return getBean("step4LogExecutionSummaryNode");
        }
        
        // 否则返回到Step1AnalyzerNode进行下一轮分析
        return getBean("step1AnalyzerNode");
    }
    
    /**
     * 解析监督结果
     */
    private void parseSupervisionResult(int step, String supervisionResult) {
        log.info("\n🔍 === 第 {} 步监督结果 ===", step);
        
        String[] lines = supervisionResult.split("\n");
        String currentSection = "";
        
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;
            
            if (line.contains("质量评估:")) {
                currentSection = "assessment";
                log.info("\n📊 质量评估:");
                continue;
            } else if (line.contains("问题识别:")) {
                currentSection = "issues";
                log.info("\n⚠️ 问题识别:");
                continue;
            } else if (line.contains("改进建议:")) {
                currentSection = "suggestions";
                log.info("\n💡 改进建议:");
                continue;
            } else if (line.contains("质量评分:")) {
                currentSection = "score";
                String score = line.substring(line.indexOf(":") + 1).trim();
                log.info("\n📊 质量评分: {}", score);
                continue;
            } else if (line.contains("是否通过:")) {
                currentSection = "pass";
                String status = line.substring(line.indexOf(":") + 1).trim();
                if (status.equals("PASS")) {
                    log.info("\n✅ 检查结果: 通过");
                } else if (status.equals("FAIL")) {
                    log.info("\n❌ 检查结果: 未通过");
                } else {
                    log.info("\n🔧 检查结果: 需要优化");
                }
                continue;
            }
            
            switch (currentSection) {
                case "assessment":
                    log.info("   📋 {}", line);
                    break;
                case "issues":
                    log.info("   ⚠️ {}", line);
                    break;
                case "suggestions":
                    log.info("   💡 {}", line);
                    break;
                default:
                    log.info("   📝 {}", line);
                    break;
            }
        }
    }
    
}
