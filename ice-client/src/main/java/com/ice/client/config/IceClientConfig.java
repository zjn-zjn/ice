package com.ice.client.config;

import com.ice.client.constant.Constant;
import com.ice.client.listener.*;
import com.ice.core.utils.IceExecutor;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

import javax.annotation.Resource;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author zjn
 */
@Configuration
@EnableConfigurationProperties(IceClientProperties.class)
public class IceClientConfig {

    @Resource
    private IceClientProperties properties;

    @Bean(name = "iceConnectionFactory")
    public ConnectionFactory iceConnectionFactory() {
        CachingConnectionFactory iceConnectionFactory = new CachingConnectionFactory();
        iceConnectionFactory.setUsername(properties.getRabbit().getUsername());
        iceConnectionFactory.setPassword(properties.getRabbit().getPassword());
        iceConnectionFactory.setHost(properties.getRabbit().getHost());
        iceConnectionFactory.setPort(properties.getRabbit().getPort());
        return iceConnectionFactory;
    }

    @Bean(name = "iceExecutor")
    public void iceExecutorConfig() {
        ExecutorService executor = new ThreadPoolExecutor(properties.getPool().getCoreSize(),
                properties.getPool().getMaxSize(),
                properties.getPool().getKeepAliveSeconds(),
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(properties.getPool().getQueueCapacity()),
                new ThreadPoolExecutor.CallerRunsPolicy());
        IceExecutor.setExecutor(executor);
    }

    @Bean(name = "iceUpdateQueue")
    @DependsOn("addressUtils")
    public Queue iceUpdateQueue() {
        return QueueBuilder.nonDurable(Constant.genUpdateTmpQueue()).exclusive().autoDelete().build();
    }

    @Bean(name = "iceUpdateExchange")
    public DirectExchange iceUpdateExchange() {
        return new DirectExchange(Constant.getUpdateExchange());
    }

    @Bean("iceUpdateBinding")
    public Binding iceUpdateBinding(
            @Qualifier("iceUpdateQueue") Queue iceUpdateQueue,
            @Qualifier("iceUpdateExchange") DirectExchange iceUpdateExchange) {
        return BindingBuilder.bind(iceUpdateQueue).to(iceUpdateExchange).with(Constant.getUpdateRouteKey(properties.getApp()));
    }

    @Bean("iceUpdateMessageContainer")
    public SimpleMessageListenerContainer iceUpdateMessageContainer(
            @Qualifier("iceUpdateQueue") Queue iceUpdateQueue,
            @Qualifier("iceConnectionFactory") ConnectionFactory iceConnectionFactory,
            @Qualifier("iceRabbitTemplate") RabbitTemplate iceRabbitTemplate) {
        SimpleMessageListenerContainer container = new SimpleMessageListenerContainer(iceConnectionFactory);
        container.setQueues(iceUpdateQueue);
        container.setExposeListenerChannel(true);
        container.setPrefetchCount(1);
        container.setConcurrentConsumers(1);
        container.setAcknowledgeMode(AcknowledgeMode.NONE);
        container.setMessageListener(new IceUpdateListener());
        return container;
    }

    @Bean(name = "iceShowConfQueue")
    public Queue iceShowConfQueue() {
        return QueueBuilder.nonDurable(Constant.getShowConfQueue(properties.getApp())).autoDelete().build();
    }

    @Bean(name = "iceShowConfExchange")
    public DirectExchange iceShowConfExchange() {
        return new DirectExchange(Constant.getShowConfExchange());
    }

    @Bean(name = "iceConfExchange")
    public DirectExchange iceConfExchange() {
        return new DirectExchange(Constant.getConfExchange());
    }

    @Bean(name = "iceConfClazzCheckExchange")
    public DirectExchange iceConfClazzCheckExchange() {
        return new DirectExchange(Constant.getConfClazzCheckExchange());
    }

    @Bean(name = "iceConfClazzCheckQueue")
    public Queue iceConfClazzCheckQueue() {
        return QueueBuilder.nonDurable(Constant.getConfClazzCheckQueue(properties.getApp())).autoDelete().build();
    }

    @Bean(name = "iceAllConfIdExchange")
    public DirectExchange iceAllConfIdExchange() {
        return new DirectExchange(Constant.getAllConfIdExchange());
    }

    @Bean(name = "iceConfQueue")
    public Queue iceConfQueue() {
        return QueueBuilder.nonDurable(Constant.getConfQueue(properties.getApp())).autoDelete().build();
    }

    @Bean(name = "iceAllConfIdQueue")
    public Queue iceAllConfIdQueue() {
        return QueueBuilder.nonDurable(Constant.getAllConfIdQueue(properties.getApp())).autoDelete().build();
    }

    @Bean("iceAllConfIdBinding")
    public Binding iceAllConfIdBinding(
            @Qualifier("iceAllConfIdQueue") Queue iceAllConfIdQueue,
            @Qualifier("iceAllConfIdExchange") DirectExchange iceAllConfIdExchange) {
        return BindingBuilder.bind(iceAllConfIdQueue).to(iceAllConfIdExchange).with(String.valueOf(properties.getApp()));
    }

    @Bean("iceShowConfBinding")
    public Binding iceShowConfBinding(
            @Qualifier("iceShowConfQueue") Queue iceShowConfQueue,
            @Qualifier("iceShowConfExchange") DirectExchange iceShowConfExchange) {
        return BindingBuilder.bind(iceShowConfQueue).to(iceShowConfExchange).with(String.valueOf(properties.getApp()));
    }

    @Bean("iceConfClazzCheckBinding")
    public Binding iceConfClazzCheckBinding(
            @Qualifier("iceConfClazzCheckQueue") Queue iceConfClazzCheckQueue,
            @Qualifier("iceConfClazzCheckExchange") DirectExchange iceConfClazzCheckExchange) {
        return BindingBuilder.bind(iceConfClazzCheckQueue).to(iceConfClazzCheckExchange).with(String.valueOf(properties.getApp()));
    }

    @Bean("iceConfBinding")
    public Binding iceConfBinding(
            @Qualifier("iceConfQueue") Queue iceConfQueue,
            @Qualifier("iceConfExchange") DirectExchange iceConfExchange) {
        return BindingBuilder.bind(iceConfQueue).to(iceConfExchange).with(String.valueOf(properties.getApp()));
    }

    @Bean("iceConfClazzCheckMessageContainer")
    public SimpleMessageListenerContainer iceConfClazzCheckMessageContainer(
            @Qualifier("iceConfClazzCheckQueue") Queue iceConfClazzCheckQueue,
            @Qualifier("iceConnectionFactory") ConnectionFactory iceConnectionFactory,
            @Qualifier("iceRabbitTemplate") RabbitTemplate iceRabbitTemplate) {
        SimpleMessageListenerContainer container = new SimpleMessageListenerContainer(iceConnectionFactory);
        container.setQueues(iceConfClazzCheckQueue);
        container.setExposeListenerChannel(true);
        container.setPrefetchCount(1);
        container.setConcurrentConsumers(1);
        container.setAcknowledgeMode(AcknowledgeMode.NONE);
        container.setMessageListener(new IceConfClazzCheckListener(iceRabbitTemplate));
        return container;
    }

    @Bean("iceShowConfMessageContainer")
    public SimpleMessageListenerContainer iceShowConfMessageContainer(
            @Qualifier("iceShowConfQueue") Queue iceShowConfQueue,
            @Qualifier("iceConnectionFactory") ConnectionFactory iceConnectionFactory,
            @Qualifier("iceRabbitTemplate") RabbitTemplate iceRabbitTemplate) {
        SimpleMessageListenerContainer container = new SimpleMessageListenerContainer(iceConnectionFactory);
        container.setQueues(iceShowConfQueue);
        container.setExposeListenerChannel(true);
        container.setPrefetchCount(1);
        container.setConcurrentConsumers(1);
        container.setAcknowledgeMode(AcknowledgeMode.NONE);
        container.setMessageListener(new IceShowConfListener(properties.getApp(), iceRabbitTemplate));
        return container;
    }

    @Bean("iceConfMessageContainer")
    public SimpleMessageListenerContainer iceConfMessageContainer(
            @Qualifier("iceConfQueue") Queue iceConfQueue,
            @Qualifier("iceConnectionFactory") ConnectionFactory iceConnectionFactory,
            @Qualifier("iceRabbitTemplate") RabbitTemplate iceRabbitTemplate) {
        SimpleMessageListenerContainer container = new SimpleMessageListenerContainer(iceConnectionFactory);
        container.setQueues(iceConfQueue);
        container.setExposeListenerChannel(true);
        container.setPrefetchCount(1);
        container.setConcurrentConsumers(1);
        container.setAcknowledgeMode(AcknowledgeMode.NONE);
        container.setMessageListener(new IceConfListener(properties.getApp(), iceRabbitTemplate));
        return container;
    }

    @Bean("iceAllConfIdMessageContainer")
    public SimpleMessageListenerContainer iceAllConfIdMessageContainer(
            @Qualifier("iceAllConfIdQueue") Queue iceAllConfIdQueue,
            @Qualifier("iceConnectionFactory") ConnectionFactory iceConnectionFactory,
            @Qualifier("iceRabbitTemplate") RabbitTemplate iceRabbitTemplate) {
        SimpleMessageListenerContainer container = new SimpleMessageListenerContainer(iceConnectionFactory);
        container.setQueues(iceAllConfIdQueue);
        container.setExposeListenerChannel(true);
        container.setPrefetchCount(1);
        container.setConcurrentConsumers(1);
        container.setAcknowledgeMode(AcknowledgeMode.NONE);
        container.setMessageListener(new IceAllConfIdListener(iceRabbitTemplate));
        return container;
    }

    @Bean(name = "iceMockQueue")
    public Queue iceMockQueue() {
        return QueueBuilder.nonDurable(Constant.getMockQueue(properties.getApp())).autoDelete().build();
    }

    @Bean(name = "iceMockExchange")
    public DirectExchange iceMockExchange() {
        return new DirectExchange(Constant.getMockExchange());
    }

    @Bean("iceMockBinding")
    public Binding iceMockBinding(
            @Qualifier("iceMockQueue") Queue iceMockQueue,
            @Qualifier("iceMockExchange") DirectExchange iceMockExchange) {
        return BindingBuilder.bind(iceMockQueue).to(iceMockExchange).with(String.valueOf(properties.getApp()));
    }

    @Bean
    public IceMockListener iceMockListener() {
        return new IceMockListener();
    }

    @Bean("iceMockMessageContainer")
    public SimpleMessageListenerContainer iceMockMessageContainer(
            @Qualifier("iceMockQueue") Queue iceMockQueue,
            @Qualifier("iceConnectionFactory") ConnectionFactory iceConnectionFactory) {
        SimpleMessageListenerContainer container = new SimpleMessageListenerContainer(iceConnectionFactory);
        container.setQueues(iceMockQueue);
        container.setExposeListenerChannel(true);
        container.setPrefetchCount(1);
        container.setConcurrentConsumers(1);
        container.setAcknowledgeMode(AcknowledgeMode.NONE);
        container.setMessageListener(iceMockListener());
        return container;
    }

    @Bean(name = "iceRabbitTemplate")
    public RabbitTemplate iceRabbitTemplate(@Qualifier("iceConnectionFactory") ConnectionFactory iceConnectionFactory) {
        RabbitTemplate iceRabbitTemplate = new RabbitTemplate(iceConnectionFactory);
        iceRabbitTemplate.setReplyTimeout(properties.getRabbit().getReplyTimeout());
        return iceRabbitTemplate;
    }

    @Bean(name = "iceAmqpAdmin")
    public AmqpAdmin iceAmqpAdmin(@Qualifier("iceConnectionFactory") ConnectionFactory iceConnectionFactory) {
        return new RabbitAdmin(iceConnectionFactory);
    }
}
