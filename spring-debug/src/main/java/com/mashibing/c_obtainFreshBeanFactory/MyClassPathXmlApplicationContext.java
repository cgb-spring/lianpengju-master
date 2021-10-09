package com.mashibing.c_obtainFreshBeanFactory;

import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * 拓展AbstractRefreshableApplicationContext.customizeBeanFactory方法。
 * 自定义beanfactory的信息
 *     AllowBeanDefinitionOverriding:允许bean的定义信息可以覆盖
 *     AllowCircularReferences:允许
 */
public class MyClassPathXmlApplicationContext extends ClassPathXmlApplicationContext {

    public MyClassPathXmlApplicationContext(String... configLocations){
        super(configLocations);
    }
    /** 自定义beanfactory
     * @param beanFactory the newly created bean factory for this context
     */
    @Override
    protected void customizeBeanFactory(DefaultListableBeanFactory beanFactory) {
        super.setAllowBeanDefinitionOverriding(false);
        super.setAllowCircularReferences(false);
        super.customizeBeanFactory(beanFactory);
    }
    public static void main(String[] args) {
        //测试加载
        ApplicationContext context = new MyClassPathXmlApplicationContext("spring-${username}.xml");
    }
}
