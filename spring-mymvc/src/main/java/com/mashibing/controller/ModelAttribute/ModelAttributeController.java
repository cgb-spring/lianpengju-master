package com.mashibing.controller.ModelAttribute;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class ModelAttributeController {
    Object o1 = null;
    Object o2 = null;
    Object o3 = null;

    @RequestMapping("update")
    public String update(@ModelAttribute("user") User user, Model model){
        System.out.println(user);
        o2 = model;
        //可以看到所有的model都是同一个对象
        System.out.println(o1==o2);
        //可以看到存储的user对象也是同一个
        System.out.println(user == o3);
        return "output";
    }

    @ModelAttribute
    public void MyModelAttribute(Model model){
        o1 = model;
        User user = new User();
        user.setId(1);
        user.setName("张三");
        user.setAge(12);
        user.setPassword("123");
        model.addAttribute("user",user);
        System.out.println("modelAttribute:"+user);
        o3 = user;
    }

    @ModelAttribute("className")
    public String setModel(){
        return this.getClass().getName();
    }

    @ModelAttribute
    public String setModel2(){
        return "mashibing";
    }
}
