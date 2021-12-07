package com.ice.client.listener;

import com.alibaba.fastjson.JSON;
import com.ice.core.base.BaseNode;
import com.ice.core.base.BaseRelation;
import com.ice.core.cache.IceHandlerCache;
import com.ice.core.handler.IceHandler;
import com.ice.core.utils.IceLinkedList;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Address;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageListener;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.amqp.support.converter.SimpleMessageConverter;

import java.util.HashSet;
import java.util.Set;

/**
 * @author zjn
 */
@Slf4j
public class IceAllConfIdListener implements MessageListener {

    private final RabbitTemplate iceRabbitTemplate;
    private final MessageConverter messageConverter = new SimpleMessageConverter();


    public IceAllConfIdListener(RabbitTemplate iceRabbitTemplate) {
        this.iceRabbitTemplate = iceRabbitTemplate;
    }

    @Override
    public void onMessage(Message message) {
        Address replyToAddress = message.getMessageProperties().getReplyToAddress();
        if (replyToAddress == null) {
            throw new AmqpRejectAndDontRequeueException("No replyToAddress in inbound AMQP Message");
        }
        if (message.getBody() != null && message.getBody().length > 0) {
            String iceIdStr = new String(message.getBody());
            long iceId = Long.parseLong(iceIdStr);
            IceHandler handler = IceHandlerCache.getHandlerById(iceId);
            if (handler != null) {
                BaseNode root = handler.getRoot();
                if (root != null) {
                    Set<Long> allIdSet = new HashSet<>();
                    findAllConfIds(root, allIdSet);
                    send(JSON.toJSONString(allIdSet), replyToAddress);
                    return;
                }
            }
        }
        send("", replyToAddress);
    }

    private void findAllConfIds(BaseNode node, Set<Long> ids) {
        Long nodeId = node.getIceNodeId();
        ids.add(nodeId);
        BaseNode forward = node.getIceForward();
        if (forward != null) {
            findAllConfIds(forward, ids);
        }
        if (node instanceof BaseRelation) {
            IceLinkedList<BaseNode> children = ((BaseRelation) node).getChildren();
            if (children == null || children.isEmpty()) {
                return;
            }
            for (IceLinkedList.Node<BaseNode> listNode = children.getFirst();
                 listNode != null; listNode = listNode.next) {
                BaseNode child = listNode.item;
                findAllConfIds(child, ids);
            }
        }
    }

    private void send(Object object, Address replyToAddress) {
        Message message = this.messageConverter.toMessage(object, new MessageProperties());
        iceRabbitTemplate.send(replyToAddress.getExchangeName(), replyToAddress.getRoutingKey(), message);
    }
}
