package com.mashibing;

import com.mashibing.methodOverrides.lookup.Apple;
import com.mashibing.methodOverrides.lookup.FruitPlate;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * spring中默认的对象都是单例的，spring会在一级缓存中持有该对象，方便下次直接获取，
 * 那么如果是原型作用域的话，会创建一个新的对象
 * 如果想在一个单例模式的bean下引用一个原型模式的bean,怎么办？
 * 在此时就需要引用lookup-method标签来解决此问题
 *
 * 通过拦截器的方式每次需要的时候都去创建最新的对象，而不会把原型对象缓存起来，
 *
 */
public class TestMethodOverride {
    public static void main(String[] args) {
        ApplicationContext ac = new ClassPathXmlApplicationContext("methodOverride.xml");
        Apple bean = ac.getBean(Apple.class);
        System.out.println(bean.getBanana());
        Apple bean2 = ac.getBean(Apple.class);
        System.out.println(bean2.getBanana());
        //        FruitPlate fruitplate1 = (FruitPlate) ac.getBean("fruitplate1");
//        fruitplate1.getFruit();
//        FruitPlate fruitplate2 = (FruitPlate) ac.getBean("fruitplate1");
//        fruitplate2.getFruit();
//        FruitPlate fruitplate2 = (FruitPlate) ac.getBean("fruitplate2");
//        fruitplate2.getFruit();
    }
}
