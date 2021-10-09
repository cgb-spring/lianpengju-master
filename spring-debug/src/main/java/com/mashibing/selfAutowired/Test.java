package com.mashibing.selfAutowired;

import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class Test {

    public static void main(String[] args) {
        ApplicationContext applicationContext = new ClassPathXmlApplicationContext("testlian.xml");
        LianController bean = applicationContext.getBean(LianController.class);
        bean.show();
    }
}
