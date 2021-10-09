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

package org.springframework.beans.factory.annotation;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.PropertyValues;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.lang.Nullable;
import org.springframework.util.ReflectionUtils;

/**
 * 注入元数据
 *
 * Internal class for managing injection metadata.
 * Not intended for direct use in applications.
 *
 * <p>Used by {@link AutowiredAnnotationBeanPostProcessor},
 * {link org.springframework.context.annotation.CommonAnnotationBeanPostProcessor} and
 * {link org.springframework.orm.jpa.support.PersistenceAnnotationBeanPostProcessor}.
 *
 * @author Juergen Hoeller
 * @since 2.5
 */
public class InjectionMetadata {

	/**
	 * An empty {@code InjectionMetadata} instance with no-op callbacks.
	 * @since 5.2
	 */
	public static final InjectionMetadata EMPTY = new InjectionMetadata(Object.class, Collections.emptyList()) {
		@Override
		protected boolean needsRefresh(Class<?> clazz) {
			return false;
		}
		@Override
		public void checkConfigMembers(RootBeanDefinition beanDefinition) {
		}
		@Override
		public void inject(Object target, @Nullable String beanName, @Nullable PropertyValues pvs) {
		}
		@Override
		public void clear(@Nullable PropertyValues pvs) {
		}
	};


	private static final Log logger = LogFactory.getLog(InjectionMetadata.class);

	private final Class<?> targetClass;

	private final Collection<InjectedElement> injectedElements;

	@Nullable
	private volatile Set<InjectedElement> checkedElements;


	/**
	 * Create a new {@code InjectionMetadata instance}.
	 * <p>Preferably use {@link #forElements} for reusing the {@link #EMPTY}
	 * instance in case of no elements.
	 * @param targetClass the target class
	 * @param elements the associated elements to inject
	 * @see #forElements
	 */
	public InjectionMetadata(Class<?> targetClass, Collection<InjectedElement> elements) {
		this.targetClass = targetClass;
		this.injectedElements = elements;
	}


	/**
	 * Determine whether this metadata instance needs to be refreshed.
	 * @param clazz the current target class
	 * @return {@code true} indicating a refresh, {@code false} otherwise
	 * @since 5.2.4
	 */
	protected boolean needsRefresh(Class<?> clazz) {
		return this.targetClass != clazz;
	}

	/**
	 * 如果集合中有注解的属性和方法，就会将它们注册到beanDefinition的集合externallyManagedConfigMembers中
	 * @param beanDefinition
	 */
	public void checkConfigMembers(RootBeanDefinition beanDefinition) {
		Set<InjectedElement> checkedElements = new LinkedHashSet<>(this.injectedElements.size());
		for (InjectedElement element : this.injectedElements) {
			Member member = element.getMember();
			if (!beanDefinition.isExternallyManagedConfigMember(member)) {
				beanDefinition.registerExternallyManagedConfigMember(member);
				checkedElements.add(element);
				if (logger.isTraceEnabled()) {
					logger.trace("Registered injected element on class [" + this.targetClass.getName() + "]: " + element);
				}
			}
		}
		this.checkedElements = checkedElements;
	}

	/**
	 * 遍历前面注册的InjectedElement，然后进行注入
	 * @param target
	 * @param beanName
	 * @param pvs
	 * @throws Throwable
	 */
	public void inject(Object target, @Nullable String beanName, @Nullable PropertyValues pvs) throws Throwable {
		Collection<InjectedElement> checkedElements = this.checkedElements;
		Collection<InjectedElement> elementsToIterate =
				(checkedElements != null ? checkedElements : this.injectedElements);
		if (!elementsToIterate.isEmpty()) {
			for (InjectedElement element : elementsToIterate) {
				if (logger.isTraceEnabled()) {
					logger.trace("Processing injected element of bean '" + beanName + "': " + element);
				}
				element.inject(target, beanName, pvs);
			}
		}
	}

	/**
	 * Clear property skipping for the contained elements.
	 * @since 3.2.13
	 */
	public void clear(@Nullable PropertyValues pvs) {
		Collection<InjectedElement> checkedElements = this.checkedElements;
		Collection<InjectedElement> elementsToIterate =
				(checkedElements != null ? checkedElements : this.injectedElements);
		if (!elementsToIterate.isEmpty()) {
			for (InjectedElement element : elementsToIterate) {
				element.clearPropertySkipping(pvs);
			}
		}
	}


	/**
	 * 根据是否有属性和方法有@Autowired和@Value注解，没有就返回InjectionMetadata.EMPTY,有的话就封装成InjectionMetadata返回
	 *
	 * Return an {@code InjectionMetadata} instance, possibly for empty elements.
	 * @param elements the elements to inject (possibly empty)
	 * @param clazz the target class
	 * @return a new {@link #InjectionMetadata(Class, Collection)} instance,
	 * or {@link #EMPTY} in case of no elements
	 * @since 5.2
	 */
	public static InjectionMetadata forElements(Collection<InjectedElement> elements, Class<?> clazz) {
		return (elements.isEmpty() ? InjectionMetadata.EMPTY : new InjectionMetadata(clazz, elements));
	}

	/**
	 * 判断给定的注入元数据是否需要被刷新
	 *
	 * Check whether the given injection metadata needs to be refreshed.
	 * @param metadata the existing metadata instance
	 * @param clazz the current target class
	 * @return {@code true} indicating a refresh, {@code false} otherwise
	 * @see #needsRefresh(Class)
	 */
	public static boolean needsRefresh(@Nullable InjectionMetadata metadata, Class<?> clazz) {
		return (metadata == null || metadata.needsRefresh(clazz));
	}


	/**
	 * A single injected element.
	 */
	public abstract static class InjectedElement {

		protected final Member member;

		protected final boolean isField;

		@Nullable
		protected final PropertyDescriptor pd;

		@Nullable
		protected volatile Boolean skip;

		protected InjectedElement(Member member, @Nullable PropertyDescriptor pd) {
			this.member = member;
			this.isField = (member instanceof Field);
			this.pd = pd;
		}

		public final Member getMember() {
			return this.member;
		}

		protected final Class<?> getResourceType() {
			if (this.isField) {
				return ((Field) this.member).getType();
			}
			else if (this.pd != null) {
				return this.pd.getPropertyType();
			}
			else {
				return ((Method) this.member).getParameterTypes()[0];
			}
		}

		protected final void checkResourceType(Class<?> resourceType) {
			if (this.isField) {
				Class<?> fieldType = ((Field) this.member).getType();
				if (!(resourceType.isAssignableFrom(fieldType) || fieldType.isAssignableFrom(resourceType))) {
					throw new IllegalStateException("Specified field type [" + fieldType +
							"] is incompatible with resource type [" + resourceType.getName() + "]");
				}
			}
			else {
				Class<?> paramType =
						(this.pd != null ? this.pd.getPropertyType() : ((Method) this.member).getParameterTypes()[0]);
				if (!(resourceType.isAssignableFrom(paramType) || paramType.isAssignableFrom(resourceType))) {
					throw new IllegalStateException("Specified parameter type [" + paramType +
							"] is incompatible with resource type [" + resourceType.getName() + "]");
				}
			}
		}

		/**
		 * 进行属性或者方法注入，但是方法注入前会判断是否已经有设置值了，有设置就不会注入，直接返回
		 *
		 * Either this or {@link #getResourceToInject} needs to be overridden.
		 */
		protected void inject(Object target, @Nullable String requestingBeanName, @Nullable PropertyValues pvs)
				throws Throwable {

			// 属性注入
			if (this.isField) {
				Field field = (Field) this.member;
				ReflectionUtils.makeAccessible(field);
				// 如果是使用字段形式的注入，getResourceToInject由子类@ResourceElement实现
				field.set(target, getResourceToInject(target, requestingBeanName));
			}
			else {
				// 此步骤检测如果bean已经显示的设置一个对象依赖引用则跳过使用setter方法再次赋值
				if (checkPropertySkipping(pvs)) {
					return;
				}
				try {
					// 方法注入
					Method method = (Method) this.member;
					// 支持私有方法
					ReflectionUtils.makeAccessible(method);
					method.invoke(target, getResourceToInject(target, requestingBeanName));
				}
				catch (InvocationTargetException ex) {
					throw ex.getTargetException();
				}
			}
		}

		/**
		 * 判断是否要略过属性注入，如果是定义的时候给定值了就忽略
		 *
		 * Check whether this injector's property needs to be skipped due to
		 * an explicit property value having been specified. Also marks the
		 * affected property as processed for other processors to ignore it.
		 */
		protected boolean checkPropertySkipping(@Nullable PropertyValues pvs) {
			Boolean skip = this.skip;
			if (skip != null) {
				return skip;
			}
			if (pvs == null) {
				this.skip = false;
				return false;
			}
			synchronized (pvs) {
				skip = this.skip;
				if (skip != null) {
					return skip;
				}
				if (this.pd != null) {
					if (pvs.contains(this.pd.getName())) {
						// Explicit value provided as part of the bean definition.
						// 给定了值就忽略，直接返回
						this.skip = true;
						return true;
					}
					// 如果是可变的话，就注册到处理过的属性集合里
					else if (pvs instanceof MutablePropertyValues) {
						((MutablePropertyValues) pvs).registerProcessedProperty(this.pd.getName());
					}
				}
				this.skip = false;
				return false;
			}
		}

		/**
		 * Clear property skipping for this element.
		 * @since 3.2.13
		 */
		protected void clearPropertySkipping(@Nullable PropertyValues pvs) {
			if (pvs == null) {
				return;
			}
			synchronized (pvs) {
				if (Boolean.FALSE.equals(this.skip) && this.pd != null && pvs instanceof MutablePropertyValues) {
					((MutablePropertyValues) pvs).clearProcessedProperty(this.pd.getName());
				}
			}
		}

		/**
		 * Either this or {@link #inject} needs to be overridden.
		 */
		@Nullable
		protected Object getResourceToInject(Object target, @Nullable String requestingBeanName) {
			return null;
		}

		@Override
		public boolean equals(@Nullable Object other) {
			if (this == other) {
				return true;
			}
			if (!(other instanceof InjectedElement)) {
				return false;
			}
			InjectedElement otherElement = (InjectedElement) other;
			return this.member.equals(otherElement.member);
		}

		@Override
		public int hashCode() {
			return this.member.getClass().hashCode() * 29 + this.member.getName().hashCode();
		}

		@Override
		public String toString() {
			return getClass().getSimpleName() + " for " + this.member;
		}
	}

}
