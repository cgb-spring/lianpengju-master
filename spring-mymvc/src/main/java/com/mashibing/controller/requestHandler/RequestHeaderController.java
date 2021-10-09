package com.mashibing.controller.requestHandler;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class RequestHeaderController {
    /**
     * 如果需要获取请求头信息该如何处理呢？
     *  可以使用@RequestHeader注解，
     *      public String header(@RequestHeader("User-Agent") String agent){
     *      相当于  request.getHeader("User-Agent")
     *
     *      如果要获取请求头中没有的信息，那么此时会报错，同样，此注解中也包含三个参数,跟@RequestParam一样
     *          value
     *          required
     *          defalutValue
     * @param agent
     * @return
     */
    @RequestMapping("/header")
    public String header(@RequestHeader("User-Agent") String agent){
        System.out.println(agent);
        return "success";
    }
}
