/*
 * Copyright 2002-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.beans;

import java.beans.PropertyDescriptor;

/**
 * spring中的核心接口，是spring中的一个包装类，具有单独或者批量获取和设置属性值，获取属性描述符以及查询属性可读可写的能力，还可以完成类型的转换
 *
 * The central interface of Spring's low-level JavaBeans infrastructure.
 *
 * <p>Typically not used directly but rather implicitly via a
 * {@link org.springframework.beans.factory.BeanFactory} or a
 * {link org.springframework.validation.DataBinder}.
 *
 * <p>Provides operations to analyze and manipulate standard JavaBeans:
 * the ability to get and set property values (individually or in bulk),
 * get property descriptors, and query the readability/writability of properties.
 *
 * <p>This interface supports <b>nested properties</b> enabling the setting
 * of properties on subproperties to an unlimited depth.
 *
 * <p>A BeanWrapper's default for the "extractOldValueForEditor" setting
 * is "false", to avoid side effects caused by getter method invocations.
 * Turn this to "true" to expose present property values to custom editors.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @since 13 April 2001
 * @see PropertyAccessor
 * @see PropertyEditorRegistry
 * @see PropertyAccessorFactory#forBeanPropertyAccess
 * @see org.springframework.beans.factory.BeanFactory
 * see org.springframework.validation.BeanPropertyBindingResult
 * see org.springframework.validation.DataBinder#initBeanPropertyAccess()
 */
public interface BeanWrapper extends ConfigurablePropertyAccessor {

	/**
	 * 指定数组和集合自动增长的限制，在普通的beanWrapper上，默认值是无限的
	 *
	 * Specify a limit for array and collection auto-growing.
	 * <p>Default is unlimited on a plain BeanWrapper.
	 * @since 4.1
	 */
	void setAutoGrowCollectionLimit(int autoGrowCollectionLimit);

	/**
	 * 返回数组和集合自动增长的限制
	 *
	 * Return the limit for array and collection auto-growing.
	 * @since 4.1
	 */
	int getAutoGrowCollectionLimit();

	/**
	 * 返回此对象包装的bean实例
	 *
	 * Return the bean instance wrapped by this object.
	 */
	Object getWrappedInstance();

	/**
	 * 返回包装的bean实例的类型
	 *
	 * Return the type of the wrapped bean instance.
	 */
	Class<?> getWrappedClass();

	/**
	 * 返回包装类的属性描述
	 *
	 * Obtain the PropertyDescriptors for the wrapped object
	 * (as determined by standard JavaBeans introspection).
	 * @return the PropertyDescriptors for the wrapped object
	 */
	PropertyDescriptor[] getPropertyDescriptors();

	/**
	 * 根据属性名称获取对应的property descriptor
	 *
	 * Obtain the property descriptor for a specific property
	 * of the wrapped object.
	 * @param propertyName the property to obtain the descriptor for
	 * (may be a nested path, but no indexed/mapped property)
	 * @return the property descriptor for the specified property
	 * @throws InvalidPropertyException if there is no such property
	 */
	PropertyDescriptor getPropertyDescriptor(String propertyName) throws InvalidPropertyException;

}
