package com.mashibing.methodOverrides.lookup;

public class Apple extends Fruit {

    private Banana banana;

    public Apple() {
        System.out.println("I got a fresh apple");
    }

    public Banana getBanana() {
        return banana;
    }

    public void setBanana(Banana banana) {
        this.banana = banana;
    }
}