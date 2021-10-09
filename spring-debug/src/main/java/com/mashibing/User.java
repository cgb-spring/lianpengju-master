package com.mashibing;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;

public class User implements BeanFactoryAware, ApplicationContextAware, EnvironmentAware {

    private int id;
    private String name;

    private BeanFactory beanFactory;

    private ApplicationContext applicationContext;

    private Environment environment;

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

    // 想要当前属性有值的话，必须要调用set方法，那么此处的set方法谁来调用?是用户自己调用嘛？应该让容器帮我们进行调用操作
    // 容器怎么知道什么时候调用呢？给他一个固定的统一的处理环节，让容器来调用这些set方法，抽象出接口
    public void setBeanFactory(BeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }

    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }
}


