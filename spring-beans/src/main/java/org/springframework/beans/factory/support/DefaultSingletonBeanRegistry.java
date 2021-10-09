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

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.BeanCreationNotAllowedException;
import org.springframework.beans.factory.BeanCurrentlyInCreationException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.config.SingletonBeanRegistry;
import org.springframework.core.SimpleAliasRegistry;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * 对接口SingletonBeanRegistry各函数的实现
 *
 * 共享bean实例的通用注册表，实现了SingletonBeanRegistry。允许注册单例实例，
 * 该实例应该为注册中心的所有调用者共享，并通过bean名称获得。还支持一次性bean实例
 * 的注册(它可能对应于已注册的单例，也可能不对应于已注册的单例)，在注册表关闭时销毁。
 * 可以注册bean之间的依赖关系，以强制执行适当的关闭顺序
 *
 * Generic registry for shared bean instances, implementing the
 * {@link org.springframework.beans.factory.config.SingletonBeanRegistry}.
 * Allows for registering singleton instances that should be shared
 * for all callers of the registry, to be obtained via bean name.
 *
 * <p>Also supports registration of
 * {@link org.springframework.beans.factory.DisposableBean} instances,
 * (which might or might not correspond to registered singletons),
 * to be destroyed on shutdown of the registry. Dependencies between
 * beans can be registered to enforce an appropriate shutdown order.
 *
 * <p>This class mainly serves as base class for
 * {@link org.springframework.beans.factory.BeanFactory} implementations,
 * factoring out the common management of singleton bean instances. Note that
 * the {@link org.springframework.beans.factory.config.ConfigurableBeanFactory}
 * interface extends the {@link SingletonBeanRegistry} interface.
 *
 * <p>Note that this class assumes neither a bean definition concept
 * nor a specific creation process for bean instances, in contrast to
 * {@link AbstractBeanFactory} and {@link DefaultListableBeanFactory}
 * (which inherit from it). Can alternatively also be used as a nested
 * helper to delegate to.
 *
 * @author Juergen Hoeller
 * @since 2.0
 * @see #registerSingleton
 * @see #registerDisposableBean
 * @see org.springframework.beans.factory.DisposableBean
 * @see org.springframework.beans.factory.config.ConfigurableBeanFactory
 */
public class DefaultSingletonBeanRegistry extends SimpleAliasRegistry implements SingletonBeanRegistry {

	/** Maximum number of suppressed exceptions to preserve. */
	private static final int SUPPRESSED_EXCEPTIONS_LIMIT = 100;


	/**
	 * 一级缓存
	 * 用于保存BeanName和创建bean实例之间的关系
	 *
	 * Cache of singleton objects: bean name to bean instance. */
//	private final Map<String, Object> singletonObjects = new ConcurrentHashMap<>(256);
	public final Map<String, Object> singletonObjects = new ConcurrentHashMap<>(256);

	/**
	 * 三级缓存
	 * 用于保存BeanName和创建bean的工厂之间的关系
	 *
	 * Cache of singleton factories: bean name to ObjectFactory. */
//	private final Map<String, ObjectFactory<?>> singletonFactories = new HashMap<>(16);
	public final Map<String, ObjectFactory<?>> singletonFactories = new HashMap<>(16);

	/**
	 * 二级缓存
	 * 保存BeanName和创建bean实例之间的关系，与singletonFactories的不同之处在于，当一个单例bean被放到这里之后，那么当bean还在创建过程中
	 * 就可以通过getBean方法获取到，可以方便进行循环依赖的检测
	 *
	 * Cache of early singleton objects: bean name to bean instance. */
//	private final Map<String, Object> earlySingletonObjects = new ConcurrentHashMap<>(16);
	public final Map<String, Object> earlySingletonObjects = new ConcurrentHashMap<>(16);

	/**
	 * 用来保存当前所有已经注册的bean
	 *
	 * Set of registered singletons, containing the bean names in registration order. */
//	private final Set<String> registeredSingletons = new LinkedHashSet<>(256);
	public final Set<String> registeredSingletons = new LinkedHashSet<>(256);

	/**
	 * 正在创建过程中的beanName集合
	 *
	 * Names of beans that are currently in creation. */
	private final Set<String> singletonsCurrentlyInCreation =
			Collections.newSetFromMap(new ConcurrentHashMap<>(16));

	/**
	 * 当前在创建检查中排除的bean名
	 *
	 * Names of beans currently excluded from in creation checks. */
	private final Set<String> inCreationCheckExclusions =
			Collections.newSetFromMap(new ConcurrentHashMap<>(16));

	/**
	 * 抑制的异常列表，可用于关联相关原因
	 *
	 * Collection of suppressed Exceptions, available for associating related causes. */
	@Nullable
	private Set<Exception> suppressedExceptions;

	/**
	 * 指示我们当前是否在destroySingletons中的标志
	 *
	 * Flag that indicates whether we're currently within destroySingletons. */
	private boolean singletonsCurrentlyInDestruction = false;

	/**
	 * 一次性Bean实例：bean名称 - DisposableBean实例。
	 * Disposable bean instances: bean name to disposable instance. */
	private final Map<String, Object> disposableBeans = new LinkedHashMap<>();

	/**
	 * 在包含的Bean名称之间映射：bean名称 - Bean包含的Bean名称集
	 *
	 * Map between containing bean names: bean name to Set of bean names that the bean contains. */
	private final Map<String, Set<String>> containedBeanMap = new ConcurrentHashMap<>(16);

	/**
	 * 存储 bean名到该bean名所要依赖的bean名 的Map
	 * Map between dependent bean names: bean name to Set of dependent bean names. */
	private final Map<String, Set<String>> dependentBeanMap = new ConcurrentHashMap<>(64);

	/**
	 * 存储 bean名到依赖于该bean名的bean名 的Map
	 * Map between depending bean names: bean name to Set of bean names for the bean's dependencies. */
	private final Map<String, Set<String>> dependenciesForBeanMap = new ConcurrentHashMap<>(64);


	/**
	 * 在给定的bean名称下，在bean注册器中将给定的现有对象注册为单例
	 *
	 * @param beanName the name of the bean
	 * @param singletonObject the existing singleton object
	 * @throws IllegalStateException
	 */
	@Override
	public void registerSingleton(String beanName, Object singletonObject) throws IllegalStateException {
		Assert.notNull(beanName, "Bean name must not be null");
		Assert.notNull(singletonObject, "Singleton object must not be null");
		// 使用singletonObjects作为锁，保证线程安全
		synchronized (this.singletonObjects) {
			// 获取beanName在singletonObjects中的单例对象
			Object oldObject = this.singletonObjects.get(beanName);
			// 如果成功获得对象
			if (oldObject != null) {
				// 非法状态异常：不能注册对象[singletonObject]，在bean名'beanName'下，已经有对象[oldObject]
				throw new IllegalStateException("Could not register object [" + singletonObject +
						"] under bean name '" + beanName + "': there is already object [" + oldObject + "] bound");
			}
			// 将beanName和singletonObject的映射关系添加到该工厂的单例缓存中
			addSingleton(beanName, singletonObject);
		}
	}

	/**
	 * 将beanName和singletonObject的映射关系添加到该工厂的单例缓存中
	 *
	 * Add the given singleton object to the singleton cache of this factory.
	 * <p>To be called for eager registration of singletons.
	 * @param beanName the name of the bean
	 * @param singletonObject the singleton object
	 */
	protected void addSingleton(String beanName, Object singletonObject) {
		synchronized (this.singletonObjects) {
			// 将映射关系添加到单例对象的高速缓存中
			this.singletonObjects.put(beanName, singletonObject);
			// 移除beanName在单例工厂缓存中的数据
			this.singletonFactories.remove(beanName);
			// 移除beanName在早期单例对象的高速缓存的数据
			this.earlySingletonObjects.remove(beanName);
			// 将beanName添加到已注册的单例集中
			this.registeredSingletons.add(beanName);
		}
	}

	/**
	 * 如果需要，添加给定的单例对象工厂来构建指定的单例对象
	 *
	 * Add the given singleton factory for building the specified singleton
	 * if necessary.
	 * <p>To be called for eager registration of singletons, e.g. to be able to
	 * resolve circular references.
	 * @param beanName the name of the bean
	 * @param singletonFactory the factory for the singleton object
	 */
	protected void addSingletonFactory(String beanName, ObjectFactory<?> singletonFactory) {
		Assert.notNull(singletonFactory, "Singleton factory must not be null");
		// 使用singletonObjects进行加锁，保证线程安全
		synchronized (this.singletonObjects) {
			// 如果单例对象的高速缓存【beam名称-bean实例】没有beanName的对象
			if (!this.singletonObjects.containsKey(beanName)) {
				// 将beanName,singletonFactory放到单例工厂的缓存【bean名称 - ObjectFactory】
				this.singletonFactories.put(beanName, singletonFactory);
				// 从早期单例对象的高速缓存【bean名称-bean实例】 移除beanName的相关缓存对象
				this.earlySingletonObjects.remove(beanName);
				// 将beanName添加已注册的单例集中
				this.registeredSingletons.add(beanName);
			}
		}
	}

	/**
	 * 获取beanName的单例对象，并允许创建早期引用
	 * @param beanName the name of the bean to look for
	 * @return
	 */
	@Override
	@Nullable
	public Object getSingleton(String beanName) {
		// 获取beanName的单例对象，并允许创建早期引用
		return getSingleton(beanName, true);
	}

	/**
	 * 获取以beanName注册的(原始)单例对象
	 *
	 * Return the (raw) singleton object registered under the given name.
	 * <p>Checks already instantiated singletons and also allows for an early
	 * reference to a currently created singleton (resolving a circular reference).
	 * @param beanName the name of the bean to look for
	 * @param allowEarlyReference whether early references should be created or not
	 * @return the registered singleton object, or {@code null} if none found
	 */
	@Nullable
	protected Object getSingleton(String beanName, boolean allowEarlyReference) {
		// Quick check for existing instance without full singleton lock
		// 从单例对象缓存中获取beanName对应的单例对象
		Object singletonObject = this.singletonObjects.get(beanName);
		// 如果单例对象缓存中没有，并且该beanName对应的单例bean正在创建中
		if (singletonObject == null && isSingletonCurrentlyInCreation(beanName)) {
			//从早期单例对象缓存中获取单例对象（之所称成为早期单例对象，是因为earlySingletonObjects里
			// 的对象的都是通过提前曝光的ObjectFactory创建出来的，还未进行属性填充等操作）
			singletonObject = this.earlySingletonObjects.get(beanName);
			// 如果在早期单例对象缓存中也没有，并且允许创建早期单例对象引用
			if (singletonObject == null && allowEarlyReference) {
				// 如果为空，则锁定全局变量并进行处理
				synchronized (this.singletonObjects) {
					// Consistent creation of early reference within full singleton lock
					singletonObject = this.singletonObjects.get(beanName);
					if (singletonObject == null) {
						singletonObject = this.earlySingletonObjects.get(beanName);
						if (singletonObject == null) {
							// 当某些方法需要提前初始化的时候则会调用addSingletonFactory方法将对应的ObjectFactory初始化策略存储在singletonFactories
							ObjectFactory<?> singletonFactory = this.singletonFactories.get(beanName);
							if (singletonFactory != null) {
								// 如果存在单例对象工厂，则通过工厂创建一个单例对象
								singletonObject = singletonFactory.getObject();
								// 记录在缓存中，二级缓存和三级缓存的对象不能同时存在
								this.earlySingletonObjects.put(beanName, singletonObject);
								// 从三级缓存中移除
								this.singletonFactories.remove(beanName);
							}
						}
					}
				}
			}
		}
		return singletonObject;
	}

//	@Nullable
//	protected Object getSingleton(String beanName, boolean allowEarlyReference) {
//		Object singletonObject = this.singletonObjects.get(beanName);
//		if (singletonObject == null && isSingletonCurrentlyInCreation(beanName)) {
//				synchronized (this.singletonObjects) {
//					singletonObject = this.earlySingletonObjects.get(beanName);
//					return singletonObject;
//				}
//			}
//		return singletonObject != null ? singletonObject:null;
//	}

//	@Nullable
//	protected Object getSingleton(String beanName, boolean allowEarlyReference) {
//		// Quick check for existing instance without full singleton lock
//		Object singletonObject = this.singletonObjects.get(beanName);
//		if (singletonObject == null && isSingletonCurrentlyInCreation(beanName) && allowEarlyReference) {
//			//防止期间有别的线程更新一级缓存
//			synchronized (this.singletonObjects) {
//				// Consistent creation of early reference within full singleton lock
//				singletonObject = this.singletonObjects.get(beanName);
//				if (singletonObject == null) {
//					//访问三级缓存
//					ObjectFactory<?> singletonFactory = this.singletonFactories.get(beanName);
//					return singletonFactory.getObject();
//				}
//			}
//		}
//		return singletonObject;
//	}

	/**
	 * 返回以给定名称注册的(原始)单例对象，如果尚未注册，则创建并注册一个对象
	 *
	 * Return the (raw) singleton object registered under the given name,
	 * creating and registering a new one if none registered yet.
	 * @param beanName the name of the bean
	 * @param singletonFactory the ObjectFactory to lazily create the singleton
	 * with, if necessary
	 * @return the registered singleton object
	 */
	public Object getSingleton(String beanName, ObjectFactory<?> singletonFactory) {
		// 如果beanName为null，抛出异常
		Assert.notNull(beanName, "Bean name must not be null");
		// 使用单例对象的高速缓存Map作为锁，保证线程同步
		synchronized (this.singletonObjects) {
			// 从单例对象的高速缓存Map中获取beanName对应的单例对象
			Object singletonObject = this.singletonObjects.get(beanName);
			// 如果单例对象获取不到
			if (singletonObject == null) {
				// 如果当前在destorySingletons中
				if (this.singletonsCurrentlyInDestruction) {
					throw new BeanCreationNotAllowedException(beanName,
							"Singleton bean creation not allowed while singletons of this factory are in destruction " +
							"(Do not request a bean from a BeanFactory in a destroy method implementation!)");
				}
				// 如果当前日志级别时调试
				if (logger.isDebugEnabled()) {
					logger.debug("Creating shared instance of singleton bean '" + beanName + "'");
				}
				// 创建单例之前的回调,默认实现将单例注册为当前正在创建中
				beforeSingletonCreation(beanName);
				// 表示生成了新的单例对象的标记，默认为false，表示没有生成新的单例对象
				boolean newSingleton = false;
				// 有抑制异常记录标记,没有时为true,否则为false
				boolean recordSuppressedExceptions = (this.suppressedExceptions == null);
				// 如果没有抑制异常记录
				if (recordSuppressedExceptions) {
					// 对抑制的异常列表进行实例化(LinkedHashSet)
					this.suppressedExceptions = new LinkedHashSet<>();
				}
				try {
					// 从单例工厂中获取对象
					singletonObject = singletonFactory.getObject();
					// 生成了新的单例对象的标记为true，表示生成了新的单例对象
					newSingleton = true;
				}
				catch (IllegalStateException ex) {
					// Has the singleton object implicitly appeared in the meantime ->
					// if yes, proceed with it since the exception indicates that state.
					// 同时，单例对象是否隐式出现 -> 如果是，请继续操作，因为异常表明该状态
					// 尝试从单例对象的高速缓存Map中获取beanName的单例对象
					singletonObject = this.singletonObjects.get(beanName);
					// 如果获取失败，抛出异常
					if (singletonObject == null) {
						throw ex;
					}
				}
				// 捕捉Bean创建异常
				catch (BeanCreationException ex) {
					// 如果没有抑制异常记录
					if (recordSuppressedExceptions) {
						// 遍历抑制的异常列表
						for (Exception suppressedException : this.suppressedExceptions) {
							// 将抑制的异常对象添加到 bean创建异常 中，这样做的，就是相当于 '因XXX异常导致了Bean创建异常‘ 的说法
							ex.addRelatedCause(suppressedException);
						}
					}
					// 抛出异常
					throw ex;
				}
				finally {
					// 如果没有抑制异常记录
					if (recordSuppressedExceptions) {
						// 将抑制的异常列表置为null，因为suppressedExceptions是对应单个bean的异常记录，置为null
						// 可防止异常信息的混乱
						this.suppressedExceptions = null;
					}
					// 创建单例后的回调,默认实现将单例标记为不在创建中
					afterSingletonCreation(beanName);
				}
				// 生成了新的单例对象
				if (newSingleton) {
					// 将beanName和singletonObject的映射关系添加到该工厂的单例缓存中:
					addSingleton(beanName, singletonObject);
				}
			}
			// 返回该单例对象
			return singletonObject;
		}
	}

	/**
	 * 将要注册的异常对象添加到 抑制异常列表中，注意抑制异常列表【#suppressedExceptions】是Set集合
	 *
	 * Register an exception that happened to get suppressed during the creation of a
	 * singleton bean instance, e.g. a temporary circular reference resolution problem.
	 * <p>The default implementation preserves any given exception in this registry's
	 * collection of suppressed exceptions, up to a limit of 100 exceptions, adding
	 * them as related causes to an eventual top-level {@link BeanCreationException}.
	 * @param ex the Exception to register
	 * @see BeanCreationException#getRelatedCauses()
	 */
	protected void onSuppressedException(Exception ex) {
		// 使用singletonObject同步加锁
		synchronized (this.singletonObjects) {
			// 如果抑制异常列表不为null
			if (this.suppressedExceptions != null && this.suppressedExceptions.size() < SUPPRESSED_EXCEPTIONS_LIMIT) {
				// 将要注册的异常对象添加到抑制异常列表中，注意抑制异常列表是Set集合
				this.suppressedExceptions.add(ex);
			}
		}
	}

	/**
	 * 从该工厂单例缓存中删除具有给定名称的Bean。如果创建失败，则能够清理饿汉式注册的单例
	 *
	 * Remove the bean with the given name from the singleton cache of this factory,
	 * to be able to clean up eager registration of a singleton if creation failed.
	 * @param beanName the name of the bean
	 * @see #getSingletonMutex()
	 */
	protected void removeSingleton(String beanName) {
		// 同步，使用单例对象的高速缓存:beam名称-bean实例作为锁
		synchronized (this.singletonObjects) {
			// 删除单例对象的高速缓存:beam名称-bean实例的对应数据
			this.singletonObjects.remove(beanName);
			// 删除单例工厂的缓存：bean名称-ObjectFactory的对应数据
			this.singletonFactories.remove(beanName);
			// 删除 单例对象的高速缓存:beam名称-bean实例的对应数据
			this.earlySingletonObjects.remove(beanName);
			// 删除已注册的单例集，按照注册顺序包含bean名称 的对应数据
			this.registeredSingletons.remove(beanName);
		}
	}

	/**
	 * 只是判断一下beanName是否在该BeanFactory的单例对象的高速缓存Map集合
	 * @param beanName the name of the bean to look for
	 * @return
	 */
	@Override
	public boolean containsSingleton(String beanName) {
		return this.singletonObjects.containsKey(beanName);
	}

	@Override
	public String[] getSingletonNames() {
		synchronized (this.singletonObjects) {
			return StringUtils.toStringArray(this.registeredSingletons);
		}
	}

	@Override
	public int getSingletonCount() {
		synchronized (this.singletonObjects) {
			return this.registeredSingletons.size();
		}
	}


	public void setCurrentlyInCreation(String beanName, boolean inCreation) {
		Assert.notNull(beanName, "Bean name must not be null");
		if (!inCreation) {
			this.inCreationCheckExclusions.add(beanName);
		}
		else {
			this.inCreationCheckExclusions.remove(beanName);
		}
	}

	/**
	 * 给定的bean名是否正在创建
	 * @param beanName
	 * @return
	 */
	public boolean isCurrentlyInCreation(String beanName) {
		Assert.notNull(beanName, "Bean name must not be null");
		// 如果当前在创建检查中排除的bean名列表中不包含该beanName且beanName实际上正在创建就返回true.
		return (!this.inCreationCheckExclusions.contains(beanName) && isActuallyInCreation(beanName));
	}

	/**
	 * 给定的bean名实际上是否正在创建
	 * @param beanName
	 * @return
	 */
	protected boolean isActuallyInCreation(String beanName) {
		return isSingletonCurrentlyInCreation(beanName);
	}

	/**
	 * 返回指定的单例bean当前是否正在创建（在整个工厂内）
	 *
	 * Return whether the specified singleton bean is currently in creation
	 * (within the entire factory).
	 * @param beanName the name of the bean
	 */
	public boolean isSingletonCurrentlyInCreation(String beanName) {
		// 从当前正在创建的bean名称set集合中判断beanName是否在集合中
		return this.singletonsCurrentlyInCreation.contains(beanName);
	}

	/**
	 * 创建单例之前的回调:
	 * 如果当前在创建检查中的排除bean名列表【inCreationCheckExclusions】中不包含该beanName且将beanName添加到
	 * 当前正在创建的bean名称列表【singletonsCurrentlyInCreation】后，出现beanName已经在当前正在创建的bean名称列表中添加过
	 *
	 * 创建单例之前的回调
	 * Callback before singleton creation.
	 * <p>The default implementation register the singleton as currently in creation.
	 * @param beanName the name of the singleton about to be created
	 * @see #isSingletonCurrentlyInCreation
	 */
	protected void beforeSingletonCreation(String beanName) {
		// 如果当前在创建检查中的排除bean名列表中不包含该beanName且将beanName添加到当前正在创建的bean名称列表后，出现
		// beanName已经在当前正在创建的bean名称列表中添加过
		if (!this.inCreationCheckExclusions.contains(beanName) && !this.singletonsCurrentlyInCreation.add(beanName)) {
			// 抛出当前正在创建的Bean异常
			throw new BeanCurrentlyInCreationException(beanName);
		}
	}

	/**
	 * 创建单例后的回调
	 * 默认实现将单例标记为不在创建中
	 *
	 * Callback after singleton creation.
	 * <p>The default implementation marks the singleton as not in creation anymore.
	 * @param beanName the name of the singleton that has been created
	 * @see #isSingletonCurrentlyInCreation
	 */
	protected void afterSingletonCreation(String beanName) {
		// 如果当前在创建检查中的排除bean名列表中不包含该beanName且将beanName从当前正在创建的bean名称列表异常后，出现
		// beanName已经没在当前正在创建的bean名称列表中出现过
		if (!this.inCreationCheckExclusions.contains(beanName) && !this.singletonsCurrentlyInCreation.remove(beanName)) {
			// 抛出非法状态异常：单例'beanName'不是当前正在创建的
			throw new IllegalStateException("Singleton '" + beanName + "' isn't currently in creation");
		}
	}


	/**
	 * 将给定Bean添加到注册中心的一次性Bean列表中
	 *
	 * Add the given bean to the list of disposable beans in this registry.
	 * <p>Disposable beans usually correspond to registered singletons,
	 * matching the bean name but potentially being a different instance
	 * (for example, a DisposableBean adapter for a singleton that does not
	 * naturally implement Spring's DisposableBean interface).
	 * @param beanName the name of the bean
	 * @param bean the bean instance
	 */
	public void registerDisposableBean(String beanName, DisposableBean bean) {
		// 使用disposableBeans加锁，保证线程安全
		synchronized (this.disposableBeans) {
			// 将beanName,bean添加到disposableBeans中
			this.disposableBeans.put(beanName, bean);
		}
	}

	/**
	 * 将containedBeanName和containingBeanName的包含关系注册到该工厂中
	 *
	 * Register a containment relationship between two beans,
	 * e.g. between an inner bean and its containing outer bean.
	 * <p>Also registers the containing bean as dependent on the contained bean
	 * in terms of destruction order.
	 * @param containedBeanName the name of the contained (inner) bean
	 * @param containingBeanName the name of the containing (outer) bean
	 * @see #registerDependentBean
	 */
	public void registerContainedBean(String containedBeanName, String containingBeanName) {
		// 使用containedBeanMap作为锁，保证线程安全
		synchronized (this.containedBeanMap) {
			// 从containedBeanMap中获取containgBeanNamed的内部Bean名列表，没有时创建一个初始化长度为8的LinkedHashSet来使用
			Set<String> containedBeans =
					this.containedBeanMap.computeIfAbsent(containingBeanName, k -> new LinkedHashSet<>(8));
			// 将containedBeanName添加到containedBeans中，如果已经添加过了，就直接返回
			if (!containedBeans.add(containedBeanName)) {
				return;
			}
		}
		// 注册containedBeanName与containingBeanName的依赖关系
		registerDependentBean(containedBeanName, containingBeanName);
	}

	/**
	 * 注册beanName与dependentBeanNamed的依赖关系
	 *
	 * Register a dependent bean for the given bean,
	 * to be destroyed before the given bean is destroyed.
	 * @param beanName the name of the bean
	 * @param dependentBeanName the name of the dependent bean
	 */
	public void registerDependentBean(String beanName, String dependentBeanName) {
		// 获取name的最终别名或者是全类名
		String canonicalName = canonicalName(beanName);

		// 使用存储bean名到该bean名所要依赖的bean名的Map作为锁，保证线程安全
		synchronized (this.dependentBeanMap) {
			// 获取canonicalName对应的用于存储依赖Bean名的Set集合，如果没有就创建一个LinkedHashSet，并与canonicalName绑定到dependentBeans中
			Set<String> dependentBeans =
					this.dependentBeanMap.computeIfAbsent(canonicalName, k -> new LinkedHashSet<>(8));
			// 如果dependentBeans已经添加过来了dependentBeanName，就结束该方法，不执行后面操作。
			if (!dependentBeans.add(dependentBeanName)) {
				return;
			}
		}

		//使用Bean依赖关系Map作为锁，保证线程安全
		synchronized (this.dependenciesForBeanMap) {
			//添加dependendtBeanName依赖于cannoicalName的映射关系到 存储 bean名到依赖于该bean名的bean名 的Map中
			Set<String> dependenciesForBean =
					this.dependenciesForBeanMap.computeIfAbsent(dependentBeanName, k -> new LinkedHashSet<>(8));
			dependenciesForBean.add(canonicalName);
		}
	}

	/**
	 * 判断beanName是否已注册依赖于dependentBeanName的关系
	 *
	 * Determine whether the specified dependent bean has been registered as
	 * dependent on the given bean or on any of its transitive dependencies.
	 * @param beanName the name of the bean to check
	 * @param dependentBeanName the name of the dependent bean
	 * @since 4.0
	 */
	protected boolean isDependent(String beanName, String dependentBeanName) {
		// 使用依赖bean关系Map作为锁，保证线程安全
		synchronized (this.dependentBeanMap) {
			return isDependent(beanName, dependentBeanName, null);
		}
	}

	/**
	 * 确定指定的依赖bean是否已注册为依赖于给定bean或其任何传递依赖
	 * @param beanName
	 * @param dependentBeanName
	 * @param alreadySeen
	 * @return
	 */
	private boolean isDependent(String beanName, String dependentBeanName, @Nullable Set<String> alreadySeen) {
		// 如果alreadySeen已经包含该beanName，返回false
		if (alreadySeen != null && alreadySeen.contains(beanName)) {
			return false;
		}
		// 获取name的最终别名或者是全类名
		String canonicalName = canonicalName(beanName);
		// 从依赖bean关系Map中获取canonicalName的依赖bean名
		Set<String> dependentBeans = this.dependentBeanMap.get(canonicalName);
		// 如果没有拿到依赖bean，返回false,表示不依赖
		if (dependentBeans == null) {
			return false;
		}
		// 如果依赖bean名中包含dependentBeanName，返回true，表示是依赖
		if (dependentBeans.contains(dependentBeanName)) {
			return true;
		}
		// 遍历依赖bean名
		for (String transitiveDependency : dependentBeans) {
			// 如果alreadySeen为null,就实例化一个HashSet
			if (alreadySeen == null) {
				alreadySeen = new HashSet<>();
			}
			// 将beanName添加到alreadySeen
			alreadySeen.add(beanName);
			// 通过递归的方式检查dependentBeanName是否依赖transitiveDependency,是就返回true
			if (isDependent(transitiveDependency, dependentBeanName, alreadySeen)) {
				return true;
			}
		}
		// 返回false，表示不是依赖
		return false;
	}

	/**
	 * 确定是否已经为给定名称注册了依赖Bean关系
	 *
	 * Determine whether a dependent bean has been registered for the given name.
	 * @param beanName the name of the bean to check
	 */
	protected boolean hasDependentBean(String beanName) {
		return this.dependentBeanMap.containsKey(beanName);
	}

	/**
	 * 如果有的话，返回依赖于指定Bean的所有Bean名称
	 *
	 * Return the names of all beans which depend on the specified bean, if any.
	 * @param beanName the name of the bean
	 * @return the array of dependent bean names, or an empty array if none
	 */
	public String[] getDependentBeans(String beanName) {
		// 从dependentBeanMap中获取依赖Bean名称的数组
		Set<String> dependentBeans = this.dependentBeanMap.get(beanName);
		// 如果dependentBeans为null
		if (dependentBeans == null) {
			return new String[0];
		}
		// 使用dependentBeanMap进行加锁，以保证Set转数组时的线程安全
		synchronized (this.dependentBeanMap) {
			// 将dependentBeans转换为数组
			return StringUtils.toStringArray(dependentBeans);
		}
	}

	/**
	 * 返回指定bean所依赖的所有bean的名称(如果有的话)
	 *
	 * Return the names of all beans that the specified bean depends on, if any.
	 * @param beanName the name of the bean
	 * @return the array of names of beans which the bean depends on,
	 * or an empty array if none
	 */
	public String[] getDependenciesForBean(String beanName) {
		// dependenciesForBeanMap：存储bean名到依赖于该bean名的bean名的Map
		// 从dependenciesForBeanMap中获取beanName的所依赖的bean的名称数组
		Set<String> dependenciesForBean = this.dependenciesForBeanMap.get(beanName);
		// 如果dependenciesForBean为null
		if (dependenciesForBean == null) {
			// 返回空字符串数组
			return new String[0];
		}
		// 使用dependenciesForBeanMap加锁，保证线程安全
		synchronized (this.dependenciesForBeanMap) {
			// 将dependenciesForBean转换为字符串数组返回出去
			return StringUtils.toStringArray(dependenciesForBean);
		}
	}

	public void destroySingletons() {
		if (logger.isTraceEnabled()) {
			logger.trace("Destroying singletons in " + this);
		}
		// 同步，使用单例对象的高速缓存:beam名称-bean实例作为锁
		synchronized (this.singletonObjects) {
			// 将当前是否在destroySingletons中的标志设置为true，表明正在destroySingletons
			this.singletonsCurrentlyInDestruction = true;
		}

		String[] disposableBeanNames;
		// 同步,使用一次性Bean实例缓存：bean名称-DisposableBean实例作为锁
		synchronized (this.disposableBeans) {
			// 复制disposableBean的key集到一个String数组
			disposableBeanNames = StringUtils.toStringArray(this.disposableBeans.keySet());
		}
		// 遍历disposableBeanNames
		for (int i = disposableBeanNames.length - 1; i >= 0; i--) {
			// 销毁disposableBeanNames[i])。先销毁依赖于disposableBeanNames[i])的bean,
			// 然后再销毁bean。
			destroySingleton(disposableBeanNames[i]);
		}

		// 清空在包含的Bean名称之间映射：bean名称-Bean包含的Bean名称集
		this.containedBeanMap.clear();
		// 清空在相关的Bean名称之间映射：bean名称-一组相关的Bean名称
		this.dependentBeanMap.clear();
		// 清空在相关的Bean名称之j键映射：bean名称bean依赖项的Bean名称集
		this.dependenciesForBeanMap.clear();
		// 清除此注册表中所有缓存的单例实例
		clearSingletonCache();
	}

	/**
	 * 清除此注册表中所有缓存的单例实例
	 *
	 * Clear all cached singleton instances in this registry.
	 * @since 4.3.15
	 */
	protected void clearSingletonCache() {
		// 加锁，使用单例对象的高速缓存:beam名称-bean实例作为锁
		synchronized (this.singletonObjects) {
			// 清空单例对象的高速缓存:beam名称-bean实例
			this.singletonObjects.clear();
			// 清空单例工厂的缓存：bean名称-ObjectFactory
			this.singletonFactories.clear();
			// 清空早期单例对象的高速缓存：bean名称-bean实例
			this.earlySingletonObjects.clear();
			// 清空已注册的单例集，按照注册顺序包含bean名称
			this.registeredSingletons.clear();
			// 设置当前是否在destroySingletons中的标志为false
			this.singletonsCurrentlyInDestruction = false;
		}
	}

	/**
	 * 销毁给定的bean。如果找到相应的一次性Bean实例，则委托给{@code destoryBean}
	 *
	 * Destroy the given bean. Delegates to {@code destroyBean}
	 * if a corresponding disposable bean instance is found.
	 * @param beanName the name of the bean
	 * @see #destroyBean
	 */
	public void destroySingleton(String beanName) {
		// Remove a registered singleton of the given name, if any.
		// 删除给定名称的已注册的单例（如果有）
		removeSingleton(beanName);

		// Destroy the corresponding DisposableBean instance.
		// 销毁相应的DisposableBean实例
		// DisposableBean:要在销毁时释放资源的bean所实现的接口.包括已注册为一次性的内部bean。
		// 在工厂关闭时调用。
		DisposableBean disposableBean;
		// 同步，将 一次性Bean实例：bean名称-DisposableBean实例作为锁
		synchronized (this.disposableBeans) {
			// 从disposableBeans移除出disposableBean对象
			disposableBean = (DisposableBean) this.disposableBeans.remove(beanName);
		}
		// 销毁给定bean,必须先销毁依赖于给定bean的bean,然后再销毁bean
		destroyBean(beanName, disposableBean);
	}

	/**
	 * 销毁给定bean,必须先销毁依赖于给定bean的bean,然后再销毁bean,不应抛出任何异常
	 *
	 * Destroy the given bean. Must destroy beans that depend on the given
	 * bean before the bean itself. Should not throw any exceptions.
	 * @param beanName the name of the bean
	 * @param bean the bean instance to destroy
	 */
	protected void destroyBean(String beanName, @Nullable DisposableBean bean) {
		// Trigger destruction of dependent beans first...
		// 先触发从依赖的bean的破坏
		Set<String> dependencies;
		// 同步,使用 在相关的Bean名称之间映射：bean名称- 一组相关的Bean名称作为锁
		synchronized (this.dependentBeanMap) {
			// Within full synchronization in order to guarantee a disconnected Set
			// 在完全同步内以确保断开连续集
			// 从dependentBeanMap中移除出beanName对应的依赖beanName集
			dependencies = this.dependentBeanMap.remove(beanName);
		}
		// 如果存在依赖的beanName集
		if (dependencies != null) {
			if (logger.isTraceEnabled()) {
				logger.trace("Retrieved dependent beans for bean '" + beanName + "': " + dependencies);
			}
			// 遍历依赖的BeanName
			for (String dependentBeanName : dependencies) {
				// 递归删除dependentBeanName的实例
				destroySingleton(dependentBeanName);
			}
		}

		// Actually destroy the bean now...
		// 实现上现在销毁的bean
		if (bean != null) {
			try {
				// 调用销毁方法
				bean.destroy();
			}
			catch (Throwable ex) {
				// 抛出异常时，打印出警告信息
				if (logger.isWarnEnabled()) {
					logger.warn("Destruction of bean with name '" + beanName + "' threw an exception", ex);
				}
			}
		}

		// Trigger destruction of contained beans...
		// 触发销毁所包含的bean
		Set<String> containedBeans;
		// 同步，使用 在包含的Bean名称之间映射：bean名称-Bean包含的Bean名称集作为锁
		synchronized (this.containedBeanMap) {
			// Within full synchronization in order to guarantee a disconnected Set
			containedBeans = this.containedBeanMap.remove(beanName);
		}
		// 如果存在BeanName包含的bean名称集
		if (containedBeans != null) {
			// 遍历BeanName包含的bean名称集
			for (String containedBeanName : containedBeans) {
				// 递归删除containedBeanName的实例
				destroySingleton(containedBeanName);
			}
		}

		// Remove destroyed bean from other beans' dependencies.
		// 从其他bean的依赖项中删除破坏的bean
		// 同步，在相关的Bean名称之间映射：bean名称- 一组相关的Bean名称作为锁
		synchronized (this.dependentBeanMap) {
			// 遍历dependentBeanMap的元素
			for (Iterator<Map.Entry<String, Set<String>>> it = this.dependentBeanMap.entrySet().iterator(); it.hasNext();) {
				Map.Entry<String, Set<String>> entry = it.next();
				Set<String> dependenciesToClean = entry.getValue();
				// 从其它bean的依赖bean集合中移除beanName
				dependenciesToClean.remove(beanName);
				// 如果依赖bean集合没有任何元素了
				if (dependenciesToClean.isEmpty()) {
					// 将整个映射关系都删除
					it.remove();
				}
			}
		}

		// Remove destroyed bean's prepared dependency information.
		// 删除销毁的bean准备的依赖的依赖项信息
		// 从在相关的Bean名称之键映射：bean名称bean依赖项的Bean名称集删除beanName的映射关系
		this.dependenciesForBeanMap.remove(beanName);
	}

	/**
	 * 将单例互斥暴露给子类和外部协作者
	 *
	 * Exposes the singleton mutex to subclasses and external collaborators.
	 * <p>Subclasses should synchronize on the given Object if they perform
	 * any sort of extended singleton creation phase. In particular, subclasses
	 * should <i>not</i> have their own mutexes involved in singleton creation,
	 * to avoid the potential for deadlocks in lazy-init situations.
	 * 如果子类执行任何扩展的单例创建阶段,则它们应在给定Object上同步.特别是,子类不应在单例创建中涉及其自己的互斥体,以避免在惰性初始化情况下出现死锁的可能性
	 *
	 */
	@Override
	public final Object getSingletonMutex() {
		return this.singletonObjects;
	}

}
