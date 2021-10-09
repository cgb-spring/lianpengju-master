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

package org.springframework.core.annotation;

import java.lang.reflect.AnnotatedElement;
import java.util.Arrays;
import java.util.List;

import org.springframework.core.DecoratingProxy;
import org.springframework.core.OrderComparator;
import org.springframework.core.annotation.MergedAnnotations.SearchStrategy;
import org.springframework.lang.Nullable;

/**
 * AnnotationAwareOrderComparator是OrderComparator的扩展,它支持Spring
 * 的org.springframework.core.Ordered接口以及@Oreder和@Priority注解,其中
 * Ordered实例提供的Order值将覆盖静态定义的注解值(如果有)
 *
 * {@code AnnotationAwareOrderComparator} is an extension of
 * {@link OrderComparator} that supports Spring's
 * {@link org.springframework.core.Ordered} interface as well as the
 * {@link Order @Order} and {link javax.annotation.Priority @Priority}
 * annotations, with an order value provided by an {@code Ordered}
 * instance overriding a statically defined annotation value (if any).
 *
 * <p>Consult the Javadoc for {@link OrderComparator} for details on the
 * sort semantics for non-ordered objects.
 *
 * @author Juergen Hoeller
 * @author Oliver Gierke
 * @author Stephane Nicoll
 * @since 2.0.1
 * @see org.springframework.core.Ordered
 * @see org.springframework.core.annotation.Order
 * see javax.annotation.Priority
 */
public class AnnotationAwareOrderComparator extends OrderComparator {

	/**
	 * AnnotationAwareOrderComparator的共享默认实例
	 *
	 * Shared default instance of {@code AnnotationAwareOrderComparator}.
	 */
	public static final AnnotationAwareOrderComparator INSTANCE = new AnnotationAwareOrderComparator();


	/**
	 * 获取obj的优先级值
	 *
	 * This implementation checks for {@link Order @Order} or
	 * {link javax.annotation.Priority @Priority} on various kinds of
	 * elements, in addition to the {@link org.springframework.core.Ordered}
	 * check in the superclass.
	 */
	@Override
	@Nullable
	protected Integer findOrder(Object obj) {
		// 优先使用父级findOrder获取obj的优先级值
		Integer order = super.findOrder(obj);
		// 如果获取成功，直接返回出去，不再进行其他操作
		if (order != null) {
			return order;
		}
		// 取出obj的所有注解，从注解中获取优先级值
		return findOrderFromAnnotation(obj);
	}

	/**
	 * 取出obj的所有注解，从注解中获取@Order或@javax.annotation.Priority的优先级值
	 * @param obj
	 * @return
	 */
	@Nullable
	private Integer findOrderFromAnnotation(Object obj) {
		// 如果obj是AnnotatedElement对象，就引用该obj，否则获取obj的Class对象作为AnnotatedElement对象，因为Class对象默认继承AnnotatedElement
		AnnotatedElement element = (obj instanceof AnnotatedElement ? (AnnotatedElement) obj : obj.getClass());
		// 创建一个新的MergedAnnotations实例，该实例包含element的所有注解(对整个类型层次结构进行完整搜索，包括超类和已实现的接口)和元注解,
		MergedAnnotations annotations = MergedAnnotations.from(element, SearchStrategy.TYPE_HIERARCHY);
		// 从annotations中获取@Order或@javax.annotation.Priority的优先级值
		Integer order = OrderUtils.getOrderFromAnnotations(element, annotations);
		// 如果优先级值为null且obj是装饰代理类
		if (order == null && obj instanceof DecoratingProxy) {
			// 获取obj的被代理的对象来递归进行再一次的获取优先级值，然后将结果返回出去
			return findOrderFromAnnotation(((DecoratingProxy) obj).getDecoratedClass());
		}
		// 返回优先级值
		return order;
	}

	/**
	 * 获取obj上Priority注解的值
	 *
	 * This implementation retrieves an @{link javax.annotation.Priority}
	 * value, allowing for additional semantics over the regular @{@link Order}
	 * annotation: typically, selecting one object over another in case of
	 * multiple matches but only one object to be returned.
	 */
	@Override
	@Nullable
	public Integer getPriority(Object obj) {
		// 如果obj是Class对象
		if (obj instanceof Class) {
			// 获取obj上声明的Priority注解的值并返回出去
			return OrderUtils.getPriority((Class<?>) obj);
		}
		// 获取obj的Class对象上声明的Priority注解的值并返回出去
		Integer priority = OrderUtils.getPriority(obj.getClass());
		// 如果Priority注解的值为null且obj是装饰代理类
		if (priority == null  && obj instanceof DecoratingProxy) {
			// 获取obj的被代理的对象来递归进行再一次的获取Priority注解的值，然后将结果返回出去
			return getPriority(((DecoratingProxy) obj).getDecoratedClass());
		}
		// 返回Priority注解的值
		return priority;
	}


	/**
	 * 使用默认的AnnotationAwareOrderComparator
	 *
	 * Sort the given list with a default {@link AnnotationAwareOrderComparator}.
	 * <p>Optimized to skip sorting for lists with size 0 or 1,
	 * in order to avoid unnecessary array extraction.
	 * @param list the List to sort
	 * @see java.util.List#sort(java.util.Comparator)
	 */
	public static void sort(List<?> list) {
		// 如果list至少有一个元素
		if (list.size() > 1) {
			// 使用默认的AnnotationAwareOrderComparator进行排序
			list.sort(INSTANCE);
		}
	}

	/**
	 * 使用默认的AnnotationAwareOrderComparator
	 *
	 * Sort the given array with a default AnnotationAwareOrderComparator.
	 * <p>Optimized to skip sorting for lists with size 0 or 1,
	 * in order to avoid unnecessary array extraction.
	 * @param array the array to sort
	 * @see java.util.Arrays#sort(Object[], java.util.Comparator)
	 */
	public static void sort(Object[] array) {
		// 如果array至少有一个元素
		if (array.length > 1) {
			// 使用默认的AnnotationAwareOrderComparator进行排序
			Arrays.sort(array, INSTANCE);
		}
	}

	/**
	 * 如果有必要，使用默认的AnnotationAwareOrderComparator对给定的数组或列表进行排序。给定其他任何值时，只需跳过排序
	 *
	 * Sort the given array or List with a default AnnotationAwareOrderComparator,
	 * if necessary. Simply skips sorting when given any other value.
	 * <p>Optimized to skip sorting for lists with size 0 or 1,
	 * in order to avoid unnecessary array extraction.
	 * @param value the array or List to sort
	 * @see java.util.Arrays#sort(Object[], java.util.Comparator)
	 */
	public static void sortIfNecessary(Object value) {
		// 如果value是对象数组
		if (value instanceof Object[]) {
			// 使用默认的OrderComparator对value数组进行排序
			sort((Object[]) value);
		}
		// 如果value是List对象
		else if (value instanceof List) {
			// 使用默认的OrderComparator对value List进行排序
			sort((List<?>) value);
		}
	}

}
