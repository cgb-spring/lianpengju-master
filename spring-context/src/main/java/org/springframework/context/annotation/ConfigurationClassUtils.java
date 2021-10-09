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

package org.springframework.context.annotation;

import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.aop.framework.AopInfrastructureBean;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.context.event.EventListenerFactory;
import org.springframework.core.Conventions;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.annotation.Order;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * 此类是Configuration配置类的工具类，
 *
 * Utilities for identifying {@link Configuration} classes.
 *
 * @author Chris Beams
 * @author Juergen Hoeller
 * @since 3.1
 */
abstract class ConfigurationClassUtils {

	// Configuration class如果是@Configuration注解标注的类，那么将属性标注为full
	public static final String CONFIGURATION_CLASS_FULL = "full";

	// 非@Configuration注解标注的类，那么将属性标注如lite
	public static final String CONFIGURATION_CLASS_LITE = "lite";

	// org.springframework.context.annotation.ConfigurationClassPostProcessor.configurationClass作为属性配置类型标记属性的key
	public static final String CONFIGURATION_CLASS_ATTRIBUTE =
			Conventions.getQualifiedAttributeName(ConfigurationClassPostProcessor.class, "configurationClass");

	// org.springframework.context.annotation.ConfigurationClassPostProcessor.order配置属性配置类排序的属性key
	private static final String ORDER_ATTRIBUTE =
			Conventions.getQualifiedAttributeName(ConfigurationClassPostProcessor.class, "order");


	private static final Log logger = LogFactory.getLog(ConfigurationClassUtils.class);

	// 定义set集合，用于存储标注配置类的注解
	private static final Set<String> candidateIndicators = new HashSet<>(8);

	static {
		candidateIndicators.add(Component.class.getName());
		candidateIndicators.add(ComponentScan.class.getName());
		candidateIndicators.add(Import.class.getName());
		candidateIndicators.add(ImportResource.class.getName());
	}


	/**
	 * 检查当前给定的beanDefinition中是否是一个配置类的候选者，
	 * 判断在嵌套的关系中是否包含配置类或者组件类或者自动注册进去的，并做相应的标记
	 *
	 * Check whether the given bean definition is a candidate for a configuration class
	 * (or a nested component class declared within a configuration/component class,
	 * to be auto-registered as well), and mark it accordingly.
	 * @param beanDef the bean definition to check
	 * @param metadataReaderFactory the current factory in use by the caller
	 * @return whether the candidate qualifies as (any kind of) configuration class
	 */
	public static boolean checkConfigurationClassCandidate(
			BeanDefinition beanDef, MetadataReaderFactory metadataReaderFactory) {

		// 获取bean定义信息中的class类名
		String className = beanDef.getBeanClassName();
		// 如果className为空，或者bean定义信息中的factoryMethod不等于空，那么直接返回
		if (className == null || beanDef.getFactoryMethodName() != null) {
			return false;
		}

		AnnotationMetadata metadata;
		// 通过注解注入的db都是AnnotatedGenericBeanDefinition，实现了AnnotatedBeanDefinition
		// spring内部的bd是RootBeanDefinition，实现了AbstractBeanDefinition
		// 此处主要用于判断是否归属于AnnotatedBeanDefinition
		if (beanDef instanceof AnnotatedBeanDefinition &&
				className.equals(((AnnotatedBeanDefinition) beanDef).getMetadata().getClassName())) {
			// Can reuse the pre-parsed metadata from the given BeanDefinition...
			// 从当前bean的定义信息中获取元数据信息
			metadata = ((AnnotatedBeanDefinition) beanDef).getMetadata();
		}
		// 判断是否是spring中默认的BeanDefinition
		else if (beanDef instanceof AbstractBeanDefinition && ((AbstractBeanDefinition) beanDef).hasBeanClass()) {
			// Check already loaded Class if present...
			// since we possibly can't even load the class file for this Class.
			// 获取当前bean对象的Class对象
			Class<?> beanClass = ((AbstractBeanDefinition) beanDef).getBeanClass();
			// 如果class实例是下面四种类或接口的子类、父接口等任何一种情况，直接返回
			if (BeanFactoryPostProcessor.class.isAssignableFrom(beanClass) ||
					BeanPostProcessor.class.isAssignableFrom(beanClass) ||
					AopInfrastructureBean.class.isAssignableFrom(beanClass) ||
					EventListenerFactory.class.isAssignableFrom(beanClass)) {
				return false;
			}
			// 为给定类创建新的AnnotationMetadata实例
			metadata = AnnotationMetadata.introspect(beanClass);
		}
		// 如果上述两种情况都不符合
		else {
			try {
				// 获取className的MetadataReader实例
				MetadataReader metadataReader = metadataReaderFactory.getMetadataReader(className);
				// 读取底层类的完整注释元数据，包括带注解方法的元数据
				metadata = metadataReader.getAnnotationMetadata();
			}
			catch (IOException ex) {
				if (logger.isDebugEnabled()) {
					logger.debug("Could not find class file for introspecting configuration annotations: " +
							className, ex);
				}
				return false;
			}
		}

		// 获取bean定义的元数据被@Configuration注解标注的属性字典值
		Map<String, Object> config = metadata.getAnnotationAttributes(Configuration.class.getName());
		// 如果bean被@Configuration注解标注，且属性proxyBeanMethods为false(使用代理模式)，则将bean定义记为full
		if (config != null && !Boolean.FALSE.equals(config.get("proxyBeanMethods"))) {
			beanDef.setAttribute(CONFIGURATION_CLASS_ATTRIBUTE, CONFIGURATION_CLASS_FULL);
		}
		// 如果bean被@configuration注解标注，且被注解@Component，@ComponentScan、@Import、@ImportResource或者@Bean标记的方法，则将bean定义标记为lite
		else if (config != null || isConfigurationCandidate(metadata)) {
			beanDef.setAttribute(CONFIGURATION_CLASS_ATTRIBUTE, CONFIGURATION_CLASS_LITE);
		}
		else {
			return false;
		}

		// It's a full or lite configuration candidate... Let's determine the order value, if any.
		// bean定义是一个标记为full或lite的候选项，如果设置order则设置order属性值
		Integer order = getOrder(metadata);
		// 如果值不为空的话，那么直接设置值到具体的beanDefinition
		if (order != null) {
			// 设置bean定义的order值
			beanDef.setAttribute(ORDER_ATTRIBUTE, order);
		}

		return true;
	}

	/**
	 * 检查给定的元数据，以查找给定的候选配置类是否被指定的注解标注
	 *
	 * Check the given metadata for a configuration class candidate
	 * (or nested component class declared within a configuration/component class).
	 * @param metadata the metadata of the annotated class
	 * @return {@code true} if the given class is to be registered for
	 * configuration class processing; {@code false} otherwise
	 */
	public static boolean isConfigurationCandidate(AnnotationMetadata metadata) {
		// Do not consider an interface or an annotation...
		// 不考虑接口或注解
		if (metadata.isInterface()) {
			return false;
		}

		// Any of the typical annotations found?
		// 检查是否被注解@Component、@ComponentScan、@Import、@ImportResource标注
		for (String indicator : candidateIndicators) {
			if (metadata.isAnnotated(indicator)) {
				return true;
			}
		}

		// Finally, let's look for @Bean methods...
		// 最后检查是否有@Bean标注的方法
		try {
			return metadata.hasAnnotatedMethods(Bean.class.getName());
		}
		catch (Throwable ex) {
			if (logger.isDebugEnabled()) {
				logger.debug("Failed to introspect @Bean methods on class [" + metadata.getClassName() + "]: " + ex);
			}
			return false;
		}
	}

	/**
	 * Determine the order for the given configuration class metadata.
	 * @param metadata the metadata of the annotated class
	 * @return the {@code @Order} annotation value on the configuration class,
	 * or {@code Ordered.LOWEST_PRECEDENCE} if none declared
	 * @since 5.0
	 */
	@Nullable
	public static Integer getOrder(AnnotationMetadata metadata) {
		Map<String, Object> orderAttributes = metadata.getAnnotationAttributes(Order.class.getName());
		return (orderAttributes != null ? ((Integer) orderAttributes.get(AnnotationUtils.VALUE)) : null);
	}

	/**
	 * Determine the order for the given configuration class bean definition,
	 * as set by {@link #checkConfigurationClassCandidate}.
	 * @param beanDef the bean definition to check
	 * @return the {@link Order @Order} annotation value on the configuration class,
	 * or {@link Ordered#LOWEST_PRECEDENCE} if none declared
	 * @since 4.2
	 */
	public static int getOrder(BeanDefinition beanDef) {
		Integer order = (Integer) beanDef.getAttribute(ORDER_ATTRIBUTE);
		return (order != null ? order : Ordered.LOWEST_PRECEDENCE);
	}

}
