package cn.bugstack.ai.domain.agent.service.armory.node;

import cn.bugstack.ai.domain.agent.model.entity.ArmoryCommandEntity;
import cn.bugstack.ai.domain.agent.model.valobj.enums.AiAgentEnumVO;
import cn.bugstack.ai.domain.agent.model.valobj.AiClientSystemPromptVO;
import cn.bugstack.ai.domain.agent.model.valobj.AiClientVO;
import cn.bugstack.ai.domain.agent.service.armory.node.factory.DefaultArmoryStrategyFactory;
import cn.bugstack.ai.domain.agent.service.tool.ToolExecutionTrace;
import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import io.modelcontextprotocol.client.McpSyncClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.stereotype.Service;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ai agent 客户端对话对象节点
 *
 * @author xiaofuge bugstack.cn @小傅哥
 * 2025/7/19 09:17
 */
@Slf4j
@Service
public class AiClientNode extends AbstractArmorySupport {

    private static final Pattern GRAFANA_RELATIVE_TIME_PATTERN = Pattern.compile(
            "^now(?:-(\\d+)([smhdw]))?$",
            Pattern.CASE_INSENSITIVE
    );

    private static final List<String> GRAFANA_TIME_FIELDS = List.of(
            "start", "end",
            "startRfc3339", "endRfc3339",
            "startTime", "endTime"
    );

    @Override
    protected String doApply(ArmoryCommandEntity requestParameter, DefaultArmoryStrategyFactory.DynamicContext dynamicContext) throws Exception {
        log.info("Ai Agent 构建节点，客户端{}", JSON.toJSONString(requestParameter));

        List<AiClientVO> aiClientList = dynamicContext.getValue(dataName());

        if (null == aiClientList || aiClientList.isEmpty()) {
            return router(requestParameter, dynamicContext);
        }

        Map<String, AiClientSystemPromptVO> systemPromptMap = dynamicContext.getValue(AiAgentEnumVO.AI_CLIENT_SYSTEM_PROMPT.getDataName());

        for (AiClientVO aiClientVO : aiClientList) {
            // 1. 预设话术
            StringBuilder defaultSystem = new StringBuilder("Ai 智能体 \r\n");
            List<String> promptIdList = aiClientVO.getPromptIdList();
            for (String promptId : promptIdList) {
                AiClientSystemPromptVO aiClientSystemPromptVO = systemPromptMap.get(promptId);
                defaultSystem.append(aiClientSystemPromptVO.getPromptContent());
            }

            // 2. 对话模型
            OpenAiChatModel chatModel = getBean(aiClientVO.getModelBeanName());

            // 3. MCP 服务
            List<McpSyncClient> mcpSyncClients = new ArrayList<>();
            List<String> mcpBeanNameList = aiClientVO.getMcpBeanNameList();
            for (String mcpBeanName : mcpBeanNameList) {
                mcpSyncClients.add(getBean(mcpBeanName));
            }

            // 4. advisor 顾问角色
            List<Advisor> advisors = new ArrayList<>();
            List<String> advisorBeanNameList = aiClientVO.getAdvisorBeanNameList();
            for (String advisorBeanName : advisorBeanNameList) {
                advisors.add(getBean(advisorBeanName));
            }

            Advisor[] advisorArray = advisors.toArray(new Advisor[]{});
            ToolCallback[] toolCallbacks = Arrays.stream(new SyncMcpToolCallbackProvider(
                            mcpSyncClients.toArray(new McpSyncClient[]{}))
                    .getToolCallbacks())
                    .map(this::traceToolCallback)
                    .toArray(ToolCallback[]::new);

            // 5. 构建对话客户端
            ChatClient chatClient = ChatClient.builder(chatModel)
                    .defaultSystem(defaultSystem.toString())
                    .defaultToolCallbacks(toolCallbacks)
                    .defaultAdvisors(advisorArray)
                    .build();

            registerBean(beanName(aiClientVO.getClientId()), ChatClient.class, chatClient);
        }

        return router(requestParameter, dynamicContext);
    }

    private ToolCallback traceToolCallback(ToolCallback delegate) {
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return delegate.getToolDefinition();
            }

            @Override
            public ToolMetadata getToolMetadata() {
                return delegate.getToolMetadata();
            }

            @Override
            public String call(String toolInput) {
                String normalizedInput = normalizeToolInput(delegate, toolInput);
                return invokeTool(delegate, () -> delegate.call(normalizedInput));
            }

            @Override
            public String call(String toolInput, ToolContext toolContext) {
                String normalizedInput = normalizeToolInput(delegate, toolInput);
                return invokeTool(delegate, () -> delegate.call(normalizedInput, toolContext));
            }
        };
    }

    private String normalizeToolInput(ToolCallback delegate, String toolInput) {
        String toolName = delegate.getToolDefinition().name();
        if (toolName.contains("JavaSDKMCPClient_saveArticle")) {
            return normalizeCsdnArticleInput(toolInput);
        }

        if (toolName.toLowerCase(Locale.ROOT).contains("prometheus")) {
            return normalizeGrafanaTimeInput(toolName, toolInput);
        }

        return toolInput;
    }

    private String normalizeCsdnArticleInput(String toolInput) {
        try {
            JSONObject toolArguments = JSON.parseObject(toolInput);
            JSONObject request = toolArguments.getJSONObject("request");
            if (request == null) {
                return toolInput;
            }

            String content = request.getString("content");
            String normalizedContent = unwrapOuterMarkdownFence(content);
            if (!normalizedContent.equals(content)) {
                request.put("content", normalizedContent);
                log.info("已移除 CSDN 文章正文的外层 Markdown 代码围栏");
            }

            return JSON.toJSONString(toolArguments);
        } catch (Exception e) {
            log.warn("CSDN 发帖参数规范化失败，将使用原始参数", e);
            return toolInput;
        }
    }

    private String normalizeGrafanaTimeInput(String toolName, String toolInput) {
        try {
            JSONObject toolArguments = JSON.parseObject(toolInput);
            Instant now = Instant.now();
            boolean timeChanged = false;
            for (String fieldName : GRAFANA_TIME_FIELDS) {
                timeChanged |= normalizeGrafanaTimeField(toolArguments, fieldName, now);
            }

            if (timeChanged) {
                log.info("已将 Grafana MCP 相对时间转换为 RFC3339: toolName={}", toolName);
                return JSON.toJSONString(toolArguments);
            }
            return toolInput;
        } catch (Exception e) {
            log.warn("Grafana MCP 时间参数规范化失败，将使用原始参数: toolName={}", toolName, e);
            return toolInput;
        }
    }

    private boolean normalizeGrafanaTimeField(JSONObject toolArguments, String fieldName, Instant now) {
        String value = toolArguments.getString(fieldName);
        if (value == null || value.isBlank()) {
            return false;
        }

        Matcher matcher = GRAFANA_RELATIVE_TIME_PATTERN.matcher(value.trim());
        if (!matcher.matches()) {
            return false;
        }

        Instant normalizedTime = now;
        if (matcher.group(1) != null) {
            long amount = Long.parseLong(matcher.group(1));
            Duration duration = switch (matcher.group(2).toLowerCase(Locale.ROOT)) {
                case "s" -> Duration.ofSeconds(amount);
                case "m" -> Duration.ofMinutes(amount);
                case "h" -> Duration.ofHours(amount);
                case "d" -> Duration.ofDays(amount);
                case "w" -> Duration.ofDays(Math.multiplyExact(amount, 7));
                default -> throw new IllegalArgumentException("Unsupported Grafana time unit");
            };
            normalizedTime = now.minus(duration);
        }

        toolArguments.put(fieldName, normalizedTime.truncatedTo(ChronoUnit.SECONDS).toString());
        return true;
    }

    private String unwrapOuterMarkdownFence(String content) {
        if (content == null || content.isBlank()) {
            return content;
        }

        Pattern outerFencePattern = Pattern.compile(
                "^\\s*```(?:markdown|md)?\\s*\\R([\\s\\S]*?)\\R?```\\s*$",
                Pattern.CASE_INSENSITIVE
        );
        Matcher matcher = outerFencePattern.matcher(content);
        return matcher.matches() ? matcher.group(1).trim() : content;
    }

    private String invokeTool(ToolCallback delegate, Supplier<String> invocation) {
        String toolName = delegate.getToolDefinition().name();
        long startedAt = System.nanoTime();
        try {
            String output = invocation.get();
            long durationMillis = (System.nanoTime() - startedAt) / 1_000_000;
            ToolExecutionTrace.recordSuccess(toolName, output, durationMillis);
            log.info("MCP工具调用完成: toolName={}, duration={}ms", toolName, durationMillis);
            return output;
        } catch (RuntimeException e) {
            long durationMillis = (System.nanoTime() - startedAt) / 1_000_000;
            ToolExecutionTrace.recordFailure(toolName, e.getMessage(), durationMillis);
            log.error("MCP工具调用失败: toolName={}, duration={}ms", toolName, durationMillis, e);
            throw e;
        }
    }

    @Override
    public StrategyHandler<ArmoryCommandEntity, DefaultArmoryStrategyFactory.DynamicContext, String> get(ArmoryCommandEntity requestParameter, DefaultArmoryStrategyFactory.DynamicContext dynamicContext) throws Exception {
        return defaultStrategyHandler;
    }

    @Override
    protected String beanName(String id) {
        return AiAgentEnumVO.AI_CLIENT.getBeanName(id);
    }

    @Override
    protected String dataName() {
        return AiAgentEnumVO.AI_CLIENT.getDataName();
    }

}
