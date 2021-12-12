package com.ice.client.listener;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.JSONSerializer;
import com.alibaba.fastjson.serializer.SerializeConfig;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.alibaba.fastjson.serializer.SimplePropertyPreFilter;
import com.ice.common.codec.IceLongCodec;
import com.ice.common.utils.AddressUtils;
import com.ice.core.base.BaseNode;
import com.ice.core.base.BaseRelation;
import com.ice.core.cache.IceConfCache;
import com.ice.core.cache.IceHandlerCache;
import com.ice.core.handler.IceHandler;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author zjn
 */
@Slf4j
@Deprecated
public class IceShowConfListener implements MessageListener {

    private static final SerializeConfig FAST_JSON_CONFIG;
    private static final SpringBeanAndForwardFilter SPRING_BEAN_FILTER = new SpringBeanAndForwardFilter();

    static {
        FAST_JSON_CONFIG = new SerializeConfig();
        FAST_JSON_CONFIG.put(Long.class, IceLongCodec.getInstance());
    }

    private final RabbitTemplate iceRabbitTemplate;
    private Integer app;
    private String address;
    private MessageConverter messageConverter = new SimpleMessageConverter();

    public IceShowConfListener(RabbitTemplate iceRabbitTemplate, MessageConverter messageConverter) {
        this.iceRabbitTemplate = iceRabbitTemplate;
        this.messageConverter = messageConverter;
    }

    public IceShowConfListener(RabbitTemplate iceRabbitTemplate) {
        this.iceRabbitTemplate = iceRabbitTemplate;
    }

    public IceShowConfListener(Integer app, RabbitTemplate iceRabbitTemplate) {
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
            Map<Object, Object> resMap = new HashMap<>();
            resMap.put("ip", getAddress());
            String iceIdStr = new String(message.getBody());
            Long iceId = Long.valueOf(iceIdStr);
            resMap.put("iceId", iceId);
            resMap.put("app", app);
            if (iceId <= 0) {
                resMap.put("handlerMap", IceHandlerCache.getIdHandlerMap());
                resMap.put("confMap", IceConfCache.getConfMap());
            } else {
                IceHandler handler = IceHandlerCache.getHandlerById(iceId);
                if (handler != null) {
                    Map<Object, Object> handlerMap = new HashMap<>();
                    handlerMap.put("iceId", handler.findIceId());
                    handlerMap.put("scenes", handler.getScenes());
                    handlerMap.put("debug", handler.getDebug());
                    handlerMap.put("start", handler.getStart());
                    handlerMap.put("end", handler.getEnd());
                    handlerMap.put("timeTypeEnum", handler.getTimeTypeEnum());
                    BaseNode root = handler.getRoot();
                    if (root != null) {
                        handlerMap.put("root", assembleNode(root));
                    }
                    resMap.put("handler", handlerMap);
                }
            }
            send(JSON.toJSONString(resMap, FAST_JSON_CONFIG, SerializerFeature.DisableCircularReferenceDetect),
                    replyToAddress);
        } else {
            send("", replyToAddress);
        }
    }

    @SuppressWarnings("unchecked")
    private Map assembleNode(BaseNode node) {
        if (node == null) {
            return null;
        }
        Map map = new HashMap<>();
        if (node instanceof BaseRelation) {
            BaseRelation relation = (BaseRelation) node;
            IceLinkedList<BaseNode> children = relation.getChildren();
            if (children != null && !children.isEmpty()) {
                List<Map> showChildren = new ArrayList<>(children.getSize());
                for (IceLinkedList.Node<BaseNode> listNode = children.getFirst();
                     listNode != null; listNode = listNode.next) {
                    BaseNode child = listNode.item;
                    Map childMap = assembleNode(child);
                    if (childMap != null) {
                        showChildren.add(childMap);
                    }
                }
                map.put("children", showChildren);
            }
            BaseNode forward = relation.getIceForward();
            if (forward != null) {
                Map forwardMap = assembleNode(forward);
                if (forwardMap != null) {
                    map.put("iceForward", forwardMap);
                }
            }
            map.put("iceNodeId", relation.getIceNodeId());
            map.put("iceTimeTypeEnum", relation.getIceTimeTypeEnum());
            map.put("iceStart", relation.getIceStart());
            map.put("iceEnd", relation.getIceEnd());
            map.put("iceNodeDebug", relation.isIceNodeDebug());
            map.put("iceInverse", relation.isIceInverse());
        } else {
            map = JSON.parseObject(JSON.toJSONString(node, FAST_JSON_CONFIG, SPRING_BEAN_FILTER,
                    SerializerFeature.DisableCircularReferenceDetect), Map.class);
            BaseNode forward = node.getIceForward();
            if (forward != null) {
                Map forwardMap = assembleNode(forward);
                if (forwardMap != null) {
                    map.put("iceForward", forwardMap);
                }
            }
        }
        return map;
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
