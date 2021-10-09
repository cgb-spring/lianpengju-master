package com.mashibing.util;

import org.springframework.beans.BeanWrapper;
import org.springframework.beans.PropertyAccessorFactory;
import org.springframework.beans.PropertyValue;

public class BeanWrapperTest {

    public static void main(String[] args) {
        User user = new User();
        BeanWrapper beanWrapper = PropertyAccessorFactory.forBeanPropertyAccess(user);
        beanWrapper.setPropertyValue("username","张三");
        System.out.println(user.getUsername());

        PropertyValue value = new PropertyValue("username","李四");
        beanWrapper.setPropertyValue(value);
        System.out.println(user.getUsername());
    }
}


class User{
    private String username;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }
}