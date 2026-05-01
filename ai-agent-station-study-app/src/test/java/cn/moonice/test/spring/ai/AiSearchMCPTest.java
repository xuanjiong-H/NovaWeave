package cn.moonice.test.spring.ai;

import com.alibaba.fastjson.JSON;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.time.Duration;

/**
 * mcp；https://sai.baidu.com/zh/
 * 百度搜索；<a href="https://sai.baidu.com/zh/detail/e014c6ffd555697deabf00d058baf388">https://sai.baidu.com/zh/detail/e014c6ffd555697deabf00d058baf388</a>
 * key申请；<a href="https://console.bce.baidu.com/iam/?_=1753597622044#/iam/apikey/list">apikey</a>
 * @author xiaofuge bugstack.cn @小傅哥
 * 2025/7/27 14:29
 */
@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
public class AiSearchMCPTest {

    @Test
    public void test() {
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(OpenAiApi.builder()
                        .baseUrl("https://api.zhizengzeng.com/v1")
                        .apiKey("sk-zk261fae862117df7e636b7e77fddabaaae69df9af8c7ad7")
                        .completionsPath("/v1/chat/completions")
                        .embeddingsPath("/v1/embeddings")
                        .build())
                .defaultOptions(OpenAiChatOptions.builder()
                        .model("gpt-4o-mini")
                        .toolCallbacks(new SyncMcpToolCallbackProvider(sseMcpClient()).getToolCallbacks())
                        .build())
                .build();

        ChatResponse call = chatModel.call(Prompt.builder().messages(new UserMessage("搜索moonice月冰技术博客有哪些项目")).build());
        log.info("测试结果:{}", JSON.toJSONString(call.getResult()));
    }

    public McpSyncClient sseMcpClient() {
        HttpClientSseClientTransport sseClientTransport = HttpClientSseClientTransport.builder("http://appbuilder.baidu.com/v2/ai_search/mcp/")
                .sseEndpoint("sse?api_key=bce-v3/ALTAK-eCtvgdAVsupHx1EKg6L24/f2e04330953ab5158557169a15356448f9f3e053")
                .build();

        McpSyncClient mcpSyncClient = McpClient.sync(sseClientTransport).requestTimeout(Duration.ofMinutes(360)).build();
        var init_sse = mcpSyncClient.initialize();
        log.info("Tool SSE MCP Initialized {}", init_sse);

        return mcpSyncClient;
    }

}
