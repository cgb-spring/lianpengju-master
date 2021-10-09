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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.util.StringValueResolver;

/**
 * AliasRegistry接口的简单实现。
 * 作为org.springframework.bean.factory.support.BeanDefinitionRegistry的基础实现
 *
 * Simple implementation of the {@link AliasRegistry} interface.
 * <p>Serves as base class for
 * {link org.springframework.beans.factory.support.BeanDefinitionRegistry}
 * implementations.
 *
 * @author Juergen Hoeller
 * @author Qimiao Chen
 * @since 2.5.2
 */
public class SimpleAliasRegistry implements AliasRegistry {

	/** Logger available to subclasses. */
	protected final Log logger = LogFactory.getLog(getClass());

	/**
	 * 从别名映射到规范名称
	 * Map from alias to canonical name. */
	private final Map<String, String> aliasMap = new ConcurrentHashMap<>(16);


	@Override
	public void registerAlias(String name, String alias) {
		Assert.hasText(name, "'name' must not be empty");
		Assert.hasText(alias, "'alias' must not be empty");
		synchronized (this.aliasMap) {
			// 如果beanName与alias相同的话不记录alias，并删除alias
			if (alias.equals(name)) {
				this.aliasMap.remove(alias);
				if (logger.isDebugEnabled()) {
					logger.debug("Alias definition '" + alias + "' ignored since it points to same name");
				}
			}
			else {
				String registeredName = this.aliasMap.get(alias);
				if (registeredName != null) {
					if (registeredName.equals(name)) {
						// An existing alias - no need to re-register
						return;
					}
					// 如果alias不允许被覆盖则抛出异常
					if (!allowAliasOverriding()) {
						throw new IllegalStateException("Cannot define alias '" + alias + "' for name '" +
								name + "': It is already registered for name '" + registeredName + "'.");
					}
					if (logger.isDebugEnabled()) {
						logger.debug("Overriding alias '" + alias + "' definition for registered name '" +
								registeredName + "' with new target name '" + name + "'");
					}
				}
				// 如果别名关系中存在环式结构，则会抛出异常，如A-B，同时出现B-A会报异常
				checkForAliasCircle(name, alias);
				this.aliasMap.put(alias, name);
				if (logger.isTraceEnabled()) {
					logger.trace("Alias definition '" + alias + "' registered for name '" + name + "'");
				}
			}
		}
	}

	/**
	 * Determine whether alias overriding is allowed.
	 * <p>Default is {@code true}.
	 */
	protected boolean allowAliasOverriding() {
		return true;
	}

	/**
	 * Determine whether the given name has the given alias registered.
	 * @param name the name to check
	 * @param alias the alias to look for
	 * @since 4.2.1
	 */
	public boolean hasAlias(String name, String alias) {
		String registeredName = this.aliasMap.get(alias);
		return ObjectUtils.nullSafeEquals(registeredName, name) || (registeredName != null
				&& hasAlias(name, registeredName));
	}

	@Override
	public void removeAlias(String alias) {
		synchronized (this.aliasMap) {
			String name = this.aliasMap.remove(alias);
			if (name == null) {
				throw new IllegalStateException("No alias '" + alias + "' registered");
			}
		}
	}

	/**
	 * 确定此给定名称是否定义为别名：返回name是否存在于别名Map【aliasMap】中
	 * @param name the name to check -- 要检查的名称
	 */
	@Override
	public boolean isAlias(String name) {
		// 返回name是否存在于别名Map中
		return this.aliasMap.containsKey(name);
	}

	/**
	 * 返回给定bean名称的别名(如果有)
	 * @param name the bean name to check for aliases -- 用来检测别名的bena名称
	 * @return 别名，如果没有则为空数组
	 */
	@Override
	public String[] getAliases(String name) {
		// 定义一个用于存放别名的集合
		List<String> result = new ArrayList<>();
		// 使用aliasMap加锁，以保证线程安全
		synchronized (this.aliasMap) {
			// 以递归的形式获取name的所有别名,将所有别名存放的result中
			retrieveAliases(name, result);
		}
		// 将result转换成String数组
		return StringUtils.toStringArray(result);
	}

	/**
	 * 以递归的形式获取name的所有别名,将所有别名存放的result中
	 *
	 * Transitively retrieve all aliases for the given name.
	 * @param name the target name to find aliases for
	 * @param result the resulting aliases list
	 */
	private void retrieveAliases(String name, List<String> result) {
		// 遍历aliasMap
		this.aliasMap.forEach((alias, registeredName) -> {
			// 如果已注册名称与要查找别名的目标名称相同
			if (registeredName.equals(name)) {
				// 将别名添加到result中
				result.add(alias);
				// 递归该方法，查找该别名的别名
				retrieveAliases(alias, result);
			}
		});
	}

	/**
	 * Resolve all alias target names and aliases registered in this
	 * registry, applying the given {@link StringValueResolver} to them.
	 * <p>The value resolver may for example resolve placeholders
	 * in target bean names and even in alias names.
	 * @param valueResolver the StringValueResolver to apply
	 */
	public void resolveAliases(StringValueResolver valueResolver) {
		Assert.notNull(valueResolver, "StringValueResolver must not be null");
		synchronized (this.aliasMap) {
			Map<String, String> aliasCopy = new HashMap<>(this.aliasMap);
			aliasCopy.forEach((alias, registeredName) -> {
				String resolvedAlias = valueResolver.resolveStringValue(alias);
				String resolvedName = valueResolver.resolveStringValue(registeredName);
				if (resolvedAlias == null || resolvedName == null || resolvedAlias.equals(resolvedName)) {
					this.aliasMap.remove(alias);
				}
				else if (!resolvedAlias.equals(alias)) {
					String existingName = this.aliasMap.get(resolvedAlias);
					if (existingName != null) {
						if (existingName.equals(resolvedName)) {
							// Pointing to existing alias - just remove placeholder
							this.aliasMap.remove(alias);
							return;
						}
						throw new IllegalStateException(
								"Cannot register resolved alias '" + resolvedAlias + "' (original: '" + alias +
								"') for name '" + resolvedName + "': It is already registered for name '" +
								registeredName + "'.");
					}
					checkForAliasCircle(resolvedName, resolvedAlias);
					this.aliasMap.remove(alias);
					this.aliasMap.put(resolvedAlias, resolvedName);
				}
				else if (!registeredName.equals(resolvedName)) {
					this.aliasMap.put(alias, resolvedName);
				}
			});
		}
	}

	/**
	 * 进行名字的规范化，其实就是获取原始名字，如果是别名就从映射中取原始名
	 *
	 * Check whether the given name points back to the given alias as an alias
	 * in the other direction already, catching a circular reference upfront
	 * and throwing a corresponding IllegalStateException.
	 * @param name the candidate name
	 * @param alias the candidate alias
	 * @see #registerAlias
	 * @see #hasAlias
	 */
	protected void checkForAliasCircle(String name, String alias) {
		if (hasAlias(alias, name)) {
			throw new IllegalStateException("Cannot register alias '" + alias +
					"' for name '" + name + "': Circular reference - '" +
					name + "' is a direct or indirect alias for '" + alias + "' already");
		}
	}

	/**
	 * 获取name的最终别名或者是全类名
	 *
	 * Determine the raw name, resolving aliases to canonical names.
	 * @param name the user-specified name
	 * @return the transformed name
	 */
	public String canonicalName(String name) {
		// 规范名称初始化化传入的name
		String canonicalName = name;
		// Handle aliasing...
		// 处理别名
		String resolvedName;
		do {
			// 将别名解析成真正的beanName
			resolvedName = this.aliasMap.get(canonicalName);
			// 如果找到了解析后的名称
			if (resolvedName != null) {
				// 规范名称重新赋值为解析后名称
				canonicalName = resolvedName;
			}
		}
		// 只要找到了解析后的名称
		while (resolvedName != null);
		return canonicalName;
	}

}
