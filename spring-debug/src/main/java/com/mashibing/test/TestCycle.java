package com.mashibing.test;

import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class TestCycle {

    public static void main(String[] args) {

        ApplicationContext ac = new ClassPathXmlApplicationContext("test.xml");
        A bean = ac.getBean(A.class);
//        System.out.println(bean);
        B bean1 = ac.getBean(B.class);
//        System.out.println(bean1);

    }
}
