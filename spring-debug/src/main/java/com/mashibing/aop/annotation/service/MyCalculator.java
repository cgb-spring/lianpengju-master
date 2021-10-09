package com.mashibing.aop.annotation.service;

import org.springframework.stereotype.Service;

@Service
public class MyCalculator {
    public Integer add(Integer i, Integer j) throws NoSuchMethodException {
        Integer result = i+j;
        return result;
    }

    public Integer sub(Integer i, Integer j) throws NoSuchMethodException {
        Integer result = i-j;
        return result;
    }

    public Integer mul(Integer i, Integer j) throws NoSuchMethodException {
        Integer result = i*j;
        return result;
    }

    public Integer div(Integer i, Integer j) throws NoSuchMethodException {
        Integer result = i/j;
        return result;
    }

    public Integer show(Integer i){
        System.out.println("show .....");
        return i;
    }
}
