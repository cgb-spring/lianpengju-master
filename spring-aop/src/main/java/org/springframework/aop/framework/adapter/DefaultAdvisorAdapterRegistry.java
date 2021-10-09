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

package org.springframework.aop.framework.adapter;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.aopalliance.aop.Advice;
import org.aopalliance.intercept.MethodInterceptor;

import org.springframework.aop.Advisor;
import org.springframework.aop.support.DefaultPointcutAdvisor;

/**
 * AdvisorAdapterRegistry的实现类。也是SpringAOP中唯一默认的实现类。
 * 持有：MethodBeforeAdviceAdapter、AfterReturningAdviceAdapter、ThrowsAdviceAdapter实例。
 *
 * Default implementation of the {@link AdvisorAdapterRegistry} interface.
 * Supports {@link org.aopalliance.intercept.MethodInterceptor},
 * {@link org.springframework.aop.MethodBeforeAdvice},
 * {@link org.springframework.aop.AfterReturningAdvice},
 * {@link org.springframework.aop.ThrowsAdvice}.
 *
 * @author Rod Johnson
 * @author Rob Harrop
 * @author Juergen Hoeller
 */
@SuppressWarnings("serial")
public class DefaultAdvisorAdapterRegistry implements AdvisorAdapterRegistry, Serializable {

	// 持有一个AdvisorAdapter的List，这个List中的adapter是与实现Spring AOP的advice增强功能相对应的
	private final List<AdvisorAdapter> adapters = new ArrayList<>(3);


	/**
	 * 此方法把已有的advice实现的adapter加入进来
	 *
	 * Create a new DefaultAdvisorAdapterRegistry, registering well-known adapters.
	 */
	public DefaultAdvisorAdapterRegistry() {
		registerAdvisorAdapter(new MethodBeforeAdviceAdapter());
		registerAdvisorAdapter(new AfterReturningAdviceAdapter());
		registerAdvisorAdapter(new ThrowsAdviceAdapter());
	}


	@Override
	public Advisor wrap(Object adviceObject) throws UnknownAdviceTypeException {
		// 如果要封装的对象本身就是Advisor类型，那么无须做任何处理
		if (adviceObject instanceof Advisor) {
			return (Advisor) adviceObject;
		}
		// 如果类型不是Advisor和Advice两种类型的数据，那么将不能进行封装
		if (!(adviceObject instanceof Advice)) {
			throw new UnknownAdviceTypeException(adviceObject);
		}
		Advice advice = (Advice) adviceObject;
		if (advice instanceof MethodInterceptor) {
			// So well-known it doesn't even need an adapter.
			// 如果是MethodInterceptor类型则使用DefaultPointcutAdvisor封装
			return new DefaultPointcutAdvisor(advice);
		}
		// 如果存在Advisor的适配器那么也同样需要进行封装
		for (AdvisorAdapter adapter : this.adapters) {
			// Check that it is supported.
			if (adapter.supportsAdvice(advice)) {
				return new DefaultPointcutAdvisor(advice);
			}
		}
		throw new UnknownAdviceTypeException(advice);
	}

	// 将 Advisor转换为 MethodInterceptor
	@Override
	public MethodInterceptor[] getInterceptors(Advisor advisor) throws UnknownAdviceTypeException {
		List<MethodInterceptor> interceptors = new ArrayList<>(3);
		// 从Advisor中获取 Advice
		Advice advice = advisor.getAdvice();
		if (advice instanceof MethodInterceptor) {
			interceptors.add((MethodInterceptor) advice);
		}
		for (AdvisorAdapter adapter : this.adapters) {
			if (adapter.supportsAdvice(advice)) {
				// 转换为对应的 MethodInterceptor类型
				// AfterReturningAdviceInterceptor MethodBeforeAdviceInterceptor  ThrowsAdviceInterceptor
				interceptors.add(adapter.getInterceptor(advisor));
			}
		}
		if (interceptors.isEmpty()) {
			throw new UnknownAdviceTypeException(advisor.getAdvice());
		}
		return interceptors.toArray(new MethodInterceptor[0]);
	}

	// 新增的 Advisor适配器
	@Override
	public void registerAdvisorAdapter(AdvisorAdapter adapter) {
		this.adapters.add(adapter);
	}

}
