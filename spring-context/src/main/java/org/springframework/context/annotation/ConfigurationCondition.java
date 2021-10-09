/*
 * Copyright 2002-2020 the original author or authors.
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

/**
 * 与@Configuration一起使用时提供更精细控制的条件，允许某些condition在匹配时根据配置阶段进行调整
 *
 * A {@link Condition} that offers more fine-grained control when used with
 * {@code @Configuration}. Allows certain conditions to adapt when they match
 * based on the configuration phase. For example, a condition that checks if a bean
 * has already been registered might choose to only be evaluated during the
 * {@link ConfigurationPhase#REGISTER_BEAN REGISTER_BEAN} {@link ConfigurationPhase}.
 *
 * @author Phillip Webb
 * @since 4.0
 * @see Configuration
 */
public interface ConfigurationCondition extends Condition {

	/**
	 * 返回判断Condition的ConfigurationCondition.ConfigurationPhase
	 *
	 * Return the {@link ConfigurationPhase} in which the condition should be evaluated.
	 */
	ConfigurationPhase getConfigurationPhase();


	/**
	 * 判断Condition的不同配置阶段
	 *
	 * The various configuration phases where the condition could be evaluated.
	 */
	enum ConfigurationPhase {

		/**
		 * @Configuration注解的类，解析的阶段判断Condition，如果Condition不匹配，则@Configuration注解的类不会加载
		 *
		 * The {@link Condition} should be evaluated as a {@code @Configuration}
		 * class is being parsed.
		 * <p>If the condition does not match at this point, the {@code @Configuration}
		 * class will not be added.
		 */
		PARSE_CONFIGURATION,

		/**
		 * @Configuration注解的类，实例化为bean的阶段判断Condition，无论Condition是否匹配，@Configuration注解的类都会加载，且类加载优先于Condition的判断
		 *
		 * The {@link Condition} should be evaluated when adding a regular
		 * (non {@code @Configuration}) bean. The condition will not prevent
		 * {@code @Configuration} classes from being added.
		 * <p>At the time that the condition is evaluated, all {@code @Configuration}
		 * classes will have been parsed.
		 */
		REGISTER_BEAN
	}

}
