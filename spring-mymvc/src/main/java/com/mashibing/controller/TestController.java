package com.mashibing.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class TestController {

    @RequestMapping("/fileupload*")
    public String test(){
        System.out.println("test");
        return "success";
    }
}
