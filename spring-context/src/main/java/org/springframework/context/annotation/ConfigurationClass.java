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

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.parsing.Location;
import org.springframework.beans.factory.parsing.Problem;
import org.springframework.beans.factory.parsing.ProblemReporter;
import org.springframework.beans.factory.support.BeanDefinitionReader;
import org.springframework.core.io.DescriptiveResource;
import org.springframework.core.io.Resource;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

/**
 * ConfigurationClass类代表一个用户定义的配置类，包括@Bean注解标注的方法，包括所有在类的父类中定义的方法
 *
 * Represents a user-defined {@link Configuration @Configuration} class.
 * Includes a set of {@link Bean} methods, including all such methods
 * defined in the ancestry of the class, in a 'flattened-out' manner.
 *
 * @author Chris Beams
 * @author Juergen Hoeller
 * @author Phillip Webb
 * @since 3.0
 * @see BeanMethod
 * @see ConfigurationClassParser
 */
final class ConfigurationClass {

	// 配置类的注解元数据
	private final AnnotationMetadata metadata;

	// 配置类的资源对象
	private final Resource resource;

	// bean名称
	@Nullable
	private String beanName;

	// 存放通过@Import注解引入的候选类是通过哪个配置类引入的，包括ImportSelector选择器类中引入的候选类、DeferredImportSelector选择器中引入的候选类、@Import引入的普通类
	private final Set<ConfigurationClass> importedBy = new LinkedHashSet<>(1);

	// 普通类的Bean方法对象
	private final Set<BeanMethod> beanMethods = new LinkedHashSet<>();

	// 存放通过@ImportResource注解引入资源类及BeanDefinitionReader读取器对应关系
	private final Map<String, Class<? extends BeanDefinitionReader>> importedResources =
			new LinkedHashMap<>();

	// 存放通过@Import注解引入的ImportBeanDefinitionRegistrar实现类，用于注册自定义bean到IOC容器
	// value值是引入当前类的类的注解元数据
	private final Map<ImportBeanDefinitionRegistrar, AnnotationMetadata> importBeanDefinitionRegistrars =
			new LinkedHashMap<>();

	// 存放将BeanMethod标记为按其条件跳过
	final Set<String> skippedBeanMethods = new HashSet<>();


	/**
	 * Create a new {@link ConfigurationClass} with the given name.
	 * @param metadataReader reader used to parse the underlying {@link Class}
	 * @param beanName must not be {@code null}
	 * @see ConfigurationClass#ConfigurationClass(Class, ConfigurationClass)
	 */
	public ConfigurationClass(MetadataReader metadataReader, String beanName) {
		Assert.notNull(beanName, "Bean name must not be null");
		this.metadata = metadataReader.getAnnotationMetadata();
		this.resource = metadataReader.getResource();
		this.beanName = beanName;
	}

	/**
	 * Create a new {@link ConfigurationClass} representing a class that was imported
	 * using the {@link Import} annotation or automatically processed as a nested
	 * configuration class (if importedBy is not {@code null}).
	 * @param metadataReader reader used to parse the underlying {@link Class}
	 * @param importedBy the configuration class importing this one or {@code null}
	 * @since 3.1.1
	 */
	public ConfigurationClass(MetadataReader metadataReader, @Nullable ConfigurationClass importedBy) {
		this.metadata = metadataReader.getAnnotationMetadata();
		this.resource = metadataReader.getResource();
		this.importedBy.add(importedBy);
	}

	/**
	 * Create a new {@link ConfigurationClass} with the given name.
	 * @param clazz the underlying {@link Class} to represent
	 * @param beanName name of the {@code @Configuration} class bean
	 * @see ConfigurationClass#ConfigurationClass(Class, ConfigurationClass)
	 */
	public ConfigurationClass(Class<?> clazz, String beanName) {
		Assert.notNull(beanName, "Bean name must not be null");
		this.metadata = AnnotationMetadata.introspect(clazz);
		this.resource = new DescriptiveResource(clazz.getName());
		this.beanName = beanName;
	}

	/**
	 * Create a new {@link ConfigurationClass} representing a class that was imported
	 * using the {@link Import} annotation or automatically processed as a nested
	 * configuration class (if imported is {@code true}).
	 * @param clazz the underlying {@link Class} to represent
	 * @param importedBy the configuration class importing this one (or {@code null})
	 * @since 3.1.1
	 */
	public ConfigurationClass(Class<?> clazz, @Nullable ConfigurationClass importedBy) {
		this.metadata = AnnotationMetadata.introspect(clazz);
		this.resource = new DescriptiveResource(clazz.getName());
		this.importedBy.add(importedBy);
	}

	/**
	 * Create a new {@link ConfigurationClass} with the given name.
	 * @param metadata the metadata for the underlying class to represent
	 * @param beanName name of the {@code @Configuration} class bean
	 * @see ConfigurationClass#ConfigurationClass(Class, ConfigurationClass)
	 */
	public ConfigurationClass(AnnotationMetadata metadata, String beanName) {
		Assert.notNull(beanName, "Bean name must not be null");
		this.metadata = metadata;
		this.resource = new DescriptiveResource(metadata.getClassName());
		this.beanName = beanName;
	}


	public AnnotationMetadata getMetadata() {
		return this.metadata;
	}

	public Resource getResource() {
		return this.resource;
	}

	public String getSimpleName() {
		return ClassUtils.getShortName(getMetadata().getClassName());
	}

	public void setBeanName(String beanName) {
		this.beanName = beanName;
	}

	@Nullable
	public String getBeanName() {
		return this.beanName;
	}

	/**
	 * 通过ImportedBy属性判断是否是通过@Import注解被引入还是被嵌套在其他配置类中被自动注入的
	 *
	 * Return whether this configuration class was registered via @{@link Import} or
	 * automatically registered due to being nested within another configuration class.
	 * @since 3.1.1
	 * @see #getImportedBy()
	 */
	public boolean isImported() {
		return !this.importedBy.isEmpty();
	}

	/**
	 * Merge the imported-by declarations from the given configuration class into this one.
	 * @since 4.0.5
	 */
	public void mergeImportedBy(ConfigurationClass otherConfigClass) {
		this.importedBy.addAll(otherConfigClass.importedBy);
	}

	/**
	 * Return the configuration classes that imported this class,
	 * or an empty Set if this configuration was not imported.
	 * @since 4.0.5
	 * @see #isImported()
	 */
	public Set<ConfigurationClass> getImportedBy() {
		return this.importedBy;
	}

	public void addBeanMethod(BeanMethod method) {
		this.beanMethods.add(method);
	}

	public Set<BeanMethod> getBeanMethods() {
		return this.beanMethods;
	}

	public void addImportedResource(String importedResource, Class<? extends BeanDefinitionReader> readerClass) {
		this.importedResources.put(importedResource, readerClass);
	}

	public void addImportBeanDefinitionRegistrar(ImportBeanDefinitionRegistrar registrar, AnnotationMetadata importingClassMetadata) {
		this.importBeanDefinitionRegistrars.put(registrar, importingClassMetadata);
	}

	public Map<ImportBeanDefinitionRegistrar, AnnotationMetadata> getImportBeanDefinitionRegistrars() {
		return this.importBeanDefinitionRegistrars;
	}

	public Map<String, Class<? extends BeanDefinitionReader>> getImportedResources() {
		return this.importedResources;
	}

	/**
	 * 配置类的校验方法
	 * @param problemReporter
	 */
	public void validate(ProblemReporter problemReporter) {
		// A configuration class may not be final (CGLIB limitation) unless it declares proxyBeanMethods=false
		// 获取@Configuration注解的属性值，除非配置类声明为proxyBeanMethods=false不适用CGLIB代理模式，否则的话不可能为final类
		Map<String, Object> attributes = this.metadata.getAnnotationAttributes(Configuration.class.getName());
		if (attributes != null && (Boolean) attributes.get("proxyBeanMethods")) {
			// 如果配置类是final类型，则抛出异常
			if (this.metadata.isFinal()) {
				problemReporter.error(new FinalConfigurationProblem());
			}
			// 校验配置类中@Bean定义的方法
			for (BeanMethod beanMethod : this.beanMethods) {
				beanMethod.validate(problemReporter);
			}
		}
	}

	@Override
	public boolean equals(@Nullable Object other) {
		return (this == other || (other instanceof ConfigurationClass &&
				getMetadata().getClassName().equals(((ConfigurationClass) other).getMetadata().getClassName())));
	}

	@Override
	public int hashCode() {
		return getMetadata().getClassName().hashCode();
	}

	@Override
	public String toString() {
		return "ConfigurationClass: beanName '" + this.beanName + "', " + this.resource;
	}


	/**
	 * Configuration classes must be non-final to accommodate CGLIB subclassing.
	 */
	private class FinalConfigurationProblem extends Problem {

		public FinalConfigurationProblem() {
			super(String.format("@Configuration class '%s' may not be final. Remove the final modifier to continue.",
					getSimpleName()), new Location(getResource(), getMetadata()));
		}
	}

}
