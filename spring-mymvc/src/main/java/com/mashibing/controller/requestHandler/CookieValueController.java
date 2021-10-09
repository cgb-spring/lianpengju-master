package com.mashibing.controller.requestHandler;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class CookieValueController {
    /**
     * 如果需要获取cookie信息该如何处理呢？
     *  可以使用@CookieValue注解，
     *      public String cookie(@CookieValue("JSESSIONID") String id){
     *      相当于
     *      Cookie[] cookies = request.getCookies();
     *      for(Cookie cookie : cookies){
     *          cookie.getValue();
     *      }
     *      如果要获取cookie中没有的信息，那么此时会报错，同样，此注解中也包含三个参数,跟@RequestParam一样
     *          value
     *          required
     *          defalutValue
     * @param id
     * @return
     */
    @RequestMapping("/cookie")
    public String cookie(@CookieValue("JSESSIONID") String id){
        System.out.println(id);
        return "success";
    }
}
