package com.mashibing.selfbdrpp;

public class Teacher {

    private String name;


    public Teacher() {
        System.out.println("创建teacher对象");
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return "Teacher{" +
                "name='" + name + '\'' +
                '}';
    }
}
