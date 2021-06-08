//package com.ice.test.listener;
//
//import com.alibaba.fastjson.JSON;
//import com.ice.client.IceClient;
//import com.ice.core.context.IcePack;
//import com.ice.core.context.IceRoam;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.amqp.core.Message;
//import org.springframework.amqp.rabbit.annotation.Exchange;
//import org.springframework.amqp.rabbit.annotation.Queue;
//import org.springframework.amqp.rabbit.annotation.QueueBinding;
//import org.springframework.amqp.rabbit.annotation.RabbitListener;
//import org.springframework.stereotype.Component;
//
//import java.util.Map;
//
///**
// * @author zjn
// * 充值消息Listener
// */
//@Slf4j
//@Component
//public class RechargeListener {
//
//    @RabbitListener(bindings = @QueueBinding(
//            exchange = @Exchange("ice.test.recharge.exchange"),
//            value = @Queue(value = "ice.test.recharge.queue", durable = "false")))
//    public void processRecharge(Message message) {
//        if (message.getBody() != null && message.getBody().length > 0) {
//            Map<String, Object> rechargeMap = JSON.parseObject(message.getBody(), Map.class);
//            /*组装数据*/
//            IcePack pack = new IcePack();
//
//            IceRoam roam = new IceRoam();
//            roam.put("uid", rechargeMap.get("uid"));
//            roam.put("spend", rechargeMap.get("spend"));
//            pack.setRoam(roam);
//            /*设置场景*/
//            pack.setScene("recharge");
//            /*扔进ice*/
//            IceClient.process(pack);
//        }
//    }
//}
