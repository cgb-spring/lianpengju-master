package com.mashibing.tx.annotation;

import com.mashibing.tx.annotation.config.TransactionConfig;
import com.mashibing.tx.annotation.dao.BookDao;
import com.mashibing.tx.annotation.service.BookService;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class TransactionTest {
    public static void main(String[] args) {
        AnnotationConfigApplicationContext applicationContext = new AnnotationConfigApplicationContext();
        applicationContext.register(TransactionConfig.class);
        applicationContext.refresh();
//        BookService bean = applicationContext.getBean(BookService.class);
//        bean.checkout("zhangsan",1);
        BookDao bean = applicationContext.getBean(BookDao.class);
        bean.test();
    }
}
