/*
 * Copyright 2002-2017 the original author or authors.
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

package org.springframework.core;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.LinkedList;
import java.util.List;

import org.springframework.lang.Nullable;

/**
 * 陆续尝试一些发现器委托类的{@link ParameterNameDiscoverer}实现。
 * 在{@code addDiscoverer}方法中最先添加的那些有效级最高。如果一个返回{@code null},尝试下一个
 *
 * {@link ParameterNameDiscoverer} implementation that tries several discoverer
 * delegates in succession. Those added first in the {@code addDiscoverer} method
 * have highest priority. If one returns {@code null}, the next will be tried.
 *
 * <p>The default behavior is to return {@code null} if no discoverer matches.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @since 2.0
 */
public class PrioritizedParameterNameDiscoverer implements ParameterNameDiscoverer {

	// 参数名发现器集合
	private final List<ParameterNameDiscoverer> parameterNameDiscoverers = new LinkedList<>();


	/**
	 * 向此{@code PrioritizedParameterNameDiscoverer}检查的发现器列表添加一个{@link ParameterNameDiscoverer}委托对象
	 *
	 * Add a further {@link ParameterNameDiscoverer} delegate to the list of
	 * discoverers that this {@code PrioritizedParameterNameDiscoverer} checks.
	 */
	public void addDiscoverer(ParameterNameDiscoverer pnd) {
		this.parameterNameDiscoverers.add(pnd);
	}


	@Override
	@Nullable
	public String[] getParameterNames(Method method) {
		// 遍历参数名发现器集合
		for (ParameterNameDiscoverer pnd : this.parameterNameDiscoverers) {
			// 通过参数名发现器获取method中的参数名数组
			String[] result = pnd.getParameterNames(method);
			// 如果result不为null，表示该参数名发现器能拿到method的参数名
			if (result != null) {
				// 返回结果
				return result;
			}
		}
		// 如果所有的参数名发现器都没法拿到参数名，就返回null
		return null;
	}

	@Override
	@Nullable
	public String[] getParameterNames(Constructor<?> ctor) {
		// 遍历参数名发现器集合
		for (ParameterNameDiscoverer pnd : this.parameterNameDiscoverers) {
			// 通过参数名发现器获取ctor中的参数名数组
			String[] result = pnd.getParameterNames(ctor);
			// 如果result不为null，表示该参数名发现器能拿到ctor的参数名
			if (result != null) {
				// 返回结果
				return result;
			}
		}
		// 如果所有的参数名发现器都没法拿到参数名，就返回null
		return null;
	}

}
