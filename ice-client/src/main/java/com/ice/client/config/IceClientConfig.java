package com.ice.client.config;

import com.ice.client.listener.IceMockListener;
import com.ice.client.listener.IceShowConfListener;
import com.ice.client.listener.IceUpdateListener;
import com.ice.common.constant.Constant;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author zjn
 */
@Configuration
public class IceClientConfig {

  @Value("${ice.rabbit.host:}")
  private String host;
  @Value("${ice.rabbit.username:}")
  private String username;
  @Value("${ice.rabbit.password:}")
  private String password;
  @Value("${ice.rabbit.port:}")
  private Integer port;

  @Value("${ice.app:}")
  private Integer app;
/*
   * 等待初始化返回时间 默认10s
   */
  @Value("${ice.init.reply.timeout:10000}")
  private int timeout;

  @Bean(name = "iceConnectionFactory")
  public ConnectionFactory iceConnectionFactory() {
    CachingConnectionFactory iceConnectionFactory = new CachingConnectionFactory();
    iceConnectionFactory.setUsername(username);
    iceConnectionFactory.setPassword(password);
    iceConnectionFactory.setHost(host);
    iceConnectionFactory.setPort(port);
    return iceConnectionFactory;
  }

  @Bean(name = "iceUpdateQueue")
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
    return BindingBuilder.bind(iceUpdateQueue).to(iceUpdateExchange).with(Constant.getUpdateRoutetKey(app));
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
    return QueueBuilder.nonDurable(Constant.getShowConfQueue(app)).autoDelete().build();
  }

  @Bean(name = "iceShowConfExchange")
  public DirectExchange iceShowConfExchange() {
    return new DirectExchange(Constant.getShowConfExchange());
  }

  @Bean("iceShowConfBinding")
  public Binding iceShowConfBinding(
      @Qualifier("iceShowConfQueue") Queue iceShowConfQueue,
      @Qualifier("iceShowConfExchange") DirectExchange iceShowConfExchange) {
    return BindingBuilder.bind(iceShowConfQueue).to(iceShowConfExchange).with(String.valueOf(app));
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
    container.setMessageListener(new IceShowConfListener(app, iceRabbitTemplate));
    return container;
  }

  @Bean(name = "iceMockQueue")
  public Queue iceMockQueue() {
    return QueueBuilder.nonDurable(Constant.getMockQueue(app)).autoDelete().build();
  }

  @Bean(name = "iceMockExchange")
  public DirectExchange iceMockExchange() {
    return new DirectExchange(Constant.getMockExchange());
  }

  @Bean("iceMockBinding")
  public Binding iceMockBinding(
      @Qualifier("iceMockQueue") Queue iceMockQueue,
      @Qualifier("iceMockExchange") DirectExchange iceMockExchange) {
    return BindingBuilder.bind(iceMockQueue).to(iceMockExchange).with(String.valueOf(app));
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
    iceRabbitTemplate.setReplyTimeout(timeout);
    return iceRabbitTemplate;
  }

  @Bean(name = "iceAmqpAdmin")
  public AmqpAdmin iceAmqpAdmin(@Qualifier("iceConnectionFactory") ConnectionFactory iceConnectionFactory) {
    return new RabbitAdmin(iceConnectionFactory);
  }
}
