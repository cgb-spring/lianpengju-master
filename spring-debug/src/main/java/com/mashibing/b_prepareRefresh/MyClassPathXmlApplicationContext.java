package com.mashibing.b_prepareRefresh;

import com.mashibing.MyBeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * 拓展prepareRefresh.initPropertySources方法
 * 作用:修改或添加系统变量或者环境变量，或者做验证。
 */
public class MyClassPathXmlApplicationContext extends ClassPathXmlApplicationContext {

    public MyClassPathXmlApplicationContext(String... configLocations){
        super(configLocations);
    }

    /**
     * 拓展initPropertySources方法,默认情况下是空的，这里会调用子类的实现。
     */
    @Override
    protected void initPropertySources() {
        System.out.println("扩展initPropertySource");
        //设置系统变量的属性值。
        getEnvironment().setRequiredProperties("abc");
        String abc = getEnvironment().getProperty("abc");
        System.out.println(abc);
    }

    public static void main(String[] args) {
        //测试加载
        ApplicationContext context = new MyClassPathXmlApplicationContext("spring-${username}.xml");
        Object person = context.getBean("person");
        System.out.println(person);
    }
}
