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

package org.springframework.beans.factory.support;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedExceptionAction;

import org.springframework.beans.BeanInstantiationException;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.lang.Nullable;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

/**
 * 简单的对象实例化策略
 *
 * Simple object instantiation strategy for use in a BeanFactory.
 *
 * <p>Does not support Method Injection, although it provides hooks for subclasses
 * to override to add Method Injection support, for example by overriding methods.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @since 1.1
 */
public class SimpleInstantiationStrategy implements InstantiationStrategy {

	// FactoryMethod的ThreadLocal对象
	private static final ThreadLocal<Method> currentlyInvokedFactoryMethod = new ThreadLocal<>();


	/**
	 * 返回当前现成所有的FactoryMethod变量值
	 *
	 * Return the factory method currently being invoked or {@code null} if none.
	 * <p>Allows factory method implementations to determine whether the current
	 * caller is the container itself as opposed to user code.
	 */
	@Nullable
	public static Method getCurrentlyInvokedFactoryMethod() {
		return currentlyInvokedFactoryMethod.get();
	}


	/**
	 * 如果发现是有方法重载的，就需要用cglib来动态代理，如果没有就直接获取默认构造方法实例化
	 *
	 * @param bd the bean definition
	 * @param beanName the name of the bean when it is created in this context.
	 * The name can be {@code null} if we are autowiring a bean which doesn't
	 * belong to the factory.
	 * @param owner the owning BeanFactory
	 * @return
	 */
	@Override
	public Object instantiate(RootBeanDefinition bd, @Nullable String beanName, BeanFactory owner) {
		// Don't override the class with CGLIB if no overrides.
		// bd对象定义中，是否包含MethodOverride列表，spring中有两个标签参数会产生MethodOverrides,分别是lookup-method,replaced-method
		// 没有MethodOverrides对象，可以直接实例化
		if (!bd.hasMethodOverrides()) {
			// 实例化对象的构造方法
			Constructor<?> constructorToUse;
			// 锁定对象，使获得实例化构造方法线程安全
			synchronized (bd.constructorArgumentLock) {
				// 查看bd对象里使用否含有构造方法
				constructorToUse = (Constructor<?>) bd.resolvedConstructorOrFactoryMethod;
				// 如果没有
				if (constructorToUse == null) {
					// 从bd中获取beanClass
					final Class<?> clazz = bd.getBeanClass();
					// 如果要实例化的beanDefinition是一个接口，则直接抛出异常
					if (clazz.isInterface()) {
						throw new BeanInstantiationException(clazz, "Specified class is an interface");
					}
					try {
						// 获取系统安全管理器
						if (System.getSecurityManager() != null) {
							constructorToUse = AccessController.doPrivileged(
									(PrivilegedExceptionAction<Constructor<?>>) clazz::getDeclaredConstructor);
						}
						else {
							// 获取默认的无参构造器
							constructorToUse = clazz.getDeclaredConstructor();
						}
						// 获取到构造器之后将构造器赋值给bd中的属性
						bd.resolvedConstructorOrFactoryMethod = constructorToUse;
					}
					catch (Throwable ex) {
						throw new BeanInstantiationException(clazz, "No default constructor found", ex);
					}
				}
			}
			// 通过反射生成具体的实例化对象
			return BeanUtils.instantiateClass(constructorToUse);
		}
		else {
			// Must generate CGLIB subclass.
			// 必须生成cglib子类
			return instantiateWithMethodInjection(bd, beanName, owner);
		}
	}

	/**
	 * 此方法没有做任何的实现，直接抛出异常，可以由子类重写覆盖
	 *
	 * Subclasses can override this method, which is implemented to throw
	 * UnsupportedOperationException, if they can instantiate an object with
	 * the Method Injection specified in the given RootBeanDefinition.
	 * Instantiation should use a no-arg constructor.
	 */
	protected Object instantiateWithMethodInjection(RootBeanDefinition bd, @Nullable String beanName, BeanFactory owner) {
		throw new UnsupportedOperationException("Method Injection not supported in SimpleInstantiationStrategy");
	}

	/**
	 * 使用有参构造方法进行实例化
	 * @param bd the bean definition
	 * @param beanName the name of the bean when it is created in this context.
	 * The name can be {@code null} if we are autowiring a bean which doesn't
	 * belong to the factory.
	 * @param owner the owning BeanFactory
	 * @param ctor the constructor to use
	 * @param args the constructor arguments to apply
	 * @return
	 */
	@Override
	public Object instantiate(RootBeanDefinition bd, @Nullable String beanName, BeanFactory owner,
			final Constructor<?> ctor, Object... args) {

		// 检查bd对象是否有MethodOverrides对象，没有的话则直接实例化对象
		if (!bd.hasMethodOverrides()) {
			if (System.getSecurityManager() != null) {
				// use own privileged to change accessibility (when security is on)
				AccessController.doPrivileged((PrivilegedAction<Object>) () -> {
					ReflectionUtils.makeAccessible(ctor);
					return null;
				});
			}
			// 通过反射实例化对象
			return BeanUtils.instantiateClass(ctor, args);
		}
		else {
			// 如果有methodOverride对象，则调用方法来进行实现
			return instantiateWithMethodInjection(bd, beanName, owner, ctor, args);
		}
	}

	/**
	 * 此方法没有做任何的实现，直接抛出异常，可以由子类重写覆盖
	 *
	 * Subclasses can override this method, which is implemented to throw
	 * UnsupportedOperationException, if they can instantiate an object with
	 * the Method Injection specified in the given RootBeanDefinition.
	 * Instantiation should use the given constructor and parameters.
	 */
	protected Object instantiateWithMethodInjection(RootBeanDefinition bd, @Nullable String beanName,
			BeanFactory owner, @Nullable Constructor<?> ctor, Object... args) {

		throw new UnsupportedOperationException("Method Injection not supported in SimpleInstantiationStrategy");
	}

	/**
	 * 使用工厂方法进行实例化
	 * @param bd the bean definition
	 * @param beanName the name of the bean when it is created in this context.
	 * The name can be {@code null} if we are autowiring a bean which doesn't
	 * belong to the factory.
	 * @param owner the owning BeanFactory
	 * @param factoryBean the factory bean instance to call the factory method on,
	 * or {@code null} in case of a static factory method
	 * @param factoryMethod the factory method to use
	 * @param args the factory method arguments to apply
	 * @return
	 */
	@Override
	public Object instantiate(RootBeanDefinition bd, @Nullable String beanName, BeanFactory owner,
			@Nullable Object factoryBean, final Method factoryMethod, Object... args) {

		try {
			// 是否包含系统安全管理器
			if (System.getSecurityManager() != null) {
				AccessController.doPrivileged((PrivilegedAction<Object>) () -> {
					ReflectionUtils.makeAccessible(factoryMethod);
					return null;
				});
			}
			else {
				// 通过反射工具类设置访问权限
				ReflectionUtils.makeAccessible(factoryMethod);
			}

			// 获取原有的Method对象
			Method priorInvokedFactoryMethod = currentlyInvokedFactoryMethod.get();
			try {
				// 设置当前的Method
				currentlyInvokedFactoryMethod.set(factoryMethod);
				// 使用factoryMethod实例化对象
				Object result = factoryMethod.invoke(factoryBean, args);
				if (result == null) {
					result = new NullBean();
				}
				return result;
			}
			finally {
				// 实例化完成后回复现场
				if (priorInvokedFactoryMethod != null) {
					currentlyInvokedFactoryMethod.set(priorInvokedFactoryMethod);
				}
				else {
					currentlyInvokedFactoryMethod.remove();
				}
			}
		}
		catch (IllegalArgumentException ex) {
			throw new BeanInstantiationException(factoryMethod,
					"Illegal arguments to factory method '" + factoryMethod.getName() + "'; " +
					"args: " + StringUtils.arrayToCommaDelimitedString(args), ex);
		}
		catch (IllegalAccessException ex) {
			throw new BeanInstantiationException(factoryMethod,
					"Cannot access factory method '" + factoryMethod.getName() + "'; is it public?", ex);
		}
		catch (InvocationTargetException ex) {
			String msg = "Factory method '" + factoryMethod.getName() + "' threw exception";
			if (bd.getFactoryBeanName() != null && owner instanceof ConfigurableBeanFactory &&
					((ConfigurableBeanFactory) owner).isCurrentlyInCreation(bd.getFactoryBeanName())) {
				msg = "Circular reference involving containing bean '" + bd.getFactoryBeanName() + "' - consider " +
						"declaring the factory method as static for independence from its containing instance. " + msg;
			}
			throw new BeanInstantiationException(factoryMethod, msg, ex.getTargetException());
		}
	}

}
