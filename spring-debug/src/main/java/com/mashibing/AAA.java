package com.mashibing;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

public class AAA implements ApplicationContextAware, BeanFactoryAware {

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

    // 如果想要该对象中的属性有值，那么就意味着必要要调用set方法来能够完成赋值操作，谁来调用当前的set方法呢？
    // 是用户自己调用嘛？对象的创建都交给容器来管理了，为什么set方法不能交给容器来管理呢，为什么需要使用者自己调用呢？
    // 在后续的环节中调用aware接口的子类统一进行处理即可
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
