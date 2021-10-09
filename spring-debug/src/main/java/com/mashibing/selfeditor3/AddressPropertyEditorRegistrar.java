package com.mashibing.selfeditor3;

import org.springframework.beans.PropertyEditorRegistrar;
import org.springframework.beans.PropertyEditorRegistry;

/**
 * @author dyan
 * @data 2020/12/12
 */
public class AddressPropertyEditorRegistrar implements PropertyEditorRegistrar {

    @Override
    public void registerCustomEditors(PropertyEditorRegistry registry) {
        registry.registerCustomEditor(Address.class, new AddressPropertyEditor());
    }
}
