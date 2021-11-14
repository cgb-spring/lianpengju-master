package com.mashibing.d_customer_tag;

import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class CustmoerTag {
    public static void main(String[] args) {
        ApplicationContext context = new ClassPathXmlApplicationContext("d_customer_tag/spring.xml");
        User user = (User)context.getBean("msb");
        System.out.println(user);
    }
}
