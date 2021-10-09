package com.mashibing.selfeditor2;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * @author dyan
 * @data 2020/12/12
 */
public class EditorTest {

    public static void main(String[] args) {
        AnnotationConfigApplicationContext applicationContext =
                new AnnotationConfigApplicationContext();
        applicationContext.register(AddressPropertyConfiguration.class);
        applicationContext.register(Customer.class);
        applicationContext.refresh();
        System.out.println(applicationContext.getBean(Customer.class));
    }
}
