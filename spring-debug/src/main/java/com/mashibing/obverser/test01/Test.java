package com.mashibing.obverser.test01;

public class Test {

    public static void main(String[] args) {
        // 创建被观察者
        BadMan bm = new BadMan();
        // 创建观察者
        GoodMan gm = new GoodMan();
        GoodMan2 gm2 = new GoodMan2();

        //向被观察者中添加观察者
        bm.addObserver(gm);
        bm.addObserver(gm2);

        //等待罪犯触发某些行为
        bm.run();
    }
}
