package com.mashibing.selftag;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

@Configuration
public class MyService {

    @Bean
    public String show(){
        return "msb";
    }
}
