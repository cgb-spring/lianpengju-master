package com.mashibing;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

public class MyBeanFactoryPostProcessor512 implements BeanFactoryPostProcessor {
    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
//        BeanDefinition person = beanFactory.getBeanDefinition("person");
//        person.set
        System.out.println("今天是5月12号-----------");
        if (beanFactory.getBean(Person.class)!=null){
            System.out.println("21122132");
        }
    }
}
