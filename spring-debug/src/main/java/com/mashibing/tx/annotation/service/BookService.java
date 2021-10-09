package com.mashibing.tx.annotation.service;

import com.mashibing.tx.annotation.dao.BookDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public class BookService {

    @Autowired
    BookDao bookDao;

    public BookDao getBookDao() {
        return bookDao;
    }

    public void setBookDao(BookDao bookDao) {
        this.bookDao = bookDao;
    }

    /**
     * 结账：传入哪个用户买了哪本书
     * @param username
     * @param id
     */
//    @Transactional(propagation = Propagation.REQUIRED)
    public void checkout(String username,int id){

        bookDao.updateStock(id);
        int price = bookDao.getPrice(id);
        bookDao.updateBalance(username,price);
//        try{

//        for (int i = 1 ;i>=0 ;i--)
//            System.out.println(10/i);
//        }catch (Exception e){
//            System.out.println("...............");
//        }
    }

}