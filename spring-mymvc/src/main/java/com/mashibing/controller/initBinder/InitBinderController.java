package com.mashibing.controller.initBinder;

import javafx.scene.input.DataFormat;
import org.springframework.beans.propertyeditors.CustomDateEditor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * InitBinder注解作用于Controller中的方法，表示为当前控制器注册一个属性编辑器
 * 对webDataBinder进行初始化且只对当前的Controller有效
 *
 *
 */

@Controller
public class InitBinderController {

    // 类型转换
    @InitBinder
    public void initBinder(WebDataBinder binder){
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        CustomDateEditor dateEditor = new CustomDateEditor(df,true);
        binder.registerCustomEditor(Date.class,dateEditor);
    }

    // 属性编辑器
    @RequestMapping("/param")
    public String getFormatDate( Date data,  Map<String,Object> map){
        System.out.println(data);
        map.put("name","zhangsan");
        map.put("age",12);
        map.put("date",data);
        return "map";
    }

    // 参数绑定
    @InitBinder("user")
    public void init1(WebDataBinder binder){
        binder.setFieldDefaultPrefix("u.");
    }

    @InitBinder("stu")
    public void init2(WebDataBinder binder){
        binder.setFieldDefaultPrefix("s.");
    }

    @RequestMapping("/getBean")
    public ModelAndView getBean(User user, @ModelAttribute("stu") Student stu){
        System.out.println(stu);
        System.out.println(user);
        String viewName = "success";
        ModelAndView modelAndView = new ModelAndView(viewName);
        modelAndView.addObject("user", user);
        modelAndView.addObject("student", stu);
        return modelAndView;
    }

}
