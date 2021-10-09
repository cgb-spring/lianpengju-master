package com.mashibing.selfeditor3;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * @author dyan
 * @data 2020/12/12
 */
public class EditorTest {

    public static void main(String[] args) {
        AnnotationConfigApplicationContext applicationContext =
                new AnnotationConfigApplicationContext(AddressPropertyConfiguration.class);
        System.out.println(applicationContext.getBean(Customer.class));
    }
}
