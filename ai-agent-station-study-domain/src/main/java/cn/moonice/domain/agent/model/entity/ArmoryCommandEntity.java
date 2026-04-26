package cn.moonice.domain.agent.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 装配命令
 *
 * @author xiaofuge bugstack.cn @小傅哥
 * 2025/6/27 07:26
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ArmoryCommandEntity {

    /**
     * 命令类型 AiAgentEnumVO.getCode
     */
    private String commandType;

    /**
     * 命令索引（clientId、modelId、apiId...）
     */
    private List<String> commandIdList;

}
