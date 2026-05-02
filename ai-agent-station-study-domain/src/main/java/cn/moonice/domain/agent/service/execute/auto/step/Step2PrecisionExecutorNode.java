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
 * 精准执行节点
 *
 * @author xiaofuge bugstack.cn @小傅哥
 * 2025/7/27 16:42
 */
@Slf4j
@Service
public class Step2PrecisionExecutorNode extends AbstractExecuteSupport {

    @Override
    protected String doApply(ExecuteCommandEntity requestParameter, DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws Exception {
        log.info("\n⚡ 阶段2: 精准任务执行");
        
        // 从动态上下文中获取分析结果
        String analysisResult = dynamicContext.getValue("analysisResult");
        if (analysisResult == null || analysisResult.trim().isEmpty()) {
            log.warn("⚠️ 分析结果为空，使用默认执行策略");
            analysisResult = "执行当前任务步骤";
        }
        
        String executionPrompt = String.format("""
                **分析师策略:** %s
                
                **执行指令:** 根据上述分析师的策略，执行具体的任务步骤。
                
                **执行要求:**
                1. 严格按照策略执行
                2. 使用必要的工具
                3. 确保执行质量
                4. 详细记录过程
                
                **输出格式:**
                执行目标: [明确的执行目标]
                执行过程: [详细的执行步骤]
                执行结果: [具体的执行成果]
                质量检查: [自我质量评估]
                """, analysisResult);

        // 获取对话客户端
        AiAgentClientFlowConfigVO aiAgentClientFlowConfigVO = dynamicContext.getAiAgentClientFlowConfigVOMap().get(AiClientTypeEnumVO.PRECISION_EXECUTOR_CLIENT.getCode());
        ChatClient chatClient = getChatClientByClientId(aiAgentClientFlowConfigVO.getClientId());

        String executionResult = chatClient
                .prompt(executionPrompt)
                .advisors(a -> a
                        .param(CHAT_MEMORY_CONVERSATION_ID_KEY, requestParameter.getSessionId())
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 1024))
                .call().content();

        parseExecutionResult(dynamicContext.getStep(), executionResult);
        
        // 将执行结果保存到动态上下文中，供下一步使用
        dynamicContext.setValue("executionResult", executionResult);
        
        // 更新执行历史
        String stepSummary = String.format("""
                === 第 %d 步执行记录 ===
                【分析阶段】%s
                【执行阶段】%s
                """, dynamicContext.getStep(), analysisResult, executionResult);
        
        dynamicContext.getExecutionHistory().append(stepSummary);

        return router(requestParameter, dynamicContext);
    }

    @Override
    public StrategyHandler<ExecuteCommandEntity, DefaultAutoAgentExecuteStrategyFactory.DynamicContext, String> get(ExecuteCommandEntity requestParameter, DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws Exception {
        return getBean("step3QualitySupervisorNode");
    }
    
    /**
     * 解析执行结果
     */
    private void parseExecutionResult(int step, String executionResult) {
        log.info("\n⚡ === 第 {} 步执行结果 ===", step);
        
        String[] lines = executionResult.split("\n");
        String currentSection = "";
        
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;
            
            if (line.contains("执行目标:")) {
                currentSection = "target";
                log.info("\n🎯 执行目标:");
                continue;
            } else if (line.contains("执行过程:")) {
                currentSection = "process";
                log.info("\n🔧 执行过程:");
                continue;
            } else if (line.contains("执行结果:")) {
                currentSection = "result";
                log.info("\n📈 执行结果:");
                continue;
            } else if (line.contains("质量检查:")) {
                currentSection = "quality";
                log.info("\n🔍 质量检查:");
                continue;
            }
            
            switch (currentSection) {
                case "target":
                    log.info("   🎯 {}", line);
                    break;
                case "process":
                    log.info("   ⚙️ {}", line);
                    break;
                case "result":
                    log.info("   📊 {}", line);
                    break;
                case "quality":
                    log.info("   ✅ {}", line);
                    break;
                default:
                    log.info("   📝 {}", line);
                    break;
            }
        }
    }
    
}
