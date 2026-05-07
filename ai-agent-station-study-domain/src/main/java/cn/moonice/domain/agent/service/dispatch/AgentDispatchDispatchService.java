package cn.moonice.domain.agent.service.dispatch;

import cn.moonice.domain.agent.adapter.repository.IAgentRepository;
import cn.moonice.domain.agent.model.entity.ExecuteCommandEntity;
import cn.moonice.domain.agent.model.valobj.AiAgentVO;
import cn.moonice.domain.agent.service.IAgentDispatchService;
import cn.moonice.domain.agent.service.execute.IExecuteStrategy;
import cn.moonice.types.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import javax.annotation.Resource;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Agent 服务接口
 *
 * @author xiaofuge bugstack.cn @小傅哥
 * 2025/9/6 06:55
 */
@Slf4j
@Service
public class AgentDispatchDispatchService implements IAgentDispatchService {

    @Resource
    private Map<String, IExecuteStrategy> executeStrategyMap;

    @Resource
    private IAgentRepository repository;

    @Resource
    private ThreadPoolExecutor threadPoolExecutor;

    @Override
    public void dispatch(ExecuteCommandEntity requestParameter, ResponseBodyEmitter emitter) throws Exception {
        AiAgentVO aiAgentVO = repository.queryAiAgentByAgentId(requestParameter.getAiAgentId());

        String strategy = aiAgentVO.getStrategy();
        IExecuteStrategy executeStrategy = executeStrategyMap.get(strategy);
        if (null == executeStrategy) {
            throw new BizException("不存在的执行策略类型 strategy:" + strategy);
        }

        // 3. 异步执行AutoAgent
        threadPoolExecutor.execute(() -> {
            try {
                executeStrategy.execute(requestParameter, emitter);
            } catch (Exception e) {
                log.error("AutoAgent执行异常：{}", e.getMessage(), e);
                try {
                    emitter.send("执行异常：" + e.getMessage());
                } catch (Exception ex) {
                    log.error("发送异常信息失败：{}", ex.getMessage(), ex);
                }
            } finally {
                try {
                    emitter.complete();
                } catch (Exception e) {
                    log.error("完成流式输出失败：{}", e.getMessage(), e);
                }
            }
        });

    }

}
