package com.mashibing.selfeditor2;

import java.beans.PropertyEditorSupport;

/**
 * @author dyan
 * @data 2020/12/12
 */
public class AddressPropertyEditor extends PropertyEditorSupport {

    @Override
    public void setAsText(String text) throws IllegalArgumentException {
        String[] s = text.split("_");
        Address address = new Address();
        address.setProvince(s[0]);
        address.setCity(s[1]);
        address.setTown(s[2]);
        setValue(address);
    }
}
