package com.mashibing.e_bf_prepared;

import com.mashibing.selfEditor.AddressPropertyEditor;
import org.springframework.beans.PropertyEditorRegistrar;
import org.springframework.beans.PropertyEditorRegistry;


/**
 * @author libin
 * @description
 * @date 2021-11-21
 */
public class AddressPropertyEditorRegistrar implements PropertyEditorRegistrar {

    @Override
    public void registerCustomEditors(PropertyEditorRegistry registry) {
        registry.registerCustomEditor(Address.class,new AddressPropertyEditor());
    }
}
