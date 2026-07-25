package cn.bugstack.ai.domain.agent.service.execute.flow.step;

import cn.bugstack.ai.domain.agent.model.entity.AutoAgentExecuteResultEntity;
import cn.bugstack.ai.domain.agent.model.entity.ExecuteCommandEntity;
import cn.bugstack.ai.domain.agent.model.valobj.AiAgentClientFlowConfigVO;
import cn.bugstack.ai.domain.agent.model.valobj.enums.AiClientTypeEnumVO;
import cn.bugstack.ai.domain.agent.service.execute.flow.step.factory.DefaultFlowAgentExecuteStrategyFactory;
import cn.bugstack.ai.domain.agent.service.tool.ToolExecutionTrace;
import cn.bugstack.ai.domain.agent.service.tool.ToolExecutionTrace.ToolExecutionRecord;
import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.net.URI;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 第四步：按顺序执行规划步骤节点
 *
 * @author xiaofuge bugstack.cn @小傅哥
 * 2025/8/25 10:30
 */
@Slf4j
@Component
public class Step4ExecuteStepsNode extends AbstractExecuteSupport {

    @Override
    public String doApply(ExecuteCommandEntity request, DefaultFlowAgentExecuteStrategyFactory.DynamicContext dynamicContext) {
        log.info("开始执行第四步：按顺序执行规划步骤");
        
        try {
            // 在获取执行器或调用任何业务工具前，再次确认规划步骤未超限
            Map<String, String> stepsMap = dynamicContext.getValue("stepsMap");
            validateStepsBeforeExecution(stepsMap, dynamicContext.getMaxPlanningSteps());

            // 获取配置信息
            AiAgentClientFlowConfigVO aiAgentClientFlowConfigVO = dynamicContext.getAiAgentClientFlowConfigVOMap().get(AiClientTypeEnumVO.EXECUTOR_CLIENT.getCode());

            // 获取规划客户端
            ChatClient executorChatClient = getChatClientByClientId(aiAgentClientFlowConfigVO.getClientId());
            
            // 按顺序执行规划步骤
            executeStepsInOrder(executorChatClient, stepsMap, dynamicContext);

            Map<String, Integer> stepErrorStats = dynamicContext.getValue("stepErrorStats");
            if (stepErrorStats != null && !stepErrorStats.isEmpty()) {
                throw new IllegalStateException(
                        "存在执行失败步骤：" + stepErrorStats.keySet()
                );
            }

            // 发送SSE结果
            AutoAgentExecuteResultEntity result = AutoAgentExecuteResultEntity.createExecutionResult(
                    null,
                    "已完成所有规划步骤的执行",
                    request.getSessionId()
            );
            sendSseResult(dynamicContext, result);
            
            // 发送总结结果到【最终执行结果】区域
            sendSummaryResult(dynamicContext, request.getSessionId());
            
            dynamicContext.setCompleted(true);
            
            log.info("第四步执行完成：所有规划步骤已执行");

            return "所有规划步骤执行完成";
        } catch (Exception e) {
            log.error("第四步执行失败", e);
            throw new IllegalStateException("执行步骤失败: " + e.getMessage(), e);
        }
    }

    @Override
    public StrategyHandler<ExecuteCommandEntity, DefaultFlowAgentExecuteStrategyFactory.DynamicContext, String> get(ExecuteCommandEntity request, DefaultFlowAgentExecuteStrategyFactory.DynamicContext dynamicContext) {
        return defaultStrategyHandler;
    }
    
    /**
     * 按顺序执行规划步骤
     */
    private void executeStepsInOrder(ChatClient executorChatClient, Map<String, String> stepsMap, DefaultFlowAgentExecuteStrategyFactory.DynamicContext dynamicContext) {
        if (stepsMap == null || stepsMap.isEmpty()) {
            log.warn("步骤映射为空，无法执行");
            return;
        }

        // 按步骤编号排序执行
        List<Integer> stepNumbers = new ArrayList<>();
        for (String stepKey : stepsMap.keySet()) {
            try {
                // 从"第1步"、"第2步"等格式中提取数字
                Pattern numberPattern = Pattern.compile("第(\\d+)步");
                Matcher matcher = numberPattern.matcher(stepKey);
                if (matcher.find()) {
                    stepNumbers.add(Integer.parseInt(matcher.group(1)));
                }
            } catch (NumberFormatException e) {
                log.warn("无法解析步骤编号: {}", stepKey);
            }
        }

        // 排序步骤编号
        stepNumbers.sort(Integer::compareTo);

        // 按顺序执行每个步骤
        for (Integer stepNumber : stepNumbers) {
            String stepKey = "第" + stepNumber + "步";
            String stepContent = null;

            // 查找匹配的步骤内容
            for (Map.Entry<String, String> entry : stepsMap.entrySet()) {
                if (entry.getKey().startsWith(stepKey)) {
                    stepContent = entry.getValue();
                    break;
                }
            }

            if (stepContent != null) {
                executeStep(executorChatClient, stepNumber, stepKey, stepContent, dynamicContext);
            } else {
                log.warn("未找到步骤内容: {}", stepKey);
            }
        }
    }
    
    /**
     * 执行单个步骤
     */
    private void executeStep(ChatClient executorChatClient, Integer stepNumber, String stepKey, String stepContent, DefaultFlowAgentExecuteStrategyFactory.DynamicContext dynamicContext) {
        log.info("\n--- 开始执行 {} ---", stepKey);
        log.info("步骤内容: {}", stepContent.substring(0, Math.min(200, stepContent.length())) + "...");

        try {
            // 更新执行上下文
            dynamicContext.setValue("currentStep", stepNumber);
            dynamicContext.setValue("currentStepKey", stepKey);
            dynamicContext.setValue("currentStepContent", stepContent);

            String executionPrompt = buildStepExecutionPrompt(
                    stepNumber,
                    stepContent,
                    dynamicContext
            );

            ToolExecutionTrace.start();
            String executionResult;
            List<ToolExecutionRecord> toolExecutionRecords;
            try {
                // 使用执行器ChatClient来执行具体步骤
                executionResult = executorChatClient.prompt()
                        .user(executionPrompt)
                        .call()
                        .content();
                toolExecutionRecords = ToolExecutionTrace.snapshot();
            } finally {
                ToolExecutionTrace.clear();
            }

            if (executionResult == null || executionResult.isBlank()) {
                throw new IllegalStateException(
                        "第" + stepNumber + "步执行模型未返回文本"
                );
            }
            log.info("步骤 {} 执行结果: {}", stepNumber, executionResult.substring(0, Math.min(150, executionResult.length())) + "...");

            String verifiedExecutionResult = buildVerifiedExecutionResult(
                    executionResult,
                    toolExecutionRecords
            );

            // 保存执行结果
            dynamicContext.setValue("step" + stepNumber + "Result", verifiedExecutionResult);
            dynamicContext.setValue("step" + stepNumber + "ModelResult", executionResult);
            dynamicContext.setValue("step" + stepNumber + "ToolExecutionRecords", toolExecutionRecords);
            appendExecutionHistory(dynamicContext, stepNumber, stepKey, toolExecutionRecords);
            
            // 发送步骤执行结果的SSE
            String requestSessionId = dynamicContext.getValue("sessionId");
            if (requestSessionId == null || requestSessionId.isBlank()) {
                throw new IllegalStateException("当前执行上下文缺少 sessionId");
            }
            AutoAgentExecuteResultEntity stepResult = AutoAgentExecuteResultEntity.createExecutionResult(
                    stepNumber,
                    "## " + stepKey + " 执行结果\n\n" + verifiedExecutionResult,
                    requestSessionId
            );
            sendSseResult(dynamicContext, stepResult);

            if (!toolExecutionRecords.isEmpty()
                    && toolExecutionRecords.stream().anyMatch(record -> !isSuccessfulToolResult(record))) {
                throw new IllegalStateException("MCP工具未返回成功的真实执行结果");
            }

            // 短暂延迟，避免请求过于频繁
            Thread.sleep(1000);

        } catch (Exception e) {
            handleStepExecutionError(stepNumber, stepKey, e, dynamicContext);
            throw new IllegalStateException("第" + stepNumber + "步执行失败", e);
        }

        log.info("--- 完成执行 {} ---", stepKey);
    }
    
    /**
     * 处理步骤执行错误
     */
    private void handleStepExecutionError(Integer stepNumber, String stepKey, Exception e, DefaultFlowAgentExecuteStrategyFactory.DynamicContext dynamicContext) {
        log.warn("步骤 {} 执行失败，尝试恢复策略", stepNumber);

        // 记录错误统计
        Map<String, Integer> errorStats = dynamicContext.getValue("stepErrorStats");
        if (errorStats == null) {
            errorStats = new HashMap<>();
            dynamicContext.setValue("stepErrorStats", errorStats);
        }
        errorStats.put("step" + stepNumber, errorStats.getOrDefault("step" + stepNumber, 0) + 1);

        // 如果是网络错误，可以尝试重试
        if (e.getMessage() != null && (e.getMessage().contains("timeout") || e.getMessage().contains("connection"))) {
            log.info("检测到网络错误，将在后续重试机制中处理");
        }

        // 标记步骤为部分完成状态
        dynamicContext.setValue("step" + stepNumber + "Status", "FAILED_WITH_ERROR");
        
        // 发送错误结果的SSE
        try {
            AutoAgentExecuteResultEntity errorResult = AutoAgentExecuteResultEntity.createExecutionResult(
                    stepNumber,
                    stepKey + " 执行失败: " + e.getMessage(),
                    dynamicContext.getValue("sessionId")
            );
            sendSseResult(dynamicContext, errorResult);
        } catch (Exception sseException) {
            log.error("发送错误SSE结果失败", sseException);
        }
    }
    
    /**
     * 构建步骤执行提示词
     */
    private String buildStepExecutionPrompt(int currentStep, String stepContent, DefaultFlowAgentExecuteStrategyFactory.DynamicContext dynamicContext) {
        return """
            你是一个智能执行助手，需要执行以下步骤。

            **当前步骤编号：**
            第%d步

            **步骤内容：**
            %s

            **用户原始请求：**
            %s

            **已完成步骤的真实结果：**
            %s

            **执行要求：**
            1. 仔细分析步骤内容，理解需要执行的具体任务。
            2. 如果涉及 MCP 工具调用，请使用当前步骤指定的相应工具。
            3. 只能执行当前步骤，不得提前执行后续步骤。
            4. 后续步骤必须使用“已完成步骤的真实结果”中的数据，不得重新编造文章内容、发布状态或文章 URL。
            5. 提供详细的执行过程和结果，使用 Markdown 格式组织内容。
            6. 如果遇到问题，请说明具体的错误信息；工具失败时不得声称成功。
            7. **重要**：执行完成后，必须在回复末尾明确输出执行结果，格式如下：

               ```
               === 执行结果 ===
               状态: [成功/失败]
               结果描述: [具体的执行结果描述]
               输出数据: [如果有具体的输出数据，请在此列出]
               ```

            请开始执行当前步骤，并严格按照要求提供详细的执行报告和结果输出。
            """.formatted(
                currentStep,
                stepContent,
                dynamicContext.getCurrentTask(),
                getPreviousStepResults(currentStep, dynamicContext)
        );
    }

    private String getPreviousStepResults(
            int currentStep,
            DefaultFlowAgentExecuteStrategyFactory.DynamicContext dynamicContext
    ) {
        StringBuilder results = new StringBuilder();

        for (int step = 1; step < currentStep; step++) {
            String result = dynamicContext.getValue("step" + step + "Result");
            if (result != null && !result.isBlank()) {
                results.append("### 第")
                        .append(step)
                        .append("步真实结果\n")
                        .append(result)
                        .append("\n\n");
            }
        }

        return results.isEmpty() ? "无" : results.toString();
    }
    
    /**
     * 发送总结结果到流式输出
     */
    private void sendSummaryResult(DefaultFlowAgentExecuteStrategyFactory.DynamicContext dynamicContext, String sessionId) {
        // 构建执行总结内容
        StringBuilder summaryContent = new StringBuilder();
        summaryContent.append("## 执行步骤完成总结\n\n");
        
        // 获取执行历史
        StringBuilder executionHistory = dynamicContext.getExecutionHistory();
        if (executionHistory != null && executionHistory.length() > 0) {
            summaryContent.append("### 已完成的工作\n");
            summaryContent.append(executionHistory.toString());
            summaryContent.append("\n\n");
        }
        
        summaryContent.append("### 执行状态\n");
        summaryContent.append("✅ 所有规划步骤已成功执行完成\n\n");
        
        summaryContent.append("### 执行效果评估\n");
        summaryContent.append("📊 任务执行流程顺利完成，各步骤按计划执行");
        
        AutoAgentExecuteResultEntity result = AutoAgentExecuteResultEntity.createSummaryResult(
                summaryContent.toString(), sessionId);
        sendSseResult(dynamicContext, result);
        log.info("📊 已发送总结结果到【最终执行结果】区域");
    }
    
    private void appendExecutionHistory(
            DefaultFlowAgentExecuteStrategyFactory.DynamicContext dynamicContext,
            int stepNumber,
            String stepKey,
            List<ToolExecutionRecord> toolExecutionRecords
    ) {
        if (toolExecutionRecords.isEmpty()) {
            return;
        }

        StringBuilder executionHistory = dynamicContext.getExecutionHistory();
        executionHistory.append("### ").append(stepKey).append("\n");

        for (ToolExecutionRecord record : toolExecutionRecords) {
            boolean succeeded = isSuccessfulToolResult(record);
            executionHistory.append("- ").append(toolDisplayName(record.toolName()))
                    .append("：").append(succeeded ? "成功" : "失败").append("\n");

            if (succeeded) {
                String articleUrl = extractArticleUrl(record.output());
                if (articleUrl != null) {
                    executionHistory.append("- 文章地址：[")
                            .append(articleUrl)
                            .append("](")
                            .append(articleUrl)
                            .append(")\n");
                }
                executionHistory.append("- 工具真实返回：`")
                        .append(escapeInlineCode(toSingleLine(record.output())))
                        .append("`\n");
            } else {
                String errorMessage = record.errorMessage() == null ? record.output() : record.errorMessage();
                executionHistory.append("- 错误信息：").append(errorMessage).append("\n");
            }
            executionHistory.append('\n');
        }
    }

    static void validateStepsBeforeExecution(Map<String, String> stepsMap, int maxPlanningSteps) {
        if (stepsMap == null || stepsMap.isEmpty()) {
            throw new IllegalStateException("步骤映射为空，无法执行");
        }
        if (stepsMap.size() > maxPlanningSteps) {
            throw new IllegalStateException(
                    "规划包含 " + stepsMap.size() + " 个业务步骤，超过最大规划步骤数 "
                            + maxPlanningSteps + "，已在执行任何业务步骤前终止"
            );
        }
    }

    private String extractArticleUrl(String toolOutput) {
        if (toolOutput == null) {
            return null;
        }

        Matcher matcher = Pattern.compile("https?://[^\\s\\\"'<>\\\\]+")
                .matcher(toolOutput);
        while (matcher.find()) {
            String candidate = matcher.group();
            try {
                URI uri = URI.create(candidate);
                String host = uri.getHost();
                String path = uri.getPath();
                if (host != null
                        && (host.equals("blog.csdn.net") || host.endsWith(".blog.csdn.net"))
                        && path != null
                        && path.contains("/article/details/")) {
                    return candidate;
                }
            } catch (IllegalArgumentException ignored) {
                // Continue checking other URLs returned by the tool.
            }
        }
        return null;
    }

    private boolean isSuccessfulToolResult(ToolExecutionRecord record) {
        if (!record.succeeded() || record.output() == null || record.output().isBlank()) {
            return false;
        }

        try {
            JSONObject result = JSON.parseObject(record.output());
            if (result.containsKey("success") && !result.getBooleanValue("success")) {
                return false;
            }
        } catch (Exception ignored) {
            String normalizedOutput = record.output().toLowerCase(Locale.ROOT);
            if (normalizedOutput.contains("失败")
                    || normalizedOutput.contains("error")
                    || normalizedOutput.contains("failed")) {
                return false;
            }
        }

        return !isCsdnPublishTool(record.toolName()) || extractArticleUrl(record.output()) != null;
    }

    private String toolDisplayName(String toolName) {
        if (isCsdnPublishTool(toolName)) {
            return "CSDN 发帖";
        }
        if (isWeixinNoticeTool(toolName)) {
            return "微信公众号消息通知";
        }
        return "工具 `" + toolName + "` 调用";
    }

    private boolean isCsdnPublishTool(String toolName) {
        return toolName.contains("JavaSDKMCPClient_saveArticle");
    }

    private boolean isWeixinNoticeTool(String toolName) {
        return toolName.contains("JavaSDKMCPClient_weixinNotice");
    }

    private String buildVerifiedExecutionResult(
            String modelResult,
            List<ToolExecutionRecord> toolExecutionRecords
    ) {
        if (toolExecutionRecords.isEmpty()) {
            return modelResult;
        }

        boolean allSucceeded = toolExecutionRecords.stream().allMatch(this::isSuccessfulToolResult);
        StringBuilder result = new StringBuilder();
        result.append("=== 执行结果 ===\n");
        result.append("状态: ").append(allSucceeded ? "成功" : "失败").append("\n");

        for (ToolExecutionRecord record : toolExecutionRecords) {
            boolean succeeded = isSuccessfulToolResult(record);
            result.append("\n### ").append(toolDisplayName(record.toolName())).append("\n");
            result.append("- 状态：").append(succeeded ? "成功" : "失败").append("\n");

            String articleUrl = isCsdnPublishTool(record.toolName())
                    ? extractArticleUrl(record.output())
                    : null;
            if (articleUrl != null) {
                result.append("- 文章地址：[").append(articleUrl).append("](")
                        .append(articleUrl).append(")\n");
            } else if (isCsdnPublishTool(record.toolName())) {
                result.append("- 文章地址：工具未返回有效的 CSDN 帖子地址\n");
            }

            String toolOutput = record.errorMessage() == null ? record.output() : record.errorMessage();
            result.append("- 工具真实返回：`")
                    .append(escapeInlineCode(toSingleLine(toolOutput)))
                    .append("`\n");
        }

        return result.toString();
    }

    private String toSingleLine(String value) {
        if (value == null || value.isBlank()) {
            return "(空返回)";
        }
        return value.replaceAll("[\\r\\n]+", " ");
    }

    private String escapeInlineCode(String value) {
        return value.replace("`", "\\`");
    }
}
