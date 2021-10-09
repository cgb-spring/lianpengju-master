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

package org.springframework.beans.factory.support;

import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.BeanCurrentlyInCreationException;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.FactoryBeanNotInitializedException;
import org.springframework.lang.Nullable;

/**
 * 支持单例注册表的基类，它需要处理FactoryBean实例，与DefaultSingletonBeanRegistry的单例管理集成
 *
 * Support base class for singleton registries which need to handle
 * {@link org.springframework.beans.factory.FactoryBean} instances,
 * integrated with {@link DefaultSingletonBeanRegistry}'s singleton management.
 *
 * <p>Serves as base class for {@link AbstractBeanFactory}.
 *
 * @author Juergen Hoeller
 * @since 2.5.1
 */
public abstract class FactoryBeanRegistrySupport extends DefaultSingletonBeanRegistry {

	/**
	 * 由FactoryBeans创建的单例对象的缓存:FactoryBean名称到对象
	 * Cache of singleton objects created by FactoryBeans: FactoryBean name to object. */
	private final Map<String, Object> factoryBeanObjectCache = new ConcurrentHashMap<>(16);


	/**
	 * 确定给定FactoryBean的创建出来的对象的类型,将调用factoryBean的getObjectType所得到结果返回出去
	 *
	 * Determine the type for the given FactoryBean.
	 * @param factoryBean the FactoryBean instance to check
	 * @return the FactoryBean's object type,
	 * or {@code null} if the type cannot be determined yet
	 */
	@Nullable
	protected Class<?> getTypeForFactoryBean(FactoryBean<?> factoryBean) {
		try {
			// 如果成功获取到系统的安全管理器
			if (System.getSecurityManager() != null) {
				// 使用特权的方式获取factoryBean的创建对象的类型并返回
				return AccessController.doPrivileged(
						(PrivilegedAction<Class<?>>) factoryBean::getObjectType, getAccessControlContext());
			}
			else {
				// 获取factoryBean的创建对象的类型并返回
				return factoryBean.getObjectType();
			}
		}
		// 捕获所有异常，一般在FactoryBean的getObjectType实现中抛出
		catch (Throwable ex) {
			// Thrown from the FactoryBean's getObjectType implementation.
			logger.info("FactoryBean threw exception from getObjectType, despite the contract saying " +
					"that it should return null if the type of its object cannot be determined yet", ex);
			return null;
		}
	}

	/**
	 * 从FactoryBean获得的对象缓存集中获取beanName对应的Bean对象
	 *
	 * Obtain an object to expose from the given FactoryBean, if available
	 * in cached form. Quick check for minimal synchronization.
	 * @param beanName the name of the bean
	 * @return the object obtained from the FactoryBean,
	 * or {@code null} if not available
	 */
	@Nullable
	protected Object getCachedObjectForFactoryBean(String beanName) {
		return this.factoryBeanObjectCache.get(beanName);
	}

	/**
	 * 获取一个对象以从给定的FactoryBean中公开
	 *
	 * Obtain an object to expose from the given FactoryBean.
	 * @param factory the FactoryBean instance
	 * @param beanName the name of the bean
	 * @param shouldPostProcess whether the bean is subject to post-processing
	 * @return the object obtained from the FactoryBean
	 * @throws BeanCreationException if FactoryBean object creation failed
	 * @see org.springframework.beans.factory.FactoryBean#getObject()
	 */
	protected Object getObjectFromFactoryBean(FactoryBean<?> factory, String beanName, boolean shouldPostProcess) {
		// 如果factory管理的对象是单例且beanName已经在该BeanFactory的单例对象的高速缓存Map集合DefaultListableBeanFactory.singletonObjects中
		if (factory.isSingleton() && containsSingleton(beanName)) {
			// 获取单例互斥体(一般使用singletonObjects)进行加锁,来保证线程安全
			synchronized (getSingletonMutex()) {
				// 获取beanName的Bean对象
				Object object = this.factoryBeanObjectCache.get(beanName);
				// 如果object为null
				if (object == null) {
					// 获取factory管理的对象实例并赋值给object
					object = doGetObjectFromFactoryBean(factory, beanName);
					// Only post-process and store if not put there already during getObject() call above
					// (e.g. because of circular reference processing triggered by custom getBean calls)
					// 仅在上面的getObject()调用期间进行后处理和存储(如果尚未放置)
					// (例如,由于自定义getBean调用触发的循环引用处理)
					// 重新从factoryBeanObjectCache中获取beanName对应bean对象
					Object alreadyThere = this.factoryBeanObjectCache.get(beanName);
					// 如果bean对象不为null
					if (alreadyThere != null) {
						// 让object引用alreadyThere
						object = alreadyThere;
					}
					else {
						// 如果要进行后处理
						if (shouldPostProcess) {
							// 如果beanName当前正在创建（在整个工厂内）
							if (isSingletonCurrentlyInCreation(beanName)) {
								// Temporarily return non-post-processed object, not storing it yet..
								// 暂时返回未处理的对象,尚未存储
								// 直接返回object
								return object;
							}
							// 创建单例之前的回调
							beforeSingletonCreation(beanName);
							try {
								// 对从FactoryBean获得的给定对象进行后处理.
								object = postProcessObjectFromFactoryBean(object, beanName);
							}
							// 捕捉所有在进行后处理的抛出的异常
							catch (Throwable ex) {
								// 抛出Bean创建异常:FactoryBean的单例对象的后处理失败
								throw new BeanCreationException(beanName,
										"Post-processing of FactoryBean's singleton object failed", ex);
							}
							finally {
								// 创建单例后的回调
								afterSingletonCreation(beanName);
							}
						}
						// beanName已经在该BeanFactory的单例对象的高速缓存Map集合DefaultListableBeanFactory.singletonObjects中
						if (containsSingleton(beanName)) {
							// 将beanName以及object添加到factoryBeanObjectCache中
							this.factoryBeanObjectCache.put(beanName, object);
						}
					}
				}
				// 返回factory管理的对象实例(该对象已经过工厂的后处理)
				return object;
			}
		}
		else {
			// 获取factory管理的对象实例
			Object object = doGetObjectFromFactoryBean(factory, beanName);
			// 如果要进行后处理
			if (shouldPostProcess) {
				try {
					// 对从FactoryBean获得的给定对象进行后处理
					object = postProcessObjectFromFactoryBean(object, beanName);
				}
				// 捕捉所有在进行后处理的抛出的异常
				catch (Throwable ex) {
					// 抛出Bean创建异常:FactoryBean的单例对象的后处理失败
					throw new BeanCreationException(beanName, "Post-processing of FactoryBean's object failed", ex);
				}
			}
			// 返回factory管理的对象实例(该对象已经过工厂的后处理)
			return object;
		}
	}

	/**
	 * 获取factory管理的对象实例
	 *
	 * Obtain an object to expose from the given FactoryBean.
	 * @param factory the FactoryBean instance
	 * @param beanName the name of the bean
	 * @return the object obtained from the FactoryBean
	 * @throws BeanCreationException if FactoryBean object creation failed
	 * @see org.springframework.beans.factory.FactoryBean#getObject()
	 */
	private Object doGetObjectFromFactoryBean(FactoryBean<?> factory, String beanName) throws BeanCreationException {
		// 定义一个用于保存factory管理的对象实例的变量
		Object object;
		try {
			// 如果有安全管理器
			if (System.getSecurityManager() != null) {
				// 获取访问控制的上下文对象
				AccessControlContext acc = getAccessControlContext();
				try {
					// 以特权方式运行来获取factory管理的对象实例赋值给object
					object = AccessController.doPrivileged((PrivilegedExceptionAction<Object>) factory::getObject, acc);
				}
				catch (PrivilegedActionException pae) {
					throw pae.getException();
				}
			}
			else {
				// 获取factory管理的对象实例赋值给object
				object = factory.getObject();
			}
		}
		catch (FactoryBeanNotInitializedException ex) {
			throw new BeanCurrentlyInCreationException(beanName, ex.toString());
		}
		catch (Throwable ex) {
			throw new BeanCreationException(beanName, "FactoryBean threw exception on object creation", ex);
		}

		// Do not accept a null value for a FactoryBean that's not fully
		// initialized yet: Many FactoryBeans just return null then.
		// 不接受尚未完全初始化的FactoryBean的null值:然后,许多FactoryBeans仅返回null
		// 如果object为null
		if (object == null) {
			// 如果 beanName当前正在创建（在整个工厂内）
			if (isSingletonCurrentlyInCreation(beanName)) {
				// 抛出当前正在创建Bean异常:当前正在创建的FactoryBean从getObject返回null
				throw new BeanCurrentlyInCreationException(
						beanName, "FactoryBean which is currently in creation returned null from getObject");
			}
			// 让object引用一个新的NullBean实例
			object = new NullBean();
		}
		// 返回factory管理的对象实例object
		return object;
	}

	/**
	 * 对从FactoryBean获得的给定对象进行后处理.生成的对象将暴露给Bean引用
	 *
	 * Post-process the given object that has been obtained from the FactoryBean.
	 * The resulting object will get exposed for bean references.
	 * <p>The default implementation simply returns the given object as-is.
	 * Subclasses may override this, for example, to apply post-processors.
	 * @param object the object obtained from the FactoryBean.
	 * @param beanName the name of the bean
	 * @return the object to expose
	 * @throws org.springframework.beans.BeansException if any post-processing failed
	 */
	protected Object postProcessObjectFromFactoryBean(Object object, String beanName) throws BeansException {
		return object;
	}

	/**
	 * Get a FactoryBean for the given bean if possible.
	 * @param beanName the name of the bean
	 * @param beanInstance the corresponding bean instance
	 * @return the bean instance as FactoryBean
	 * @throws BeansException if the given bean cannot be exposed as a FactoryBean
	 */
	protected FactoryBean<?> getFactoryBean(String beanName, Object beanInstance) throws BeansException {
		if (!(beanInstance instanceof FactoryBean)) {
			throw new BeanCreationException(beanName,
					"Bean instance of type [" + beanInstance.getClass() + "] is not a FactoryBean");
		}
		return (FactoryBean<?>) beanInstance;
	}

	/**
	 * 重写以清除FactoryBean对象缓存
	 *
	 * Overridden to clear the FactoryBean object cache as well.
	 */
	@Override
	protected void removeSingleton(String beanName) {
		// 获取单例互斥体，一般使用singletonObjects
		synchronized (getSingletonMutex()) {
			// 从该工厂单例缓存中删除具有给定名称的Bean。如果创建失败，则能够清理饿汉式注册 的单例
			super.removeSingleton(beanName);
			// factoryBeanObjectCache:由FactoryBeans创建的单例对象的缓存:
			// 删除beanName对应的factoryBean对象
			this.factoryBeanObjectCache.remove(beanName);
		}
	}

	/**
	 * Overridden to clear the FactoryBean object cache as well.
	 */
	@Override
	protected void clearSingletonCache() {
		synchronized (getSingletonMutex()) {
			super.clearSingletonCache();
			this.factoryBeanObjectCache.clear();
		}
	}

	/**
	 * 返回此bean工厂的安全上下文。如果设置了安全管理器，则将使用此方法返回的安全上下文的特权来执行与用户代码的交互
	 *
	 * Return the security context for this bean factory. If a security manager
	 * is set, interaction with the user code will be executed using the privileged
	 * of the security context returned by this method.
	 * @see AccessController#getContext()
	 */
	protected AccessControlContext getAccessControlContext() {
		return AccessController.getContext();
	}

}
