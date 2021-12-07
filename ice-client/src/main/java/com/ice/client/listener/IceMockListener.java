package com.ice.client.listener;

import com.alibaba.fastjson.JSON;
import com.ice.client.IceClient;
import com.ice.core.context.IcePack;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageListener;

/**
 * @author zjn
 * mock信息
 */
@Slf4j
public class IceMockListener implements MessageListener {

    @Override
    public void onMessage(Message message) {
        if (message.getBody() != null && message.getBody().length > 0) {
            String json = new String(message.getBody());
            IcePack pack = JSON.parseObject(json, IcePack.class);
            IceClient.process(pack);
        }
    }
}
