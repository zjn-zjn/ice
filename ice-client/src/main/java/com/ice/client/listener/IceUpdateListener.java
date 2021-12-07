package com.ice.client.listener;

import com.alibaba.fastjson.JSON;
import com.ice.common.dto.IceTransferDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageListener;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * @author zjn
 */
@Slf4j
public final class IceUpdateListener implements MessageListener {

    private static volatile boolean waitInit = true;

    private static volatile long initVersion;

    private List<Message> waitMessageList = new ArrayList<>();

    public static void initEnd(long version) {
        waitInit = false;
        initVersion = version;
    }

    @Override
    public void onMessage(Message message) {
        try {
            if (waitInit) {
                log.info("wait init message:{}", JSON.toJSONString(message));
                waitMessageList.add(message);
                return;
            }
            if (!CollectionUtils.isEmpty(waitMessageList)) {
                for (Message waitMessage : waitMessageList) {
                    handleBeforeInitMessage(waitMessage);
                }
                waitMessageList = null;
            }
            handleMessage(message);
        } catch (Exception e) {
            log.error("ice listener update error message:{} e:", JSON.toJSONString(message), e);
        }
    }

    private void handleBeforeInitMessage(Message message) {
        String json = new String(message.getBody());
        IceTransferDto iceInfo = JSON.parseObject(json, IceTransferDto.class);
        if (iceInfo.getVersion() > initVersion) {
            /*一旦后面有出现比initVersion大的version 将initVersion置为-1 防止server端重启导致version从0开始*/
            log.info("ice listener update wait msg iceStart iceInfo:{}", json);
            IceUpdate.update(iceInfo);
            log.info("ice listener update wait msg iceEnd success");
            return;
        }
        log.info("ice listener msg version low then init version:{}, msg:{}", initVersion, JSON.toJSONString(iceInfo));
    }

    private void handleMessage(Message message) {
        String json = new String(message.getBody());
        IceTransferDto iceInfo = JSON.parseObject(json, IceTransferDto.class);
        log.info("ice listener update msg iceStart iceInfo:{}", json);
        IceUpdate.update(iceInfo);
        log.info("ice listener update msg iceEnd success");
    }
}
