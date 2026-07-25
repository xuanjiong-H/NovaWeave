package cn.bugstack.ai.domain.agent.service.tool;

import cn.bugstack.ai.domain.agent.adapter.repository.IAgentRepository;
import cn.bugstack.ai.domain.agent.model.valobj.AiClientVO;
import io.modelcontextprotocol.client.McpSyncClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads the executor's live MCP tool definitions without granting those tools
 * to the planning client.
 */
@Slf4j
@Service
public class McpToolCatalogService {

    private final IAgentRepository repository;
    private final ApplicationContext applicationContext;

    public McpToolCatalogService(IAgentRepository repository, ApplicationContext applicationContext) {
        this.repository = repository;
        this.applicationContext = applicationContext;
    }

    public String describeTools(String executorClientId) {
        try {
            List<AiClientVO> clientList = repository.AiClientVOByClientIds(List.of(executorClientId));
            if (clientList == null || clientList.isEmpty()) {
                return "未找到执行器 Client 配置，clientId=" + executorClientId + "。\n";
            }

            List<String> mcpBeanNames = clientList.get(0).getMcpBeanNameList();
            if (mcpBeanNames == null || mcpBeanNames.isEmpty()) {
                return "执行器未直接绑定 MCP 工具，clientId=" + executorClientId + "。\n";
            }

            List<McpSyncClient> mcpClients = new ArrayList<>();
            for (String mcpBeanName : mcpBeanNames) {
                mcpClients.add(applicationContext.getBean(mcpBeanName, McpSyncClient.class));
            }

            ToolCallback[] callbacks = new SyncMcpToolCallbackProvider(
                    mcpClients.toArray(new McpSyncClient[0])
            ).getToolCallbacks();
            if (callbacks.length == 0) {
                return "执行器绑定的 MCP 服务没有返回可用工具，clientId=" + executorClientId + "。\n";
            }

            List<ToolDescriptor> descriptors = new ArrayList<>();
            for (ToolCallback callback : callbacks) {
                ToolDefinition definition = callback.getToolDefinition();
                descriptors.add(new ToolDescriptor(
                        definition.name(),
                        definition.description(),
                        definition.inputSchema()
                ));
            }
            return formatDescriptors(descriptors);
        } catch (Exception e) {
            log.error("获取执行器 MCP 工具清单失败，clientId={}", executorClientId, e);
            return "获取执行器 MCP 工具清单失败，clientId=" + executorClientId
                    + "，原因：" + e.getMessage() + "。\n";
        }
    }

    public static String formatDescriptors(List<ToolDescriptor> descriptors) {
        StringBuilder toolsInfo = new StringBuilder();
        for (ToolDescriptor descriptor : descriptors) {
            toolsInfo.append("#### `").append(descriptor.name()).append("`\n");
            toolsInfo.append("- 描述：")
                    .append(isBlank(descriptor.description()) ? "未提供" : descriptor.description())
                    .append("\n");
            toolsInfo.append("- 输入参数 Schema：\n```json\n")
                    .append(isBlank(descriptor.inputSchema()) ? "{}" : descriptor.inputSchema())
                    .append("\n```\n\n");
        }
        return toolsInfo.toString();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public record ToolDescriptor(String name, String description, String inputSchema) {
    }
}
