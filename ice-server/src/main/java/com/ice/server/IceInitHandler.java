package com.ice.server;

import com.ice.server.service.IceServerService;
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
 * client start init msg handle
 */
@Slf4j
@Component
public class IceInitHandler {

    private final IceServerService iceServerService;

    @Contract(pure = true)
    public IceInitHandler(IceServerService iceServerService) {
        this.iceServerService = iceServerService;
    }

    @RabbitListener(bindings = @QueueBinding(
            exchange = @Exchange("#{T(com.ice.server.constant.Constant).getInitExchange()}"),
            value = @Queue(value = "ice.init.queue",
                    durable = "true")))
    public String processMessage(Message message) {
        if (message.getBody() != null && message.getBody().length > 0) {
            String appStr = new String(message.getBody());
            Integer app = Integer.valueOf(appStr);
            return iceServerService.getInitJson(app);
        }
        return "";
    }
}
