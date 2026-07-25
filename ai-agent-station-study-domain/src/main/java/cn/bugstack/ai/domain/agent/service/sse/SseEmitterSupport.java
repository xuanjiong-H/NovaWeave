package cn.bugstack.ai.domain.agent.service.sse;

import cn.bugstack.ai.domain.agent.model.entity.AutoAgentExecuteResultEntity;
import com.alibaba.fastjson.JSON;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import java.io.IOException;

public final class SseEmitterSupport {

    private SseEmitterSupport() {
    }

    public static void send(ResponseBodyEmitter emitter, AutoAgentExecuteResultEntity result) {
        try {
            emitter.send("data: " + JSON.toJSONString(result) + "\n\n");
        } catch (IOException | IllegalStateException e) {
            throw new SseConnectionClosedException("SSE connection is no longer writable", e);
        }
    }

}
