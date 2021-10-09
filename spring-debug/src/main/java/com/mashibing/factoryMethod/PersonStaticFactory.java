package com.mashibing.factoryMethod;

public class PersonStaticFactory {

    public static Person getPerson(String name){
        Person person = new Person();
        person.setId(1);
        person.setName(name);
        return person;
    }

    public static Person getPerson(int age){
        return new Person();
    }

    public static Person getPerson(String name,int id){
        Person person = new Person();
        person.setId(1);
        person.setName(name);
        return person;
    }
}