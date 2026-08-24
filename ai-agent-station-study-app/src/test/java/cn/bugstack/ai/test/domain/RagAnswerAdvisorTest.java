package cn.bugstack.ai.test.domain;

import cn.bugstack.ai.domain.agent.service.armory.node.factory.element.RagAnswerAdvisor;
import org.junit.Test;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.model.tool.DefaultToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RagAnswerAdvisorTest {

    @Test
    public void shouldPreservePromptMessagesAndMcpToolOptions() {
        ToolCallback toolCallback = mock(ToolCallback.class);
        when(toolCallback.getToolDefinition()).thenReturn(ToolDefinition.builder()
                .name("query_prometheus")
                .description("执行 PromQL 查询")
                .inputSchema("{\"type\":\"object\"}")
                .build());

        ToolCallingChatOptions chatOptions = DefaultToolCallingChatOptions.builder()
                .toolCallbacks(toolCallback)
                .build();
        Prompt prompt = Prompt.builder()
                .messages(
                        new SystemMessage("必须调用 MCP 工具获取实时数据"),
                        new UserMessage("查询 big-market-app 的接口 QPS")
                )
                .chatOptions(chatOptions)
                .build();
        ChatClientRequest request = ChatClientRequest.builder()
                .prompt(prompt)
                .context(Map.of("traceId", "test-trace"))
                .build();

        VectorStore vectorStore = mock(VectorStore.class);
        when(vectorStore.similaritySearch(org.mockito.ArgumentMatchers.any(SearchRequest.class)))
                .thenReturn(List.of(new Document("Grafana MCP 工具使用说明")));
        RagAnswerAdvisor advisor = new RagAnswerAdvisor(vectorStore, SearchRequest.builder().topK(4).build());

        ChatClientRequest advisedRequest = advisor.before(request, mock(AdvisorChain.class));

        assertEquals("必须调用 MCP 工具获取实时数据", advisedRequest.prompt().getSystemMessage().getText());
        assertTrue(advisedRequest.prompt().getUserMessage().getText().contains("查询 big-market-app 的接口 QPS"));
        assertTrue(advisedRequest.prompt().getUserMessage().getText().contains("Grafana MCP 工具使用说明"));
        assertTrue(advisedRequest.prompt().getUserMessage().getText().contains("必须优先调用当前请求提供的 MCP 工具"));
        assertEquals("test-trace", advisedRequest.context().get("traceId"));
        assertNotNull(advisedRequest.context().get("qa_retrieved_documents"));

        assertTrue(advisedRequest.prompt().getOptions() instanceof ToolCallingChatOptions);
        ToolCallingChatOptions advisedOptions = (ToolCallingChatOptions) advisedRequest.prompt().getOptions();
        assertEquals(1, advisedOptions.getToolCallbacks().size());
        assertEquals("query_prometheus", advisedOptions.getToolCallbacks().get(0).getToolDefinition().name());
    }
}
