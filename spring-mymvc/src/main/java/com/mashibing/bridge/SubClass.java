package com.mashibing.bridge;

import com.mashibing.bean.User;

/**
 * 在此方法中只声明了一个方法，但是从字节码中可以看到三个方法，
 * 第一个是无参的构造方法，编译器自动生成
 * 第二个是实现接口中的方法
 * 第三个是编译器自动生成的桥接方法，可以看到那两个标志，同时方法的参数和返回值都是Object类型
 *
 * 在声明SuperClass类型的变量时，不指定泛型类型，那么在方法调用时就可以传任何类型的参数，因为SuperClass中的方法参数实际上是Object类型，而且
 * 编译器也不能够发现错误，但是当运行的时候就会发现参数类型不是Subclass声明的类型，会抛出类型转换异常，因为此时调用的是桥接方法，而在桥接方法中会
 * 进行类型强制转换，所以会抛出此异常
 *
 * 如果在声明的时候直接就指定好了泛型的类型，那么编译的时候就会出现异常情况，这样的话就会把错误提前
 *
 * 为什么要生成桥接方法呢？
 *      在1.5版本之前创建一个集合对象之后，可以向其中放置任何类型的元素，无法确定和限制具体的类型，而引入泛型之后可以指定存放什么类型的数据，
 *      泛型在此处起到的作用就是检查向集合中添加的对象类型是否匹配泛型类型，如果不正常，那么在编译的时候就会出错，而不必等到运行时才发现错误
 *      但是泛型是在之后的版本中出现的，为了向前兼容，所以会在编译时去掉泛型，但是可以通过反射API来获取泛型的信息，在编译时可以通过泛型来保证
 *      类型的正确性，而不必等到运行时才发现类型不正确，正是由于java泛型的擦除特性，如果不生成桥接方法，那么与1.5之前的字节码就不兼容了。
 *      因为有了继承关系，如果不生成桥接方法，那么SubClass就没有实现接口中声明的方法，语义就不正确了，所以编译器才会生成桥接方法来保证兼容性
 *
 *
 *
 */

public class SubClass implements SuperClass<String>{
    @Override
    public String method(String param) {
        return param;
    }

    public static void main(String[] args) {
//        SuperClass superClass = new SubClass();
//        System.out.println(superClass.method("123"));
//        System.out.println(superClass.method(new Object()));
    }

}
