package com.mashibing.bridge;

/**
 * 桥接方法是jdk1.5之后引入泛型之后，为了使java的泛型方法生成的字节码和1.5版本前的字节码相兼容，由编译器自动生成的方法
 *
 * 在使用的时候可以通过Method.isBridge方法来判断一个方法是否是桥接方法，在字节码中桥接方法会被标记为ACC_BRIDGE和ACC_SYNTHETIC
 * ACC_BRIDGE说明这个方法是由编译生成的桥接方法
 * ACC_SYNTHETIC说明这个方法是由编译器生成的，并不会在源代码中出现
 *
 * 什么时候编译器会生成桥接方法呢？
 * 一个子类在继承或实现一个父类的泛型方法时，在子类中明确指定了泛型类型，那么在编译时编译器会自动生成桥接方法
 *
 * 大家可以通过  javap -verbose SubClass.class来查看字节码文件
 *
 */
public interface SuperClass<T> {

    T method(T param);
}

