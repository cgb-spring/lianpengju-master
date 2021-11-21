package com.mashibing.e_bf_prepared;

import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * 拓展AbstractRefreshableApplicationContext.customizeBeanFactory方法。
 * 自定义beanfactory的信息
 * AllowBeanDefinitionOverriding:允许bean的定义信息可以覆盖
 * AllowCircularReferences:允许
 */
public class BfPrepareApplicationContext {

    public static void main(String[] args) {
        //测试加载
        ApplicationContext context = new ClassPathXmlApplicationContext("/e_bf_prepared/spring.xml");
    }
}
