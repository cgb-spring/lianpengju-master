/*
 * Copyright 2002-2018 the original author or authors.
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

package org.springframework.context.annotation;

import org.springframework.beans.factory.parsing.Problem;
import org.springframework.beans.factory.parsing.ProblemReporter;
import org.springframework.core.type.MethodMetadata;

/**
 * Represents a {@link Configuration @Configuration} class method marked with the
 * {@link Bean @Bean} annotation.
 *
 * @author Chris Beams
 * @author Juergen Hoeller
 * @since 3.0
 * @see ConfigurationClass
 * @see ConfigurationClassParser
 * @see ConfigurationClassBeanDefinitionReader
 */
final class BeanMethod extends ConfigurationMethod {

	public BeanMethod(MethodMetadata metadata, ConfigurationClass configurationClass) {
		super(metadata, configurationClass);
	}

	/**
	 * 验证@Bean注解的方法，如果是静态的，就立即返回，否则的话需要判断是否可以覆盖
	 * @param problemReporter
	 */
	@Override
	public void validate(ProblemReporter problemReporter) {
		// 判断是否是静态的
		if (getMetadata().isStatic()) {
			// static @Bean methods have no constraints to validate -> return immediately
			// 静态@Bean方法没有约束校验，立即返回约束验证
			return;
		}

		if (this.configurationClass.getMetadata().isAnnotated(Configuration.class.getName())) {
			// 判断该配置类方法是否可以重写（非静态方法、非final方法，非private方法）
			if (!getMetadata().isOverridable()) {
				// instance @Bean methods within @Configuration classes must be overridable to accommodate CGLIB
				// @Configuration标注的配置类的@Bean方法要想使用CGLIB代理必须是可重写的
				problemReporter.error(new NonOverridableMethodError());
			}
		}
	}


	private class NonOverridableMethodError extends Problem {

		public NonOverridableMethodError() {
			super(String.format("@Bean method '%s' must not be private or final; change the method's modifiers to continue",
					getMetadata().getMethodName()), getResourceLocation());
		}
	}
}
