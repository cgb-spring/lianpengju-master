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

package org.springframework.core;

/**
 * 用于管理别名的公共接口，定义对别名的简单增删操作
 *
 * Common interface for managing aliases. Serves as a super-interface for
 * {link org.springframework.beans.factory.support.BeanDefinitionRegistry}.
 * 定义对alias的简单增删改操作
 * @author Juergen Hoeller
 * @since 2.5.2
 */
public interface AliasRegistry {

	/**
	 * 注册表中给name注册一个别名
	 *
	 * Given a name, register an alias for it.
	 * @param name the canonical name
	 * @param alias the alias to be registered
	 * @throws IllegalStateException if the alias is already in use
	 * and may not be overridden
	 */
	void registerAlias(String name, String alias);

	/**
	 * 移除注册表中的别名alias
	 *
	 * Remove the specified alias from this registry.
	 * @param alias the alias to remove
	 * @throws IllegalStateException if no such alias was found
	 */
	void removeAlias(String alias);

	/**
	 * 校验注册表中是否存在别名name
	 *
	 * Determine whether the given name is defined as an alias
	 * (as opposed to the name of an actually registered component).
	 * @param name the name to check
	 * @return whether the given name is an alias
	 */
	boolean isAlias(String name);

	/**
	 * 在注册表中获取给定的所有别名信息
	 *
	 * Return the aliases for the given name, if defined.
	 * @param name the name to check for aliases
	 * @return the aliases, or an empty array if none
	 */
	String[] getAliases(String name);

}
