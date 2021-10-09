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

package org.springframework.core;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * 装饰ClassLoader的基类，例如{@link OverridingClassLoader} 和
 * {link org.springframework.instrument.classloading.ShadowingClassLoader}，
 * 提供对排除的包和类的通用处理。
 *
 * Base class for decorating ClassLoaders such as {@link OverridingClassLoader}
 * and {link org.springframework.instrument.classloading.ShadowingClassLoader},
 * providing common handling of excluded packages and classes.
 *
 * @author Juergen Hoeller
 * @author Rod Johnson
 * @since 2.5.2
 */
public abstract class DecoratingClassLoader extends ClassLoader {

	static {
		// 通过该方法，可以使得ClassLoader执行并行加载机制，提高加载效率
		ClassLoader.registerAsParallelCapable();
	}


	// 要排除的包
	private final Set<String> excludedPackages = Collections.newSetFromMap(new ConcurrentHashMap<>(8));

	// 要排除的类
	private final Set<String> excludedClasses = Collections.newSetFromMap(new ConcurrentHashMap<>(8));


	/**
	 * Create a new DecoratingClassLoader with no parent ClassLoader.
	 */
	public DecoratingClassLoader() {
	}

	/**
	 * Create a new DecoratingClassLoader using the given parent ClassLoader
	 * for delegation.
	 */
	public DecoratingClassLoader(@Nullable ClassLoader parent) {
		super(parent);
	}


	/**
	 * 添加要从装饰中排除的类名（例如，覆盖）
	 *
	 * Add a package name to exclude from decoration (e.g. overriding).
	 * <p>Any class whose fully-qualified name starts with the name registered
	 * here will be handled by the parent ClassLoader in the usual fashion.
	 * @param packageName the package name to exclude
	 */
	public void excludePackage(String packageName) {
		Assert.notNull(packageName, "Package name must not be null");
		this.excludedPackages.add(packageName);
	}

	/**
	 * Add a class name to exclude from decoration (e.g. overriding).
	 * <p>Any class name registered here will be handled by the parent
	 * ClassLoader in the usual fashion.
	 * @param className the class name to exclude
	 */
	public void excludeClass(String className) {
		Assert.notNull(className, "Class name must not be null");
		this.excludedClasses.add(className);
	}

	/**
	 * 确定此类加载器是否将指定的类从装饰中排除
	 *
	 * Determine whether the specified class is excluded from decoration
	 * by this class loader.
	 * <p>The default implementation checks against excluded packages and classes.
	 * @param className the class name to check
	 * @return whether the specified class is eligible
	 * @see #excludePackage
	 * @see #excludeClass
	 */
	protected boolean isExcluded(String className) {
		if (this.excludedClasses.contains(className)) {
			return true;
		}
		for (String packageName : this.excludedPackages) {
			if (className.startsWith(packageName)) {
				return true;
			}
		}
		return false;
	}

}
