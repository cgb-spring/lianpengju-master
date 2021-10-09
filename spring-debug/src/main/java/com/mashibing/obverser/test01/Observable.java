package com.mashibing.obverser.test01;

/**
 * 被观察者
 */
public interface Observable {

     public void addObserver(Observer observer);
     public void deleteObserver(Observer observer);
     public void notifyObserver(String str);
}
