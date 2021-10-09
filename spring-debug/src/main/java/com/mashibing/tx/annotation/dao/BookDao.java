package com.mashibing.tx.annotation.dao;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public class BookDao {

    @Autowired
    JdbcTemplate jdbcTemplate;

    public JdbcTemplate getJdbcTemplate() {
        return jdbcTemplate;
    }

    public void setJdbcTemplate(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 减去某个用户的余额
     * @param userName
     * @param price
     */
//    @Transactional(propagation = Propagation.SUPPORTS)
    public void updateBalance(String userName,int price){
        String sql = "update account set balance=balance-? where username=?";
//        for (int i = 0; i <2 ; i++) {
//            int a = 1/i;
//        }
        jdbcTemplate.update(sql,price,userName);
    }

    /**
     * 按照图书的id来获取图书的价格
     * @param id
     * @return
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public int getPrice(int id){
        String sql = "select price from book where id=?";
        return jdbcTemplate.queryForObject(sql,Integer.class,id);
    }

    /**
     * 减库存，减去某本书的库存
     * @param id
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void updateStock(int id){
        String sql = "update book_stock set stock=stock-1 where id=?";
        jdbcTemplate.update(sql,id);
        for (int i = 1 ;i>=0 ;i--)
            System.out.println(10/i);
    }

    public void test(){
        updateStock(1);
    }
}