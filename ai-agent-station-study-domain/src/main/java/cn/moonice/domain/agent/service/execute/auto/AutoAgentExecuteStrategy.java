package cn.moonice.domain.agent.service.execute.auto;

import cn.moonice.domain.agent.model.entity.ExecuteCommandEntity;
import cn.moonice.domain.agent.service.execute.IExecuteStrategy;
import cn.moonice.domain.agent.service.execute.auto.step.factory.DefaultAutoAgentExecuteStrategyFactory;
import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 自动执行策略
 * @author xiaofuge bugstack.cn @小傅哥
 * 2025/8/5 09:49
 */
@Slf4j
@Service
public class AutoAgentExecuteStrategy implements IExecuteStrategy {

    @Resource
    private DefaultAutoAgentExecuteStrategyFactory defaultAutoAgentExecuteStrategyFactory;

    @Override
    public void execute(ExecuteCommandEntity executeCommandEntity) throws Exception {
        StrategyHandler<ExecuteCommandEntity, DefaultAutoAgentExecuteStrategyFactory.DynamicContext, String> executeHandler
                = defaultAutoAgentExecuteStrategyFactory.armoryStrategyHandler();
        String apply = executeHandler.apply(executeCommandEntity, new DefaultAutoAgentExecuteStrategyFactory.DynamicContext());
        log.info("测试结果:{}", apply);
    }

}
