package com.mashibing.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.FlashMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.support.RequestContextUtils;

import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;

@Controller
public class FlashMapController {

    @RequestMapping(value = "/submit", method = RequestMethod.POST)
    public String submit(RedirectAttributes attr) {

        // 将参数值设置到Input_Flash_Map_Attribute中，然后放到model中
        ((FlashMap) ((ServletRequestAttributes) (RequestContextHolder.getRequestAttributes())).
                getRequest().getAttribute(DispatcherServlet.OUTPUT_FLASH_MAP_ATTRIBUTE)).put("name", "张三丰");
        // 放到flashmap中，同时也设置到model中
        attr.addFlashAttribute("ordersId","xxx");
        // 将参数拼接到url中
        attr.addAttribute("local","china");
        // 获取flashmap中的值
        HttpServletRequest request = ((ServletRequestAttributes) (RequestContextHolder.getRequestAttributes())).getRequest();
        FlashMap outputFlashMap = RequestContextUtils.getOutputFlashMap(request);
        System.out.println(outputFlashMap.get("name"));
        System.out.println(outputFlashMap.get("ordersId"));
        System.out.println(outputFlashMap.get("local"));
        Object attribute = request.getAttribute(DispatcherServlet.OUTPUT_FLASH_MAP_ATTRIBUTE);
        System.out.println(attribute);
        return "redirect:showorders";
    }

    @RequestMapping(value = "/showorders",method = RequestMethod.GET)
    public String showOrders(Model model){
        System.out.println(model.getAttribute("name"));
        System.out.println(model.getAttribute("ordersId"));
        System.out.println(model.getAttribute("local"));
        return "order";
    }
}
