package com.mashibing.e_bf_prepared;

/**
 * @author libin
 * @description
 * @date 2021-11-21
 */
public class Customer {

    private String name;

    private Address address;

    @Override
    public String toString() {
        return "Customer{" +
                "name='" + name + '\'' +
                ", address=" + address +
                '}';
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Address getAddress() {
        return address;
    }

    public void setAddress(Address address) {
        this.address = address;
    }
}
