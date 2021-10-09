package com.mashibing;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

public class MyBeanFactoryPostProcessor implements BeanFactoryPostProcessor {
    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
//        BeanDefinition dataSource = beanFactory.getBeanDefinition("dataSource");
//        Object username = dataSource.getPropertyValues().get("username");
//        if(username.equals("${jdbc.username}")){
//            username = "root";
//        }

    }
}
