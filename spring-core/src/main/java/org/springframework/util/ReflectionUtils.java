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

package org.springframework.util;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.springframework.lang.Nullable;

/**
 * 简单的实用程序，用于实用反射API和处理反射异常
 *
 * Simple utility class for working with the reflection API and handling
 * reflection exceptions.
 *
 * <p>Only intended for internal use.
 *
 * @author Juergen Hoeller
 * @author Rob Harrop
 * @author Rod Johnson
 * @author Costin Leau
 * @author Sam Brannen
 * @author Chris Beams
 * @since 1.2.2
 */
public abstract class ReflectionUtils {

	/**
	 * 与未在{@code java.lang.Object}上声明的所有非桥接非合成方法匹配的预购建方法过滤器
	 *
	 * Pre-built MethodFilter that matches all non-bridge non-synthetic methods
	 * which are not declared on {@code java.lang.Object}.
	 * @since 3.0.5
	 */
	public static final MethodFilter USER_DECLARED_METHODS =
			// 当method不是桥接方法 又 不是合成方法时,返回true；否则返回false
			(method -> !method.isBridge() && !method.isSynthetic());

	/**
	 * 匹配所有非静态，非final修饰的属性的预购建属性过滤器
	 *
	 * Pre-built FieldFilter that matches all non-static, non-final fields.
	 */
	public static final FieldFilter COPYABLE_FIELDS =
			// 获取field的修复符，如果是被static修饰的或者是被final修饰的属性会返回true；否则false
			(field -> !(Modifier.isStatic(field.getModifiers()) || Modifier.isFinal(field.getModifiers())));


	/**
	 * CGLIB重命名方法的命名前缀
	 * Naming prefix for CGLIB-renamed methods.
	 * @see #isCglibRenamedMethod
	 */
	private static final String CGLIB_RENAMED_METHOD_PREFIX = "CGLIB$";

	// 空类数组
	private static final Class<?>[] EMPTY_CLASS_ARRAY = new Class<?>[0];

	// 空方法数组
	private static final Method[] EMPTY_METHOD_ARRAY = new Method[0];

	// 空属性数组
	private static final Field[] EMPTY_FIELD_ARRAY = new Field[0];

	// 空对象数组
	private static final Object[] EMPTY_OBJECT_ARRAY = new Object[0];


	/**
	 * 缓存{@link Class#getDeclaredMethods()}的方法对象以及等效的从基于Java8的接口默认方法，允许快速迭代
	 *
	 * Cache for {@link Class#getDeclaredMethods()} plus equivalent default methods
	 * from Java 8 based interfaces, allowing for fast iteration.
	 */
	private static final Map<Class<?>, Method[]> declaredMethodsCache = new ConcurrentReferenceHashMap<>(256);

	/**
	 * 缓存 {@link Class#getDeclaredFields()},允许快速迭代
	 *
	 * Cache for {@link Class#getDeclaredFields()}, allowing for fast iteration.
	 */
	private static final Map<Class<?>, Field[]> declaredFieldsCache = new ConcurrentReferenceHashMap<>(256);


	// Exception handling

	/**
	 * 处理给定的反射异常
	 *
	 * Handle the given reflection exception.
	 * <p>Should only be called if no checked exception is expected to be thrown
	 * by a target method, or if an error occurs while accessing a method or field.
	 * <p>Throws the underlying RuntimeException or Error in case of an
	 * InvocationTargetException with such a root cause. Throws an
	 * IllegalStateException with an appropriate message or
	 * UndeclaredThrowableException otherwise.
	 * @param ex the reflection exception to handle
	 */
	public static void handleReflectionException(Exception ex) {
		//如果ex是NoSuchMethodException的子类或本类
		if (ex instanceof NoSuchMethodException) {
			//抛出IllegalStateException，并描述方法没有找到的异常信息
			throw new IllegalStateException("Method not found: " + ex.getMessage());
		}
		//如果ex是IllegalAccessException的子类或本类
		if (ex instanceof IllegalAccessException) {
			//抛出IllegalStateException，并描述无法访问目标方法或者属性的异常信息
			throw new IllegalStateException("Could not access method or field: " + ex.getMessage());
		}
		//如果ex是InvocationTargetException的子类或本类。
		// InvocationTargetException:当被调用的方法的内部抛出了异常而没有被捕获时，将由此异常接收
		if (ex instanceof InvocationTargetException) {
			handleInvocationTargetException((InvocationTargetException) ex);
		}
		if (ex instanceof RuntimeException) {
			throw (RuntimeException) ex;
		}
		throw new UndeclaredThrowableException(ex);
	}

	/**
	 * 处理给定的InvocationTargetException，仅当目标方法预期不会抛出任何被检查异常时，才应该调用此方法
	 *
	 * Handle the given invocation target exception. Should only be called if no
	 * checked exception is expected to be thrown by the target method.
	 * <p>Throws the underlying RuntimeException or Error in case of such a root
	 * cause. Throws an UndeclaredThrowableException otherwise.
	 * @param ex the invocation target exception to handle
	 */
	public static void handleInvocationTargetException(InvocationTargetException ex) {
		// 如果适用，将基础异常重新抛出到{@link RuntimeException} 或者{@link Error}。否则
		// 抛出一个{@link UndeclaredThrowableException}.
		rethrowRuntimeException(ex.getTargetException());
	}

	/**
	 * 重新抛出给定的{@link Throwable exception},这大概是{@link InvocationTargetException}的目标异常。仅当目标方法预期不会抛出任何被检查异常时,才应该调用此方法。
	 *
	 * Rethrow the given {@link Throwable exception}, which is presumably the
	 * <em>target exception</em> of an {@link InvocationTargetException}.
	 * Should only be called if no checked exception is expected to be thrown
	 * by the target method.
	 * <p>Rethrows the underlying exception cast to a {@link RuntimeException} or
	 * {@link Error} if appropriate; otherwise, throws an
	 * {@link UndeclaredThrowableException}.
	 * @param ex the exception to rethrow
	 * @throws RuntimeException the rethrown exception
	 */
	public static void rethrowRuntimeException(Throwable ex) {
		// 如果ex是RuntimeException的子类或本类
		if (ex instanceof RuntimeException) {
			// 将ex强转成RuntimeException后抛出
			throw (RuntimeException) ex;
		}
		// 如果ex是Error的子类或本类
		if (ex instanceof Error) {
			// 将ex强转成Error后抛出
			throw (Error) ex;
		}
		throw new UndeclaredThrowableException(ex);
	}

	/**
	 * 重新抛出给定的{@link Throwable exception},这大概是{@link InvocationTargetException}的目标异常。仅当目标方法预期不会抛出任何被检查异常时,才应该调用此方法
	 *
	 * Rethrow the given {@link Throwable exception}, which is presumably the
	 * <em>target exception</em> of an {@link InvocationTargetException}.
	 * Should only be called if no checked exception is expected to be thrown
	 * by the target method.
	 * <p>Rethrows the underlying exception cast to an {@link Exception} or
	 * {@link Error} if appropriate; otherwise, throws an
	 * {@link UndeclaredThrowableException}.
	 * @param ex the exception to rethrow
	 * @throws Exception the rethrown exception (in case of a checked exception)
	 */
	public static void rethrowException(Throwable ex) throws Exception {
		// 如果ex是Exception的子类或本类
		if (ex instanceof Exception) {
			// 将ex强转成Exception后抛出异常
			throw (Exception) ex;
		}
		// 如果ex是Error的子类或本类
		if (ex instanceof Error) {
			// 将ex强转成Error后抛出异常
			throw (Error) ex;
		}
		throw new UndeclaredThrowableException(ex);
	}


	// Constructor handling

	/**
	 * 获取给定的类和参数的一个可访问构造函数
	 *
	 * Obtain an accessible constructor for the given class and parameters.
	 * @param clazz the clazz to check
	 * @param parameterTypes the parameter types of the desired constructor
	 * @return the constructor reference
	 * @throws NoSuchMethodException if no such constructor exists
	 * @since 5.0
	 */
	public static <T> Constructor<T> accessibleConstructor(Class<T> clazz, Class<?>... parameterTypes)
			throws NoSuchMethodException {

		// 获取clazz的接收parameterTypes参数类型的构造函数
		Constructor<T> ctor = clazz.getDeclaredConstructor(parameterTypes);
		// 将ctor设置成可访问
		makeAccessible(ctor);
		return ctor;
	}

	/**
	 * 使给定的构造函数可访问，并在需要时显示设置它的可访问性。{@code setAccessible(true)}方法仅在实际需要时才会被调用，
	 * 以避免与JVM SecurityManager发生不必要的冲突（如果处于活动状态)
	 *
	 * Make the given constructor accessible, explicitly setting it accessible
	 * if necessary. The {@code setAccessible(true)} method is only called
	 * when actually necessary, to avoid unnecessary conflicts with a JVM
	 * SecurityManager (if active).
	 * @param ctor the constructor to make accessible
	 * @see java.lang.reflect.Constructor#setAccessible
	 */
	@SuppressWarnings("deprecation")  // on JDK 9
	public static void makeAccessible(Constructor<?> ctor) {
		// 如果(构造函数不是public 或者 声明该构造函数的类不是public) 其 构造函数不可访问时
		if ((!Modifier.isPublic(ctor.getModifiers()) ||
				!Modifier.isPublic(ctor.getDeclaringClass().getModifiers())) && !ctor.isAccessible()) {
			// 将构造函数设置成可访问
			ctor.setAccessible(true);
		}
	}


	// Method handling

	/**
	 * 尝试去找到一个在提供的类和提供的方法名和无参的{@link Method}。搜寻所有的父类直到{@code Object}
	 *
	 * Attempt to find a {@link Method} on the supplied class with the supplied name
	 * and no parameters. Searches all superclasses up to {@code Object}.
	 * <p>Returns {@code null} if no {@link Method} can be found.
	 * @param clazz the class to introspect
	 * @param name the name of the method
	 * @return the Method object, or {@code null} if none found
	 */
	@Nullable
	public static Method findMethod(Class<?> clazz, String name) {
		// 查询方法，传入空类数组表示查询无参方法
		return findMethod(clazz, name, EMPTY_CLASS_ARRAY);
	}

	/**
	 *  尝试去找到一个在提供的类和提供的方法名和给定的参数类型数组的{@link Method}。
	 *  搜寻所有的父类直到{@code Object}
	 *
	 * Attempt to find a {@link Method} on the supplied class with the supplied name
	 * and parameter types. Searches all superclasses up to {@code Object}.
	 * <p>Returns {@code null} if no {@link Method} can be found.
	 * @param clazz the class to introspect
	 * @param name the name of the method
	 * @param paramTypes the parameter types of the method
	 * (may be {@code null} to indicate any signature)
	 * @return the Method object, or {@code null} if none found
	 */
	@Nullable
	public static Method findMethod(Class<?> clazz, String name, @Nullable Class<?>... paramTypes) {
		// 如果clazz为null，抛出异常
		Assert.notNull(clazz, "Class must not be null");
		// 如果name为null，抛出异常
		Assert.notNull(name, "Method name must not be null");
		// 将clazz赋值searchType，表示当前搜索的类
		Class<?> searchType = clazz;
		// 如果当前搜索的类不为null，就继续循环
		while (searchType != null) {
			// 如果searchType是接口，就取其所有public方法(因为接口的方法都是public的，所以这里相当于取出接口中的所有方法)。
			// 否则searchType为类，取其所有定义的方法
			Method[] methods = (searchType.isInterface() ? searchType.getMethods() :
					getDeclaredMethods(searchType, false));
			// 遍历所有方法
			for (Method method : methods) {
				// 如果method的名称等于name且(参数类型数组为null或者paramTypes等于method的参数类型数组)
				if (name.equals(method.getName()) && (paramTypes == null || hasSameParams(method, paramTypes))) {
					return method;
				}
			}
			// 获取searchType的父类重新赋值给searchType，从父类中查找方法
			searchType = searchType.getSuperclass();
		}
		// 如果找不到方法时，返回null
		return null;
	}

	private static boolean hasSameParams(Method method, Class<?>[] paramTypes) {
		return (paramTypes.length == method.getParameterCount() &&
				Arrays.equals(paramTypes, method.getParameterTypes()));
	}

	/**
	 * 针对提供的目标对象执行指定的没有参数的{@link Method}。
	 * 当执行一个静态的{@link Method}时，目标对象可以为null
	 *
	 * Invoke the specified {@link Method} against the supplied target object with no arguments.
	 * The target object can be {@code null} when invoking a static {@link Method}.
	 * <p>Thrown exceptions are handled via a call to {@link #handleReflectionException}.
	 * @param method the method to invoke
	 * @param target the target object to invoke the method on
	 * @return the invocation result, if any
	 * @see #invokeMethod(java.lang.reflect.Method, Object, Object[])
	 */
	@Nullable
	public static Object invokeMethod(Method method, @Nullable Object target) {
		// 执行method，插入EMPTY_OBJECT_ARRAY，表示无参数方法
		return invokeMethod(method, target, EMPTY_OBJECT_ARRAY);
	}

	/**
	 * 针对提供的目标对象执行指定的参数数组的{@link Method}。
	 * 当执行一个静态的{@link Method}时，目标对象可以为null
	 *
	 * Invoke the specified {@link Method} against the supplied target object with the
	 * supplied arguments. The target object can be {@code null} when invoking a
	 * static {@link Method}.
	 * <p>Thrown exceptions are handled via a call to {@link #handleReflectionException}.
	 * @param method the method to invoke
	 * @param target the target object to invoke the method on
	 * @param args the invocation arguments (may be {@code null})
	 * @return the invocation result, if any
	 */
	@Nullable
	public static Object invokeMethod(Method method, @Nullable Object target, @Nullable Object... args) {
		try {
			// 执行obj的带args的method，并返回其执行结果
			return method.invoke(target, args);
		}
		catch (Exception ex) {
			// 处理给定的反射异常
			handleReflectionException(ex);
		}
		throw new IllegalStateException("Should never get here");
	}

	/**
	 * 确定给定方法是否显示声明给定异常或其父类之一，这意味着该类型的异常可以在反射调用中按原样传播
	 *
	 * Determine whether the given method explicitly declares the given
	 * exception or one of its superclasses, which means that an exception
	 * of that type can be propagated as-is within a reflective invocation.
	 * @param method the declaring method
	 * @param exceptionType the exception to throw
	 * @return {@code true} if the exception can be thrown as-is;
	 * {@code false} if it needs to be wrapped
	 */
	public static boolean declaresException(Method method, Class<?> exceptionType) {
		// 如果method为null，抛出异常
		Assert.notNull(method, "Method must not be null");
		// 获取method的所有异常类
		Class<?>[] declaredExceptions = method.getExceptionTypes();
		// 遍历所有异常类
		for (Class<?> declaredException : declaredExceptions) {
			// 如果exceptionType是declaredException的子类或本身
			if (declaredException.isAssignableFrom(exceptionType)) {
				return true;
			}
		}
		// 在method没有找到exceptionType类型的声明异常时，返回false
		return false;
	}

	/**
	 * 执行给定回调操作在给定类的所有匹配方法，本地声明或与之等效(例如给定类实现的基于Java8接口上的默认方法)
	 *
	 * Perform the given callback operation on all matching methods of the given
	 * class, as locally declared or equivalent thereof (such as default methods
	 * on Java 8 based interfaces that the given class implements).
	 * @param clazz the class to introspect
	 * @param mc the callback to invoke for each method
	 * @throws IllegalStateException if introspection fails
	 * @since 4.2
	 * @see #doWithMethods
	 */
	public static void doWithLocalMethods(Class<?> clazz, MethodCallback mc) {
		// 从缓存中获取clazz的所有声明的方法，包括它的所有接口中所有默认方法,没有时从clazz中获取，然后添加到缓存中;
		Method[] methods = getDeclaredMethods(clazz, false);
		// 遍历methods
		for (Method method : methods) {
			try {
				// 对method执行回调操作
				mc.doWith(method);
			}
			catch (IllegalAccessException ex) {
				throw new IllegalStateException("Not allowed to access method '" + method.getName() + "': " + ex);
			}
		}
	}

	/**
	 * 执行给定回调操作在给定类的所有匹配方法
	 *
	 * Perform the given callback operation on all matching methods of the given
	 * class and superclasses.
	 * <p>The same named method occurring on subclass and superclass will appear
	 * twice, unless excluded by a {@link MethodFilter}.
	 * @param clazz the class to introspect
	 * @param mc the callback to invoke for each method
	 * @throws IllegalStateException if introspection fails
	 * @see #doWithMethods(Class, MethodCallback, MethodFilter)
	 */
	public static void doWithMethods(Class<?> clazz, MethodCallback mc) {
		doWithMethods(clazz, mc, null);
	}

	/**
	 * 执行给定回调操作在给定类和父类(或者给定的接口或父接口)的所有匹配方法
	 *
	 * Perform the given callback operation on all matching methods of the given
	 * class and superclasses (or given interface and super-interfaces).
	 * <p>The same named method occurring on subclass and superclass will appear
	 * twice, unless excluded by the specified {@link MethodFilter}.
	 * @param clazz the class to introspect
	 * @param mc the callback to invoke for each method
	 * @param mf the filter that determines the methods to apply the callback to
	 * @throws IllegalStateException if introspection fails
	 */
	public static void doWithMethods(Class<?> clazz, MethodCallback mc, @Nullable MethodFilter mf) {
		// Keep backing up the inheritance hierarchy.
		// 从缓存中获取clazz的所有声明的方法，包括它的所有接口中所有默认方法；没有时就从{@code clazz}中获取，再添加到缓存中，
		Method[] methods = getDeclaredMethods(clazz, false);
		// 遍历所有方法
		for (Method method : methods) {
			// 如果mf不为null 且 method不满足mf的匹配要求
			if (mf != null && !mf.matches(method)) {
				// 跳过该method
				continue;
			}
			try {
				// 对method执行回调操作
				mc.doWith(method);
			}
			catch (IllegalAccessException ex) {
				throw new IllegalStateException("Not allowed to access method '" + method.getName() + "': " + ex);
			}
		}
		// 如果clazz的父类不为nulll且(mf不是与未在{@code java.lang.Object}上声明的所有非桥接非合成方法匹配的预购建方法过滤器或者clazz的父类不为Object
		if (clazz.getSuperclass() != null && (mf != USER_DECLARED_METHODS || clazz.getSuperclass() != Object.class)) {
			// 递归方法
			// 执行给定回调操作在clazz的父类的所有匹配方法, 子类和父类发生的相同命名方法将出现两次，
			// 子类和父类发生的相同命名方法将出现两次，除非被mf排查
			doWithMethods(clazz.getSuperclass(), mc, mf);
		}
		// 如果clazz是接口
		else if (clazz.isInterface()) {
			// 遍历clazz的所有接口
			for (Class<?> superIfc : clazz.getInterfaces()) {
				// 递归方法
				// 执行给定回调操作在superIfc的所有匹配方法, 子类和父类发生的相同命名方法将出现两次，
				// 子类和父类发生的相同命名方法将出现两次，除非被mf排查
				doWithMethods(superIfc, mc, mf);
			}
		}
	}

	/**
	 * 获取子类和其父类的所有声明方法，首先包括子类方法
	 *
	 * Get all declared methods on the leaf class and all superclasses.
	 * Leaf class methods are included first.
	 * @param leafClass the class to introspect
	 * @throws IllegalStateException if introspection fails
	 */
	public static Method[] getAllDeclaredMethods(Class<?> leafClass) {
		// 定义一个存储Method的32大小的ArrayList对象
		final List<Method> methods = new ArrayList<>(32);
		// 执行给定回调操作在给定类和父类(或者给定的接口或父接口)的所有匹配方法
		doWithMethods(leafClass, methods::add);
		// 返回所有方法，传入EMPTY_METHOD_ARRAY不过是告诉toArray方法，生成的数组的元素类型是什么，如果EMPTYP_METHOD_ARRAY的长度不足以
		// 装载所有的method，就会创建一个新的数组来装载这些method，然后返回出去
		return methods.toArray(EMPTY_METHOD_ARRAY);
	}

	/**
	 * 在子类和所有超类上获取一组唯一的已声明方法。
	 * 首先包含子类方法和然后遍历父类层次结构任何方法，找到和签名匹配的已经包括被过滤出来的一个方法。
	 *
	 * Get the unique set of declared methods on the leaf class and all superclasses.
	 * Leaf class methods are included first and while traversing the superclass hierarchy
	 * any methods found with signatures matching a method already included are filtered out.
	 * @param leafClass the class to introspect
	 * @throws IllegalStateException if introspection fails
	 */
	public static Method[] getUniqueDeclaredMethods(Class<?> leafClass) {
		return getUniqueDeclaredMethods(leafClass, null);
	}

	/**
	 * 在子类和所有超类上获取一组唯一的已声明方法，即被重写非协变返回类型的方法
	 * 首先包含子类方法和然后遍历父类层次结构任何方法，将过滤出所有与已包含的方法匹配的签名方法。
	 *
	 * Get the unique set of declared methods on the leaf class and all superclasses.
	 * Leaf class methods are included first and while traversing the superclass hierarchy
	 * any methods found with signatures matching a method already included are filtered out.
	 * @param leafClass the class to introspect
	 * @param mf the filter that determines the methods to take into account
	 * @throws IllegalStateException if introspection fails
	 * @since 5.2
	 */
	public static Method[] getUniqueDeclaredMethods(Class<?> leafClass, @Nullable MethodFilter mf) {
		// 定义一个存储Method的32大小的ArrayList对象
		final List<Method> methods = new ArrayList<>(32);
		doWithMethods(leafClass, method -> {
			// 已知签名标记，为ture表示已经找到重写方法，但不是被重写带协变返回类型的方法；
			boolean knownSignature = false;
			// 协变：就是父类型到子类型，变得越来越具体，在Java中体现在返回值类型不变或更加具体(异常类型也是如此)等。
			// 定义存储被重写带协变返回类型的方法变量
			Method methodBeingOverriddenWithCovariantReturnType = null;
			// 变量所有方法
			for (Method existingMethod : methods) {
				// 如果method的方法名与existingMethod的方法名相等且method的参数类型数组与existingMethod的参数类型数组也相等
				if (method.getName().equals(existingMethod.getName()) &&
						method.getParameterCount() == existingMethod.getParameterCount() &&
						Arrays.equals(method.getParameterTypes(), existingMethod.getParameterTypes())) {
					// Is this a covariant return type situation?
					// 如果existingMethod的返回类型不等于method的返回类型 且 existingMethod的返回类型是method返回类型的子类或本身
					if (existingMethod.getReturnType() != method.getReturnType() &&
							existingMethod.getReturnType().isAssignableFrom(method.getReturnType())) {
						// 认为existingMethod就是被重写带协变返回类型的方法，并将existingMethod赋值给methodBeingOverriddenWithCovariantReturnType
						methodBeingOverriddenWithCovariantReturnType = existingMethod;
					}
					else {
						// 已知签名标记设置为false
						knownSignature = true;
					}
					// 跳出循环
					break;
				}
			}
			// 如果被重写带协变返回类型的方法不为null时
			if (methodBeingOverriddenWithCovariantReturnType != null) {
				// 移除在methods中的被重写带协变返回类型的方法
				methods.remove(methodBeingOverriddenWithCovariantReturnType);
			}
			// 如果已知签名标记为false且不是Cglib重命名方法
			if (!knownSignature && !isCglibRenamedMethod(method)) {
				// 将method添加method中
				methods.add(method);
			}
		}, mf);
		// 返回所有方法，传入EMPTY_METHOD_ARRAY不过是告诉toArray方法，生成的数组的元素类型是什么，如果EMPTYP_METHOD_ARRAY的长度不足以
		// 装载所有的method，就会创建一个新的数组来装载这些method，然后返回出去
		return methods.toArray(EMPTY_METHOD_ARRAY);
	}

	/**
	 * 使用一个本地缓存为了避免JVM的安全管理检查和新建Method实例
	 *
	 * Variant of {@link Class#getDeclaredMethods()} that uses a local cache in
	 * order to avoid the JVM's SecurityManager check and new Method instances.
	 * In addition, it also includes Java 8 default methods from locally
	 * implemented interfaces, since those are effectively to be treated just
	 * like declared methods.
	 * @param clazz the class to introspect
	 * @return the cached array of methods
	 * @throws IllegalStateException if introspection fails
	 * @since 5.2
	 * @see Class#getDeclaredMethods()
	 */
	public static Method[] getDeclaredMethods(Class<?> clazz) {
		// 从缓存中获取clazz的所有声明的方法，包括它的所有接口中所有默认方法,没有时从clazz中获取，然后添加到缓存中;
		// 如果defensize为true，得到的是克隆版
		return getDeclaredMethods(clazz, true);
	}

	/**
	 * 从缓存中获取clazz的所有声明的方法，包括它的所有接口中所有默认方法；没有时就从{@code clazz}中获取，再添加到缓存中，再返回出去
	 * @param clazz
	 * @param defensive
	 * @return
	 */
	private static Method[] getDeclaredMethods(Class<?> clazz, boolean defensive) {
		// 如果clazz为null，抛出异常
		Assert.notNull(clazz, "Class must not be null");
		// 从缓存中获取clazz的所有方法对象数组
		Method[] result = declaredMethodsCache.get(clazz);
		// 如果方法对象数组为null
		if (result == null) {
			try {
				// 获取clazz的所有声明方法
				Method[] declaredMethods = clazz.getDeclaredMethods();
				// 获取clazz的所有接口中的所有默认方法
				List<Method> defaultMethods = findConcreteMethodsOnInterfaces(clazz);
				// 如果默认方法不为null,表示有默认方法
				if (defaultMethods != null) {
					// 初始化result数组，长度为clazz的所有方法数+所有默认方法数
					result = new Method[declaredMethods.length + defaultMethods.size()];
					// 将declaredMethods的元素复制到result中
					System.arraycopy(declaredMethods, 0, result, 0, declaredMethods.length);
					// 定义index,初始化为clazz的所有声明方法数
					int index = declaredMethods.length;
					// 遍历默认方法数组，将所有默认方法赋值到result中，从index开始
					for (Method defaultMethod : defaultMethods) {
						result[index] = defaultMethod;
						index++;
					}
				}
				else {
					// 没有默认方法时，直接引用declaredMethods
					result = declaredMethods;
				}
				// 将result添加到缓存中，如果result里没有方法，就直接将EMPTY_METHOD_ARRAY添加到缓存中。这样做可以有效的减少内存消耗。
				// 因为将EMPTY_METHOD_ARRAY添加到缓存中，其实只不是缓存指向了EMPTY_METHOD_ARRAY的地址，而result是每次方法执行都是一个新的对象，
				// 而只是用于表示clazz没有方法，用同一对象一样可以表示出来就没有用result了
				declaredMethodsCache.put(clazz, (result.length == 0 ? EMPTY_METHOD_ARRAY : result));
			}
			catch (Throwable ex) {
				throw new IllegalStateException("Failed to introspect Class [" + clazz.getName() +
						"] from ClassLoader [" + clazz.getClassLoader() + "]", ex);
			}
		}
		// 如果result长度为0或者防御标记为true，直接返回result；否则克隆一份result返回出去
		return (result.length == 0 || !defensive) ? result : result.clone();
	}

	/**
	 * 获取给定类的所有接口中的所有默认方法
	 * @param clazz
	 * @return
	 */
	@Nullable
	private static List<Method> findConcreteMethodsOnInterfaces(Class<?> clazz) {
		// 定义存放接口的默认方法的列表
		List<Method> result = null;
		// 遍历clazz的所有接口
		for (Class<?> ifc : clazz.getInterfaces()) {
			// 遍历ifc的所有方法
			for (Method ifcMethod : ifc.getMethods()) {
				// 如果ifcMethod不为null，表示默认方法
				if (!Modifier.isAbstract(ifcMethod.getModifiers())) {
					// 如果result为null
					if (result == null) {
						// 将result初始化成ArrayList对象
						result = new ArrayList<>();
					}
					// 添加默认方法到result
					result.add(ifcMethod);
				}
			}
		}
		// 返回存放接口的默认方法的列表，如果没有找到默认方法时，返回null
		return result;
	}

	/**
	 * 判断给定方法是否是'equals'方法
	 *
	 * Determine whether the given method is an "equals" method.
	 * @see java.lang.Object#equals(Object)
	 */
	public static boolean isEqualsMethod(@Nullable Method method) {
		// 如果method为null 或者 method的名称不为'equals'
		if (method == null || !method.getName().equals("equals")) {
			return false;
		}
		// 获取method的参数类型数组
		if (method.getParameterCount() != 1) {
			return false;
		}
		// 如果参数类型数组长度为1且第一个参数类型为Object，返回true；否则返回false
		return method.getParameterTypes()[0] == Object.class;
	}

	/**
	 * 判断给定方法是否为'hashCode'方法
	 *
	 * Determine whether the given method is a "hashCode" method.
	 * @see java.lang.Object#hashCode()
	 */
	public static boolean isHashCodeMethod(@Nullable Method method) {
		// 如果method不为null且method的名称等于'hashCode'且可接收参数数量为0，返回true；否则返回false
		return (method != null && method.getName().equals("hashCode") && method.getParameterCount() == 0);
	}

	/**
	 * 判断给定的方法是否为'toString'方法
	 *
	 * Determine whether the given method is a "toString" method.
	 * @see java.lang.Object#toString()
	 */
	public static boolean isToStringMethod(@Nullable Method method) {
		// 如果method不为null且method的名称等于'toString'且可接收参数数量为0，返回true；否则返回false
		return (method != null && method.getName().equals("toString") && method.getParameterCount() == 0);
	}

	/**
	 *  确定给定方法是否最初由 {@link java.lang.Object} 声明
	 *
	 * Determine whether the given method is originally declared by {@link java.lang.Object}.
	 */
	public static boolean isObjectMethod(@Nullable Method method) {
		// 如果method不为null且(method的声明类是Object或者method是Object的equal方法或者method是Object的'hashCode'方法
		// 获取method是object的'toString'方法
		return (method != null && (method.getDeclaringClass() == Object.class ||
				isEqualsMethod(method) || isHashCodeMethod(method) || isToStringMethod(method)));
	}

	/**
	 * 确定给定方法是否是一个CGLIB的'重命名'方法，遵循模式'CGLIB$methodName$0'
	 *
	 * Determine whether the given method is a CGLIB 'renamed' method,
	 * following the pattern "CGLIB$methodName$0".
	 * @param renamedMethod the method to check
	 */
	public static boolean isCglibRenamedMethod(Method renamedMethod) {
		// 获取重命名方法
		String name = renamedMethod.getName();
		// 如果name是以'CGLIB$'开头
		if (name.startsWith(CGLIB_RENAMED_METHOD_PREFIX)) {
			// 取出name的最后一个字符
			int i = name.length() - 1;
			// 循环，只要i>0且name里的第i个字母是数字
			while (i >= 0 && Character.isDigit(name.charAt(i))) {
				i--;
			}
			// 如果(i>'CGLIB$'字符创长度且i<name的最后一个字符的位置)且name的第i个字符是'$'就返回true；否则返回false
			return (i > CGLIB_RENAMED_METHOD_PREFIX.length() && (i < name.length() - 1) && name.charAt(i) == '$');
		}
		// 连开头都不是'CGLIB$'的方法名，肯定不是CGLIB的重命名方法，直接返回false
		return false;
	}

	/**
	 * 使给定的方法可访问，在需要时显式设置它的可访问性。{@code setAccessible(true)}方法仅在实际需要时才会被调用，
	 * 以避免与JVM SecurityManager发生不必要的冲突（如果处于活动状态)
	 *
	 * Make the given method accessible, explicitly setting it accessible if
	 * necessary. The {@code setAccessible(true)} method is only called
	 * when actually necessary, to avoid unnecessary conflicts with a JVM
	 * SecurityManager (if active).
	 * @param method the method to make accessible
	 * @see java.lang.reflect.Method#setAccessible
	 */
	@SuppressWarnings("deprecation")  // on JDK 9
	public static void makeAccessible(Method method) {
		// 如果(method不是public或者method的声明类不是public)且method不可访问
		if ((!Modifier.isPublic(method.getModifiers()) ||
				!Modifier.isPublic(method.getDeclaringClass().getModifiers())) && !method.isAccessible()) {
			// 设置methid为可访问
			method.setAccessible(true);
		}
	}


	// Field handling

	/**
	 * 尝试去找到一个在提供的类和提供的属性名的{@link Field field}。搜寻所有的父类直到{@code Object}
	 *
	 * Attempt to find a {@link Field field} on the supplied {@link Class} with the
	 * supplied {@code name}. Searches all superclasses up to {@link Object}.
	 * @param clazz the class to introspect
	 * @param name the name of the field
	 * @return the corresponding Field object, or {@code null} if not found
	 */
	@Nullable
	public static Field findField(Class<?> clazz, String name) {
		return findField(clazz, name, null);
	}

	/**
	 * 尝试去找到一个在提供的类和提供的属性名和或者提供的类型的{@link Field field}。搜寻所有的父类直到{@code Object}
	 *
	 * Attempt to find a {@link Field field} on the supplied {@link Class} with the
	 * supplied {@code name} and/or {@link Class type}. Searches all superclasses
	 * up to {@link Object}.
	 * @param clazz the class to introspect
	 * @param name the name of the field (may be {@code null} if type is specified)
	 * @param type the type of the field (may be {@code null} if name is specified)
	 * @return the corresponding Field object, or {@code null} if not found
	 */
	@Nullable
	public static Field findField(Class<?> clazz, @Nullable String name, @Nullable Class<?> type) {
		// 如果clazz为null，抛出异常
		Assert.notNull(clazz, "Class must not be null");
		// 如果name和type都为null时，抛出异常
		Assert.isTrue(name != null || type != null, "Either name or type of the field must be specified");
		// 设置当前搜索类为clazz
		Class<?> searchType = clazz;
		// 只要searchType不是Object且searchType不为null，继续循环
		while (Object.class != searchType && searchType != null) {
			// 获取targetClass定义的所有属性
			Field[] fields = getDeclaredFields(searchType);
			// 遍历属性
			for (Field field : fields) {
				// 如果(name不为null或者name等于file的名称)
				if ((name == null || name.equals(field.getName())) &&
						(type == null || type.equals(field.getType()))) {
					return field;
				}
			}
			// 获取searchType的父类重新赋值给searchType，从父类中查找方法
			searchType = searchType.getSuperclass();
		}
		// 如果找不到方法时，返回null
		return null;
	}

	/**
	 * 将在指定的{@linkplain Object target object}上提供的{@linkplain Field file object}表示的字段设置为指定的{@code value}
	 *
	 * Set the field represented by the supplied {@linkplain Field field object} on
	 * the specified {@linkplain Object target object} to the specified {@code value}.
	 * <p>In accordance with {@link Field#set(Object, Object)} semantics, the new value
	 * is automatically unwrapped if the underlying field has a primitive type.
	 * <p>This method does not support setting {@code static final} fields.
	 * <p>Thrown exceptions are handled via a call to {@link #handleReflectionException(Exception)}.
	 * @param field the field to set
	 * @param target the target object on which to set the field
	 * @param value the value to set (may be {@code null})
	 */
	public static void setField(Field field, @Nullable Object target, @Nullable Object value) {
		try {
			// 将value设置到target的field里
			field.set(target, value);
		}
		catch (IllegalAccessException ex) {
			// 捕捉非法访问异常，并处理
			handleReflectionException(ex);
		}
	}

	/**
	 * 获取在指定的{@linkplain Object target object}上提供的{@linkplain Field file object}的属性值。
	 * 根据{@link Field#get(Object)}语义，如果基础字段具有原始类型，则返回值会自动包装。
	 *
	 * Get the field represented by the supplied {@link Field field object} on the
	 * specified {@link Object target object}. In accordance with {@link Field#get(Object)}
	 * semantics, the returned value is automatically wrapped if the underlying field
	 * has a primitive type.
	 * <p>Thrown exceptions are handled via a call to {@link #handleReflectionException(Exception)}.
	 * @param field the field to get
	 * @param target the target object from which to get the field
	 * @return the field's current value
	 */
	@Nullable
	public static Object getField(Field field, @Nullable Object target) {
		try {
			// 从target中获取field的属性值
			return field.get(target);
		}
		catch (IllegalAccessException ex) {
			// 处理给定的反射异常
			handleReflectionException(ex);
		}
		throw new IllegalStateException("Should never get here");
	}

	/**
	 * 在给定类中所有本地声明的字段上调用给定的回调
	 *
	 * Invoke the given callback on all locally declared fields in the given class.
	 * @param clazz the target class to analyze
	 * @param fc the callback to invoke for each field
	 * @throws IllegalStateException if introspection fails
	 * @since 4.2
	 * @see #doWithFields
	 */
	public static void doWithLocalFields(Class<?> clazz, FieldCallback fc) {
		// 遍历clazz中所有声明的属性
		for (Field field : getDeclaredFields(clazz)) {
			try {
				// 对field进行fc的回调操作
				fc.doWith(field);
			}
			catch (IllegalAccessException ex) {
				throw new IllegalStateException("Not allowed to access field '" + field.getName() + "': " + ex);
			}
		}
	}

	/**
	 * 对在目标类里的所有属性执行给定的回调操作，向上类层次结构，以获取所有声明的属性
	 *
	 * Invoke the given callback on all fields in the target class, going up the
	 * class hierarchy to get all declared fields.
	 * @param clazz the target class to analyze
	 * @param fc the callback to invoke for each field
	 * @throws IllegalStateException if introspection fails
	 */
	public static void doWithFields(Class<?> clazz, FieldCallback fc) {
		doWithFields(clazz, fc, null);
	}

	/**
	 * 对在目标类里的所有属性执行给定的回调操作，向上类层次结构，以获取所有声明的属性
	 *
	 * Invoke the given callback on all fields in the target class, going up the
	 * class hierarchy to get all declared fields.
	 * @param clazz the target class to analyze
	 * @param fc the callback to invoke for each field
	 * @param ff the filter that determines the fields to apply the callback to
	 * @throws IllegalStateException if introspection fails
	 */
	public static void doWithFields(Class<?> clazz, FieldCallback fc, @Nullable FieldFilter ff) {
		// Keep backing up the inheritance hierarchy.
		// 保持备份继续层次结构
		Class<?> targetClass = clazz;
		do {
			// 获取targetClass定义的所有属性
			Field[] fields = getDeclaredFields(targetClass);
			// 遍历所有属性
			for (Field field : fields) {
				// 如果ff不为null且field不满足ff的匹配要求
				if (ff != null && !ff.matches(field)) {
					// 跳过该属性
					continue;
				}
				try {
					// 对field执行回调操作
					fc.doWith(field);
				}
				catch (IllegalAccessException ex) {
					throw new IllegalStateException("Not allowed to access field '" + field.getName() + "': " + ex);
				}
			}
			// 获取targetClass的父类并重新赋值给targetClass，以实现向上类层次结构的遍历
			targetClass = targetClass.getSuperclass();
		}
		// 只要targetClass不为null且targetClass不是Object继续循环
		while (targetClass != null && targetClass != Object.class);
	}

	/**
	 * 使用一个本地缓存为了避免JVM的安全管理检查和新建Method实例。
	 *
	 * This variant retrieves {@link Class#getDeclaredFields()} from a local cache
	 * in order to avoid the JVM's SecurityManager check and defensive array copying.
	 * @param clazz the class to introspect
	 * @return the cached array of fields
	 * @throws IllegalStateException if introspection fails
	 * @see Class#getDeclaredFields()
	 */
	private static Field[] getDeclaredFields(Class<?> clazz) {
		// 如果clazz为null，抛出异常
		Assert.notNull(clazz, "Class must not be null");
		// 从缓存获取clazz的属性数组
		Field[] result = declaredFieldsCache.get(clazz);
		// 如果result为null，表示还没有加入缓存
		if (result == null) {
			try {
				// 获取clazz所有定义的属性
				result = clazz.getDeclaredFields();
				// 将result添加到缓存中，如果result里没有方法，就直接将EMPTY_FIELD_ARRAY添加到缓存中。这样做可以有效的减少内存消耗。
				// 因为将EMPTY_FIELD_ARRAY添加到缓存中，其实只不是缓存指向了EMPTY_FIELD_ARRAY的地址，而result是每次方法执行都是一个新的对象，
				// 而只是用于表示clazz没有方法，用同一对象一样可以表示出来就没有用result了
				declaredFieldsCache.put(clazz, (result.length == 0 ? EMPTY_FIELD_ARRAY : result));
			}
			catch (Throwable ex) {
				throw new IllegalStateException("Failed to introspect Class [" + clazz.getName() +
						"] from ClassLoader [" + clazz.getClassLoader() + "]", ex);
			}
		}
		// 返回clazz的属性数组
		return result;
	}

	/**
	 * 给定源对象和目标对象，必须是同一个类或者一个父类，赋值所有属性，包括遗传属性。设计用于使用公共无参构造函数处理对象
	 *
	 * Given the source object and the destination, which must be the same class
	 * or a subclass, copy all fields, including inherited fields. Designed to
	 * work on objects with public no-arg constructors.
	 * @throws IllegalStateException if introspection fails
	 */
	public static void shallowCopyFieldState(final Object src, final Object dest) {
		// 如果src为null，抛出异常
		Assert.notNull(src, "Source for field copy cannot be null");
		// 如果dest为null，抛出异常
		Assert.notNull(dest, "Destination for field copy cannot be null");
		// 如果src不是dest的子类或本类
		if (!src.getClass().isAssignableFrom(dest.getClass())) {
			throw new IllegalArgumentException("Destination class [" + dest.getClass().getName() +
					"] must be same or subclass as source class [" + src.getClass().getName() + "]");
		}
		// 执行属性的回调操作
		doWithFields(src.getClass(), field -> {
			// 让field变成可访问
			makeAccessible(field);
			// 获取src的field的属性值
			Object srcValue = field.get(src);
			// 将srcValue赋值给desc的field中
			field.set(dest, srcValue);
			// COPYABLE_FIELDS:匹配所有非静态，非final修饰的属性的预购建属性过滤器
		}, COPYABLE_FIELDS);
	}

	/**
	 * 确定给定的属性是否是'public static final'常量
	 *
	 * Determine whether the given field is a "public static final" constant.
	 * @param field the field to check
	 */
	public static boolean isPublicStaticFinal(Field field) {
		// 获取field的修饰符
		int modifiers = field.getModifiers();
		// 如果修饰符是'public' 且 又是'static' 且又是 'final' 就返回true；否则返回false
		return (Modifier.isPublic(modifiers) && Modifier.isStatic(modifiers) && Modifier.isFinal(modifiers));
	}

	/**
	 * 使给定的属性可访问，并在需要时显示设置它的可访问性。{@code setAccessible(true)}方法仅在实际需要时才会被调用，
	 * 以避免与JVM SecurityManager发生不必要的冲突（如果处于活动状态)
	 *
	 * Make the given field accessible, explicitly setting it accessible if
	 * necessary. The {@code setAccessible(true)} method is only called
	 * when actually necessary, to avoid unnecessary conflicts with a JVM
	 * SecurityManager (if active).
	 * @param field the field to make accessible
	 * @see java.lang.reflect.Field#setAccessible
	 */
	@SuppressWarnings("deprecation")  // on JDK 9
	public static void makeAccessible(Field field) {
		// 如果(fied不是'public'或者声明field的类不是是'public'或者field不是'final')且field不可访问
		if ((!Modifier.isPublic(field.getModifiers()) ||
				!Modifier.isPublic(field.getDeclaringClass().getModifiers()) ||
				Modifier.isFinal(field.getModifiers())) && !field.isAccessible()) {
			// 将field设置成可访问
			field.setAccessible(true);
		}
	}


	// Cache handling

	/**
	 * 清空内部方法/属性缓存
	 *
	 * Clear the internal method/field cache.
	 * @since 4.2.4
	 */
	public static void clearCache() {
		// 清空方法缓存
		declaredMethodsCache.clear();
		// 清空属性缓存
		declaredFieldsCache.clear();
	}


	/**
	 * 对每个方法采取的行为
	 *
	 * Action to take on each method.
	 */
	@FunctionalInterface
	public interface MethodCallback {

		/**
		 * 使用给定的方法执行操作
		 *
		 * Perform an operation using the given method.
		 * @param method the method to operate on
		 */
		void doWith(Method method) throws IllegalArgumentException, IllegalAccessException;
	}


	/**
	 * 回调可选，用于过滤由方法回调操作的方法
	 *
	 * Callback optionally used to filter methods to be operated on by a method callback.
	 */
	@FunctionalInterface
	public interface MethodFilter {

		/**
		 * 确定给定的方法是否匹配
		 *
		 * Determine whether the given method matches.
		 * @param method the method to check
		 */
		boolean matches(Method method);
	}


	/**
	 * 在层次结构中每个属性上调用的回调接口
	 *
	 * Callback interface invoked on each field in the hierarchy.
	 */
	@FunctionalInterface
	public interface FieldCallback {

		/**
		 * 使用给定的属性执行操作
		 *
		 * Perform an operation using the given field.
		 * @param field the field to operate on
		 */
		void doWith(Field field) throws IllegalArgumentException, IllegalAccessException;
	}


	/**
	 * 调用可选 用于过滤由属性回调操作的属性
	 *
	 * Callback optionally used to filter fields to be operated on by a field callback.
	 */
	@FunctionalInterface
	public interface FieldFilter {

		/**
		 * 确定给定的属性是否匹配
		 *
		 * Determine whether the given field matches.
		 * @param field the field to check
		 */
		boolean matches(Field field);
	}

}
