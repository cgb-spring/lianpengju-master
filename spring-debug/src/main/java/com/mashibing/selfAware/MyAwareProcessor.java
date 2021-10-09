package com.mashibing.selfAware;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.*;

import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedAction;

public class MyAwareProcessor implements BeanPostProcessor {

    private final ConfigurableApplicationContext applicationContext;

    public MyAwareProcessor(ConfigurableApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {

        AccessControlContext acc = null;

        if (System.getSecurityManager() != null) {
            acc = this.applicationContext.getBeanFactory().getAccessControlContext();
        }

        ((ApplicationContextAware) bean).setApplicationContext(this.applicationContext);
        return bean;
    }
}
