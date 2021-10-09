package com.mashibing.adapter;

import java.util.ArrayList;
import java.util.List;

/**
 * springmvc中使用了适配器模式，原因在于controller类型不同，有多重实现方式，那么调用方法就不是确定的，
 * 如果不适用适配器模式，就有可能需要在执行的时候添加一系列的分支判断，如果需要新增一个controller类型，那么就需要在
 * 代码中添加一个分支判断，这样的程序太难以维护了，因此加入适配器模式，可以让每一个适配器对应一种controller的类型，来分别调用处理
 * 这样在扩展的时候只需要增加一个适配器来扩展即可
 *
 *
 */

public class AdapterTest {
    public static List<HandlerAdapter> handlerAdapters = new ArrayList<HandlerAdapter>();

    public AdapterTest(){
        handlerAdapters.add(new AnnotationHandlerAdapter());
        handlerAdapters.add(new HttpHandlerAdapter());
        handlerAdapters.add(new SimpleHandlerAdapter());
    }


    public void doDispatch(){

        //此处模拟SpringMVC从request取handler的对象，仅仅new出，可以出，
        //不论实现何种Controller，适配器总能经过适配以后得到想要的结果
//      HttpController controller = new HttpController();
      AnnotationController controller = new AnnotationController();
//        SimpleController controller = new SimpleController();
        //得到对应适配器
        HandlerAdapter adapter = getHandler(controller);
        //通过适配器执行对应的controller对应方法
        adapter.handle(controller);

    }

    public HandlerAdapter getHandler(Controller controller){
        for(HandlerAdapter adapter: handlerAdapters){
            if(adapter.supports(controller)){
                return adapter;
            }
        }
        return null;
    }

    public static void main(String[] args) {
        new AdapterTest().doDispatch();
    }
}
