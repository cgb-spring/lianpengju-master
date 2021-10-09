/*
 * Copyright 2002-2019 the original author or authors.
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

package org.springframework.beans.factory.support;

import org.springframework.beans.factory.config.BeanDefinition;

/**
 * Default implementation of the {@link BeanNameGenerator} interface, delegating to
 * {@link BeanDefinitionReaderUtils#generateBeanName(BeanDefinition, BeanDefinitionRegistry)}.
 *
 * @author Juergen Hoeller
 * @since 2.0.3
 */
public class DefaultBeanNameGenerator implements BeanNameGenerator {

	/**
	 * DefaultBeanNameGenerator实例常量，用于在AbstractBeanDefinitionReader类中属性beanNameGenerator的设置
	 *
	 * A convenient constant for a default {@code DefaultBeanNameGenerator} instance,
	 * as used for {@link AbstractBeanDefinitionReader} setup.
	 * @since 5.2
	 */
	public static final DefaultBeanNameGenerator INSTANCE = new DefaultBeanNameGenerator();


	/**
	 * 通过BeanDefinitionReaderUtils工具类的generateBeanName方法生成指定BeanDefinition的bean名称
	 *
	 * @param definition the bean definition to generate a name for
	 * @param registry the bean definition registry that the given definition
	 * is supposed to be registered with
	 * @return
	 */
	@Override
	public String generateBeanName(BeanDefinition definition, BeanDefinitionRegistry registry) {
		return BeanDefinitionReaderUtils.generateBeanName(definition, registry);
	}

}
