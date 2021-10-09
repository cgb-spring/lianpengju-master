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

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.aopalliance.intercept.Interceptor;
import org.aopalliance.intercept.MethodInterceptor;

import org.springframework.aop.Advisor;
import org.springframework.aop.IntroductionAdvisor;
import org.springframework.aop.IntroductionAwareMethodMatcher;
import org.springframework.aop.MethodMatcher;
import org.springframework.aop.PointcutAdvisor;
import org.springframework.aop.framework.adapter.AdvisorAdapterRegistry;
import org.springframework.aop.framework.adapter.GlobalAdvisorAdapterRegistry;
import org.springframework.lang.Nullable;

/**
 * AdvisorChainFactory的实现类。
 * 也是SpringAOP中唯一默认的实现类
 *
 * A simple but definitive way of working out an advice chain for a Method,
 * given an {@link Advised} object. Always rebuilds each advice chain;
 * caching can be provided by subclasses.
 *
 * @author Juergen Hoeller
 * @author Rod Johnson
 * @author Adrian Colyer
 * @since 2.0.3
 */
@SuppressWarnings("serial")
public class DefaultAdvisorChainFactory implements AdvisorChainFactory, Serializable {

	@Override
	public List<Object> getInterceptorsAndDynamicInterceptionAdvice(
			Advised config, Method method, @Nullable Class<?> targetClass) {

		// This is somewhat tricky... We have to process introductions first,
		// but we need to preserve order in the ultimate list.
		// 这里用了一个单例模式 获取DefaultAdvisorAdapterRegistry实例
		// 在Spring中把每一个功能都分的很细，每个功能都会有相应的类去处理 符合单一职责原则的地方很多 这也是值得我们借鉴的一个地方
		// AdvisorAdapterRegistry这个类的主要作用是将Advice适配为Advisor 将Advisor适配为对应的MethodInterceptor
		AdvisorAdapterRegistry registry = GlobalAdvisorAdapterRegistry.getInstance();
		Advisor[] advisors = config.getAdvisors();
		// 创建一个初始大小为 之前获取到的 通知个数的集合
		List<Object> interceptorList = new ArrayList<>(advisors.length);
		// 如果目标类为null的话，则从方法签名中获取目标类
		Class<?> actualClass = (targetClass != null ? targetClass : method.getDeclaringClass());
		// 判断目标类是否存在引介增强,通常为false
		Boolean hasIntroductions = null;

		// 循环目标方法匹配的通知
		for (Advisor advisor : advisors) {
			// 如果是PointcutAdvisor类型的实例
			if (advisor instanceof PointcutAdvisor) {
				// Add it conditionally.
				PointcutAdvisor pointcutAdvisor = (PointcutAdvisor) advisor;
				// 如果提前进行过切点的匹配了或者当前的Advisor适用于目标类
				if (config.isPreFiltered() || pointcutAdvisor.getPointcut().getClassFilter().matches(actualClass)) {
					MethodMatcher mm = pointcutAdvisor.getPointcut().getMethodMatcher();
					boolean match;
					//检测Advisor是否适用于此目标方法
					if (mm instanceof IntroductionAwareMethodMatcher) {
						if (hasIntroductions == null) {
							hasIntroductions = hasMatchingIntroductions(advisors, actualClass);
						}
						match = ((IntroductionAwareMethodMatcher) mm).matches(method, actualClass, hasIntroductions);
					}
					else {
						match = mm.matches(method, actualClass);
					}
					if (match) {
						// 拦截器链是通过AdvisorAdapterRegistry来加入的，这个AdvisorAdapterRegistry对advice织入具备很大的作用
						MethodInterceptor[] interceptors = registry.getInterceptors(advisor);
						// 使用MethodMatchers的matches方法进行匹配判断
						if (mm.isRuntime()) {
							// Creating a new object instance in the getInterceptors() method
							// isn't a problem as we normally cache created chains.
							// 动态切入点则会创建一个InterceptorAndDynamicMethodMatcher对象
							// 这个对象包含MethodInterceptor和MethodMatcher的实例
							for (MethodInterceptor interceptor : interceptors) {
								interceptorList.add(new InterceptorAndDynamicMethodMatcher(interceptor, mm));
							}
						}
						else {
							// 添加到列表中
							interceptorList.addAll(Arrays.asList(interceptors));
						}
					}
				}
			}
			// 如果是引介增强
			else if (advisor instanceof IntroductionAdvisor) {
				IntroductionAdvisor ia = (IntroductionAdvisor) advisor;
				if (config.isPreFiltered() || ia.getClassFilter().matches(actualClass)) {
					// 将Advisor转换为Interceptor
					Interceptor[] interceptors = registry.getInterceptors(advisor);
					interceptorList.addAll(Arrays.asList(interceptors));
				}
			}
			// 以上两种都不是
			else {
				// 将Advisor转换为Interceptor
				Interceptor[] interceptors = registry.getInterceptors(advisor);
				interceptorList.addAll(Arrays.asList(interceptors));
			}
		}

		return interceptorList;
	}

	/**
	 * 判断Advisors是否符合配置要求
	 *
	 * Determine whether the Advisors contain matching introductions.
	 */
	private static boolean hasMatchingIntroductions(Advisor[] advisors, Class<?> actualClass) {
		for (Advisor advisor : advisors) {
			if (advisor instanceof IntroductionAdvisor) {
				IntroductionAdvisor ia = (IntroductionAdvisor) advisor;
				if (ia.getClassFilter().matches(actualClass)) {
					return true;
				}
			}
		}
		return false;
	}

}
