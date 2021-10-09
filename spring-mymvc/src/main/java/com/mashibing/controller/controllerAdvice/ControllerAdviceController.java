package com.mashibing.controller.controllerAdvice;

import org.springframework.core.annotation.Order;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.servlet.ModelAndView;

import java.util.HashMap;
import java.util.Map;

/**
 * 使用@ControllerAdvice注解的Controller是一个增强的Controller，主要有三个功能
 * 1、全局异常处理
 * 2、全局数据绑定
 * 3、全局数据预处理
 */
@ControllerAdvice
public class ControllerAdviceController {

    //全局异常处理
    @ExceptionHandler(Exception.class)
    public ModelAndView customerException(Exception e){
        ModelAndView mv = new ModelAndView();
        mv.addObject("message",e.getMessage());
        mv.setViewName("myerror");
        return mv;
    }

    // 全局数据绑定
    @ModelAttribute(name="md")
    public Map<String,Object> mydata(){
        HashMap<String,Object> map = new HashMap<>();
        map.put("age",99);
        map.put("gender","男");
        return map;
    }

    @InitBinder("a")
    public void a(WebDataBinder binder){
        binder.setFieldDefaultPrefix("a.");
    }

    @InitBinder("b")
    public void b(WebDataBinder binder){
        binder.setFieldDefaultPrefix("b.");
    }
}
