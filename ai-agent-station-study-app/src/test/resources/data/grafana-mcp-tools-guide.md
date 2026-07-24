# Grafana MCP 工具使用指南

## 概述
本文档详细介绍了如何使用Grafana MCP工具进行自动化监控分析，包括工具调用方法、参数配置和实际应用场景。

## 可用的MCP工具

### 1. grafana/list_datasources
列出所有可用的数据源

#### 功能描述
- 获取Grafana中配置的所有数据源
- 返回数据源的基本信息（ID、名称、类型、URL等）
- 用于确认Prometheus数据源的可用性

#### 使用场景
- 系统分析前的数据源检查
- 确认监控数据的来源
- 多数据源环境的管理

#### 返回信息
```json
{
  "id": 1,
  "name": "Prometheus",
  "type": "prometheus",
  "url": "http://prometheus:9090",
  "access": "proxy",
  "isDefault": true
}
```

### 2. grafana/query_prometheus
执行Prometheus查询

#### 功能描述
- 执行PromQL查询语句
- 支持时间范围查询
- 返回结构化的监控数据

#### 参数说明
- `query`: PromQL查询语句
- `start`: 查询开始时间（可选）
- `end`: 查询结束时间（可选）
- `step`: 查询步长（可选）

#### 常用查询示例

##### CPU使用率查询
```promql
# 整体CPU使用率
100 - (avg(irate(node_cpu_seconds_total{mode="idle"}[5m])) * 100)

# 按实例分组的CPU使用率
100 - (avg by (instance)(irate(node_cpu_seconds_total{mode="idle"}[5m])) * 100)
```

##### 内存使用率查询
```promql
# 内存使用率
(1 - (node_memory_MemAvailable_bytes / node_memory_MemTotal_bytes)) * 100

# 内存使用量（GB）
(node_memory_MemTotal_bytes - node_memory_MemAvailable_bytes) / 1024 / 1024 / 1024
```

##### 磁盘使用率查询
```promql
# 磁盘使用率
(1 - (node_filesystem_avail_bytes{fstype!="tmpfs"} / node_filesystem_size_bytes{fstype!="tmpfs"})) * 100

# 磁盘可用空间（GB）
node_filesystem_avail_bytes{fstype!="tmpfs"} / 1024 / 1024 / 1024
```

##### 网络流量查询
```promql
# 网络接收速率（bps）
irate(node_network_receive_bytes_total{device!="lo"}[5m]) * 8

# 网络发送速率（bps）
irate(node_network_transmit_bytes_total{device!="lo"}[5m]) * 8
```

## 自动化分析流程

### 标准监控分析流程

1. **数据源检查**
   ```
   grafana/list_datasources
   ```
   - 确认Prometheus数据源可用
   - 获取数据源配置信息

2. **CPU分析**
   ```
   grafana/query_prometheus
   query: 100 - (avg(irate(node_cpu_seconds_total{mode="idle"}[1h])) * 100)
   ```
   - 查询过去1小时的CPU使用率
   - 分析CPU负载趋势

3. **内存分析**
   ```
   grafana/query_prometheus
   query: (1 - (node_memory_MemAvailable_bytes / node_memory_MemTotal_bytes)) * 100
   ```
   - 查询当前内存使用率
   - 评估内存压力状况

4. **磁盘分析**
   ```
   grafana/query_prometheus
   query: (1 - (node_filesystem_avail_bytes{fstype!="tmpfs"} / node_filesystem_size_bytes{fstype!="tmpfs"})) * 100
   ```
   - 查询磁盘使用情况
   - 识别存储空间风险

5. **网络分析**
   ```
   grafana/query_prometheus
   query: irate(node_network_receive_bytes_total{device!="lo"}[1h]) * 8
   ```
   - 查询网络流量状况
   - 分析网络性能指标

### 深度分析流程

#### 性能瓶颈分析
1. **识别高负载时段**
   ```promql
   # 查询CPU峰值时间
   max_over_time((100 - (avg(irate(node_cpu_seconds_total{mode="idle"}[5m])) * 100))[24h:1h])
   ```

2. **分析资源关联性**
   ```promql
   # CPU和内存关联分析
   (
     (100 - (avg(irate(node_cpu_seconds_total{mode="idle"}[5m])) * 100)) +
     ((1 - (node_memory_MemAvailable_bytes / node_memory_MemTotal_bytes)) * 100)
   ) / 2
   ```

3. **趋势预测**
   ```promql
   # 磁盘使用增长趋势
   predict_linear(node_filesystem_avail_bytes{fstype!="tmpfs"}[24h], 7*24*3600)
   ```

## 数据解释和分析标准

### CPU数据解释
- **数值范围**: 0-100%
- **正常范围**: 0-70%
- **警告范围**: 70-90%
- **危险范围**: 90-100%

### 内存数据解释
- **数值范围**: 0-100%
- **正常范围**: 0-80%
- **警告范围**: 80-95%
- **危险范围**: 95-100%

### 磁盘数据解释
- **数值范围**: 0-100%
- **正常范围**: 0-85%
- **警告范围**: 85-95%
- **危险范围**: 95-100%

### 网络数据解释
- **单位**: bps (bits per second)
- **正常范围**: 根据网络带宽确定
- **异常指标**: 突然的流量峰值或持续的高流量

## 报告生成模板

### 系统健康报告模板
```markdown
## 系统运行状态分析报告

### 📊 监控数据概览
- **监控时间范围**: {time_range}
- **数据源**: {datasource_info}
- **分析时间**: {analysis_time}

### 🖥️ CPU 使用情况
- **平均使用率**: {cpu_avg}%
- **峰值使用率**: {cpu_max}%
- **状态评估**: {cpu_status}
- **趋势分析**: {cpu_trend}

### 💾 内存使用情况
- **当前使用率**: {memory_usage}%
- **可用内存**: {memory_available}GB
- **状态评估**: {memory_status}
- **使用趋势**: {memory_trend}

### 💿 磁盘使用情况
- **使用率**: {disk_usage}%
- **可用空间**: {disk_available}GB
- **状态评估**: {disk_status}
- **空间预警**: {disk_warning}

### 🌐 网络流量情况
- **接收速率**: {network_rx} bps
- **发送速率**: {network_tx} bps
- **状态评估**: {network_status}
- **流量模式**: {network_pattern}

### 📈 综合评估
- **系统健康度**: {overall_health}
- **关键发现**: {key_findings}
- **优化建议**: {recommendations}
- **告警建议**: {alert_suggestions}
```

## 错误处理和故障排查

### 常见错误

1. **数据源连接失败**
   - 检查Prometheus服务状态
   - 验证网络连接
   - 确认认证配置

2. **查询语法错误**
   - 验证PromQL语法
   - 检查指标名称拼写
   - 确认标签选择器

3. **数据缺失**
   - 检查时间范围设置
   - 验证指标采集状态
   - 确认数据保留策略

### 故障排查步骤

1. **验证数据源**
   ```
   grafana/list_datasources
   ```

2. **测试基础查询**
   ```
   grafana/query_prometheus
   query: up
   ```

3. **检查指标可用性**
   ```
   grafana/query_prometheus
   query: {__name__=~"node_.*"}
   ```

4. **验证时间范围**
   ```
   grafana/query_prometheus
   query: node_cpu_seconds_total
   ```
   实时查询省略 `start` 和 `end`，以 Prometheus 查询时刻为准。只有查询历史趋势时才传入 RFC3339 格式的绝对时间。

## 最佳实践

1. **查询优化**
   - 使用适当的时间范围
   - 避免过于复杂的查询
   - 合理使用聚合函数

2. **数据解释**
   - 结合业务场景分析
   - 考虑历史基线对比
   - 关注趋势而非瞬时值

3. **报告生成**
   - 提供清晰的状态评估
   - 包含具体的数值信息
   - 给出可操作的建议

4. **自动化流程**
   - 标准化查询流程
   - 建立分析模板
   - 实现智能告警判断
