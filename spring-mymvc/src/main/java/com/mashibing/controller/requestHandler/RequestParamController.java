package com.mashibing.controller.requestHandler;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class RequestParamController {
    /**
     * 如何获取SpringMVC中请求中的信息
     *  默认情况下，可以直接在方法的参数中填写跟请求一样的名称，此时会默认接受参数
     *      如果有值，直接赋值，如果没有，那么直接给空值
     *
     * @RequestParam:获取请求中的参数值,使用此注解之后，参数的名称不需要跟请求的名称一致，但是必须要写
     *      public String request(@RequestParam("user") String username){
     *
     *      此注解还包含三个参数：
     *      value:表示要获取的参数值
     *      required：表示此参数是否必须，默认是true，如果不写参数那么会报错，如果值为false，那么不写参数不会有任何错误
     *      defaultValue:如果在使用的时候没有传递参数，那么定义默认值即可
     *
     *
     * @param username
     * @return
     */
    @RequestMapping("/request")
    public String request(@RequestParam(value = "user",required = false,defaultValue = "hehe") String username){
        System.out.println(username);
        return "success";
    }
}
