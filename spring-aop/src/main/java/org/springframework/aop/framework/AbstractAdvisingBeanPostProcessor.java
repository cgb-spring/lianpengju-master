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

package org.springframework.aop.framework;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.aop.Advisor;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.lang.Nullable;

/**
 * BeanFactoryPostProcessor的实现类，应用spring aop到指定的bean中
 *
 * Base class for {@link BeanPostProcessor} implementations that apply a
 * Spring AOP {@link Advisor} to specific beans.
 *
 * @author Juergen Hoeller
 * @since 3.2
 */
@SuppressWarnings("serial")
public abstract class AbstractAdvisingBeanPostProcessor extends ProxyProcessorSupport implements BeanPostProcessor {

	// 当前BeanPostProcessor将要应用到符合条件的目标bean上的advisor
	@Nullable
	protected Advisor advisor;

	// this.advisor应用到已有advisor的最外面（离目标bean最远），还是最里面（离目标bean最近）
	protected boolean beforeExistingAdvisors = false;

	// 缓存机制，如果某个bean class符合被本BeanPostProcessor添加Advisor的条件，则将其缓存下来，处理每个bean时会尝试使用该缓存
	// 如果缓存中没有，会尝试使用AopUtil.canApply进行检测
	private final Map<Class<?>, Boolean> eligibleBeans = new ConcurrentHashMap<>(256);


	/**
	 * Set whether this post-processor's advisor is supposed to apply before
	 * existing advisors when encountering a pre-advised object.
	 * <p>Default is "false", applying the advisor after existing advisors, i.e.
	 * as close as possible to the target method. Switch this to "true" in order
	 * for this post-processor's advisor to wrap existing advisors as well.
	 * <p>Note: Check the concrete post-processor's javadoc whether it possibly
	 * changes this flag by default, depending on the nature of its advisor.
	 */
	public void setBeforeExistingAdvisors(boolean beforeExistingAdvisors) {
		this.beforeExistingAdvisors = beforeExistingAdvisors;
	}


	/**
	 * 直接返回目标对象，任何处理都不做
	 * @param bean the new bean instance
	 * @param beanName the name of the bean
	 * @return
	 */
	@Override
	public Object postProcessBeforeInitialization(Object bean, String beanName) {
		return bean;
	}

	/**
	 *  bean 初始化后置处理
	 *  1. 如果属性 this.advisor 为 null 或者目标 bean 本身是一个 AopInfrastructureBean,
	 *  则不对赌目标 bean 做包装处理，而是直接返回目标 bean
	 *   2. 否则如果目标 bean 是 Advised 实例，未被冻结，并且符合当前 BeanPostProcessor
	 *  自定义的条件，则将 this.advisor 添加给它
	 *   3. 否则如果目标 bean  也不是 Advised 实例，但是符合当前 BeanPostProcessor
	 *   自定义的条件，则将为目标 bean 创建一个代理对象，将 this.advisor 添加给代理对象,
	 *   然后返回该代理对象用于代理目标 bean
	 *    4. 目标 bean 不符合当前 BeanPostProcessor 自定义的条件，则直接返回目标 bean 自身。
	 *
	 * @param bean the new bean instance
	 * @param beanName the name of the bean
	 * @return
	 */
	@Override
	public Object postProcessAfterInitialization(Object bean, String beanName) {
		// 如果advisor为空或者bean归属于AopInfrastructureBean子类，直接返回
		if (this.advisor == null || bean instanceof AopInfrastructureBean) {
			// Ignore AOP infrastructure such as scoped proxies.
			return bean;
		}

		// 判断bean是否是Advised类型，如果是的话，则直接添加advisor到目标类中
		if (bean instanceof Advised) {
			Advised advised = (Advised) bean;
			if (!advised.isFrozen() && isEligible(AopUtils.getTargetClass(bean))) {
				// Add our local Advisor to the existing proxy's Advisor chain...
				if (this.beforeExistingAdvisors) {
					advised.addAdvisor(0, this.advisor);
				}
				else {
					advised.addAdvisor(this.advisor);
				}
				return bean;
			}
		}

		// 目标bean符合当前BeanPostProcessor添加Advisor的条件
		if (isEligible(bean, beanName)) {
			// 准备相应的代理创建工厂
			ProxyFactory proxyFactory = prepareProxyFactory(bean, beanName);
			if (!proxyFactory.isProxyTargetClass()) {
				evaluateProxyInterfaces(bean.getClass(), proxyFactory);
			}
			// 设置 this.advisor到代理对象工厂
			proxyFactory.addAdvisor(this.advisor);
			// 调用代理工厂自定义方法进行自定义
			// 当前类中 customizeProxyFactory 是一个空方法，
			// 但实现子类可以覆盖实现提供自己的自定义逻辑
			customizeProxyFactory(proxyFactory);
			// 创建代理对象
			return proxyFactory.getProxy(getProxyClassLoader());
		}

		// No proxy needed.
		// 目标 bean 不符合当前 BeanPostProcessor 添加 Advisor 的条件
		return bean;
	}

	/**
	 * 提供实现检测目标bean是否符合被添加Advisor的条件
	 *
	 * Check whether the given bean is eligible for advising with this
	 * post-processor's {@link Advisor}.
	 * <p>Delegates to {@link #isEligible(Class)} for target class checking.
	 * Can be overridden e.g. to specifically exclude certain beans by name.
	 * <p>Note: Only called for regular bean instances but not for existing
	 * proxy instances which implement {@link Advised} and allow for adding
	 * the local {@link Advisor} to the existing proxy's {@link Advisor} chain.
	 * For the latter, {@link #isEligible(Class)} is being called directly,
	 * with the actual target class behind the existing proxy (as determined
	 * by {@link AopUtils#getTargetClass(Object)}).
	 * @param bean the bean instance
	 * @param beanName the name of the bean
	 * @see #isEligible(Class)
	 */
	protected boolean isEligible(Object bean, String beanName) {
		return isEligible(bean.getClass());
	}

	/**
	 * Check whether the given class is eligible for advising with this
	 * post-processor's {@link Advisor}.
	 * <p>Implements caching of {@code canApply} results per bean target class.
	 * @param targetClass the class to check against
	 * @see AopUtils#canApply(Advisor, Class)
	 */
	protected boolean isEligible(Class<?> targetClass) {
		// 先尝试缓存
		Boolean eligible = this.eligibleBeans.get(targetClass);
		if (eligible != null) {
			return eligible;
		}
		if (this.advisor == null) {
			return false;
		}
		// 缓存没有命中，使用 AopUtils#getTargetClass(Object) 进行检测
		eligible = AopUtils.canApply(this.advisor, targetClass);
		this.eligibleBeans.put(targetClass, eligible);
		return eligible;
	}

	/**
	 * 准备目标bean的代理工厂对象
	 *
	 * Prepare a {@link ProxyFactory} for the given bean.
	 * <p>Subclasses may customize the handling of the target instance and in
	 * particular the exposure of the target class. The default introspection
	 * of interfaces for non-target-class proxies and the configured advisor
	 * will be applied afterwards; {@link #customizeProxyFactory} allows for
	 * late customizations of those parts right before proxy creation.
	 * @param bean the bean instance to create a proxy for
	 * @param beanName the corresponding bean name
	 * @return the ProxyFactory, initialized with this processor's
	 * {@link ProxyConfig} settings and the specified bean
	 * @since 4.2.3
	 * @see #customizeProxyFactory
	 */
	protected ProxyFactory prepareProxyFactory(Object bean, String beanName) {
		ProxyFactory proxyFactory = new ProxyFactory();
		proxyFactory.copyFrom(this);
		proxyFactory.setTarget(bean);
		return proxyFactory;
	}

	/**
	 * 空的方法实现，子类可以覆盖实现该方法用于对目标对象的代理工厂对象做定制
	 *
	 * Subclasses may choose to implement this: for example,
	 * to change the interfaces exposed.
	 * <p>The default implementation is empty.
	 * @param proxyFactory the ProxyFactory that is already configured with
	 * target, advisor and interfaces and will be used to create the proxy
	 * immediately after this method returns
	 * @since 4.2.3
	 * @see #prepareProxyFactory
	 */
	protected void customizeProxyFactory(ProxyFactory proxyFactory) {
	}

}
