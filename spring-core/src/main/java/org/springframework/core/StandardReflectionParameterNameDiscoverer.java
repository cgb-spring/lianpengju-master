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
import java.lang.reflect.Parameter;

import org.springframework.lang.Nullable;

/**
 * {@link ParameterNameDiscoverer}实现类,使用JDK8的反射工具内省参数名(基于'-parameters'编译器标记)
 *
 * {@link ParameterNameDiscoverer} implementation which uses JDK 8's reflection facilities
 * for introspecting parameter names (based on the "-parameters" compiler flag).
 *
 * @author Juergen Hoeller
 * @since 4.0
 * @see java.lang.reflect.Method#getParameters()
 * @see java.lang.reflect.Parameter#getName()
 */
public class StandardReflectionParameterNameDiscoverer implements ParameterNameDiscoverer {

	@Override
	@Nullable
	public String[] getParameterNames(Method method) {
		return getParameterNames(method.getParameters());
	}

	@Override
	@Nullable
	public String[] getParameterNames(Constructor<?> ctor) {
		return getParameterNames(ctor.getParameters());
	}

	@Nullable
	private String[] getParameterNames(Parameter[] parameters) {
		// 初始化一个用于存放参数名的数组，长度为parameters的长度
		String[] parameterNames = new String[parameters.length];
		// 遍历Parameter对象数组
		for (int i = 0; i < parameters.length; i++) {
			// 获取Parameters中的第i个Parameter对象
			Parameter param = parameters[i];
			// Parameter.isNamePresent：如果参数具有根据类文件的名称，则返回true。否则返回false。
			// 参数是否具有名称由声明该参数的方法MethodParameters确定。简单来说就是验证参数名是不是可用
			if (!param.isNamePresent()) {
				// 返回null，表示获取参数名失败
				return null;
			}
			// 获取param的名称赋值给第i个Parameter对象
			parameterNames[i] = param.getName();
		}
		// 返回参数名数组
		return parameterNames;
	}

}
