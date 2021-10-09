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

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.aop.framework.autoproxy.AutoProxyUtils;
import org.springframework.aop.scope.ScopedObject;
import org.springframework.aop.scope.ScopedProxyUtils;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.MethodIntrospector;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;

/**
 * 此类用来对@EventListener提供支持
 * 1、解析@EventListener,获取拦截方法
 * 2、对拦截方法进行转换，编程ApplicationListener
 * 3、将转换的ApplicationListener放到Spring容器中
 *
 * Registers {@link EventListener} methods as individual {@link ApplicationListener} instances.
 * Implements {@link BeanFactoryPostProcessor} (as of 5.1) primarily for early retrieval,
 * avoiding AOP checks for this processor bean and its {@link EventListenerFactory} delegates.
 *
 * @author Stephane Nicoll
 * @author Juergen Hoeller
 * @since 4.2
 * @see EventListenerFactory
 * @see DefaultEventListenerFactory
 */
public class EventListenerMethodProcessor
		implements SmartInitializingSingleton, ApplicationContextAware, BeanFactoryPostProcessor {

	protected final Log logger = LogFactory.getLog(getClass());

	// 用于记录检测发现@EventListener注解方法，生成和注册ApplicationListener实例的应用上下文
	@Nullable
	private ConfigurableApplicationContext applicationContext;

	// 记录当前BeanFactory
	@Nullable
	private ConfigurableListableBeanFactory beanFactory;

	// 记录从容器中找到的所有EventListenerFactory
	@Nullable
	private List<EventListenerFactory> eventListenerFactories;

	private final EventExpressionEvaluator evaluator = new EventExpressionEvaluator();

	// 缓存机制，记住那些任何方法上没有使用注解@EventListener的类，避免二次处理
	private final Set<Class<?>> nonAnnotatedClasses = Collections.newSetFromMap(new ConcurrentHashMap<>(64));


	@Override
	public void setApplicationContext(ApplicationContext applicationContext) {
		Assert.isTrue(applicationContext instanceof ConfigurableApplicationContext,
				"ApplicationContext does not implement ConfigurableApplicationContext");
		this.applicationContext = (ConfigurableApplicationContext) applicationContext;
	}

	@Override
	public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
		this.beanFactory = beanFactory;

		// 从容器中找到所有的EventListenerFactory组件，比如常见的TransactionalEventListenerFactory，用于支持使用@TransactionalEventListener注解的事件监听器
		// @TransactionalEventListener是一种特殊的@EventListener，它定义的事件监听器应用于事务提交或者回滚的某些特殊时机
		// 由ProxyTransactionManagementConfiguration注册到容器
		// DefaultEventListenerFactory最低的优先级，如果其他EventListenerFactory都不支持的时候使用
		Map<String, EventListenerFactory> beans = beanFactory.getBeansOfType(EventListenerFactory.class, false, false);
		List<EventListenerFactory> factories = new ArrayList<>(beans.values());
		// 排序
		AnnotationAwareOrderComparator.sort(factories);
		// 保存
		this.eventListenerFactories = factories;
	}


	@Override
	public void afterSingletonsInstantiated() {
		ConfigurableListableBeanFactory beanFactory = this.beanFactory;
		Assert.state(this.beanFactory != null, "No ConfigurableListableBeanFactory set");
		// 获取容器中所有bean组件的名称
		String[] beanNames = beanFactory.getBeanNamesForType(Object.class);
		for (String beanName : beanNames) {
			// 遍历每个bean组件，检测其中@EventListener注解方法，生成和注册ApplicationListener实例
			if (!ScopedProxyUtils.isScopedTarget(beanName)) {
				Class<?> type = null;
				try {
					type = AutoProxyUtils.determineTargetClass(beanFactory, beanName);
				}
				catch (Throwable ex) {
					// An unresolvable bean type, probably from a lazy bean - let's ignore it.
					if (logger.isDebugEnabled()) {
						logger.debug("Could not resolve target class for bean with name '" + beanName + "'", ex);
					}
				}
				if (type != null) {
					if (ScopedObject.class.isAssignableFrom(type)) {
						try {
							Class<?> targetClass = AutoProxyUtils.determineTargetClass(
									beanFactory, ScopedProxyUtils.getTargetBeanName(beanName));
							if (targetClass != null) {
								type = targetClass;
							}
						}
						catch (Throwable ex) {
							// An invalid scoped proxy arrangement - let's ignore it.
							if (logger.isDebugEnabled()) {
								logger.debug("Could not resolve target bean for scoped proxy '" + beanName + "'", ex);
							}
						}
					}
					try {
						// 针对一个bean的真正的@EventListener注解方法检测，ApplicationListener实例生成和注册
						processBean(beanName, type);
					}
					catch (Throwable ex) {
						throw new BeanInitializationException("Failed to process @EventListener " +
								"annotation on bean with name '" + beanName + "'", ex);
					}
				}
			}
		}
	}

	/**
	 * 该方法拿到某个bean的名称和它的目标类，再这个范围上检测@EventListener注解方法，生成和注册ApplicationListener实例
	 * @param beanName
	 * @param targetType
	 */
	private void processBean(final String beanName, final Class<?> targetType) {
		if (!this.nonAnnotatedClasses.contains(targetType) &&
				AnnotationUtils.isCandidateClass(targetType, EventListener.class) &&
				!isSpringContainerClass(targetType)) {

			Map<Method, EventListener> annotatedMethods = null;
			try {
				// 检测当前类targetType上使用了注解@EventListener的方法
				annotatedMethods = MethodIntrospector.selectMethods(targetType,
						(MethodIntrospector.MetadataLookup<EventListener>) method ->
								AnnotatedElementUtils.findMergedAnnotation(method, EventListener.class));
			}
			catch (Throwable ex) {
				// An unresolvable type in a method signature, probably from a lazy bean - let's ignore it.
				if (logger.isDebugEnabled()) {
					logger.debug("Could not resolve methods for bean with name '" + beanName + "'", ex);
				}
			}

			if (CollectionUtils.isEmpty(annotatedMethods)) {
				// 如果当前类targetType中没有任何使用了注解@EventListener的方法，则将该类保存到缓存nonAnnotatedClasses，从而
				// 避免当前处理方法重入该类，避免二次处理
				this.nonAnnotatedClasses.add(targetType);
				if (logger.isTraceEnabled()) {
					logger.trace("No @EventListener annotations found on bean class: " + targetType.getName());
				}
			}
			else {
				// Non-empty set of methods
				// 如果当前类targetType中有些方法使用了注解@EventListener，那么根据方法上的信息对应的创建和注册ApplicationListener实例
				ConfigurableApplicationContext context = this.applicationContext;
				Assert.state(context != null, "No ApplicationContext set");
				// 此处使用了this.eventListenerFactories,这些EventListenerFactory是在该类postProcessBeanFactory方法调用时被记录的
				List<EventListenerFactory> factories = this.eventListenerFactories;
				Assert.state(factories != null, "EventListenerFactory List not initialized");
				for (Method method : annotatedMethods.keySet()) {
					for (EventListenerFactory factory : factories) {
						if (factory.supportsMethod(method)) {
							Method methodToUse = AopUtils.selectInvocableMethod(method, context.getType(beanName));
							// 如果当前EventListenerFactory支持处理该@EventListener注解的方法，则使用它创建ApplicationListener
							ApplicationListener<?> applicationListener =
									factory.createApplicationListener(beanName, targetType, methodToUse);
							if (applicationListener instanceof ApplicationListenerMethodAdapter) {
								((ApplicationListenerMethodAdapter) applicationListener).init(context, this.evaluator);
							}
							// 将创建的ApplicationListener加入到容器中
							context.addApplicationListener(applicationListener);
							break;
						}
					}
				}
				if (logger.isDebugEnabled()) {
					logger.debug(annotatedMethods.size() + " @EventListener methods processed on bean '" +
							beanName + "': " + annotatedMethods);
				}
			}
		}
	}

	/**
	 * Determine whether the given class is an {@code org.springframework}
	 * bean class that is not annotated as a user or test {@link Component}...
	 * which indicates that there is no {@link EventListener} to be found there.
	 * @since 5.1
	 */
	private static boolean isSpringContainerClass(Class<?> clazz) {
		return (clazz.getName().startsWith("org.springframework.") &&
				!AnnotatedElementUtils.isAnnotated(ClassUtils.getUserClass(clazz), Component.class));
	}

}
