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

package org.springframework.util;

import java.beans.Introspector;
import java.io.Closeable;
import java.io.Externalizable;
import java.io.Serializable;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;

import org.springframework.lang.Nullable;

/**
 * 其他的{@code java.lang.Class}使用方法。主要供框架内部使用
 *
 * Miscellaneous {@code java.lang.Class} utility methods.
 * Mainly for internal use within the framework.
 *
 * @author Juergen Hoeller
 * @author Keith Donald
 * @author Rob Harrop
 * @author Sam Brannen
 * @since 1.1
 * @see TypeUtils
 * @see ReflectionUtils
 */
public abstract class ClassUtils {

	/**
	 * 数组类名的后缀：'[]'
	 * Suffix for array class names: {@code "[]"}. */
	public static final String ARRAY_SUFFIX = "[]";

	/**
	 * 内部数组类名前缀：'['
	 * Prefix for internal array class names: {@code "["}. */
	private static final String INTERNAL_ARRAY_PREFIX = "[";

	/**
	 * 内部非原始数组类名前缀：'[L'
	 * Prefix for internal non-primitive array class names: {@code "[L"}. */
	private static final String NON_PRIMITIVE_ARRAY_PREFIX = "[L";

	/**
	 * 空类数组
	 * A reusable empty class array constant. */
	private static final Class<?>[] EMPTY_CLASS_ARRAY = {};

	/**
	 * 包名分割符：'.'
	 * The package separator character: {@code '.'}. */
	private static final char PACKAGE_SEPARATOR = '.';

	/**
	 * 路径分割符:'/'
	 * The path separator character: {@code '/'}. */
	private static final char PATH_SEPARATOR = '/';

	/**
	 * 内部类分割符:'$'
	 * The inner class separator character: {@code '$'}. */
	private static final char INNER_CLASS_SEPARATOR = '$';

	/**
	 * CGLIB类分割符：'$$'
	 * The CGLIB class separator: {@code "$$"}. */
	public static final String CGLIB_CLASS_SEPARATOR = "$$";

	/**
	 * class文件后缀：'.class'
	 * The ".class" file suffix. */
	public static final String CLASS_FILE_SUFFIX = ".class";


	/**
	 * 使用原始包装类作为key和相应原始类作为value的映射
	 *
	 * Map with primitive wrapper type as key and corresponding primitive
	 * type as value, for example: Integer.class -> int.class.
	 */
	private static final Map<Class<?>, Class<?>> primitiveWrapperTypeMap = new IdentityHashMap<>(8);

	/**
	 * 使用原始类作为key和相应的原始包装类作为value的映射
	 *
	 * Map with primitive type as key and corresponding wrapper
	 * type as value, for example: int.class -> Integer.class.
	 */
	private static final Map<Class<?>, Class<?>> primitiveTypeToWrapperMap = new IdentityHashMap<>(8);

	/**
	 * 使用原始类名作为key和相应的原始类作为value的映射
	 *
	 * Map with primitive type name as key and corresponding primitive
	 * type as value, for example: "int" -> "int.class".
	 */
	private static final Map<String, Class<?>> primitiveTypeNameMap = new HashMap<>(32);

	/**
	 * 用通用的Java语言类名作为key和对应的类作为vale的映射。主要用于远程调用的有效反序列化
	 *
	 * Map with common Java language class name as key and corresponding Class as value.
	 * Primarily for efficient deserialization of remote invocations.
	 */
	private static final Map<String, Class<?>> commonClassCache = new HashMap<>(64);

	/**
	 * 搜索'primary'用户级接口时应该忽略的通用Java语言接口
	 *
	 * Common Java language interfaces which are supposed to be ignored
	 * when searching for 'primary' user-level interfaces.
	 */
	private static final Set<Class<?>> javaLanguageInterfaces;

	/**
	 * 接口方法的缓存对象
	 *
	 * Cache for equivalent methods on an interface implemented by the declaring class.
	 */
	private static final Map<Method, Method> interfaceMethodCache = new ConcurrentReferenceHashMap<>(256);


	static {

		//将所有原始包装类和原始类添加到primitiveWrapperTypeMap
		primitiveWrapperTypeMap.put(Boolean.class, boolean.class);
		primitiveWrapperTypeMap.put(Byte.class, byte.class);
		primitiveWrapperTypeMap.put(Character.class, char.class);
		primitiveWrapperTypeMap.put(Double.class, double.class);
		primitiveWrapperTypeMap.put(Float.class, float.class);
		primitiveWrapperTypeMap.put(Integer.class, int.class);
		primitiveWrapperTypeMap.put(Long.class, long.class);
		primitiveWrapperTypeMap.put(Short.class, short.class);
		primitiveWrapperTypeMap.put(Void.class, void.class);

		// Map entry iteration is less expensive to initialize than forEach with lambdas
		// 与使用lamdbas的forEach相比,映射条目迭代的初始化成本更低
		// 遍历primitiveWrapperTypeMap
		for (Map.Entry<Class<?>, Class<?>> entry : primitiveWrapperTypeMap.entrySet()) {
			// 将primitiveWrapperTypeMap的每个条目存储的原始类和原始包装类添加到primitiveTypeToWrapperMap中
			primitiveTypeToWrapperMap.put(entry.getValue(), entry.getKey());
			// 注册entry的原始包装类到ClassUtils的缓存中
			registerCommonClasses(entry.getKey());
		}

		// 定义一个初始容量为32的HashSet，用于存储原始类型
		Set<Class<?>> primitiveTypes = new HashSet<>(32);
		// 将primitiveWrapperTypeMap存储的原始类型添加到primitiveTypes中
		primitiveTypes.addAll(primitiveWrapperTypeMap.values());
		// 将所有原始类型数组添加到primitiveTypes中
		Collections.addAll(primitiveTypes, boolean[].class, byte[].class, char[].class,
				double[].class, float[].class, int[].class, long[].class, short[].class);
		// 遍历primitveType
		for (Class<?> primitiveType : primitiveTypes) {
			// 取出primitiveType的类名作为key,primitiveType本身作为value添加到primitiveTypeNameMap中
			primitiveTypeNameMap.put(primitiveType.getName(), primitiveType);
		}

		// 注册原始包装类型数组到ClassUtils的缓存中
		registerCommonClasses(Boolean[].class, Byte[].class, Character[].class, Double[].class,
				Float[].class, Integer[].class, Long[].class, Short[].class);
		// 注册Number,String,Class,Object类以及数组类型到ClassUtils的缓存中
		registerCommonClasses(Number.class, Number[].class, String.class, String[].class,
				Class.class, Class[].class, Object.class, Object[].class);
		// 注册常用异常类到ClassUtils的缓存中
		registerCommonClasses(Throwable.class, Exception.class, RuntimeException.class,
				Error.class, StackTraceElement.class, StackTraceElement[].class);
		// 注册枚举类，常用集合类，迭代器类到ClassUtils缓存中
		registerCommonClasses(Enum.class, Iterable.class, Iterator.class, Enumeration.class,
				Collection.class, List.class, Set.class, Map.class, Map.Entry.class, Optional.class);
		// 定义常用java语言接口数组，存放着序列化，反序列化，可关闭，自动关闭，克隆，比较接口
		Class<?>[] javaLanguageInterfaceArray = {Serializable.class, Externalizable.class,
				Closeable.class, AutoCloseable.class, Cloneable.class, Comparable.class};
		// 注册序列化，反序列化，可关闭，自动关闭，克隆，比较接口到ClassUtils缓存中
		registerCommonClasses(javaLanguageInterfaceArray);
		// 将序列化，反序列化，可关闭，自动关闭，克隆，比较接口添加到javaLanguageInterfaces中
		javaLanguageInterfaces = new HashSet<>(Arrays.asList(javaLanguageInterfaceArray));
	}


	/**
	 * 注册给定的通用类到ClassUtils的缓存中
	 *
	 * Register the given common classes with the ClassUtils cache.
	 */
	private static void registerCommonClasses(Class<?>... commonClasses) {
		// 遍历commonClasses
		for (Class<?> clazz : commonClasses) {
			// 将clazz的类名作为key,clazz作为value添加到commonClassCache中
			commonClassCache.put(clazz.getName(), clazz);
		}
	}

	/**
	 *  获取默认类加载器，一般返回线程上下文类加载器，没有就返回加载ClassUtils的类加载器，还是没有就返回系统类加载器，最后还是没有就返回null
	 *
	 * Return the default ClassLoader to use: typically the thread context
	 * ClassLoader, if available; the ClassLoader that loaded the ClassUtils
	 * class will be used as fallback.
	 * <p>Call this method if you intend to use the thread context ClassLoader
	 * in a scenario where you clearly prefer a non-null ClassLoader reference:
	 * for example, for class path resource loading (but not necessarily for
	 * {@code Class.forName}, which accepts a {@code null} ClassLoader
	 * reference as well).
	 * @return the default ClassLoader (only {@code null} if even the system
	 * ClassLoader isn't accessible)
	 * @see Thread#getContextClassLoader()
	 * @see ClassLoader#getSystemClassLoader()
	 */
	@Nullable
	public static ClassLoader getDefaultClassLoader() {
		// 定义类加载器变量c1
		ClassLoader cl = null;
		try {
			// 获取当前线程的上下文类加载器
			cl = Thread.currentThread().getContextClassLoader();
		}
		catch (Throwable ex) {
			// Cannot access thread context ClassLoader - falling back...
		}
		// 如果cl为null
		if (cl == null) {
			// No thread context class loader -> use class loader of this class.
			// 没有线程上下文类加载器 -> 使用这个类的类加载器
			// 获取ClassUtils的类加载器
			cl = ClassUtils.class.getClassLoader();
			// 如果c1还为null
			if (cl == null) {
				// getClassLoader() returning null indicates the bootstrap ClassLoader
				// getClassLoader() 返回null表示boostrap ClassLoader
				try {
					// 获取系统的类加载器
					cl = ClassLoader.getSystemClassLoader();
				}
				catch (Throwable ex) {
					// Cannot access system ClassLoader - oh well, maybe the caller can live with null...
				}
			}
		}
		return cl;
	}

	/**
	 * 如果需要,用环境的Bean ClassLoader覆盖线程上下文ClassLoader.即如果Bean ClassLoader已经不等于线程上下文ClassLoader
	 *
	 * Override the thread context ClassLoader with the environment's bean ClassLoader
	 * if necessary, i.e. if the bean ClassLoader is not equivalent to the thread
	 * context ClassLoader already.
	 * @param classLoaderToUse the actual ClassLoader to use for the thread context
	 * @return the original thread context ClassLoader, or {@code null} if not overridden
	 */
	@Nullable
	public static ClassLoader overrideThreadContextClassLoader(@Nullable ClassLoader classLoaderToUse) {
		// 获取当前线程
		Thread currentThread = Thread.currentThread();
		// 获取当前线程的ClassLoader
		ClassLoader threadContextClassLoader = currentThread.getContextClassLoader();
		// 如果classLoaderToUse不为null且classLoaderToUser不等于当前线程的ClassLoader
		if (classLoaderToUse != null && !classLoaderToUse.equals(threadContextClassLoader)) {
			// 设置当前线程的ClassLoader为classLoaderToUser
			currentThread.setContextClassLoader(classLoaderToUse);
			//返 回原来的线程上下文ClassLoader
			return threadContextClassLoader;
		}
		// 如果classLoaderToUser为null或者classLoaderToUser等于当前线程的ClassLoader
		else {
			return null;
		}
	}

	/**
	 * 使用classLoader加载name对应的Class对象
	 *
	 * Replacement for {@code Class.forName()} that also returns Class instances
	 * for primitives (e.g. "int") and array class names (e.g. "String[]").
	 * Furthermore, it is also capable of resolving inner class names in Java source
	 * style (e.g. "java.lang.Thread.State" instead of "java.lang.Thread$State").
	 * @param name the name of the Class
	 * @param classLoader the class loader to use
	 * (may be {@code null}, which indicates the default class loader)
	 * @return a class instance for the supplied name
	 * @throws ClassNotFoundException if the class was not found
	 * @throws LinkageError if the class file could not be loaded
	 * @see Class#forName(String, boolean, ClassLoader)
	 */
	public static Class<?> forName(String name, @Nullable ClassLoader classLoader)
			throws ClassNotFoundException, LinkageError {

		// 如果name为null，抛出异常
		Assert.notNull(name, "Name must not be null");

		// 如果name是个原始类型名，就获取其对应的Class
		Class<?> clazz = resolvePrimitiveClassName(name);
		// 如果clazz为null
		if (clazz == null) {
			// 从缓存Map中获取name对应的Class
			clazz = commonClassCache.get(name);
		}
		// 如果clazz不为null
		if (clazz != null) {
			// 直接返回clazz
			return clazz;
		}

		// "java.lang.String[]" style arrays
		// "java.lang.String[]" style arrays 'java.lang.String[]'样式数组，表示原始数组类名
		// 如果name是以'[]'结尾的
		if (name.endsWith(ARRAY_SUFFIX)) {
			// 截取出name'[]'前面的字符串赋值给elementClassName
			String elementClassName = name.substring(0, name.length() - ARRAY_SUFFIX.length());
			// 传入elementClassName递归本方法获取其Class
			Class<?> elementClass = forName(elementClassName, classLoader);
			// 新建一个elementClass类型长度为0的数组，然后获取其类型返回出去
			return Array.newInstance(elementClass, 0).getClass();
		}

		// "[Ljava.lang.String;" style arrays
		// 如果names是以'[L'开头且以';'结尾
		if (name.startsWith(NON_PRIMITIVE_ARRAY_PREFIX) && name.endsWith(";")) {
			// 截取出name'[L'到';'之间的字符串赋值给elementName
			String elementName = name.substring(NON_PRIMITIVE_ARRAY_PREFIX.length(), name.length() - 1);
			// 传入elementName递归本方法获取其Class
			Class<?> elementClass = forName(elementName, classLoader);
			// 新建一个elementClass类型长度为0的数组，然后获取其类型返回出去
			return Array.newInstance(elementClass, 0).getClass();
		}

		// "[[I" or "[[Ljava.lang.String;" style arrays
		// "[[I" or "[[Ljava.lang.String;" style arrays '[[I' 或者 '[[Ljava.lang.String;'样式数组，表示内部数组类名
		if (name.startsWith(INTERNAL_ARRAY_PREFIX)) {
			// 截取出name '['后面的字符串赋值给elementName
			String elementName = name.substring(INTERNAL_ARRAY_PREFIX.length());
			// 传入elementName递归本方法获取其Class
			Class<?> elementClass = forName(elementName, classLoader);
			// 新建一个elementClass类型长度为0的数组，然后获取其类型返回出去
			return Array.newInstance(elementClass, 0).getClass();
		}

		// 将classLoader赋值给clToUse变量
		ClassLoader clToUse = classLoader;
		// 如果clToUse为null
		if (clToUse == null) {
			// 获取默认类加载器，一般返回线程上下文类加载器，没有就返回加载ClassUtils的类加载器，
			// 还是没有就返回系统类加载器，最后还是没有就返回null
			clToUse = getDefaultClassLoader();
		}
		try {
			// 返回与给定的字符串名称相关联类或接口的Class对象。
			return Class.forName(name, false, clToUse);
		}
		catch (ClassNotFoundException ex) {
			// 如果找到不到类时
			// 获取最后一个包名分割符'.'的索引位置
			int lastDotIndex = name.lastIndexOf(PACKAGE_SEPARATOR);
			// 如果找到索引位置
			if (lastDotIndex != -1) {
				// 尝试将name转换成内部类名,innerClassName=name的包名+'$'+name的类名
				String innerClassName =
						name.substring(0, lastDotIndex) + INNER_CLASS_SEPARATOR + name.substring(lastDotIndex + 1);
				try {
					// 通过clToUse获取innerClassName对应的Class对象
					return Class.forName(innerClassName, false, clToUse);
				}
				catch (ClassNotFoundException ex2) {
					// Swallow - let original exception get through
				}
			}
			// 当将name转换成内部类名仍然获取不到Class对象时,抛出异常
			throw ex;
		}
	}

	/**
	 * 将给定的类名解析成Class实例，支持原始类型(如'int')和数组类型名(如'String[]').
	 *
	 * Resolve the given class name into a Class instance. Supports
	 * primitives (like "int") and array class names (like "String[]").
	 * <p>This is effectively equivalent to the {@code forName}
	 * method with the same arguments, with the only difference being
	 * the exceptions thrown in case of class loading failure.
	 * @param className the name of the Class
	 * @param classLoader the class loader to use
	 * (may be {@code null}, which indicates the default class loader)
	 * @return a class instance for the supplied name
	 * @throws IllegalArgumentException if the class name was not resolvable
	 * (that is, the class could not be found or the class file could not be loaded)
	 * @throws IllegalStateException if the corresponding class is resolvable but
	 * there was a readability mismatch in the inheritance hierarchy of the class
	 * (typically a missing dependency declaration in a Jigsaw module definition
	 * for a superclass or interface implemented by the class to be loaded here)
	 * @see #forName(String, ClassLoader)
	 */
	public static Class<?> resolveClassName(String className, @Nullable ClassLoader classLoader)
			throws IllegalArgumentException {

		try {
			// 使用classLoader加载className对应的Class对象
			return forName(className, classLoader);
		}
		catch (IllegalAccessError err) {
			// 类的继承层次结构中的可读性不匹配时，抛出异常
			throw new IllegalStateException("Readability mismatch in inheritance hierarchy of class [" +
					className + "]: " + err.getMessage(), err);
		}
		catch (LinkageError err) {
			// 无法解析类定义时，抛出异常
			throw new IllegalArgumentException("Unresolvable class definition for class [" + className + "]", err);
		}
		catch (ClassNotFoundException ex) {
			// 没有找到对应类时，抛出异常
			throw new IllegalArgumentException("Could not find class [" + className + "]", ex);
		}
	}

	/**
	 * 确定是否存在{@code className}对应的 {@link Class}并且可以加载。
	 * 如果该类或者该类的依赖项不存在或者无法加载时，返回false
	 *
	 * Determine whether the {@link Class} identified by the supplied name is present
	 * and can be loaded. Will return {@code false} if either the class or
	 * one of its dependencies is not present or cannot be loaded.
	 * @param className the name of the class to check
	 * @param classLoader the class loader to use
	 * (may be {@code null} which indicates the default class loader)
	 * @return whether the specified class is present (including all of its
	 * superclasses and interfaces)
	 * @throws IllegalStateException if the corresponding class is resolvable but
	 * there was a readability mismatch in the inheritance hierarchy of the class
	 * (typically a missing dependency declaration in a Jigsaw module definition
	 * for a superclass or interface implemented by the class to be checked here)
	 */
	public static boolean isPresent(String className, @Nullable ClassLoader classLoader) {
		try {
			// 使用classLoader加载className对应的Class对象
			forName(className, classLoader);
			return true;
		}
		catch (IllegalAccessError err) {
			throw new IllegalStateException("Readability mismatch in inheritance hierarchy of class [" +
					className + "]: " + err.getMessage(), err);
		}
		catch (Throwable ex) {
			// Typically ClassNotFoundException or NoClassDefFoundError...
			return false;
		}
	}

	/**
	 * 检查给定的类对象在给定的类加载器中是否可见
	 *
	 * Check whether the given class is visible in the given ClassLoader.
	 * @param clazz the class to check (typically an interface)
	 * @param classLoader the ClassLoader to check against
	 * (may be {@code null} in which case this method will always return {@code true})
	 */
	public static boolean isVisible(Class<?> clazz, @Nullable ClassLoader classLoader) {
		// 如果classLoader为null
		if (classLoader == null) {
			// 直接返回true
			return true;
		}
		try {
			// 获取clazz的ClassLoader对象，如果与classLoader的同一个
			if (clazz.getClassLoader() == classLoader) {
				return true;
			}
		}
		catch (SecurityException ex) {
			// Fall through to loadable check below
		}

		// Visible if same Class can be loaded from given ClassLoader
		// 如果可以从给定的ClassLoader中加载相同的Class对象,就表示可见
		return isLoadable(clazz, classLoader);
	}

	/**
	 * 检查给定的类对象在给定的上下文中是否缓存安全，即判断是否由给定的类加载器或者给定的类加载的父级类加载器加载过
	 *
	 * Check whether the given class is cache-safe in the given context,
	 * i.e. whether it is loaded by the given ClassLoader or a parent of it.
	 * @param clazz the class to analyze
	 * @param classLoader the ClassLoader to potentially cache metadata in
	 * (may be {@code null} which indicates the system class loader)
	 */
	public static boolean isCacheSafe(Class<?> clazz, @Nullable ClassLoader classLoader) {
		Assert.notNull(clazz, "Class must not be null");
		try {
			// 获取clazz的类加载器
			ClassLoader target = clazz.getClassLoader();
			// Common cases
			// 如果target等于给定的类加载器或者target为null
			if (target == classLoader || target == null) {
				return true;
			}
			// 如果给定的类加载器为null
			if (classLoader == null) {
				return false;
			}
			// Check for match in ancestors -> positive
			// 检查是否匹配祖先 -> 肯定
			// 将给定的类加载器赋值给current
			ClassLoader current = classLoader;
			// 遍历,只要current不为null，
			while (current != null) {
				// 获取当前类加载器的父级类加载器并赋值给current
				current = current.getParent();
				// 如果当前类加载器是clazz的类加载器
				if (current == target) {
					return true;
				}
			}
			// Check for match in children -> negative
			// 检查是否匹配子级 -> 否定
			// 遍历，只要target不为null
			while (target != null) {
				// 获取target的父级类加载器并赋值给target
				target = target.getParent();
				// 如果target是给定的类加载器
				if (target == classLoader) {
					return false;
				}
			}
		}
		catch (SecurityException ex) {
			// Fall through to loadable check below
		}

		// Fallback for ClassLoaders without parent/child relationship:
		// safe if same Class can be loaded from given ClassLoader
		// 没有父/子关系的类加载器后备
		// 如果可以从给定的类加载器中加载同一个Class对象，则表示安全。
		// 如果classLoader不为null且如果clazz可在classloader中加载，则返回true；否则返回false
		return (classLoader != null && isLoadable(clazz, classLoader));
	}

	/**
	 * 检查给定的class对象是否可在给定的类加载器中加载
	 *
	 * Check whether the given class is loadable in the given ClassLoader.
	 * @param clazz the class to check (typically an interface)
	 * @param classLoader the ClassLoader to check against
	 * @since 5.0.6
	 */
	private static boolean isLoadable(Class<?> clazz, ClassLoader classLoader) {
		try {
			// 获取clazz的全类名让classLoader加载对应的Class对象，如果该class对象等于clazz，返回true;否则返回false
			return (clazz == classLoader.loadClass(clazz.getName()));
			// Else: different class with same name found
		}
		catch (ClassNotFoundException ex) {
			// No corresponding class found at all
			// 完全找不到对应的类对象,直接返回false
			return false;
		}
	}

	/**
	 *  将给定的类名解析为原始类，如果合适，根据JVM对原始类的命名规则
	 *
	 * Resolve the given class name as primitive class, if appropriate,
	 * according to the JVM's naming rules for primitive classes.
	 * <p>Also supports the JVM's internal class names for primitive arrays.
	 * Does <i>not</i> support the "[]" suffix notation for primitive arrays;
	 * this is only supported by {@link #forName(String, ClassLoader)}.
	 * @param name the name of the potentially primitive class
	 * @return the primitive class, or {@code null} if the name does not denote
	 * a primitive class or primitive array class
	 */
	@Nullable
	public static Class<?> resolvePrimitiveClassName(@Nullable String name) {
		Class<?> result = null;
		// Most class names will be quite long, considering that they
		// SHOULD sit in a package, so a length check is worthwhile.
		// 考虑到应该将它们放在包装中，大多数类名都将很长，因此进行长度检查是值得的
		// 如果name不为null 且 name的长度小于等于8
		if (name != null && name.length() <= 7) {
			// Could be a primitive - likely.
			// 可能是原始类型 - 可能的
			// 从primitiveTypeNameMap中获取name对应的Class
			result = primitiveTypeNameMap.get(name);
		}
		return result;
	}

	/**
	 * 检查给定的类对象是否表示原始包装类
	 *
	 * Check if the given class represents a primitive wrapper,
	 * i.e. Boolean, Byte, Character, Short, Integer, Long, Float, Double, or
	 * Void.
	 * @param clazz the class to check
	 * @return whether the given class is a primitive wrapper class
	 */
	public static boolean isPrimitiveWrapper(Class<?> clazz) {
		Assert.notNull(clazz, "Class must not be null");
		// 判断clazz在primitiveWrapperTypeMap中是否存在
		return primitiveWrapperTypeMap.containsKey(clazz);
	}

	/**
	 * Check if the given class represents a primitive (i.e. boolean, byte,
	 * char, short, int, long, float, or double), {@code void}, or a wrapper for
	 * those types (i.e. Boolean, Byte, Character, Short, Integer, Long, Float,
	 * Double, or Void).
	 * @param clazz the class to check
	 * @return {@code true} if the given class represents a primitive, void, or
	 * a wrapper class
	 */
	public static boolean isPrimitiveOrWrapper(Class<?> clazz) {
		Assert.notNull(clazz, "Class must not be null");
		return (clazz.isPrimitive() || isPrimitiveWrapper(clazz));
	}

	/**
	 * 检查给定的类对象是否是原始类型
	 *
	 * Check if the given class represents an array of primitives,
	 * i.e. boolean, byte, char, short, int, long, float, or double.
	 * @param clazz the class to check
	 * @return whether the given class is a primitive array class
	 */
	public static boolean isPrimitiveArray(Class<?> clazz) {
		Assert.notNull(clazz, "Class must not be null");
		// 如果clazz是原始类型或者clazz是原始包装类，返回true；否则返回false
		return (clazz.isArray() && clazz.getComponentType().isPrimitive());
	}

	/**
	 * 检查给定的类对象是否是原始类型数组
	 *
	 * Check if the given class represents an array of primitive wrappers,
	 * i.e. Boolean, Byte, Character, Short, Integer, Long, Float, or Double.
	 * @param clazz the class to check
	 * @return whether the given class is a primitive wrapper array class
	 */
	public static boolean isPrimitiveWrapperArray(Class<?> clazz) {
		Assert.notNull(clazz, "Class must not be null");
		// 如果clazz是数组类型且clazz的元素类型是原始类型
		return (clazz.isArray() && isPrimitiveWrapper(clazz.getComponentType()));
	}

	/**
	 * 如果给定的类是原始类型，则对其进行解析，返回对应原始类型的原始包装类
	 *
	 * Resolve the given class if it is a primitive class,
	 * returning the corresponding primitive wrapper type instead.
	 * @param clazz the class to check
	 * @return the original class, or a primitive wrapper for the original primitive type
	 */
	public static Class<?> resolvePrimitiveIfNecessary(Class<?> clazz) {
		Assert.notNull(clazz, "Class must not be null");
		// 如果clazz是原始类型且clazz不是void,返回在pritiveTypeToWrapperMap对应clazz的原始包装类，否则直接返回clazz
		return (clazz.isPrimitive() && clazz != void.class ? primitiveTypeToWrapperMap.get(clazz) : clazz);
	}

	/**
	 *   检查是否可以将右侧类型分配给左侧类型，假设可以通过反射进行设置。将原始包装类视为可分配给相应的原始类型。
	 *
	 * Check if the right-hand side type may be assigned to the left-hand side
	 * type, assuming setting by reflection. Considers primitive wrapper
	 * classes as assignable to the corresponding primitive types.
	 * @param lhsType the target type
	 * @param rhsType the value type that should be assigned to the target type
	 * @return if the target type is assignable from the value type
	 * @see TypeUtils#isAssignable(java.lang.reflect.Type, java.lang.reflect.Type)
	 */
	public static boolean isAssignable(Class<?> lhsType, Class<?> rhsType) {
		Assert.notNull(lhsType, "Left-hand side type must not be null");
		Assert.notNull(rhsType, "Right-hand side type must not be null");
		// isAssignableFrom:从类继承的角度去判断,判断是否为某个类的父类或其本身.(父类.class.isAssignableFrom(子类.class))
		// 如果lhsType是否rhsType的父类或其本身
		if (lhsType.isAssignableFrom(rhsType)) {
			return true;
		}
		// 如果lhsType是原始类型
		if (lhsType.isPrimitive()) {
			// 获取rhsType在primitiveWrapperTypeMap对应的原始类型
			Class<?> resolvedPrimitive = primitiveWrapperTypeMap.get(rhsType);
			// 如果lhsType是lhsType的原始类型
			return (lhsType == resolvedPrimitive);
		}
		// 如果lhsType不是原始类型
		else {
			// 获取rhsType在primitiveWrapperTypeMap对应的原始类型
			Class<?> resolvedWrapper = primitiveTypeToWrapperMap.get(rhsType);
			// 如果resolvedWrapper不为null且lhsType是resolvedWrpper的父类或其本身
			return (resolvedWrapper != null && lhsType.isAssignableFrom(resolvedWrapper));
		}
	}

	/**
	 * 根据给定值确定给定类是否可分配，假设通过反射进行设置。将原始包装类视为可分配给相应的原始类型。
	 *
	 * Determine if the given type is assignable from the given value,
	 * assuming setting by reflection. Considers primitive wrapper classes
	 * as assignable to the corresponding primitive types.
	 * @param type the target type
	 * @param value the value that should be assigned to the type
	 * @return if the type is assignable from the value
	 */
	public static boolean isAssignableValue(Class<?> type, @Nullable Object value) {
		Assert.notNull(type, "Type must not be null");
		// 如果value不为null,获取value的类型，根据type确定value的类型是否可分配;否则只有type不是原始类型,返回true；否则返回false
		return (value != null ? isAssignable(type, value.getClass()) : !type.isPrimitive());
	}

	/**
	 * 转换 '/'-基于资源路径 为 '.'-基于完全限定类名
	 *
	 * Convert a "/"-based resource path to a "."-based fully qualified class name.
	 * @param resourcePath the resource path pointing to a class
	 * @return the corresponding fully qualified class name
	 */
	public static String convertResourcePathToClassName(String resourcePath) {
		Assert.notNull(resourcePath, "Resource path must not be null");
		// 将resourcePath的'/'替换成'.'并返回出去
		return resourcePath.replace(PATH_SEPARATOR, PACKAGE_SEPARATOR);
	}

	/**
	 * 转换 '.' - 基于完全限定类名 为 '/' -基于资源路径
	 *
	 * Convert a "."-based fully qualified class name to a "/"-based resource path.
	 * @param className the fully qualified class name
	 * @return the corresponding resource path, pointing to the class
	 */
	public static String convertClassNameToResourcePath(String className) {
		Assert.notNull(className, "Class name must not be null");
		// 将className的'.'替换成'/'，并返回出去
		return className.replace(PACKAGE_SEPARATOR, PATH_SEPARATOR);
	}

	/**
	 * 取出{@code clazz}的包名，转换成资源路径，再加上{@code resourceName},然后返回出去。
	 *
	 * Return a path suitable for use with {@code ClassLoader.getResource}
	 * (also suitable for use with {@code Class.getResource} by prepending a
	 * slash ('/') to the return value). Built by taking the package of the specified
	 * class file, converting all dots ('.') to slashes ('/'), adding a trailing slash
	 * if necessary, and concatenating the specified resource name to this.
	 * <br/>As such, this function may be used to build a path suitable for
	 * loading a resource file that is in the same package as a class file,
	 * although {@link org.springframework.core.io.ClassPathResource} is usually
	 * even more convenient.
	 * @param clazz the Class whose package will be used as the base
	 * @param resourceName the resource name to append. A leading slash is optional.
	 * @return the built-up resource path
	 * @see ClassLoader#getResource
	 * @see Class#getResource
	 */
	public static String addResourcePathToPackagePath(Class<?> clazz, String resourceName) {
		Assert.notNull(resourceName, "Resource name must not be null");
		// 如果resourceName不是以'/'开头
		if (!resourceName.startsWith("/")) {
			// 提取出clazz的包名,并转换成资源路径，然后加上'/',再加上resourceName，然后返回出去
			return classPackageAsResourcePath(clazz) + '/' + resourceName;
		}
		// 提取出clazz的包名,并转换成资源路径，在加上resourceName，然后返回出去
		return classPackageAsResourcePath(clazz) + resourceName;
	}

	/**
	 * 提取出{@code clazz}的包名,并将包名的'.'替换成'/'然后返回出去
	 *
	 * Given an input class object, return a string which consists of the
	 * class's package name as a pathname, i.e., all dots ('.') are replaced by
	 * slashes ('/'). Neither a leading nor trailing slash is added. The result
	 * could be concatenated with a slash and the name of a resource and fed
	 * directly to {@code ClassLoader.getResource()}. For it to be fed to
	 * {@code Class.getResource} instead, a leading slash would also have
	 * to be prepended to the returned value.
	 * @param clazz the input class. A {@code null} value or the default
	 * (empty) package will result in an empty string ("") being returned.
	 * @return a path which represents the package name
	 * @see ClassLoader#getResource
	 * @see Class#getResource
	 */
	public static String classPackageAsResourcePath(@Nullable Class<?> clazz) {
		// 如果clazz为null
		if (clazz == null) {
			// 直接返回空字符串
			return "";
		}
		// 获取类全名
		String className = clazz.getName();
		// 获取className的最后一个'.'索引位置
		int packageEndIndex = className.lastIndexOf(PACKAGE_SEPARATOR);
		// 如果没有找到索引位置
		if (packageEndIndex == -1) {
			// 直接返回空字符串
			return "";
		}
		// 截取出packageEndIndex之前的字符串作为包名
		String packageName = className.substring(0, packageEndIndex);
		// 将packageName的'.'替换成'/'然后返回出去
		return packageName.replace(PACKAGE_SEPARATOR, PATH_SEPARATOR);
	}

	/**
	 * 构建一个字符串，该字符串给定的数组中的类/接口名组成
	 *
	 * Build a String that consists of the names of the classes/interfaces
	 * in the given array.
	 * <p>Basically like {@code AbstractCollection.toString()}, but stripping
	 * the "class "/"interface " prefix before every class name.
	 * @param classes an array of Class objects
	 * @return a String of form "[com.foo.Bar, com.foo.Baz]"
	 * @see java.util.AbstractCollection#toString()
	 */
	public static String classNamesToString(Class<?>... classes) {
		// classes转换成List集合,调用classNamesToString(Collection)方法然后返回出去
		return classNamesToString(Arrays.asList(classes));
	}

	/**
	 * 构建一个字符串，该字符串给定的数组中的类/接口名组成
	 *
	 * Build a String that consists of the names of the classes/interfaces
	 * in the given collection.
	 * <p>Basically like {@code AbstractCollection.toString()}, but stripping
	 * the "class "/"interface " prefix before every class name.
	 * @param classes a Collection of Class objects (may be {@code null})
	 * @return a String of form "[com.foo.Bar, com.foo.Baz]"
	 * @see java.util.AbstractCollection#toString()
	 */
	public static String classNamesToString(@Nullable Collection<Class<?>> classes) {
		// 如果clases为null或者classes是空集合
		if (CollectionUtils.isEmpty(classes)) {
			// 直接返回'[]'
			return "[]";
		}
		// StringJsoner是Java8新出的一个类，用于构造由分隔符分隔的字符序列，并可选择性地从提供的前缀开始和
		// 以提供的后缀结尾。省的我们开发人员再次通过StringBuffer或者StingBuilder拼接。
		StringJoiner stringJoiner = new StringJoiner(", ", "[", "]");
		// 遍历classes
		for (Class<?> clazz : classes) {
			// 获取clazz的类全名，然后添加到stringJoiner中
			stringJoiner.add(clazz.getName());
		}
		//输出拼装后的字符串
		return stringJoiner.toString();
	}

	/**
	 * 将给定的Collection对象复制到元素类型为{@code Class}的数组中
	 *
	 * Copy the given {@code Collection} into a {@code Class} array.
	 * <p>The {@code Collection} must contain {@code Class} elements only.
	 * @param collection the {@code Collection} to copy
	 * @return the {@code Class} array
	 * @since 3.1
	 * @see StringUtils#toStringArray
	 */
	public static Class<?>[] toClassArray(@Nullable Collection<Class<?>> collection) {
		// collection对象的元素复制到元素类型为{@code Class}的数组中,传入的数组长度小于collection的长度时，
		// 会自动进行扩容
		return (!CollectionUtils.isEmpty(collection) ? collection.toArray(EMPTY_CLASS_ARRAY) : EMPTY_CLASS_ARRAY);
	}

	/**
	 * 以数组形式返回给定实现的所有接口，包括超类实现的接口
	 *
	 * Return all interfaces that the given instance implements as an array,
	 * including ones implemented by superclasses.
	 * @param instance the instance to analyze for interfaces
	 * @return all interfaces that the given instance implements as an array
	 */
	public static Class<?>[] getAllInterfaces(Object instance) {
		// 如果instance为null，抛出异常
		Assert.notNull(instance, "Instance must not be null");
		return getAllInterfacesForClass(instance.getClass());
	}

	/**
	 * 以数组形式返回给定实现的所有接口，包括超类实现的接口
	 *
	 * Return all interfaces that the given class implements as an array,
	 * including ones implemented by superclasses.
	 * <p>If the class itself is an interface, it gets returned as sole interface.
	 * @param clazz the class to analyze for interfaces
	 * @return all interfaces that the given object implements as an array
	 */
	public static Class<?>[] getAllInterfacesForClass(Class<?> clazz) {
		return getAllInterfacesForClass(clazz, null);
	}

	/**
	 * 以数组形式返回给定实现的所有接口，包括超类实现的接口
	 *
	 * Return all interfaces that the given class implements as an array,
	 * including ones implemented by superclasses.
	 * <p>If the class itself is an interface, it gets returned as sole interface.
	 * @param clazz the class to analyze for interfaces
	 * @param classLoader the ClassLoader that the interfaces need to be visible in
	 * (may be {@code null} when accepting all declared interfaces)
	 * @return all interfaces that the given object implements as an array
	 */
	public static Class<?>[] getAllInterfacesForClass(Class<?> clazz, @Nullable ClassLoader classLoader) {
		// 调用getAllInterfacesForClassAsSet(clazz, classLoader)方法，收集clazz的所有对classLoader可见的接口，
		// 包括超类实现的接口,得到Set对象，将对Set转换成Class对象数组
		return toClassArray(getAllInterfacesForClassAsSet(clazz, classLoader));
	}

	/**
	 * 以数组形式返回给定实现的所有接口，包括超类实现的接口
	 *
	 * Return all interfaces that the given instance implements as a Set,
	 * including ones implemented by superclasses.
	 * @param instance the instance to analyze for interfaces
	 * @return all interfaces that the given instance implements as a Set
	 */
	public static Set<Class<?>> getAllInterfacesAsSet(Object instance) {
		Assert.notNull(instance, "Instance must not be null");
		// 获取instance的Class对象，再获取该Class对象的所有接口，包括超类实现的接口，以Set形式返回出去
		return getAllInterfacesForClassAsSet(instance.getClass());
	}

	/**
	 * 以Set形式返回给定实现的所有接口，包括超类实现的接口
	 *
	 * Return all interfaces that the given class implements as a Set,
	 * including ones implemented by superclasses.
	 * <p>If the class itself is an interface, it gets returned as sole interface.
	 * @param clazz the class to analyze for interfaces
	 * @return all interfaces that the given object implements as a Set
	 */
	public static Set<Class<?>> getAllInterfacesForClassAsSet(Class<?> clazz) {
		return getAllInterfacesForClassAsSet(clazz, null);
	}

	/**
	 * 以Set形式返回给定实现的所有接口，包括超类实现的接口
	 *
	 * Return all interfaces that the given class implements as a Set,
	 * including ones implemented by superclasses.
	 * <p>If the class itself is an interface, it gets returned as sole interface.
	 * @param clazz the class to analyze for interfaces
	 * @param classLoader the ClassLoader that the interfaces need to be visible in
	 * (may be {@code null} when accepting all declared interfaces)
	 * @return all interfaces that the given object implements as a Set
	 */
	public static Set<Class<?>> getAllInterfacesForClassAsSet(Class<?> clazz, @Nullable ClassLoader classLoader) {
		Assert.notNull(clazz, "Class must not be null");
		// 如果clazz是接口且clazz在给定的classLoader中是可见的
		if (clazz.isInterface() && isVisible(clazz, classLoader)) {
			// 返回一个不可变只包含clazz的Set对象
			return Collections.singleton(clazz);
		}
		// LinkedHashSet:是一种按照插入顺序维护集合中条目的链表。这允许对集合进行插入顺序迭代。
		// 也就是说，当使用迭代器循环遍历LinkedHashSet时，元素将按插入顺序返回。
		// 然后将哈希码用作存储与键相关联的数据的索引。将键转换为其哈希码是自动执行的。
		// 定义一个用于存储接口类对象的Set
		Set<Class<?>> interfaces = new LinkedHashSet<>();
		// 将clazz赋值给current,表示当前类对象
		Class<?> current = clazz;
		// 遍历，只要current不为null
		while (current != null) {
			// 获取current的所有接口
			Class<?>[] ifcs = current.getInterfaces();
			// 遍历ifcs
			for (Class<?> ifc : ifcs) {
				// 如果ifc在给定的classLoader中是可见的
				if (isVisible(ifc, classLoader)) {
					// 将ifc添加到interfaces中
					interfaces.add(ifc);
				}
			}
			// 获取current的父类重新赋值给current
			current = current.getSuperclass();
		}
		// 返回存储接口类对象的Set
		return interfaces;
	}

	/**
	 * 为给定的接口创建一个复合接口类，在一个类中实现给定接口
	 *
	 * Create a composite interface Class for the given interfaces,
	 * implementing the given interfaces in one single Class.
	 * <p>This implementation builds a JDK proxy class for the given interfaces.
	 * @param interfaces the interfaces to merge
	 * @param classLoader the ClassLoader to create the composite Class in
	 * @return the merged interface as Class
	 * @throws IllegalArgumentException if the specified interfaces expose
	 * conflicting method signatures (or a similar constraint is violated)
	 * @see java.lang.reflect.Proxy#getProxyClass
	 */
	@SuppressWarnings("deprecation")  // on JDK 9
	public static Class<?> createCompositeInterface(Class<?>[] interfaces, @Nullable ClassLoader classLoader) {
		Assert.notEmpty(interfaces, "Interface array must not be empty");
		// 创建动态代理类，得到的Class对象可以通过获取InvocationHandler类型的构造函数进行实例化代理对象
		return Proxy.getProxyClass(classLoader, interfaces);
	}

	/**
	 * 确定给定类的共同祖先类(如果有)
	 *
	 * introspect:中文意思：内省，在计算机科学中，内省是指计算机程序在运行时（Run time）
	 * 检查对象（Object）类型的一种能力，通常也可以称作运行时类型检查。不应该将内省和反射混淆。
	 * 对于内省，反射更进一步，是指计算机程序在运行时（Run time）可以访问、检测和修改它本身状态或行为的一种能力。
	 *
	 *
	 * Determine the common ancestor of the given classes, if any.
	 * @param clazz1 the class to introspect
	 * @param clazz2 the other class to introspect
	 * @return the common ancestor (i.e. common superclass, one interface
	 * extending the other), or {@code null} if none found. If any of the
	 * given classes is {@code null}, the other class will be returned.
	 * @since 3.2.6
	 */
	@Nullable
	public static Class<?> determineCommonAncestor(@Nullable Class<?> clazz1, @Nullable Class<?> clazz2) {
		//如果clazz1为null
		if (clazz1 == null) {
			//直接返回clazz2
			return clazz2;
		}
		//如果clazz2为null
		if (clazz2 == null) {
			//直接返回clazz1
			return clazz1;
		}
		//如果clazz1是clazz2的父类或是其本身
		if (clazz1.isAssignableFrom(clazz2)) {
			//直接返回clazz1
			return clazz1;
		}
		//如果clazz2是clazz1的父类或是其本身
		if (clazz2.isAssignableFrom(clazz1)) {
			//直接返回clazz2
			return clazz2;
		}
		//将clazz1赋值给ancestor，表示当前祖先类
		Class<?> ancestor = clazz1;
		//循环，只要ancestor不是clazz2的父类或是其本身
		do {
			//获取ancestor的父类重新赋值给ancestor
			ancestor = ancestor.getSuperclass();
			//如果ancesstor为null或者ancestor为Object
			if (ancestor == null || Object.class == ancestor) {
				//直接返回null
				return null;
			}
		}
		while (!ancestor.isAssignableFrom(clazz2));
		//返回当前祖先类
		return ancestor;
	}

	/**
	 * 确定给定的接口是否是一个公共Java语言接口
	 *
	 * Determine whether the given interface is a common Java language interface:
	 * {@link Serializable}, {@link Externalizable}, {@link Closeable}, {@link AutoCloseable},
	 * {@link Cloneable}, {@link Comparable} - all of which can be ignored when looking
	 * for 'primary' user-level interfaces. Common characteristics: no service-level
	 * operations, no bean property methods, no default methods.
	 * @param ifc the interface to check
	 * @since 5.0.3
	 */
	public static boolean isJavaLanguageInterface(Class<?> ifc) {
		// 如果ifc在javaLanguageInterfaces中存在
		return javaLanguageInterfaces.contains(ifc);
	}

	/**
	 * 确定所提供的类是否为内部类，即一个封闭类的非静态成员
	 *
	 * Determine if the supplied class is an <em>inner class</em>,
	 * i.e. a non-static member of an enclosing class.
	 * @return {@code true} if the supplied class is an inner class
	 * @since 5.0.5
	 * @see Class#isMemberClass()
	 */
	public static boolean isInnerClass(Class<?> clazz) {
		// 如果clazz是成员类且clazz没有被静态修饰符修饰
		return (clazz.isMemberClass() && !Modifier.isStatic(clazz.getModifiers()));
	}

	/**
	 * 判断给定的对象是否是一个CGLIB代理类
	 *
	 * Check whether the given object is a CGLIB proxy.
	 * @param object the object to check
	 * @see #isCglibProxyClass(Class)
	 * see org.springframework.aop.support.AopUtils#isCglibProxy(Object)
	 * @deprecated as of 5.2, in favor of custom (possibly narrower) checks
	 */
	@Deprecated
	public static boolean isCglibProxy(Object object) {
		// 获取object的类对象，判断类对象是否是CGLIB代理类
		return isCglibProxyClass(object.getClass());
	}

	/**
	 * 检查指定类是否是一个CGLIB生成的类
	 *
	 * Check whether the specified class is a CGLIB-generated class.
	 * @param clazz the class to check
	 * @see #isCglibProxyClassName(String)
	 * @deprecated as of 5.2, in favor of custom (possibly narrower) checks
	 */
	@Deprecated
	public static boolean isCglibProxyClass(@Nullable Class<?> clazz) {
		// 如果clazz不为null且获取clazz的全类名是CGLIB代理类的全类类名时，返回true；否则返回false
		return (clazz != null && isCglibProxyClassName(clazz.getName()));
	}

	/**
	 * 检查指定全类名是否是一个CGLIB生成的类
	 *
	 * Check whether the specified class name is a CGLIB-generated class.
	 * @param className the class name to check
	 * @deprecated as of 5.2, in favor of custom (possibly narrower) checks
	 */
	@Deprecated
	public static boolean isCglibProxyClassName(@Nullable String className) {
		// 如果className不为null且className包含'$$'的字符串，返回true
		return (className != null && className.contains(CGLIB_CLASS_SEPARATOR));
	}

	/**
	 * 返回给定实例的用户定义类：通常给定的实例是简单的类，但是在一个CGLIB生成的子类情况下，则是原始类
	 *
	 * Return the user-defined class for the given instance: usually simply
	 * the class of the given instance, but the original class in case of a
	 * CGLIB-generated subclass.
	 * @param instance the instance to check
	 * @return the user-defined class
	 */
	public static Class<?> getUserClass(Object instance) {
		Assert.notNull(instance, "Instance must not be null");
		//获取instance的类对象，得到用户定义的类，但是在一个CGLIB生成的子类情况下，则是原始类
		return getUserClass(instance.getClass());
	}

	/**
	 *  返回给定实例的用户定义类：通常给定的实例是简单的类，但是在一个CGLIB生成的子类情况下，则是原始类
	 *
	 * Return the user-defined class for the given class: usually simply the given
	 * class, but the original class in case of a CGLIB-generated subclass.
	 * @param clazz the class to check
	 * @return the user-defined class
	 */
	public static Class<?> getUserClass(Class<?> clazz) {
		// 如果clazz的全类名包含'$$'字符串，表示它有可能是GGLIB生成的子类
		if (clazz.getName().contains(CGLIB_CLASS_SEPARATOR)) {
			// 获取clazz的父类
			Class<?> superclass = clazz.getSuperclass();
			// 如果superclass不为null且superclass不是Object
			if (superclass != null && superclass != Object.class) {
				// 直接返回父类
				return superclass;
			}
		}
		// 直接返回检查类
		return clazz;
	}

	/**
	 * 返回给定对象的类的一个描述名：通常是简单类名，但是数组就是元素类型名+'[]'，和一个JDK代理的已实现接口的附加列表
	 *
	 * Return a descriptive name for the given object's type: usually simply
	 * the class name, but component type class name + "[]" for arrays,
	 * and an appended list of implemented interfaces for JDK proxies.
	 * @param value the value to introspect
	 * @return the qualified name of the class
	 */
	@Nullable
	public static String getDescriptiveType(@Nullable Object value) {
		//如果value为null
		if (value == null) {
			//直接返回null
			return null;
		}
		//获取value的类
		Class<?> clazz = value.getClass();
		//如果clazz是JDK代理类
		if (Proxy.isProxyClass(clazz)) {
			//拼装前缀 ，类全名+'implementing'
			String prefix = clazz.getName() + " implementing ";
			//新建一个StringJoiner对象，每个添加的字符串都会加入prefix前缀和''后缀，
			// 并使用','分割每个添加进来字符串
			StringJoiner result = new StringJoiner(",", prefix, "");
			//遍历clazz的所有接口
			for (Class<?> ifc : clazz.getInterfaces()) {
				//将ifc的类全名添加到result中
				result.add(ifc.getName());
			}
			//获取拼装好的字符串
			return result.toString();
		}
		else {
			//getTypeName:数组->java.lang.String[],成员内部类->lang.reflect.AAA$BBB,匿名内部类->lang.reflect.AAA$4
			//	普通类->lang.reflect.AAA,基本数据类型-> int
			return clazz.getTypeName();
		}
	}

	/**
	 * 检查给定类是否匹配用户指定全类名
	 *
	 * Check whether the given class matches the user-specified type name.
	 * @param clazz the class to check
	 * @param typeName the type name to match
	 */
	public static boolean matchesTypeName(Class<?> clazz, @Nullable String typeName) {
		// getTypeName:数组->java.lang.String[],成员内部类->lang.reflect.AAA$BBB,匿名内部类->lang.reflect.AAA$4
		// 普通类->lang.reflect.AAA,基本数据类型-> int
		// 如果 typeName不为null且(获取clazz的类型名等于typName或者获取clazz的简单类名等 typeName)返回true；否则返回false
		return (typeName != null &&
				(typeName.equals(clazz.getTypeName()) || typeName.equals(clazz.getSimpleName())));
	}

	/**
	 * 获取没有合格包名的类名
	 *
	 * Get the class name without the qualified package name.
	 * @param className the className to get the short name for
	 * @return the class name of the class without the package name
	 * @throws IllegalArgumentException if the className is empty
	 */
	public static String getShortName(String className) {
		Assert.hasLength(className, "Class name must not be empty");
		// 获取className最后一个'.'的索引位置
		int lastDotIndex = className.lastIndexOf(PACKAGE_SEPARATOR);
		// 获取className的'$$'的索引位置
		int nameEndIndex = className.indexOf(CGLIB_CLASS_SEPARATOR);
		// 如果没有找到'$$'的索引位置
		if (nameEndIndex == -1) {
			// 将className的长度赋值给nameEndIndex
			nameEndIndex = className.length();
		}
		// 截取出'.'到nameEndIndex之间的字符串
		String shortName = className.substring(lastDotIndex + 1, nameEndIndex);
		// 将shortName的'$'替换成'.'重新赋值给shortName,表示将 AAA$BBB ->AAA.BBB
		shortName = shortName.replace(INNER_CLASS_SEPARATOR, PACKAGE_SEPARATOR);
		return shortName;
	}

	/**
	 *  获取没有合格包名的类名
	 *
	 * Get the class name without the qualified package name.
	 * @param clazz the class to get the short name for
	 * @return the class name of the class without the package name
	 */
	public static String getShortName(Class<?> clazz) {
		// 获取clazz合格的全类名，再获取没有合格包名的类名
		return getShortName(getQualifiedName(clazz));
	}

	/**
	 * 以没有大写的JavaBeans属性格式返回java类的短字符串名.如果是内部类,则去除外部类名称
	 *
	 * Return the short string name of a Java class in uncapitalized JavaBeans
	 * property format. Strips the outer class name in case of an inner class.
	 * @param clazz the class
	 * @return the short name rendered in a standard JavaBeans property format
	 * @see java.beans.Introspector#decapitalize(String)
	 */
	public static String getShortNameAsProperty(Class<?> clazz) {
		// 获取没有合格包名的类名
		String shortName = getShortName(clazz);
		// 获取shorName的最后一个'.'的索引位置
		int dotIndex = shortName.lastIndexOf(PACKAGE_SEPARATOR);
		// 如果找到'.'的索引位置，就截取出'.'后面的字符串,重新赋值给shortName
		shortName = (dotIndex != -1 ? shortName.substring(dotIndex + 1) : shortName);
		// Introspector.decapitalize:获得一个字符串并将它转换成普通 java 变量名称大写形式的实用工具方法。
		// 这通常意味着将首字符从大写转换成小写，但在（不平常的）特殊情况下，当有多个字符且第一个和第二个字符都是大写字符时，不执行任何操作。
		// 因此 "STRINGS" 变成 "STRINGS"，"STing" 变成 "STing"，"Sting" 变成 "sting",但 "string" 仍然是 "string"。
		return Introspector.decapitalize(shortName);
	}

	/**
	 * 确定类文件的名，相对于包含的包
	 *
	 * Determine the name of the class file, relative to the containing
	 * package: e.g. "String.class"
	 * @param clazz the class
	 * @return the file name of the ".class" file
	 */
	public static String getClassFileName(Class<?> clazz) {
		Assert.notNull(clazz, "Class must not be null");
		// 获取clazz的全类名
		String className = clazz.getName();
		// 获取clazz的最后一个'.'索引位置
		int lastDotIndex = className.lastIndexOf(PACKAGE_SEPARATOR);
		// 截取出clazz的'.'后面的字符串加上'.class'，然后返回出去
		return className.substring(lastDotIndex + 1) + CLASS_FILE_SUFFIX;
	}

	/**
	 * 确定给定类的包名,例如{@code java.lang.String}类的'java.lang'
	 *
	 * Determine the name of the package of the given class,
	 * e.g. "java.lang" for the {@code java.lang.String} class.
	 * @param clazz the class
	 * @return the package name, or the empty String if the class
	 * is defined in the default package
	 */
	public static String getPackageName(Class<?> clazz) {
		Assert.notNull(clazz, "Class must not be null");
		// 获取clazz的全类名，截取出包名
		return getPackageName(clazz.getName());
	}

	/**
	 * 确定给定类的包名,例如{@code java.lang.String}类的'java.lang'
	 *
	 * Determine the name of the package of the given fully-qualified class name,
	 * e.g. "java.lang" for the {@code java.lang.String} class name.
	 * @param fqClassName the fully-qualified class name
	 * @return the package name, or the empty String if the class
	 * is defined in the default package
	 */
	public static String getPackageName(String fqClassName) {
		Assert.notNull(fqClassName, "Class name must not be null");
		// 获取fqClassName的最后一个'.'索引位置
		int lastDotIndex = fqClassName.lastIndexOf(PACKAGE_SEPARATOR);
		// 如果找到'.'的索引位置，就截取出fqClassName中lastDotIndex后面的字符串;否则返回空字符串
		return (lastDotIndex != -1 ? fqClassName.substring(0, lastDotIndex) : "");
	}

	/**
	 *  获取没有合格包名的类名:通常是简单类名，但是数组就是元素类型名+'[]'，和一个JDK代理的已实现接口的附加列表
	 *
	 * Return the qualified name of the given class: usually simply
	 * the class name, but component type class name + "[]" for arrays.
	 * @param clazz the class
	 * @return the qualified name of the class
	 */
	public static String getQualifiedName(Class<?> clazz) {
		Assert.notNull(clazz, "Class must not be null");
		// getTypeName:数组->java.lang.String[],成员内部类->lang.reflect.AAA$BBB,匿名内部类->lang.reflect.AAA$4
		// 普通类->lang.reflect.AAA,基本数据类型-> int
		return clazz.getTypeName();
	}

	/**
	 * 返回给定方法对象的合格名称，有完全合格的接口名/类名+'.'+方法名组成
	 *
	 * Return the qualified name of the given method, consisting of
	 * fully qualified interface/class name + "." + method name.
	 * @param method the method
	 * @return the qualified name of the method
	 */
	public static String getQualifiedMethodName(Method method) {
		// 以定义method的类作为拼装方法合格名称的类名
		return getQualifiedMethodName(method, null);
	}

	/**
	 * 返回给定方法对象的合格名称，有完全合格的接口名/类名+'.'+方法名组成
	 *
	 * Return the qualified name of the given method, consisting of
	 * fully qualified interface/class name + "." + method name.
	 * @param method the method
	 * @param clazz the clazz that the method is being invoked on
	 * (may be {@code null} to indicate the method's declaring class)
	 * @return the qualified name of the method
	 * @since 4.3.4
	 */
	public static String getQualifiedMethodName(Method method, @Nullable Class<?> clazz) {
		Assert.notNull(method, "Method must not be null");
		// 如果clazz不为null，就用clazz,否则使用声明method的类对象，然后取出类对象的全类名，加上'.',再加上方法名，最后返回出去
		return (clazz != null ? clazz : method.getDeclaringClass()).getName() + '.' + method.getName();
	}

	/**
	 * 确定给定的类是否具有代用给定签名的公共构造函数
	 *
	 * Determine whether the given class has a public constructor with the given signature.
	 * <p>Essentially translates {@code NoSuchMethodException} to "false".
	 * @param clazz the clazz to analyze
	 * @param paramTypes the parameter types of the method
	 * @return whether the class has a corresponding constructor
	 * @see Class#getConstructor
	 */
	public static boolean hasConstructor(Class<?> clazz, Class<?>... paramTypes) {
		//获取paramTypes的clazz的构造函数对象,如果不为null返回true；否则返回false
		return (getConstructorIfAvailable(clazz, paramTypes) != null);
	}

	/**
	 * 确定给定的类是否具有代用给定签名的公共构造函数,和返回是否可用(否则返回{@code null})
	 *
	 * Determine whether the given class has a public constructor with the given signature,
	 * and return it if available (else return {@code null}).
	 * <p>Essentially translates {@code NoSuchMethodException} to {@code null}.
	 * @param clazz the clazz to analyze
	 * @param paramTypes the parameter types of the method
	 * @return the constructor, or {@code null} if not found
	 * @see Class#getConstructor
	 */
	@Nullable
	public static <T> Constructor<T> getConstructorIfAvailable(Class<T> clazz, Class<?>... paramTypes) {
		Assert.notNull(clazz, "Class must not be null");
		try {
			// 获取paramTypes的clazz的构造函数对象
			return clazz.getConstructor(paramTypes);
		}
		catch (NoSuchMethodException ex) {
			return null;
		}
	}

	/**
	 * 确定给定的类是否具有带有给定签名的公共方法
	 *
	 * Determine whether the given class has a public method with the given signature.
	 * @param clazz the clazz to analyze
	 * @param method the method to look for
	 * @return whether the class has a corresponding method
	 * @since 5.2.3
	 */
	public static boolean hasMethod(Class<?> clazz, Method method) {
		Assert.notNull(clazz, "Class must not be null");
		Assert.notNull(method, "Method must not be null");
		if (clazz == method.getDeclaringClass()) {
			return true;
		}
		String methodName = method.getName();
		Class<?>[] paramTypes = method.getParameterTypes();
		return getMethodOrNull(clazz, methodName, paramTypes) != null;
	}

	/**
	 * Determine whether the given class has a public method with the given signature.
	 * <p>Essentially translates {@code NoSuchMethodException} to "false".
	 * @param clazz the clazz to analyze
	 * @param methodName the name of the method
	 * @param paramTypes the parameter types of the method
	 * @return whether the class has a corresponding method
	 * @see Class#getMethod
	 */
	public static boolean hasMethod(Class<?> clazz, String methodName, Class<?>... paramTypes) {
		return (getMethodIfAvailable(clazz, methodName, paramTypes) != null);
	}

	/**
	 * 确定给定的类是否具有带有给定签名的公共方法，并返回(如果可用)(否则抛出{@code IllegalStateException}异常).
	 *
	 * Determine whether the given class has a public method with the given signature,
	 * and return it if available (else throws an {@code IllegalStateException}).
	 * <p>In case of any signature specified, only returns the method if there is a
	 * unique candidate, i.e. a single public method with the specified name.
	 * <p>Essentially translates {@code NoSuchMethodException} to {@code IllegalStateException}.
	 * @param clazz the clazz to analyze
	 * @param methodName the name of the method
	 * @param paramTypes the parameter types of the method
	 * (may be {@code null} to indicate any signature)
	 * @return the method (never {@code null})
	 * @throws IllegalStateException if the method has not been found
	 * @see Class#getMethod
	 */
	public static Method getMethod(Class<?> clazz, String methodName, @Nullable Class<?>... paramTypes) {
		Assert.notNull(clazz, "Class must not be null");
		Assert.notNull(methodName, "Method name must not be null");
		// 如果paramType不为null
		if (paramTypes != null) {
			try {
				// 获取clazz的方法名为methodName和参数类型为paramType的方法对象
				return clazz.getMethod(methodName, paramTypes);
			}
			catch (NoSuchMethodException ex) {
				// 捕捉没有找到对应方法的异常，抛出IllegalStateException
				throw new IllegalStateException("Expected method not found: " + ex);
			}
		}
		else {
			// 获取在clazz中匹配methodName的候选方法
			Set<Method> candidates = findMethodCandidatesByName(clazz, methodName);
			// 如果只匹配到一个方法
			if (candidates.size() == 1) {
				// 取出candidates的迭代器，获取第一个元素并返回出去
				return candidates.iterator().next();
			}
			// 如果没有匹配到方法，直接抛出IllegalStateException
			else if (candidates.isEmpty()) {
				throw new IllegalStateException("Expected method not found: " + clazz.getName() + '.' + methodName);
			}
			// 如果匹配到多个方法对象，直接抛出IllegalStateException
			else {
				throw new IllegalStateException("No unique method found: " + clazz.getName() + '.' + methodName);
			}
		}
	}

	/**
	 *  确定给定的类是否具有带有给定签名的公共方法，并返回(如果可用)(否则返回{@code null})
	 *
	 * Determine whether the given class has a public method with the given signature,
	 * and return it if available (else return {@code null}).
	 * <p>In case of any signature specified, only returns the method if there is a
	 * unique candidate, i.e. a single public method with the specified name.
	 * <p>Essentially translates {@code NoSuchMethodException} to {@code null}.
	 * @param clazz the clazz to analyze
	 * @param methodName the name of the method
	 * @param paramTypes the parameter types of the method
	 * (may be {@code null} to indicate any signature)
	 * @return the method, or {@code null} if not found
	 * @see Class#getMethod
	 */
	@Nullable
	public static Method getMethodIfAvailable(Class<?> clazz, String methodName, @Nullable Class<?>... paramTypes) {
		Assert.notNull(clazz, "Class must not be null");
		Assert.notNull(methodName, "Method name must not be null");
		// 如果paramTypes不为null
		if (paramTypes != null) {
			// 获取clazz的方法名为methodName和参数类型为paramType的方法对象
			return getMethodOrNull(clazz, methodName, paramTypes);
		}
		else {
			// 获取在clazz中匹配methodName的候选方法
			Set<Method> candidates = findMethodCandidatesByName(clazz, methodName);
			// 如果只匹配到一个方法
			if (candidates.size() == 1) {
				// 取出candidates的迭代器，获取第一个元素并返回出去
				return candidates.iterator().next();
			}
			// 如果匹配到多个方法对象，直接返回null
			return null;
		}
	}

	/**
	 * 返回给定名称（带有任何参数类型)的方法数量，从给定的类和/或其父类。包含非public方法
	 *
	 * Return the number of methods with a given name (with any argument types),
	 * for the given class and/or its superclasses. Includes non-public methods.
	 * @param clazz	the clazz to check
	 * @param methodName the name of the method
	 * @return the number of methods with the given name
	 */
	public static int getMethodCountForName(Class<?> clazz, String methodName) {
		Assert.notNull(clazz, "Class must not be null");
		Assert.notNull(methodName, "Method name must not be null");
		// 初始化方法数量为0
		int count = 0;
		// 获取clazz的所有方法
		Method[] declaredMethods = clazz.getDeclaredMethods();
		// 遍历clazz的所有方法
		for (Method method : declaredMethods) {
			// 如果method的方法名等于methodName
			if (methodName.equals(method.getName())) {
				// 计数器+1
				count++;
			}
		}
		// 获取clazz的所有接口
		Class<?>[] ifcs = clazz.getInterfaces();
		// 遍历clazz的所有接口
		for (Class<?> ifc : ifcs) {
			// 递归方法，取得ifc中匹配methodNamde的方法数量，再加到count上
			count += getMethodCountForName(ifc, methodName);
		}
		// 如果clazz的父类不为null
		if (clazz.getSuperclass() != null) {
			// 递归方法，取得clazz的父类中匹配methodName的方法数量，再加到count上
			count += getMethodCountForName(clazz.getSuperclass(), methodName);
		}
		return count;
	}

	/**
	 * 给定的类或其超类之一是否至少具有一个或多个带有提供的名称（带有任何参数类型)的方法？
	 *
	 * Does the given class or one of its superclasses at least have one or more
	 * methods with the supplied name (with any argument types)?
	 * Includes non-public methods.
	 * @param clazz	the clazz to check
	 * @param methodName the name of the method
	 * @return whether there is at least one method with the given name
	 */
	public static boolean hasAtLeastOneMethodWithName(Class<?> clazz, String methodName) {
		Assert.notNull(clazz, "Class must not be null");
		Assert.notNull(methodName, "Method name must not be null");
		// 获取clazz的所有方法(包括非public方法)
		Method[] declaredMethods = clazz.getDeclaredMethods();
		// 遍历clazz的所有方法
		for (Method method : declaredMethods) {
			// 如果method的名称等于methodName
			if (method.getName().equals(methodName)) {
				// 直接返回true
				return true;
			}
		}
		// 获取clazz的所有接口
		Class<?>[] ifcs = clazz.getInterfaces();
		// 遍历clazz的所有接口
		for (Class<?> ifc : ifcs) {
			// 递归方法,如果ifc至少有一个使用methodName的方法
			if (hasAtLeastOneMethodWithName(ifc, methodName)) {
				// 直接返回true
				return true;
			}
		}
		// 获取clazz的父类，如有不为null且父类中至少有一个使用methodName的方法，就返回true；否则返回false
		return (clazz.getSuperclass() != null && hasAtLeastOneMethodWithName(clazz.getSuperclass(), methodName));
	}

	/**
	 *  给定一个可能来自接口以及在当前反射调用中使用的目标类的方法,找到相应的目标方法(如果有),
	 *  例如,该方法可以是{@code IFoo.bar()}，目标类可以使{@code DefaultFoo}.
	 * 	在这种情况下，该方法可以使{@code DefaultFoo.bar()}.这样可以找到该方法的属性
	 *
	 * Given a method, which may come from an interface, and a target class used
	 * in the current reflective invocation, find the corresponding target method
	 * if there is one. E.g. the method may be {@code IFoo.bar()} and the
	 * target class may be {@code DefaultFoo}. In this case, the method may be
	 * {@code DefaultFoo.bar()}. This enables attributes on that method to be found.
	 * <p><b>NOTE:</b> In contrast to {link org.springframework.aop.support.AopUtils#getMostSpecificMethod},
	 * this method does <i>not</i> resolve Java 5 bridge methods automatically.
	 * Call {@link org.springframework.core.BridgeMethodResolver#findBridgedMethod}
	 * if bridge method resolution is desirable (e.g. for obtaining metadata from
	 * the original method definition).
	 * <p><b>NOTE:</b> Since Spring 3.1.1, if Java security settings disallow reflective
	 * access (e.g. calls to {@code Class#getDeclaredMethods} etc, this implementation
	 * will fall back to returning the originally provided method.
	 * @param method the method to be invoked, which may come from an interface
	 * @param targetClass the target class for the current invocation
	 * (may be {@code null} or may not even implement the method)
	 * @return the specific target method, or the original method if the
	 * {@code targetClass} does not implement it
	 * @see #getInterfaceMethodIfPossible
	 */
	public static Method getMostSpecificMethod(Method method, @Nullable Class<?> targetClass) {
		// 如果targetClass不为null且targetClass不是声明method的类且method在targetClass中可重写
		if (targetClass != null && targetClass != method.getDeclaringClass() && isOverridable(method, targetClass)) {
			try {
				// 如果method是public方法
				if (Modifier.isPublic(method.getModifiers())) {
					try {
						// 获取targetClass的方法名为method的方法名和参数类型数组为method的参数类型数组的方法对象
						return targetClass.getMethod(method.getName(), method.getParameterTypes());
					}
					catch (NoSuchMethodException ex) {
						// 捕捉没有找到方法异常,直接返回给定的方法
						return method;
					}
				}
				else {
					// 反射获取targetClass的方法名为method的方法名和参数类型数组为method的参数类型数组的方法对象
					Method specificMethod =
							ReflectionUtils.findMethod(targetClass, method.getName(), method.getParameterTypes());
					//如果specificMethod不为null,就返回specificMethod,否则返回给定的方法
					return (specificMethod != null ? specificMethod : method);
				}
			}
			catch (SecurityException ex) {
				// Security settings are disallowing reflective access; fall back to 'method' below.
				// 安全性设置不允许反射式访问；回到下面的'method'
			}
		}
		// 直接返回给定的方法
		return method;
	}

	/**
	 * 获取method相应的接口方法对象，如果找不到，则返回原始方法
	 *
	 * Determine a corresponding interface method for the given method handle, if possible.
	 * <p>This is particularly useful for arriving at a public exported type on Jigsaw
	 * which can be reflectively invoked without an illegal access warning.
	 * @param method the method to be invoked, potentially from an implementation class
	 * @return the corresponding interface method, or the original method if none found
	 * @since 5.1
	 * @see #getMostSpecificMethod
	 */
	public static Method getInterfaceMethodIfPossible(Method method) {
		// 如果method是Public方法且声明method的类不是接口
		if (!Modifier.isPublic(method.getModifiers()) || method.getDeclaringClass().isInterface()) {
			return method;
		}
		return interfaceMethodCache.computeIfAbsent(method, key -> {
			// 获取声明method的类
			Class<?> current = key.getDeclaringClass();
			// 如果current不为null且current不是Object类
			while (current != null && current != Object.class) {
				// 获取current的所有接口
				Class<?>[] ifcs = current.getInterfaces();
				// 遍历所有接口
				for (Class<?> ifc : ifcs) {
					try {
						// 获取ifc的方法名为method的方法名和参数类型数组为method的参数类型数组的方法对象
						return ifc.getMethod(key.getName(), key.getParameterTypes());
					}
					catch (NoSuchMethodException ex) {
						// ignore
					}
				}
				// 取出current的父类重新赋值给current
				current = current.getSuperclass();
			}
			// 如果method不是Public方法 或者 声明method的类是接口时，直接返回method
			return key;
		});
	}

	/**
	 * 确定给定的方法是有用户声明的，或者只是指向用户声明方法
	 *
	 * Determine whether the given method is declared by the user or at least pointing to
	 * a user-declared method.
	 * <p>Checks {@link Method#isSynthetic()} (for implementation methods) as well as the
	 * {@code GroovyObject} interface (for interface methods; on an implementation class,
	 * implementations of the {@code GroovyObject} methods will be marked as synthetic anyway).
	 * Note that, despite being synthetic, bridge methods ({@link Method#isBridge()}) are considered
	 * as user-level methods since they are eventually pointing to a user-declared generic method.
	 * @param method the method to check
	 * @return {@code true} if the method can be considered as user-declared; [@code false} otherwise
	 */
	public static boolean isUserLevelMethod(Method method) {
		Assert.notNull(method, "Method must not be null");
		// 桥接方法：就是说一个子类在继承（或实现）一个父类（或接口）的泛型方法时，
		// 		在子类中明确指定了泛型类型，那么在编译时编译器会自动生成桥接方法（当然还有其他情况会生成桥接方法，
		// 		这里只是列举了其中一种情况）。https://blog.csdn.net/qq_32647893/article/details/81071336
		// isSynthetic:判断此字段是否为编译期间生成的合成字段，如果是则返回true; 否则返回false。
		// 			https://www.jianshu.com/p/3f75221437a3
		// 如果method是桥接方法或者(method不是编译期间生成的合成字段且method不是GroovyObject的方法时返回true
		// 否则返回false
		return (method.isBridge() || (!method.isSynthetic() && !isGroovyObjectMethod(method)));
	}

	/**
	 * 判断是否GroovyObject的方法，GroovyObject表示Groovy语言
	 * @param method
	 * @return
	 */
	private static boolean isGroovyObjectMethod(Method method) {
		// 获取声明method的类的全类名 ，如果等于'groovy.lang.GroovyObject'就返回true；否则返回false
		return method.getDeclaringClass().getName().equals("groovy.lang.GroovyObject");
	}

	/**
	 * 确定给定的方法在给定目标类中是否可重写
	 * Determine whether the given method is overridable in the given target class.
	 * @param method the method to check
	 * @param targetClass the target class to check against
	 */
	private static boolean isOverridable(Method method, @Nullable Class<?> targetClass) {
		// 如果method是private
		if (Modifier.isPrivate(method.getModifiers())) {
			return false;
		}
		// 如果method是public或method是否Protected
		if (Modifier.isPublic(method.getModifiers()) || Modifier.isProtected(method.getModifiers())) {
			return true;
		}
		// 如果targetClass为null或者获取声明method的类的包名等于目标类的包名是返回true，否则返回false
		return (targetClass == null ||
				getPackageName(method.getDeclaringClass()).equals(getPackageName(targetClass)));
	}

	/**
	 * 返回类的公共静态方法
	 *
	 * Return a public static method of a class.
	 * @param clazz the class which defines the method
	 * @param methodName the static method name
	 * @param args the parameter types to the method
	 * @return the static method, or {@code null} if no static method was found
	 * @throws IllegalArgumentException if the method name is blank or the clazz is null
	 */
	@Nullable
	public static Method getStaticMethod(Class<?> clazz, String methodName, Class<?>... args) {
		Assert.notNull(clazz, "Class must not be null");
		Assert.notNull(methodName, "Method name must not be null");
		try {
			// 获取clazz的方法名为methodName和参数类型为args的方法对象
			Method method = clazz.getMethod(methodName, args);
			// 如果method是静态方法，就返回出去；否则返回null
			return Modifier.isStatic(method.getModifiers()) ? method : null;
		}
		catch (NoSuchMethodException ex) {
			// 捕捉未找到方法异常，直接返回null
			return null;
		}
	}


	@Nullable
	private static Method getMethodOrNull(Class<?> clazz, String methodName, Class<?>[] paramTypes) {
		try {
			return clazz.getMethod(methodName, paramTypes);
		}
		catch (NoSuchMethodException ex) {
			return null;
		}
	}

	/**
	 * 按方法名查询候选方法
	 * @param clazz
	 * @param methodName
	 * @return
	 */
	private static Set<Method> findMethodCandidatesByName(Class<?> clazz, String methodName) {
		// 定义一个初始容量为1专门用于存储匹配上methodName的Method对象的Set集合
		Set<Method> candidates = new HashSet<>(1);
		// 获取clazz的所有方法
		Method[] methods = clazz.getMethods();
		// 遍历方法对象
		for (Method method : methods) {
			// 获取method的方法名，如果等于methodName
			if (methodName.equals(method.getName())) {
				// 将method对象添加到candidate
				candidates.add(method);
			}
		}
		return candidates;
	}

}
