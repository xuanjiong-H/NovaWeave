package cn.bugstack.ai.domain.agent.service.execute.flow.step;

import cn.bugstack.ai.domain.agent.model.entity.AutoAgentExecuteResultEntity;
import cn.bugstack.ai.domain.agent.model.entity.ExecuteCommandEntity;
import cn.bugstack.ai.domain.agent.service.execute.flow.parser.FlowExecutionPlanParser;
import cn.bugstack.ai.domain.agent.service.execute.flow.step.factory.DefaultFlowAgentExecuteStrategyFactory;
import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 步骤3：规划步骤解析节点
 *
 * @author xiaofuge bugstack.cn @小傅哥
 * 2025/8/25 11:00
 */
@Slf4j
@Service
public class Step3ParseStepsNode extends AbstractExecuteSupport {

    @Resource
    private Step4ExecuteStepsNode step4ExecuteStepsNode;

    @Override
    protected String doApply(ExecuteCommandEntity requestParameter, DefaultFlowAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws Exception {
        log.info("\n--- 步骤3: 规划步骤解析 ---");
        
        String planningResult = dynamicContext.getValue("planningResult");
        
        FlowExecutionPlanParser.Result parsedPlan = FlowExecutionPlanParser.parse(planningResult);
        FlowExecutionPlanParser.validateExecutable(parsedPlan, dynamicContext.getMaxPlanningSteps());
        Map<String, String> stepsMap = parsedPlan.steps();

        log.info("成功解析 {} 个执行步骤", stepsMap.size());
        
        // 保存解析结果到上下文
        dynamicContext.setValue("stepsMap", stepsMap);
        
        // 构建解析结果摘要
        StringBuilder parseResult = new StringBuilder();
        parseResult.append("## 步骤解析结果\n\n");
        parseResult.append(String.format("成功解析 %d 个执行步骤：\n\n", stepsMap.size()));
        
        for (Map.Entry<String, String> entry : stepsMap.entrySet()) {
            parseResult.append(String.format("- **%s**: %s\n", 
                entry.getKey(), 
                entry.getValue().split("\n")[0])); // 只显示标题部分
        }
        
        // 发送SSE结果
        AutoAgentExecuteResultEntity result = AutoAgentExecuteResultEntity.createAnalysisSubResult(
                null,
                "analysis_progress", 
                parseResult.toString(), 
                requestParameter.getSessionId());
        sendSseResult(dynamicContext, result);
        
        return router(requestParameter, dynamicContext);
    }

    @Override
    public StrategyHandler<ExecuteCommandEntity, DefaultFlowAgentExecuteStrategyFactory.DynamicContext, String> get(ExecuteCommandEntity requestParameter, DefaultFlowAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws Exception {
        return step4ExecuteStepsNode;
    }

}
