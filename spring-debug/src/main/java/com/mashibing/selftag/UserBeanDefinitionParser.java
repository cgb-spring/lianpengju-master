package com.mashibing.selftag;

import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.xml.AbstractSingleBeanDefinitionParser;
import org.springframework.util.StringUtils;
import org.w3c.dom.Element;

public class UserBeanDefinitionParser  extends AbstractSingleBeanDefinitionParser {

    // 返回属性值所对应的对象
    @Override
    protected Class<?> getBeanClass(Element element) {
        return User.class;
    }

    @Override
    protected void doParse(Element element, BeanDefinitionBuilder builder) {
        // 获取标签具体的属性值
       String userName = element.getAttribute("userName");
       String email = element.getAttribute("email");
       String password = element.getAttribute("password");

       if(StringUtils.hasText(userName)){
           builder.addPropertyValue("username",userName);
       }
       if (StringUtils.hasText(email)){
           builder.addPropertyValue("email",email);
       }
       if (StringUtils.hasText(password)){
           builder.addPropertyValue("password",password);
       }
    }
}
