package com.mashibing.controller.testController;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class Test03 {
    @RequestMapping("/test03")
    public void  test03(){
        System.out.println("Controller注解 ......");
    }
}
