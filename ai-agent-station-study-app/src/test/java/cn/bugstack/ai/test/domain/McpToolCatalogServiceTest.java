package cn.bugstack.ai.test.domain;

import cn.bugstack.ai.domain.agent.service.tool.McpToolCatalogService;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertTrue;

public class McpToolCatalogServiceTest {

    @Test
    public void shouldFormatLiveToolMetadataForAnalyzer() {
        String catalog = McpToolCatalogService.formatDescriptors(List.of(
                new McpToolCatalogService.ToolDescriptor(
                        "query_prometheus",
                        "执行 PromQL 查询",
                        "{\"type\":\"object\"}"
                ),
                new McpToolCatalogService.ToolDescriptor(
                        "list_datasources",
                        null,
                        null
                )
        ));

        assertTrue(catalog.contains("`query_prometheus`"));
        assertTrue(catalog.contains("执行 PromQL 查询"));
        assertTrue(catalog.contains("{\"type\":\"object\"}"));
        assertTrue(catalog.contains("`list_datasources`"));
        assertTrue(catalog.contains("未提供"));
        assertTrue(catalog.contains("{}"));
    }
}
