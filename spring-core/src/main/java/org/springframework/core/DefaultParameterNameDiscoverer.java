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

package org.springframework.core;

/**
 * {@link ParameterNameDiscoverer}策略接口的默认实现，使用Java 8标准反射机制(如果有),
 * 然后回退基于ASM的{@link LocalVariableTableParameterNameDiscoverer},以检查在类文件中
 * 调试信息
 *
 * Default implementation of the {@link ParameterNameDiscoverer} strategy interface,
 * using the Java 8 standard reflection mechanism (if available), and falling back
 * to the ASM-based {@link LocalVariableTableParameterNameDiscoverer} for checking
 * debug information in the class file.
 *
 * <p>If a Kotlin reflection implementation is present,
 * {@link KotlinReflectionParameterNameDiscoverer} is added first in the list and
 * used for Kotlin classes and interfaces. When compiling or running as a GraalVM
 * native image, the {@code KotlinReflectionParameterNameDiscoverer} is not used.
 *
 * <p>Further discoverers may be added through {@link #addDiscoverer(ParameterNameDiscoverer)}.
 *
 * @author Juergen Hoeller
 * @author Sebastien Deleuze
 * @author Sam Brannen
 * @since 4.0
 * @see StandardReflectionParameterNameDiscoverer
 * @see LocalVariableTableParameterNameDiscoverer
 * @see KotlinReflectionParameterNameDiscoverer
 */
public class DefaultParameterNameDiscoverer extends PrioritizedParameterNameDiscoverer {

	public DefaultParameterNameDiscoverer() {
		// 如果项目不是运行在GraalVM环境里且存在kotlin反射
		if (KotlinDetector.isKotlinReflectPresent() && !GraalDetector.inImageCode()) {
			// 添加Kotlin的反射工具内省参数名发现器
			addDiscoverer(new KotlinReflectionParameterNameDiscoverer());
		}
		// 添加使用JDK8的反射工具内省参数名(基于'-parameters'编译器标记)的参数名发现器
		addDiscoverer(new StandardReflectionParameterNameDiscoverer());
		// 添加基于ASM库对Class文件的解析获取LocalVariableTable信息来发现参数名 的参数名发现器
		addDiscoverer(new LocalVariableTableParameterNameDiscoverer());
	}

}
