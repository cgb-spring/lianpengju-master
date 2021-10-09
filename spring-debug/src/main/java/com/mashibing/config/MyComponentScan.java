package com.mashibing.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

@Configuration
@ComponentScan("com.mashibing.selftag")
public class MyComponentScan {

    @ComponentScan("com.mashibing.selftag")
    @Configuration
    @Order(90)
    class InnerClass{

    }

}
