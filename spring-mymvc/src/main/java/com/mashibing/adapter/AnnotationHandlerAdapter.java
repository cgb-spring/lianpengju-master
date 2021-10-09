package com.mashibing.adapter;

public class AnnotationHandlerAdapter implements HandlerAdapter {  
  
    public void handle(Object handler) {  
        ((AnnotationController)handler).doAnnotationHandler();  
    }  
  
    public boolean supports(Object handler) {  
          
        return (handler instanceof AnnotationController);  
    }  
  
}  