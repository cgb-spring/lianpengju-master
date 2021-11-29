package com.mashibing.e_bf_prepared;


import java.beans.PropertyEditorSupport;

/**
 * @author libin
 * @description
 * @date 2021-11-21
 */
public class AddressPropertyEditor extends PropertyEditorSupport {
    @Override
    public void setAsText(String text) throws IllegalArgumentException {
        String[] s = text.split("_");
        Address address = new Address();
        address.setProvince(s[0]);
        address.setCity(s[1]);
        address.setTown(s[2]);
        this.setValue(address);
    }
}
