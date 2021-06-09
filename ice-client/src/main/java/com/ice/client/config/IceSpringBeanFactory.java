package com.ice.client.config;

import com.ice.core.utils.IceBeanUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/**
 * @author zjn
 * 使用AutowireCapableBeanFactory实现
 */
@Component("iceBeanFactory")
public class IceSpringBeanFactory implements IceBeanUtils.IceBeanFactory, ApplicationContextAware {

  private AutowireCapableBeanFactory beanFactory;

/*
   * 注入Bean
   *
   * @param existingBean
   */
  @Override
  public void autowireBean(Object existingBean) {
    this.beanFactory.autowireBean(existingBean);
  }

/*
   * 检查是否有此Bean
   *
   * @param name
   */
  @Override
  public boolean containsBean(String name) {
    return this.beanFactory.containsBean(name);
  }

/*
   * 根据名称获取bean
   *
   * @param name beanName
   */
  @Override
  public Object getBean(String name) {
    return beanFactory.getBean(name);
  }

/*
   * @param applicationContext
   * @throws BeansException
   */
  @Override
  public void setApplicationContext(ApplicationContext applicationContext) {
    this.beanFactory = applicationContext.getAutowireCapableBeanFactory();
    /*将初始化完的beanFactory塞入IceBeanUtils*/
    IceBeanUtils.setFactory(this);
  }
}
