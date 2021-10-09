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

package org.springframework.core;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import org.springframework.lang.Nullable;

/**
 * 用于发现方法和构造函数的参数名的接口
 *
 * Interface to discover parameter names for methods and constructors.
 *
 *
 * 参数名并非总是可以发现参数名称，但可以尝试各种策略，比如寻找在编译时可能发生调试信号，和寻找可选的附带AspectJ注解方法的argname注解值
 *
 * <p>Parameter name discovery is not always possible, but various strategies are
 * available to try, such as looking for debug information that may have been
 * emitted at compile time, and looking for argname annotation values optionally
 * accompanying AspectJ annotated methods.
 *
 * @author Rod Johnson
 * @author Adrian Colyer
 * @since 2.0
 */
public interface ParameterNameDiscoverer {

	/**
	 *  返回方法的参数名，如果不能确定就返回{@code null}
	 *
	 * Return parameter names for a method, or {@code null} if they cannot be determined.
	 * <p>Individual entries in the array may be {@code null} if parameter names are only
	 * available for some parameters of the given method but not for others. However,
	 * it is recommended to use stub parameter names instead wherever feasible.
	 * @param method the method to find parameter names for
	 * @return an array of parameter names if the names can be resolved,
	 * or {@code null} if they cannot
	 */
	@Nullable
	String[] getParameterNames(Method method);

	/**
	 * 返回构造函数的参数名，如果不能确定就返回{@code null}
	 *
	 * Return parameter names for a constructor, or {@code null} if they cannot be determined.
	 * <p>Individual entries in the array may be {@code null} if parameter names are only
	 * available for some parameters of the given constructor but not for others. However,
	 * it is recommended to use stub parameter names instead wherever feasible.
	 * @param ctor the constructor to find parameter names for
	 * @return an array of parameter names if the names can be resolved,
	 * or {@code null} if they cannot
	 */
	@Nullable
	String[] getParameterNames(Constructor<?> ctor);

}
