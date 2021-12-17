package com.ice.client.listener;

import com.ice.client.utils.AddressUtils;
import com.ice.common.enums.NodeTypeEnum;
import com.ice.core.leaf.base.BaseLeafFlow;
import com.ice.core.leaf.base.BaseLeafNone;
import com.ice.core.leaf.base.BaseLeafResult;
import com.ice.core.relation.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Address;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageListener;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.amqp.support.converter.SimpleMessageConverter;

/**
 * @author zjn
 */
@Slf4j
public class IceConfClazzCheckListener implements MessageListener {

    private final RabbitTemplate iceRabbitTemplate;
    private final MessageConverter messageConverter = new SimpleMessageConverter();

    public IceConfClazzCheckListener(RabbitTemplate iceRabbitTemplate) {
        this.iceRabbitTemplate = iceRabbitTemplate;
    }

    @Override
    public void onMessage(Message message) {
        Address replyToAddress = message.getMessageProperties().getReplyToAddress();
        if (replyToAddress == null) {
            throw new AmqpRejectAndDontRequeueException("No replyToAddress in inbound AMQP Message");
        }
        String[] clazzTypes = new String(message.getBody()).split(",");
        String clazz = clazzTypes[0];
        byte type = Byte.parseByte(clazzTypes[1]);
        try {
            Class<?> clientClazz = Class.forName(clazz);
            NodeTypeEnum typeEnum = NodeTypeEnum.getEnum(type);
            boolean res = false;
            switch (typeEnum) {
                case ALL:
                    res = All.class.isAssignableFrom(clientClazz);
                    break;
                case AND:
                    res = And.class.isAssignableFrom(clientClazz);
                    break;
                case NONE:
                    res = None.class.isAssignableFrom(clientClazz);
                    break;
                case TRUE:
                    res = True.class.isAssignableFrom(clientClazz);
                    break;
                case ANY:
                    res = Any.class.isAssignableFrom(clientClazz);
                    break;
                case LEAF_FLOW:
                    res = BaseLeafFlow.class.isAssignableFrom(clientClazz);
                    break;
                case LEAF_NONE:
                    res = BaseLeafNone.class.isAssignableFrom(clientClazz);
                    break;
                case LEAF_RESULT:
                    res = BaseLeafResult.class.isAssignableFrom(clientClazz);
                    break;
            }
            if (res) {
                send("1", replyToAddress);
            } else {
                send("0,type not match in " + AddressUtils.getAddress() + " input(" + clazz + "|" + type + ")", replyToAddress);
            }
        } catch (ClassNotFoundException e) {
            send("0,class not found in " + AddressUtils.getAddress() + " input(" + clazz + "|" + type + ")", replyToAddress);
        } catch (Exception e) {
            send("0," + AddressUtils.getAddress(), replyToAddress);
        }
    }

    private void send(Object object, Address replyToAddress) {
        Message message = this.messageConverter.toMessage(object, new MessageProperties());
        iceRabbitTemplate.send(replyToAddress.getExchangeName(), replyToAddress.getRoutingKey(), message);
    }
}
