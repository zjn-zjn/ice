package com.ice.client.listener;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.JSONSerializer;
import com.alibaba.fastjson.serializer.SerializeConfig;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.alibaba.fastjson.serializer.SimplePropertyPreFilter;
import com.ice.common.codec.IceLongCodec;
import com.ice.common.model.IceClientConf;
import com.ice.common.model.IceClientNode;
import com.ice.client.utils.AddressUtils;
import com.ice.core.base.BaseNode;
import com.ice.core.base.BaseRelation;
import com.ice.core.cache.IceConfCache;
import com.ice.core.utils.IceBeanUtils;
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

import java.util.ArrayList;
import java.util.List;

/**
 * @author zjn
 */
@Slf4j
public class IceConfListener implements MessageListener {

    private static final SerializeConfig FAST_JSON_CONFIG;
    private static final SpringBeanAndForwardFilter SPRING_BEAN_FILTER = new SpringBeanAndForwardFilter();

    static {
        FAST_JSON_CONFIG = new SerializeConfig();
        FAST_JSON_CONFIG.put(Long.class, IceLongCodec.getInstance());
    }

    private final RabbitTemplate iceRabbitTemplate;
    private final Integer app;
    private String address;
    private final MessageConverter messageConverter = new SimpleMessageConverter();

    public IceConfListener(Integer app, RabbitTemplate iceRabbitTemplate) {
        this.app = app;
        this.iceRabbitTemplate = iceRabbitTemplate;
    }

    private String getAddress() {
        address = address == null ? AddressUtils.getAddress() : address;
        return address;
    }

    @Override
    public void onMessage(Message message) {
        Address replyToAddress = message.getMessageProperties().getReplyToAddress();
        if (replyToAddress == null) {
            throw new AmqpRejectAndDontRequeueException("No replyToAddress in inbound AMQP Message");
        }
        if (message.getBody() != null && message.getBody().length > 0) {
            IceClientConf clientConf = new IceClientConf();
            clientConf.setIp(getAddress());
            String confIdStr = new String(message.getBody());
            long confId = Long.parseLong(confIdStr);
            clientConf.setApp(app);
            clientConf.setConfId(confId);
            BaseNode node = IceConfCache.getConfById(confId);
            if (node != null) {
                clientConf.setNode(assembleNode(node));
            }
            send(JSON.toJSONString(clientConf, FAST_JSON_CONFIG, SerializerFeature.DisableCircularReferenceDetect),
                    replyToAddress);
        } else {
            send("", replyToAddress);
        }
    }

    private IceClientNode assembleNode(BaseNode node) {
        if (node == null) {
            return null;
        }
        IceClientNode clientNode = new IceClientNode();
        if (node instanceof BaseRelation) {
            BaseRelation relation = (BaseRelation) node;
            IceLinkedList<BaseNode> children = relation.getChildren();
            if (children != null && !children.isEmpty()) {
                List<IceClientNode> showChildren = new ArrayList<>(children.getSize());
                for (IceLinkedList.Node<BaseNode> listNode = children.getFirst();
                     listNode != null; listNode = listNode.next) {
                    BaseNode child = listNode.item;
                    IceClientNode childMap = assembleNode(child);
                    if (childMap != null) {
                        showChildren.add(childMap);
                    }
                }
                clientNode.setChildren(showChildren);
            }

        }
        BaseNode forward = node.getIceForward();
        if (forward != null) {
            IceClientNode forwardNode = assembleNode(forward);
            if (forwardNode != null) {
                clientNode.setIceForward(forwardNode);
            }
        }
        clientNode.setIceNodeId(node.getIceNodeId());
        clientNode.setIceTimeTypeEnum(node.getIceTimeTypeEnum());
        clientNode.setIceStart(node.getIceStart());
        clientNode.setIceEnd(node.getIceEnd());
        clientNode.setIceNodeDebug(node.isIceNodeDebug());
        clientNode.setIceInverse(node.isIceInverse());
        return clientNode;
    }

    private void send(Object object, Address replyToAddress) {
        Message message = this.messageConverter.toMessage(object, new MessageProperties());
        iceRabbitTemplate.send(replyToAddress.getExchangeName(), replyToAddress.getRoutingKey(), message);
    }

    private static final class SpringBeanAndForwardFilter extends SimplePropertyPreFilter {
        @Override
        public boolean apply(JSONSerializer serializer, Object source, String name) {
            if ("iceForward".equals(name)) {
                return false;
            }
            return !IceBeanUtils.containsBean(name);
        }
    }
}
