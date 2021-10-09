package com.mashibing.controller.sessionAttribute;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.SessionAttributes;
import org.springframework.web.bind.support.SessionStatus;

@Controller
@RequestMapping("/book")
@SessionAttributes(value = {"book","description"},types = {Double.class})
public class SessionAttributeController {
    private final Log logger = LogFactory.getLog(SessionAttributeController.class);

    @RequestMapping("/index")
    public String index(Model model){
        model.addAttribute("book","金刚经");
        model.addAttribute("description","经典");
        model.addAttribute("price",new Double("999.99"));
        return "redirect:get";
    }

    @RequestMapping("/get")
    public String getBySessionAttributes(@ModelAttribute("book") String book, ModelMap model, SessionStatus sessionStatus){
        logger.info("=====getBySessionAttributes======");
        logger.info(book);
        logger.info(model.get("book"));
        logger.info(model.get("description"));
        logger.info(model.get("price"));
        sessionStatus.setComplete();
        return "redirect:complete";
    }

    @RequestMapping("/complete")
    public String afterComplete(ModelMap modelMap){
        logger.info("=========afterComplete=========");
        logger.info(modelMap.get("book"));
        logger.info(modelMap.get("description"));
        logger.info(modelMap.get("price"));
        return "index";
    }
}
