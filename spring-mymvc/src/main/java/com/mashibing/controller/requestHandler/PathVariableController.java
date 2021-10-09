package com.mashibing.controller.requestHandler;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class PathVariableController {

    @RequestMapping(value = "/pathVariable/{name}")
    public String pathVariable(@PathVariable("name") String name){
        System.out.println(name);
        return "hello";
    }
}
