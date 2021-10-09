package com.mashibing.populateBean;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessor;
import org.springframework.stereotype.Component;

@Component
public class PersonInstantiationAwareBeanPostProcessor implements InstantiationAwareBeanPostProcessor {

    @Override
    public boolean postProcessAfterInstantiation(Object bean, String beanName) throws BeansException {
        System.out.println("PersonInstantiationAwareBeanPostProcessor---被调用执行");
        Person person = null;
        if (bean instanceof Person){
            person = (Person) bean;
            person.setName("zhangsan");
            return true;
        }else{
            return false;
        }
    }
}
