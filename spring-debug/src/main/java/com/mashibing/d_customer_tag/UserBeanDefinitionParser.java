package com.mashibing.d_customer_tag;

import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.xml.AbstractSingleBeanDefinitionParser;
import org.springframework.util.StringUtils;
import org.w3c.dom.Element;

/**
 * 标签属性解析
 */
public class UserBeanDefinitionParser extends AbstractSingleBeanDefinitionParser {

    @Override
    protected Class<?> getBeanClass(Element element) {
        return User.class;
    }

    @Override
    protected void doParse(Element element, BeanDefinitionBuilder builder) {
        //获取属性值
        String userName  = element.getAttribute("userName");
        String email  = element.getAttribute("email");
        String password  = element.getAttribute("password");

        if(StringUtils.hasLength(userName)){
            builder.addPropertyValue("userName",userName);
        }

        if(StringUtils.hasLength(email)){
            builder.addPropertyValue("email",email);
        }
        if(StringUtils.hasLength(password)){
            builder.addPropertyValue("password",password);
        }
    }
}
