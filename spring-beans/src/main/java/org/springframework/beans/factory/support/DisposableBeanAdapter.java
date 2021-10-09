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

import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.DestructionAwareBeanPostProcessor;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

/**
 * 实际一次性Bean和可运行接口适配器，对给定Bean实例执行各种销毁步骤
 *
 * Adapter that implements the {@link DisposableBean} and {@link Runnable}
 * interfaces performing various destruction steps on a given bean instance:
 * <ul>
 * <li>DestructionAwareBeanPostProcessors;
 * <li>the bean implementing DisposableBean itself;
 * <li>a custom destroy method specified on the bean definition.
 * </ul>
 *
 * @author Juergen Hoeller
 * @author Costin Leau
 * @author Stephane Nicoll
 * @since 2.0
 * @see AbstractBeanFactory
 * @see org.springframework.beans.factory.DisposableBean
 * @see org.springframework.beans.factory.config.DestructionAwareBeanPostProcessor
 * @see AbstractBeanDefinition#getDestroyMethodName()
 */
@SuppressWarnings("serial")
class DisposableBeanAdapter implements DisposableBean, Runnable, Serializable {

	private static final String CLOSE_METHOD_NAME = "close";

	private static final String SHUTDOWN_METHOD_NAME = "shutdown";

	private static final Log logger = LogFactory.getLog(DisposableBeanAdapter.class);


	private final Object bean;

	private final String beanName;

	/**
	 * bean是否是DisposableBean实例&&'destroy'没有受外部管理的销毁方法的标记
	 */
	private final boolean invokeDisposableBean;

	/**
	 * beanDefinition 是否允许访问非公共构造函数和方法标记
	 */
	private final boolean nonPublicAccessAllowed;

	@Nullable
	private final AccessControlContext acc;

	@Nullable
	private String destroyMethodName;

	@Nullable
	private transient Method destroyMethod;

	@Nullable
	private List<DestructionAwareBeanPostProcessor> beanPostProcessors;


	/**
	 * Create a new DisposableBeanAdapter for the given bean.
	 * @param bean the bean instance (never {@code null})
	 * @param beanName the name of the bean
	 * @param beanDefinition the merged bean definition
	 * @param postProcessors the List of BeanPostProcessors
	 * (potentially DestructionAwareBeanPostProcessor), if any
	 */
	public DisposableBeanAdapter(Object bean, String beanName, RootBeanDefinition beanDefinition,
			List<BeanPostProcessor> postProcessors, @Nullable AccessControlContext acc) {

		Assert.notNull(bean, "Disposable bean must not be null");
		this.bean = bean;
		this.beanName = beanName;
		// bean是否是DisposableBean实例&&'destroy'没有受外部管理的销毁方法
		this.invokeDisposableBean =
				(this.bean instanceof DisposableBean && !beanDefinition.isExternallyManagedDestroyMethod("destroy"));
		// beanDefinition是否允许访问非公共构造函数和方法
		this.nonPublicAccessAllowed = beanDefinition.isNonPublicAccessAllowed();
		this.acc = acc;
		// 根据需要推断破环方法名
		String destroyMethodName = inferDestroyMethodIfNecessary(bean, beanDefinition);
		// 如果destroyMethodName不为null&&(bean是否是DisposableBean实例&&'destory'没有受外部管理的销毁方法
		// &&destroyMethodName是'destroy') &&destroyMethodName不是外部受管理的销毁方法
		if (destroyMethodName != null && !(this.invokeDisposableBean && "destroy".equals(destroyMethodName)) &&
				!beanDefinition.isExternallyManagedDestroyMethod(destroyMethodName)) {
			this.destroyMethodName = destroyMethodName;
			// 根据beanDefinition是否允许访问非公共构造函数和方法的情况来查找最小参数(最好是none)的销毁方法对象
			Method destroyMethod = determineDestroyMethod(destroyMethodName);
			// 如果destroyMethod为null
			if (destroyMethod == null) {
				// 如果beanDefinition配置的destroy方法为默认方法
				if (beanDefinition.isEnforceDestroyMethod()) {
					throw new BeanDefinitionValidationException("Could not find a destroy method named '" +
							destroyMethodName + "' on bean with name '" + beanName + "'");
				}
			}
			else {
				// 获取destroyMethod的参数类型数组
				Class<?>[] paramTypes = destroyMethod.getParameterTypes();
				// 如果参数类型数组大于1
				if (paramTypes.length > 1) {
					throw new BeanDefinitionValidationException("Method '" + destroyMethodName + "' of bean '" +
							beanName + "' has more than one parameter - not supported as destroy method");
				}
				// 参数类型数组为1&&第一个参数类型不时Boolean类
				else if (paramTypes.length == 1 && boolean.class != paramTypes[0]) {
					throw new BeanDefinitionValidationException("Method '" + destroyMethodName + "' of bean '" +
							beanName + "' has a non-boolean parameter - not supported as destroy method");
				}
				// 获取destroyMethod相应的接口方法对象，如果找不到，则返回原始方法
				destroyMethod = ClassUtils.getInterfaceMethodIfPossible(destroyMethod);
			}
			this.destroyMethod = destroyMethod;
		}
		// 搜索列表中的所有可支持Bean销毁的DestructionAwareBeanPostProcessors
		this.beanPostProcessors = filterPostProcessors(postProcessors, bean);
	}

	/**
	 * Create a new DisposableBeanAdapter for the given bean.
	 * @param bean the bean instance (never {@code null})
	 * @param postProcessors the List of BeanPostProcessors
	 * (potentially DestructionAwareBeanPostProcessor), if any
	 */
	public DisposableBeanAdapter(Object bean, List<BeanPostProcessor> postProcessors, AccessControlContext acc) {
		Assert.notNull(bean, "Disposable bean must not be null");
		this.bean = bean;
		this.beanName = bean.getClass().getName();
		this.invokeDisposableBean = (this.bean instanceof DisposableBean);
		this.nonPublicAccessAllowed = true;
		this.acc = acc;
		this.beanPostProcessors = filterPostProcessors(postProcessors, bean);
	}

	/**
	 * Create a new DisposableBeanAdapter for the given bean.
	 */
	private DisposableBeanAdapter(Object bean, String beanName, boolean invokeDisposableBean,
			boolean nonPublicAccessAllowed, @Nullable String destroyMethodName,
			@Nullable List<DestructionAwareBeanPostProcessor> postProcessors) {

		this.bean = bean;
		this.beanName = beanName;
		this.invokeDisposableBean = invokeDisposableBean;
		this.nonPublicAccessAllowed = nonPublicAccessAllowed;
		this.acc = null;
		this.destroyMethodName = destroyMethodName;
		this.beanPostProcessors = postProcessors;
	}


	/**
	 * 根据需要推断破环方法
	 *
	 * If the current value of the given beanDefinition's "destroyMethodName" property is
	 * {@link AbstractBeanDefinition#INFER_METHOD}, then attempt to infer a destroy method.
	 * Candidate methods are currently limited to public, no-arg methods named "close" or
	 * "shutdown" (whether declared locally or inherited). The given BeanDefinition's
	 * "destroyMethodName" is updated to be null if no such method is found, otherwise set
	 * to the name of the inferred method. This constant serves as the default for the
	 * {@code @Bean#destroyMethod} attribute and the value of the constant may also be
	 * used in XML within the {@code <bean destroy-method="">} or {@code
	 * <beans default-destroy-method="">} attributes.
	 * <p>Also processes the {@link java.io.Closeable} and {@link java.lang.AutoCloseable}
	 * interfaces, reflectively calling the "close" method on implementing beans as well.
	 */
	@Nullable
	private String inferDestroyMethodIfNecessary(Object bean, RootBeanDefinition beanDefinition) {
		// 获取销毁方法名
		String destroyMethodName = beanDefinition.getDestroyMethodName();
		// destroyMethodName是'(inferred)'||(destroyMethodName为null&&bean是AutoCloseable实例)
		if (AbstractBeanDefinition.INFER_METHOD.equals(destroyMethodName) ||
				(destroyMethodName == null && bean instanceof AutoCloseable)) {
			// Only perform destroy method inference or Closeable detection
			// in case of the bean not explicitly implementing DisposableBean
			// 只在Bean没有显示实现DisposableBean的情况下执行销毁方法推断或关闭检测
			// 如果bean不是DisposableBean实例
			if (!(bean instanceof DisposableBean)) {
				try {
					// 获取bean的'close'公共方法
					return bean.getClass().getMethod(CLOSE_METHOD_NAME).getName();
				}
				// 如果没有找到
				catch (NoSuchMethodException ex) {
					try {
						// 获取bean的'shutdown'公共方法
						return bean.getClass().getMethod(SHUTDOWN_METHOD_NAME).getName();
					}
					catch (NoSuchMethodException ex2) {
						// no candidate destroy method found
						// 没有找到候选的销毁方法
					}
				}
			}
			// 没有找到候选的销毁方法时，返回null
			return null;
		}
		// 如果destroyMethodName不是空字符，就返回destroyMethodName,否则返回null
		return (StringUtils.hasLength(destroyMethodName) ? destroyMethodName : null);
	}

	/**
	 * 搜索列表中的所有可支持Bean销毁的DestructionAwareBeanPostProcessors
	 *
	 * Search for all DestructionAwareBeanPostProcessors in the List.
	 * @param processors the List to search
	 * @return the filtered List of DestructionAwareBeanPostProcessors
	 */
	@Nullable
	private List<DestructionAwareBeanPostProcessor> filterPostProcessors(List<BeanPostProcessor> processors, Object bean) {
		// 定义已过滤出DestructionAwareBeanPostProcessors的列表
		List<DestructionAwareBeanPostProcessor> filteredPostProcessors = null;
		// 如果processors不为空列表
		if (!CollectionUtils.isEmpty(processors)) {
			// 实例化filteredPostProcessors
			filteredPostProcessors = new ArrayList<>(processors.size());
			// 遍历processors
			for (BeanPostProcessor processor : processors) {
				// 如果processor是DestructionAwareBeanPostProcessor实例
				if (processor instanceof DestructionAwareBeanPostProcessor) {
					DestructionAwareBeanPostProcessor dabpp = (DestructionAwareBeanPostProcessor) processor;
					// 如果bean是否需要由这个后处理其销毁
					if (dabpp.requiresDestruction(bean)) {
						// 将dabpp添加到filteredPostProcessors中
						filteredPostProcessors.add(dabpp);
					}
				}
			}
		}
		return filteredPostProcessors;
	}


	@Override
	public void run() {
		destroy();
	}

	@Override
	public void destroy() {
		if (!CollectionUtils.isEmpty(this.beanPostProcessors)) {
			for (DestructionAwareBeanPostProcessor processor : this.beanPostProcessors) {
				processor.postProcessBeforeDestruction(this.bean, this.beanName);
			}
		}

		if (this.invokeDisposableBean) {
			if (logger.isTraceEnabled()) {
				logger.trace("Invoking destroy() on bean with name '" + this.beanName + "'");
			}
			try {
				if (System.getSecurityManager() != null) {
					AccessController.doPrivileged((PrivilegedExceptionAction<Object>) () -> {
						((DisposableBean) this.bean).destroy();
						return null;
					}, this.acc);
				}
				else {
					((DisposableBean) this.bean).destroy();
				}
			}
			catch (Throwable ex) {
				String msg = "Invocation of destroy method failed on bean with name '" + this.beanName + "'";
				if (logger.isDebugEnabled()) {
					logger.warn(msg, ex);
				}
				else {
					logger.warn(msg + ": " + ex);
				}
			}
		}

		if (this.destroyMethod != null) {
			invokeCustomDestroyMethod(this.destroyMethod);
		}
		else if (this.destroyMethodName != null) {
			Method methodToInvoke = determineDestroyMethod(this.destroyMethodName);
			if (methodToInvoke != null) {
				invokeCustomDestroyMethod(ClassUtils.getInterfaceMethodIfPossible(methodToInvoke));
			}
		}
	}


	/**
	 *根据beanDefinition是否允许访问非公共构造函数和方法的情况来查找最小参数(最好是none)的销毁方法对象
	 * @param name 销毁方法名
	 */
	@Nullable
	private Method determineDestroyMethod(String name) {
		try {
			// 如果安全管理器不为null
			if (System.getSecurityManager() != null) {
				// 以特权方式根据beanDefinition是否允许访问非公共构造函数和方法的情况来查找最小参数(最好是none)的销毁方法对象
				return AccessController.doPrivileged((PrivilegedAction<Method>) () -> findDestroyMethod(name));
			}
			else {
				// 根据beanDefinition是否允许访问非公共构造函数和方法的情况来查找最小参数(最好是none)的销毁方法对象
				return findDestroyMethod(name);
			}
		}
		catch (IllegalArgumentException ex) {
			// 捕捉查找方法对象时抛出的非法参数异常
			// 重新抛出BeanDefinition验证异常：不能找到唯一销毁方法在名为'beanName'的Bean对象中：ex异常信息
			throw new BeanDefinitionValidationException("Could not find unique destroy method on bean with name '" +
					this.beanName + ": " + ex.getMessage());
		}
	}

	/**
	 * 根据beanDefinition是否允许访问非公共构造函数和方法的情况来查找最小参数(最好是none)的销毁方法对象
	 * @param name 销毁方法名
	 * @return 销毁方法对象
	 */
	@Nullable
	private Method findDestroyMethod(String name) {
		// findMethodWithMinimalParameters(Class<?> clazz,String methodName)：找到一个具有给定方法名和最小参数(最好是none)的
		// 方法，在给定的类或它的超类中声明。 首选公共方法，但也将返回受保护的包访问或私有访问
		// BeanUtils.findMethodWithMinimalParameters(Method[] methods, String methodName)：在给定方法列表中找到一个具有给定
		// 方法名和最小参数(最好是无)的方法
		// 根据beanDefinition是否允许访问非公共构造函数和方法的情况来查找最小参数(最好是none)的销毁方法对象
		return (this.nonPublicAccessAllowed ?
				BeanUtils.findMethodWithMinimalParameters(this.bean.getClass(), name) :
				BeanUtils.findMethodWithMinimalParameters(this.bean.getClass().getMethods(), name));
	}

	/**
	 * Invoke the specified custom destroy method on the given bean.
	 * <p>This implementation invokes a no-arg method if found, else checking
	 * for a method with a single boolean argument (passing in "true",
	 * assuming a "force" parameter), else logging an error.
	 */
	private void invokeCustomDestroyMethod(final Method destroyMethod) {
		int paramCount = destroyMethod.getParameterCount();
		final Object[] args = new Object[paramCount];
		if (paramCount == 1) {
			args[0] = Boolean.TRUE;
		}
		if (logger.isTraceEnabled()) {
			logger.trace("Invoking destroy method '" + this.destroyMethodName +
					"' on bean with name '" + this.beanName + "'");
		}
		try {
			if (System.getSecurityManager() != null) {
				AccessController.doPrivileged((PrivilegedAction<Object>) () -> {
					ReflectionUtils.makeAccessible(destroyMethod);
					return null;
				});
				try {
					AccessController.doPrivileged((PrivilegedExceptionAction<Object>) () ->
						destroyMethod.invoke(this.bean, args), this.acc);
				}
				catch (PrivilegedActionException pax) {
					throw (InvocationTargetException) pax.getException();
				}
			}
			else {
				ReflectionUtils.makeAccessible(destroyMethod);
				destroyMethod.invoke(this.bean, args);
			}
		}
		catch (InvocationTargetException ex) {
			String msg = "Destroy method '" + this.destroyMethodName + "' on bean with name '" +
					this.beanName + "' threw an exception";
			if (logger.isDebugEnabled()) {
				logger.warn(msg, ex.getTargetException());
			}
			else {
				logger.warn(msg + ": " + ex.getTargetException());
			}
		}
		catch (Throwable ex) {
			logger.warn("Failed to invoke destroy method '" + this.destroyMethodName +
					"' on bean with name '" + this.beanName + "'", ex);
		}
	}


	/**
	 * Serializes a copy of the state of this class,
	 * filtering out non-serializable BeanPostProcessors.
	 */
	protected Object writeReplace() {
		List<DestructionAwareBeanPostProcessor> serializablePostProcessors = null;
		if (this.beanPostProcessors != null) {
			serializablePostProcessors = new ArrayList<>();
			for (DestructionAwareBeanPostProcessor postProcessor : this.beanPostProcessors) {
				if (postProcessor instanceof Serializable) {
					serializablePostProcessors.add(postProcessor);
				}
			}
		}
		return new DisposableBeanAdapter(this.bean, this.beanName, this.invokeDisposableBean,
				this.nonPublicAccessAllowed, this.destroyMethodName, serializablePostProcessors);
	}


	/**
	 * 检查bean是否有destroy方法
	 *
	 * Check whether the given bean has any kind of destroy method to call.
	 * @param bean the bean instance
	 * @param beanDefinition the corresponding bean definition
	 */
	public static boolean hasDestroyMethod(Object bean, RootBeanDefinition beanDefinition) {
		// DisposableBean:要在销毁时释放资源的bean所实现的接口
		// 如果bean时DisposableBean实例||bean是AutoClosable实例
		if (bean instanceof DisposableBean || bean instanceof AutoCloseable) {
			// 返回true
			return true;
		}
		// 获取销毁方法
		String destroyMethodName = beanDefinition.getDestroyMethodName();
		// AbstractBeanDefinition.INFER_METHOD：常量，指示容器应该尝试推断Bean的销毁方法名，而不是显示地指定方法名。
		// 值'(inferred)'是专门设计 用来在方法名中包含非法字符的，以确保不会与合法命名的同名方法发生冲突。目前，
		// 在销毁方法推断过程中检测到方法名'close'和'shutdown'(如果存在特点Bean类上的话)
		// 如果destroyMethodName是'(inferred)'
		if (AbstractBeanDefinition.INFER_METHOD.equals(destroyMethodName)) {
			// 确定bean类是否具有close/shutdown的公共方法 ，将其结果返回出去
			return (ClassUtils.hasMethod(bean.getClass(), CLOSE_METHOD_NAME) ||
					ClassUtils.hasMethod(bean.getClass(), SHUTDOWN_METHOD_NAME));
		}
		// 返回destroyMethodName是否即不是null又不是长度为0的结果
		return StringUtils.hasLength(destroyMethodName);
	}

	/**
	 * 检查给定Bean是否有应用于它的可识别销毁的后处理器
	 *
	 * Check whether the given bean has destruction-aware post-processors applying to it.
	 * @param bean the bean instance
	 * @param postProcessors the post-processor candidates
	 */
	public static boolean hasApplicableProcessors(Object bean, List<BeanPostProcessor> postProcessors) {
		// 如果postProcessors不是空数组
		if (!CollectionUtils.isEmpty(postProcessors)) {
			// 遍历postProcessors
			for (BeanPostProcessor processor : postProcessors) {
				// 如果processor是DestructionAwareBeanPostProcessor实例
				if (processor instanceof DestructionAwareBeanPostProcessor) {
					DestructionAwareBeanPostProcessor dabpp = (DestructionAwareBeanPostProcessor) processor;
					// 确定bean是否需要由dabpp销毁
					if (dabpp.requiresDestruction(bean)) {
						// 是就返回true
						return true;
					}
				}
			}
		}
		// 默认返回false
		return false;
	}

}
