package com.mashibing.supplier;

import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class TestSupplier {

    public static void main(String[] args) {
        ApplicationContext ac = new ClassPathXmlApplicationContext("supplier.xml");
        User bean = ac.getBean(User.class);
        System.out.println(bean.getUsername());
    }
}
