package cn.moonice.domain.agent.service.execute;

import cn.moonice.domain.agent.model.entity.ExecuteCommandEntity;

/**
 * 执行策略接口
 *
 * @author xiaofuge bugstack.cn @小傅哥
 * 2025/8/5 09:48
 */
public interface IExecuteStrategy {

    void execute(ExecuteCommandEntity requestParameter) throws Exception;

}
