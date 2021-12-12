package com.ice.client.config;

import com.ice.core.utils.IceBeanUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/**
 * @author zjn
 * use AutowireCapableBeanFactory
 */
@Component("iceBeanFactory")
public class IceSpringBeanFactory implements IceBeanUtils.IceBeanFactory, ApplicationContextAware {

    private AutowireCapableBeanFactory beanFactory;

    @Override
    public void autowireBean(Object existingBean) {
        this.beanFactory.autowireBean(existingBean);
    }

    @Override
    public boolean containsBean(String name) {
        return this.beanFactory.containsBean(name);
    }

    @Override
    public Object getBean(String name) {
        return beanFactory.getBean(name);
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.beanFactory = applicationContext.getAutowireCapableBeanFactory();
        /*set init beanFactory to IceBeanUtils*/
        IceBeanUtils.setFactory(this);
    }
}
