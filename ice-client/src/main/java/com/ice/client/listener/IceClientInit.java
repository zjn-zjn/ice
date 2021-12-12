package com.ice.client.listener;

import com.alibaba.fastjson.JSON;
import com.ice.client.config.IceClientProperties;
import com.ice.common.constant.Constant;
import com.ice.common.exception.IceException;
import com.ice.common.dto.IceTransferDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;

/**
 * @author zjn
 */
@Slf4j
@Service
@DependsOn("iceBeanFactory")
public final class IceClientInit implements InitializingBean {

    @Resource
    private IceClientProperties properties;

    @Resource(name = "iceAmqpAdmin")
    private AmqpAdmin iceAmqpAdmin;

    @Resource(name = "iceRabbitTemplate")
    private RabbitTemplate iceRabbitTemplate;

    /*
     * to avoid loss update msg in init,make mq init first
     */
    @Override
    public void afterPropertiesSet() {
        log.info("ice client init iceStart");

        Object obj = iceRabbitTemplate.convertSendAndReceive(Constant.getInitExchange(), "", String.valueOf(properties.getApp()));
        String json = (String) obj;
        if (StringUtils.hasLength(json)) {
            IceTransferDto infoDto = JSON.parseObject(json, IceTransferDto.class);
            log.info("ice client init content:{}", JSON.toJSONString(infoDto));
            IceUpdate.update(infoDto);
            log.info("ice client init iceEnd success");
            IceUpdateListener.initEnd(infoDto.getVersion());
            return;
        }
        throw new IceException("ice init error maybe server is down app:" + properties.getApp());
    }
}
