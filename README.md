# NovaWeave

NovaWeave 是一个基于 Java 和 Spring AI 构建的可配置化 AI Agent 智能体平台。

项目通过统一的 Agent 装配流程，将大语言模型、系统提示词、Advisor、MCP 工具和 RAG 能力组合起来，并支持多种 Agent 执行策略。开发者可以通过数据库配置和管理接口创建、调整和装配智能体，而不需要频繁修改核心业务代码。

## 核心能力

- **多种 Agent 执行策略**
  - `Fixed Agent`：按照固定流程执行任务。
  - `Flow Agent`：根据任务规划结果生成并执行多步骤流程。
  - `Auto Agent`：通过分析、执行、质量监督和结果汇总完成自主任务处理。
- **MCP 工具集成**
  - 支持 SSE 和 Stdio 两种 MCP 传输方式。
  - 支持动态加载外部工具，例如搜索、文章发布、消息通知、Elasticsearch 和 Grafana 等。
- **配置驱动的 Agent 装配**
  - 支持配置 Agent、模型、API、系统提示词、Advisor、MCP 工具和 RAG 资源。
  - 支持运行时重新装配 Agent 和模型 API。
- **RAG 检索增强**
  - 支持文档解析和知识内容导入。
  - 使用 PostgreSQL + pgvector 保存向量数据。
- **流式响应**
  - 通过 SSE 返回 Agent 执行过程和结果，适合接入 Web 前端。
- **管理与扩展能力**
  - 提供 Agent、模型、API、MCP、提示词、Advisor 和 RAG 配置管理接口。
  - 基于领域驱动设计和多模块 Maven 结构组织代码。

## 技术栈

- Java 17+
- Spring Boot 3.4
- Spring AI 1.0
- Spring Web MVC
- MyBatis
- MySQL
- PostgreSQL + pgvector
- MCP Client
- Elasticsearch MCP
- Grafana MCP
- Maven

## 项目结构

```text
NovaWeave/
├── ai-agent-station-study-api/              # API 接口、DTO 和响应对象
├── ai-agent-station-study-app/              # Spring Boot 启动模块和应用配置
├── ai-agent-station-study-domain/           # Agent 核心领域逻辑和执行策略
├── ai-agent-station-study-infrastructure/   # 数据访问和基础设施实现
├── ai-agent-station-study-trigger/          # HTTP 接口、管理接口和任务触发器
├── ai-agent-station-study-types/            # 通用类型、枚举和基础组件
├── config/                                  # 本地环境变量配置
└── docs/                                    # 数据库脚本和部署相关文件
```

## Agent 执行流程

```text
用户请求
   |
   v
Agent Dispatch
   |
   +--> Fixed Agent
   +--> Flow Agent --> 规划 --> 解析 --> 执行
   +--> Auto Agent  --> 分析 --> 执行 --> 质量检查 --> 汇总
   |
   v
模型调用与 MCP 工具调用
   |
   v
SSE 流式返回结果
```

## 环境要求

- JDK 17 或更高版本
- Maven 3.9+
- MySQL
- PostgreSQL 及 pgvector 扩展
- 可用的大语言模型 API
- 可选的 MCP 服务

## 设计特点

- 使用多模块 Maven 结构隔离 API、领域、基础设施和触发层。
- 使用领域服务和策略模式组织不同类型的 Agent 执行逻辑。
- 使用配置驱动方式降低新增模型和工具的接入成本。
- 使用 MCP 统一扩展 Agent 的外部工具调用能力。
- 使用 SSE 支持长任务和多步骤执行过程的实时反馈。

## License

本项目基于 [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0) 开源。
