package com.mashibing.cycle;

import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class TestCycle {

    public static void main(String[] args) {
        ApplicationContext ac = new ClassPathXmlApplicationContext("cycle.xml");
        A bean = ac.getBean(A.class);
        System.out.println(bean.getB());
        System.out.println(bean.getName());
        B bean1 = ac.getBean(B.class);
        System.out.println(bean1.getA());

    }
}
