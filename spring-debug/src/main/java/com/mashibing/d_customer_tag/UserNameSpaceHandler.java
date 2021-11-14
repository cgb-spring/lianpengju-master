package com.mashibing.d_customer_tag;

import org.springframework.beans.factory.xml.NamespaceHandlerSupport;

/**
 * 标签解析类。
 */
public class UserNameSpaceHandler extends NamespaceHandlerSupport {

    @Override
    public void init() {
        registerBeanDefinitionParser("user",new UserBeanDefinitionParser());
    }
}
