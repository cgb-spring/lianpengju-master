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

package org.springframework.context.support;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.core.DecoratingClassLoader;
import org.springframework.core.OverridingClassLoader;
import org.springframework.core.SmartClassLoader;
import org.springframework.lang.Nullable;
import org.springframework.util.ReflectionUtils;

/**
 * 覆盖ClassLoader的特殊变体,用于{@link AbstractApplicationContext}中的临时类型匹配。
 * 为每个{@code loadClass}调用从缓存的字节数组中重新定义类，以便在父类ClassLoader中拾取
 * 最近加载的类型
 *
 * Special variant of an overriding ClassLoader, used for temporary type
 * matching in {@link AbstractApplicationContext}. Redefines classes from
 * a cached byte array for every {@code loadClass} call in order to
 * pick up recently loaded types in the parent ClassLoader.
 *
 * @author Juergen Hoeller
 * @since 2.5
 * @see AbstractApplicationContext
 * @see org.springframework.beans.factory.config.ConfigurableBeanFactory#setTempClassLoader
 */
class ContextTypeMatchClassLoader extends DecoratingClassLoader implements SmartClassLoader {

	static {
		// 通过该方法，可以使得ClassLoader执行并行加载机制，提高加载效率。
		ClassLoader.registerAsParallelCapable();
	}


	private static Method findLoadedClassMethod;

	static {
		try {
			findLoadedClassMethod = ClassLoader.class.getDeclaredMethod("findLoadedClass", String.class);
		}
		catch (NoSuchMethodException ex) {
			throw new IllegalStateException("Invalid [java.lang.ClassLoader] class: no 'findLoadedClass' method defined!");
		}
	}


	/** Cache for byte array per class name. */
	private final Map<String, byte[]> bytesCache = new ConcurrentHashMap<>(256);


	public ContextTypeMatchClassLoader(@Nullable ClassLoader parent) {
		super(parent);
	}

	@Override
	public Class<?> loadClass(String name) throws ClassNotFoundException {
		// 使用ContextOverridingClassLoader对象加载指定的类对象
		// ContextOverridingClassLoader:用于为每个加载的类创建ClassLoader。
		// 缓存文件内容，但为每个调用重新定义类
		return new ContextOverridingClassLoader(getParent()).loadClass(name);
	}

	@Override
	public boolean isClassReloadable(Class<?> clazz) {
		// 如果该类的类加载器是ContextOverridingClassLoader对象，则返回true，
		// 表示该类可重载（在此ClassLoader中）
		return (clazz.getClassLoader() instanceof ContextOverridingClassLoader);
	}


	/**
	 * 用于为每个加载的类创建ClassLoader。缓存文件内容，但为每个调用重新定义类
	 *
	 * ClassLoader to be created for each loaded class.
	 * Caches class file content but redefines class for each call.
	 */
	private class ContextOverridingClassLoader extends OverridingClassLoader {

		public ContextOverridingClassLoader(ClassLoader parent) {
			super(parent);
		}

		@Override
		protected boolean isEligibleForOverriding(String className) {
			// 如果该className需要排除或者是ContextTypeMatchClassLoader需要排除的
			if (isExcluded(className) || ContextTypeMatchClassLoader.this.isExcluded(className)) {
				// 返回false，表示className不适合该类加载器加载
				return false;
			}
			// 使findLoadedClass方法对象变成可访问
			ReflectionUtils.makeAccessible(findLoadedClassMethod);
			// 获取父级类加载器
			ClassLoader parent = getParent();
			// 循环获取每层的父类加载器
			while (parent != null) {
				// 使用父类加载器反射执行'findLoadedClass'方法，以判断给className是否加载过
				if (ReflectionUtils.invokeMethod(findLoadedClassMethod, parent, className) != null) {
					// 加载过的，返回false，表示className不适合该类加载器加载
					return false;
				}
				parent = parent.getParent();
			}
			// 默认情况，返回false，表示该className适合该类加载器加载
			return true;
		}

		@Override
		protected Class<?> loadClassForOverriding(String name) throws ClassNotFoundException {
			// 从字节缓存中获取name对应的字节数组
			byte[] bytes = bytesCache.get(name);
			// 如果存在
			if (bytes == null) {
				// 加载name的定义字节
				bytes = loadBytesForClass(name);
				// 如果字节不为null
				if (bytes != null) {
					// 将字节缓存起来
					bytesCache.put(name, bytes);
				}
				else {
					return null;
				}
			}
			// 根据字节构建类对象
			return defineClass(name, bytes, 0, bytes.length);
		}
	}

}
