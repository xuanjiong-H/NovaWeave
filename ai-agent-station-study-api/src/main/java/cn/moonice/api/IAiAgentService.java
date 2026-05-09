package cn.moonice.api;

import cn.moonice.api.dto.AiAgentResponseDTO;
import cn.moonice.api.dto.ArmoryAgentRequestDTO;
import cn.moonice.api.dto.AutoAgentRequestDTO;
import cn.moonice.api.response.Response;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import java.util.List;

/**
 * Ai Agent 服务接口
 *
 * @author xiaofuge bugstack.cn @小傅哥
 * 2025/8/7 17:52
 */
public interface IAiAgentService {

    ResponseBodyEmitter autoAgent(AutoAgentRequestDTO request, HttpServletResponse response);

    /**
     * 装配智能体
     */
    Response<Boolean> armoryAgent(ArmoryAgentRequestDTO request);

    /**
     * 查询可用的智能体列表
     */
    Response<List<AiAgentResponseDTO>> queryAvailableAgents();

}
