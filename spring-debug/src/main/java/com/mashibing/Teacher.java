package com.mashibing;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

public class Teacher{
    private int id;
    private String name;

    private ApplicationContext applicationContext;
    private BeanFactory beanFactory;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ApplicationContext getApplicationContext() {
        return applicationContext;
    }

    // 此处的set方法谁来进行调用？对象的创建都交给sping来管理了，那么方法的调用也应该交给spring容器
    // 给出某一个公共的方法来完成这些set方法的调用即可
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    public BeanFactory getBeanFactory() {
        return beanFactory;
    }

    public void setBeanFactory(BeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }
}
