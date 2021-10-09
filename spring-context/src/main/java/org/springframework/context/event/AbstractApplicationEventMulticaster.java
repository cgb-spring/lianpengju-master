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

package org.springframework.context.event;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ObjectUtils;

/**
 * AbstractApplicationEventMulticaster是ApplicationEventMulticaster接口的抽象实现，提供基本的监听器注册工具方法（注册和移除监听器）
 * 默认情况下不允许同一个监听器有多个实例，因为该类会将监听器保存在ListenerRetriever集合类的set集合中
 *
 * Abstract implementation of the {@link ApplicationEventMulticaster} interface,
 * providing the basic listener registration facility.
 *
 * <p>Doesn't permit multiple instances of the same listener by default,
 * as it keeps listeners in a linked Set. The collection class used to hold
 * ApplicationListener objects can be overridden through the "collectionClass"
 * bean property.
 *
 * <p>Implementing ApplicationEventMulticaster's actual {@link #multicastEvent} method
 * is left to subclasses. {@link SimpleApplicationEventMulticaster} simply multicasts
 * all events to all registered listeners, invoking them in the calling thread.
 * Alternative implementations could be more sophisticated in those respects.
 *
 * @author Juergen Hoeller
 * @author Stephane Nicoll
 * @since 1.2.3
 * @see #getApplicationListeners(ApplicationEvent, ResolvableType)
 * @see SimpleApplicationEventMulticaster
 */
public abstract class AbstractApplicationEventMulticaster
		implements ApplicationEventMulticaster, BeanClassLoaderAware, BeanFactoryAware {

	// 创建监听器助手类，用于存放应用程序的监听器集合，参数是否是预过滤监听器为false
	private final ListenerRetriever defaultRetriever = new ListenerRetriever(false);

	// ListenerCacheKey是基于事件类型和源类型的类作为key用来存储监听器助手defaultRetriever
	final Map<ListenerCacheKey, ListenerRetriever> retrieverCache = new ConcurrentHashMap<>(64);

	// 类加载器
	@Nullable
	private ClassLoader beanClassLoader;

	// IOC容器工厂类
	@Nullable
	private ConfigurableBeanFactory beanFactory;

	// 互斥的监听器助手类
	private Object retrievalMutex = this.defaultRetriever;


	@Override
	public void setBeanClassLoader(ClassLoader classLoader) {
		this.beanClassLoader = classLoader;
	}

	@Override
	public void setBeanFactory(BeanFactory beanFactory) {
		//如果 beanFactory 不是 ConfigurableBeanFactory 实例
		if (!(beanFactory instanceof ConfigurableBeanFactory)) {
			// 抛出非法状态异常：不在 ConfigurableBeanFactory：beanFactory
			throw new IllegalStateException("Not running in a ConfigurableBeanFactory: " + beanFactory);
		}
		this.beanFactory = (ConfigurableBeanFactory) beanFactory;
		//如果beanClassLoader为null
		if (this.beanClassLoader == null) {
			//获取beanFactory的类加载器以加载Bean类(即使无法使用系统ClassLoader,也只能为null)
			this.beanClassLoader = this.beanFactory.getBeanClassLoader();
		}
		//获取beanFactory使用的单例互斥锁(用于外部协作者)
		this.retrievalMutex = this.beanFactory.getSingletonMutex();
	}

	/**
	 * 获取当前BeanFactory
	 * @return
	 */
	private ConfigurableBeanFactory getBeanFactory() {
		//如果beanFactory为null
		if (this.beanFactory == null) {
			throw new IllegalStateException("ApplicationEventMulticaster cannot retrieve listener beans " +
					"because it is not associated with a BeanFactory");
		}
		//返回当前BeanFactory
		return this.beanFactory;
	}


	/**
	 * 添加应用程序监听器类
	 * @param listener the listener to add
	 */
	@Override
	public void addApplicationListener(ApplicationListener<?> listener) {
		//使用retrievalMutex加锁，保证线程安全
		synchronized (this.retrievalMutex) {
			// Explicitly remove target for a proxy, if registered already,
			// in order to avoid double invocations of the same listener.
			// 显示删除代理的目标(如果已经注册)，以避免对同一个监听器的两次调用获取listener背后的singleton目标对象
			Object singletonTarget = AopProxyUtils.getSingletonTarget(listener);
			//如果singletonTarget是ApplicationListener实例
			if (singletonTarget instanceof ApplicationListener) {
				//将singletonTarget从defaultRetriever.applicationListeners中移除
				this.defaultRetriever.applicationListeners.remove(singletonTarget);
			}
			//将listener添加到defaultRetriever.applicationListeners中
			this.defaultRetriever.applicationListeners.add(listener);
			//清空缓存，因为listener可能支持缓存的某些事件类型和源类型，所以要刷新缓存
			this.retrieverCache.clear();
		}
	}

	@Override
	public void addApplicationListenerBean(String listenerBeanName) {
		//使用retrievalMutex加锁，保证线程安全
		synchronized (this.retrievalMutex) {
			// 将listenerBeanName添加到defaultRetriever的applicationListenerBeans
			this.defaultRetriever.applicationListenerBeans.add(listenerBeanName);
			// 清空缓存，因为listener可能支持缓存的某些事件类型和源类型，所以要刷新缓存
			this.retrieverCache.clear();
		}
	}

	@Override
	public void removeApplicationListener(ApplicationListener<?> listener) {
		//使用retrievalMutex加锁，保证线程安全
		synchronized (this.retrievalMutex) {
			//将listener从retriever的ApplicationListener对象集合中移除
			this.defaultRetriever.applicationListeners.remove(listener);
			//清空缓存，因为listener可能支持缓存的某些事件类型和源类型，所以要刷新缓存
			this.retrieverCache.clear();
		}
	}

	@Override
	public void removeApplicationListenerBean(String listenerBeanName) {
		//使用retrievalMutex加锁，保证线程安全
		synchronized (this.retrievalMutex) {
			//将listener从retriever的ApplicationListener对象集合中移除
			this.defaultRetriever.applicationListenerBeans.remove(listenerBeanName);
			//清空缓存，因为listener可能支持缓存的某些事件类型和源类型，所以要刷新缓存
			this.retrieverCache.clear();
		}
	}

	@Override
	public void removeAllListeners() {
		//使用retrievalMutex加锁，保证线程安全
		synchronized (this.retrievalMutex) {
			//清空defaultRetriever的ApplicationListener对象集合
			this.defaultRetriever.applicationListeners.clear();
			//清空defaultRetriever的BeanFactory中的applicationListener类型Bean名集合
			this.defaultRetriever.applicationListenerBeans.clear();
			//清空缓存，因为listener可能支持缓存的某些事件类型和源类型，所以要刷新缓存
			this.retrieverCache.clear();
		}
	}


	/**
	 * 返回包含所有applicationListenere的集合
	 *
	 * Return a Collection containing all ApplicationListeners.
	 * @return a Collection of ApplicationListeners
	 * @see org.springframework.context.ApplicationListener
	 */
	protected Collection<ApplicationListener<?>> getApplicationListeners() {
		//使用retrievalMutex加锁，保证线程安全
		synchronized (this.retrievalMutex) {
			//返回defaultRetriever的包含所有applicationListenere的集合
			return this.defaultRetriever.getApplicationListeners();
		}
	}

	/**
	 * 返回给定事件类型匹配的 applicationListenere 集合。不匹配的监听器很早就排除在外
	 *
	 * Return a Collection of ApplicationListeners matching the given
	 * event type. Non-matching listeners get excluded early.
	 * @param event the event to be propagated. Allows for excluding
	 * non-matching listeners early, based on cached matching information.
	 * @param eventType the event type
	 * @return a Collection of ApplicationListeners
	 * @see org.springframework.context.ApplicationListener
	 */
	protected Collection<ApplicationListener<?>> getApplicationListeners(
			ApplicationEvent event, ResolvableType eventType) {

		//event.getSource:事件最初在其上发生的对象。
		//获取event最初在其上发生的对象
		Object source = event.getSource();
		//如果source不为nul就获取source的Class对象；否则引用null
		Class<?> sourceType = (source != null ? source.getClass() : null);
		//如果source不为nul就获取source的Class对象；否则引用null
		ListenerCacheKey cacheKey = new ListenerCacheKey(eventType, sourceType);

		// Quick check for existing entry on ConcurrentHashMap...
		// 在ConcurrentHashMap上快速检查现有条目,从retrieverCache中获取cacheKey对应的ListenerRetriever对象
		ListenerRetriever retriever = this.retrieverCache.get(cacheKey);
		//如果retriever不为null
		if (retriever != null) {
			//获取ListenerRetriever存放的所有ApplicationListener对象
			return retriever.getApplicationListeners();
		}

		// 判断是否由给定的类加载器或者给定的类加载的父级类加载器加载过
		// 如果beanClassLoader为null||(event的Class对象已经被beanClassLoader加载过&&(sourceType为null||
		// sourceType已经被beanClassLoader 加载过))
		if (this.beanClassLoader == null ||
				(ClassUtils.isCacheSafe(event.getClass(), this.beanClassLoader) &&
						(sourceType == null || ClassUtils.isCacheSafe(sourceType, this.beanClassLoader)))) {
			// Fully synchronized building and caching of a ListenerRetriever
			// ListenerRetriever完全同步的构建和缓存,使用retrievalMutex加锁保证线程安全
			synchronized (this.retrievalMutex) {
				//从retrieverCache中获取cacheKey对应的ListenerRetriever对象
				retriever = this.retrieverCache.get(cacheKey);
				//如果retriever不为null
				if (retriever != null) {
					//获取ListenerRetriever存放的所有ApplicationListener对象
					return retriever.getApplicationListeners();
				}
				//新建一个ListenerRetriever对象
				retriever = new ListenerRetriever(true);
				//过滤出所有支持eventType以及sourceType的ApplicationListener对象的列表
				Collection<ApplicationListener<?>> listeners =
						retrieveApplicationListeners(eventType, sourceType, retriever);
				//将cacheKey,retriever绑定到retrieverCache中
				this.retrieverCache.put(cacheKey, retriever);
				//返回listeners
				return listeners;
			}
		}
		else {
			// No ListenerRetriever caching -> no synchronization necessary
			// 没有ListenerRetriever缓存 -> 不需要同步
			//过滤出所有支持eventType以及sourceType的ApplicationListener对象的列表，不使用缓存
			return retrieveApplicationListeners(eventType, sourceType, null);
		}
	}

	/**
	 * 实际检索给定事件和原类型的应用程序监听器
	 *
	 * Actually retrieve the application listeners for the given event and source type.
	 * @param eventType the event type
	 * @param sourceType the event source type
	 * @param retriever the ListenerRetriever, if supposed to populate one (for caching purposes)
	 * @return the pre-filtered list of application listeners for the given event and source type
	 */
	private Collection<ApplicationListener<?>> retrieveApplicationListeners(
			ResolvableType eventType, @Nullable Class<?> sourceType, @Nullable ListenerRetriever retriever) {

		//存放所有支持eventType以及sourceType的ApplicationListener对象的列表
		List<ApplicationListener<?>> allListeners = new ArrayList<>();
		// 创建应用程序监听器集合，去重
		Set<ApplicationListener<?>> listeners;
		Set<String> listenerBeans;
		//使用retrievalMutex加锁，保证线程安全
		synchronized (this.retrievalMutex) {
			//初始化编程方式添加的静态ApplicationListener对象集合
			listeners = new LinkedHashSet<>(this.defaultRetriever.applicationListeners);
			// 初始化BeanFactory中的applicationListener类型Bean名集合
			listenerBeans = new LinkedHashSet<>(this.defaultRetriever.applicationListenerBeans);
		}

		// Add programmatically registered listeners, including ones coming
		// from ApplicationListenerDetector (singleton beans and inner beans).
		// 添加以编程方式注册的监听器，包括来自ApplicationListenerDeteor(单例Bean和内部Bean)的监听器
		// 遍历listeners
		for (ApplicationListener<?> listener : listeners) {
			//如果listener支持eventType事件类型以及sourceType源类型
			if (supportsEvent(listener, eventType, sourceType)) {
				//如果retriever不为null
				if (retriever != null) {
					//将listener添加到retriever的ApplicationListener对象集合中
					retriever.applicationListeners.add(listener);
				}
				//将listener添加到allListeners中
				allListeners.add(listener);
			}
		}

		// Add listeners by bean name, potentially overlapping with programmatically
		// registered listeners above - but here potentially with additional metadata.
		// 按Bean名称添加监听器，可能与编程方式重叠————但这里可能有额外的元数据
		// 如果listenerBean不是空集
		if (!listenerBeans.isEmpty()) {
			//获取当前BeanFactory
			ConfigurableBeanFactory beanFactory = getBeanFactory();
			//遍历 listenerBeans
			for (String listenerBeanName : listenerBeans) {
				try {
					//如果通过在尝试实例化listenerBeanName的BeanDefinition的监听器之前检查到其泛型声明的事件类型支持eventType
					if (supportsEvent(beanFactory, listenerBeanName, eventType)) {
						//从beanFactory中获取名为listenerBeanName的ApplicationListener类型的Bean对象
						ApplicationListener<?> listener =
								beanFactory.getBean(listenerBeanName, ApplicationListener.class);
						//如果allListeners不包含listener&&listener支持eventType事件类型以及sourceType源类型
						if (!allListeners.contains(listener) && supportsEvent(listener, eventType, sourceType)) {
							//如果retriever不为null
							if (retriever != null) {
								//如果listenerBeanName在beanFactory中的Bean对象是单例
								if (beanFactory.isSingleton(listenerBeanName)) {
									//将listener添加到retriever的ApplicationListener对象集合中
									retriever.applicationListeners.add(listener);
								}
								else {
									//将listener添加到allListeners中
									retriever.applicationListenerBeans.add(listenerBeanName);
								}
							}
							//将listener添加到allListeners中
							allListeners.add(listener);
						}
					}
					else {
						// Remove non-matching listeners that originally came from
						// ApplicationListenerDetector, possibly ruled out by additional
						// BeanDefinition metadata (e.g. factory method generics) above.
						// 删除最初来自ApplicationListenerDetector的不匹配监听器，可能被上面附加的BeanDefinition元数据
						// (例如工厂方法泛型) 排除
						// 获取在beanFactory中listenerBeanName的Bean对象
						Object listener = beanFactory.getSingleton(listenerBeanName);
						//retriever不为null
						if (retriever != null) {
							//将listener从retriever的ApplicationListener对象集合中移除
							retriever.applicationListeners.remove(listener);
						}
						//将listener从allListeners移除
						allListeners.remove(listener);
					}
				}
				// 没有这样的BeanDefinition异常
				catch (NoSuchBeanDefinitionException ex) {
					// Singleton listener instance (without backing bean definition) disappeared -
					// probably in the middle of the destruction phase
				}
			}
		}

		//AnnotationAwareOrderComparator：OrderComparator的扩展,它支持Spring 的org.springframework.core.Ordered接口以及@Oreder和@Priority注解,
		//其中Ordered实例提供的Order值将覆盖静态定义的注解值(如果有)
		//使用AnnotationAwareOrderComparator对allListeners进行排序
		AnnotationAwareOrderComparator.sort(allListeners);
		//如果retriever不为null&&retriever的applicationListenerBeans是空集
		if (retriever != null && retriever.applicationListenerBeans.isEmpty()) {
			//将retriever的applicationListeners的元素情空
			retriever.applicationListeners.clear();
			//将allListeners添加到retriever的applicationListeners
			retriever.applicationListeners.addAll(allListeners);
		}
		//返回allListeners
		return allListeners;
	}

	/**
	 * 通过在尝试实例化BeanDefinition的监听器之前检查其通用声明的事件类型，尽早筛选监听器
	 *
	 * Filter a bean-defined listener early through checking its generically declared
	 * event type before trying to instantiate it.
	 * <p>If this method returns {@code true} for a given listener as a first pass,
	 * the listener instance will get retrieved and fully evaluated through a
	 * {@link #supportsEvent(ApplicationListener, ResolvableType, Class)} call afterwards.
	 * @param beanFactory the BeanFactory that contains the listener beans
	 * @param listenerBeanName the name of the bean in the BeanFactory
	 * @param eventType the event type to check
	 * @return whether the given listener should be included in the candidates
	 * for the given event type
	 * @see #supportsEvent(Class, ResolvableType)
	 * @see #supportsEvent(ApplicationListener, ResolvableType, Class)
	 */
	private boolean supportsEvent(
			ConfigurableBeanFactory beanFactory, String listenerBeanName, ResolvableType eventType) {

		//获取listenerBeanName在beanFactory中的Class对象
		Class<?> listenerType = beanFactory.getType(listenerBeanName);
		//如果listenerType为null||listenerType是GenericApplicationListener的子类或本身||listenerType
		//是SmartApplicationListener子类或本身
		if (listenerType == null || GenericApplicationListener.class.isAssignableFrom(listenerType) ||
				SmartApplicationListener.class.isAssignableFrom(listenerType)) {
			//返回true
			return true;
		}
		//如果listenerType不支持eventType的事件对象
		if (!supportsEvent(listenerType, eventType)) {
			//返回false
			return false;
		}
		try {
			//从beanFactory中获取listenerBeanName的合并BeanDefinition，如有必要，将子bean定义与其父级合并
			BeanDefinition bd = beanFactory.getMergedBeanDefinition(listenerBeanName);
			//获取bd指定的Bean类的ResolvableType对象，然后转换为ApplicationListener的ResolvableType，最后获取第一个泛型参数的ResolveableType
			ResolvableType genericEventType = bd.getResolvableType().as(ApplicationListener.class).getGeneric();
			//如果genericEventType是ResolvableType.NONE||eventType是genericEventType的子类或实现，就返回true；否则返回false
			return (genericEventType == ResolvableType.NONE || genericEventType.isAssignableFrom(eventType));
		}
		//捕捉 没有这样BeanDefinition异常
		catch (NoSuchBeanDefinitionException ex) {
			// Ignore - no need to check resolvable type for manually registered singleton
			return true;
		}
	}

	/**
	 * 在尝试实例化监听器之前，通过检查其通用声明的事件类型，尽早筛选监听器
	 *
	 * Filter a listener early through checking its generically declared event
	 * type before trying to instantiate it.
	 * <p>If this method returns {@code true} for a given listener as a first pass,
	 * the listener instance will get retrieved and fully evaluated through a
	 * {@link #supportsEvent(ApplicationListener, ResolvableType, Class)} call afterwards.
	 * @param listenerType the listener's type as determined by the BeanFactory
	 * @param eventType the event type to check
	 * @return whether the given listener should be included in the candidates
	 * for the given event type
	 */
	protected boolean supportsEvent(Class<?> listenerType, ResolvableType eventType) {
		//解析listenerType声明的事件类型
		ResolvableType declaredEventType = GenericApplicationListenerAdapter.resolveDeclaredEventType(listenerType);
		//如果declaredEventType为null||eventType是declaredEventType的子类或本身
		return (declaredEventType == null || declaredEventType.isAssignableFrom(eventType));
	}

	/**
	 * 确定给定监听器是否支持给定事件
	 *
	 * Determine whether the given listener supports the given event.
	 * <p>The default implementation detects the {@link SmartApplicationListener}
	 * and {@link GenericApplicationListener} interfaces. In case of a standard
	 * {@link ApplicationListener}, a {@link GenericApplicationListenerAdapter}
	 * will be used to introspect the generically declared type of the target listener.
	 * @param listener the target listener to check
	 * @param eventType the event type to check against
	 * @param sourceType the source type to check against
	 * @return whether the given listener should be included in the candidates
	 * for the given event type
	 */
	protected boolean supportsEvent(
			ApplicationListener<?> listener, ResolvableType eventType, @Nullable Class<?> sourceType) {

		//如果listener是GenericApplicationListener实例，将将其强转为GenericApplicationListener对象，否则创建一个
		//GenericApplicationListenerAdapter对象来封装listener作为GenericApplicationListener对象
		GenericApplicationListener smartListener = (listener instanceof GenericApplicationListener ?
				(GenericApplicationListener) listener : new GenericApplicationListenerAdapter(listener));
		//如果smartListener支持eventType事件类型&& smartListener支持sourceType源类型【源类型是指产生eventType类型的对象的类】
		return (smartListener.supportsEventType(eventType) && smartListener.supportsSourceType(sourceType));
	}


	/**
	 * 根据事件类型和源类型为ListenerRetievers缓存键
	 *
	 * Cache key for ListenerRetrievers, based on event type and source type.
	 */
	private static final class ListenerCacheKey implements Comparable<ListenerCacheKey> {

		private final ResolvableType eventType;

		@Nullable
		private final Class<?> sourceType;

		/**
		 * 根据eventType和sourceType新建一个ListenerRetievers缓存键
		 * @param eventType
		 * @param sourceType
		 */
		public ListenerCacheKey(ResolvableType eventType, @Nullable Class<?> sourceType) {
			Assert.notNull(eventType, "Event type must not be null");
			this.eventType = eventType;
			this.sourceType = sourceType;
		}

		@Override
		public boolean equals(@Nullable Object other) {
			//如果other与this是同一个对象
			if (this == other) {
				return true;
			}
			//如果other不是ListenerCacheKey的实例
			if (!(other instanceof ListenerCacheKey)) {
				return false;
			}
			//将other强转为ListenerCacheKey对象
			ListenerCacheKey otherKey = (ListenerCacheKey) other;
			//ObjectUtils.nullSafeEquals:确定给定的对象是否相等，如果两个都为null返回true ,如果其中一个为null，返回false
			//如果eventType与otherKey.eventType相同&&sourceType与otherKey.sourceType相同时，返回true；否则返回false
			return (this.eventType.equals(otherKey.eventType) &&
					ObjectUtils.nullSafeEquals(this.sourceType, otherKey.sourceType));
		}

		@Override
		public int hashCode() {
			return this.eventType.hashCode() * 29 + ObjectUtils.nullSafeHashCode(this.sourceType);
		}

		@Override
		public String toString() {
			return "ListenerCacheKey [eventType = " + this.eventType + ", sourceType = " + this.sourceType + "]";
		}

		@Override
		public int compareTo(ListenerCacheKey other) {
			// 获取比较eventype序列化成字符串与other.eventType序列化成字符串的结果
			int result = this.eventType.toString().compareTo(other.eventType.toString());
			// 如果result为0时
			if (result == 0) {
				// 如果sourceType为null
				if (this.sourceType == null) {
					// 如果other.sourceType为null，返回0；否则返回-1
					return (other.sourceType == null ? 0 : -1);
				}
				// 如果other.sourceType为null
				if (other.sourceType == null) {
					return 1;
				}
				//获取比较sourceType类名与other.sourceType类名的结果
				result = this.sourceType.getName().compareTo(other.sourceType.getName());
			}
			return result;
		}
	}


	/**
	 * 封装特定目标监听器的 Hellper 类，允许高效地检索预过滤的监听器
	 *
	 * Helper class that encapsulates a specific set of target listeners,
	 * allowing for efficient retrieval of pre-filtered listeners.
	 * <p>An instance of this helper gets cached per event type and source type.
	 */
	private class ListenerRetriever {

		// ApplicationListener 对象集合
		public final Set<ApplicationListener<?>> applicationListeners = new LinkedHashSet<>();

		// BeanFactory中的applicationListener类型Bean名集合
		public final Set<String> applicationListenerBeans = new LinkedHashSet<>();

		// 是否预过滤监听器
		private final boolean preFiltered;

		/**
		 * 新建一个ListenerRetriever实例
		 * @param preFiltered
		 */
		public ListenerRetriever(boolean preFiltered) {
			this.preFiltered = preFiltered;
		}

		// 获取ListenerRetriever存放的所有ApplicationListener对象
		public Collection<ApplicationListener<?>> getApplicationListeners() {
			// 定义存放所有监听器的集合
			List<ApplicationListener<?>> allListeners = new ArrayList<>(
					this.applicationListeners.size() + this.applicationListenerBeans.size());
			// 将applicationListeners添加到allListeners中
			allListeners.addAll(this.applicationListeners);
			// 如果applicationListenerBeans不是空集合
			if (!this.applicationListenerBeans.isEmpty()) {
				// 获取当前上下文BeanFactory
				BeanFactory beanFactory = getBeanFactory();
				// 遍历applicationListenerBeans
				for (String listenerBeanName : this.applicationListenerBeans) {
					try {
						// 从beanFactory中获取名为listenerBeanName的ApplicationListener类型的Bean对象
						ApplicationListener<?> listener = beanFactory.getBean(listenerBeanName, ApplicationListener.class);
						// 如果允许预过滤||allListeneres没有包含该listener
						if (this.preFiltered || !allListeners.contains(listener)) {
							// 将listener添加allListeners中
							allListeners.add(listener);
						}
					}
					catch (NoSuchBeanDefinitionException ex) {
						// Singleton listener instance (without backing bean definition) disappeared -
						// probably in the middle of the destruction phase
					}
				}
			}
			// 如果不允许预过滤||applicationListenerBeans不是空集合
			if (!this.preFiltered || !this.applicationListenerBeans.isEmpty()) {
				// AnnotationAwareOrderComparator：OrderComparator的扩展,它支持Spring 的org.springframework.core.Ordered接口以及@Oreder和@Priority注解,
				// 其中Ordered实例提供的Order值将覆盖静态定义的注解值(如果有)
				// 使用AnnotationAwareOrderComparator对allListeners进行排序
				AnnotationAwareOrderComparator.sort(allListeners);
			}
			return allListeners;
		}
	}

}
