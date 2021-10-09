package com.mashibing.controller.responsebodyAdvice;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;

@Controller
public class ResponseBodyAdviceController {

    @RequestMapping("responseBodyAdvice")
    @ResponseBody
    public String responseBodyAdvice(HttpServletRequest request, Model model){
        model.addAttribute("msg","go go go!");
        return "go";
    }
}
