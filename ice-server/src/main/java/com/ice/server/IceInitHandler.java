package com.ice.server;

import com.ice.server.service.ServerService;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Contract;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * @author zjn
 * client在启动时向server发送请求init消息处理
 */
@Slf4j
@Component
public class IceInitHandler {

    private final ServerService serverService;

    @Contract(pure = true)
    public IceInitHandler(ServerService serverService) {
        this.serverService = serverService;
    }

    @RabbitListener(bindings = @QueueBinding(
            exchange = @Exchange("#{T(com.ice.common.constant.Constant).getInitExchange()}"),
            value = @Queue(value = "ice.init.queue",
                    durable = "true")))
    public String processMessage(Message message) {
        if (message.getBody() != null && message.getBody().length > 0) {
            String appStr = new String(message.getBody());
            Integer app = Integer.valueOf(appStr);
            return serverService.getInitJson(app);
        }
        return "";
    }
}
