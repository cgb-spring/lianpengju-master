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

package org.springframework.core;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import org.springframework.lang.Nullable;
import org.springframework.util.ObjectUtils;

/**
 * 有序对象的比较实现，按顺序值升序或优先级降序排序
 *
 * {@link Comparator} implementation for {@link Ordered} objects, sorting
 * by order value ascending, respectively by priority descending.
 *
 * <h3>{@code PriorityOrdered} Objects</h3>
 * <p>{@link PriorityOrdered} objects will be sorted with higher priority than
 * <em>plain</em> {@code Ordered} objects.
 *
 * <h3>Same Order Objects</h3>
 * <p>Objects that have the same order value will be sorted with arbitrary
 * ordering with respect to other objects with the same order value.
 *
 * <h3>Non-ordered Objects</h3>
 * <p>Any object that does not provide its own order value is implicitly
 * assigned a value of {@link Ordered#LOWEST_PRECEDENCE}, thus ending up
 * at the end of a sorted collection in arbitrary order with respect to
 * other objects with the same order value.
 *
 * @author Juergen Hoeller
 * @author Sam Brannen
 * @since 07.04.2003
 * @see Ordered
 * @see PriorityOrdered
 * @see org.springframework.core.annotation.AnnotationAwareOrderComparator
 * @see java.util.List#sort(java.util.Comparator)
 * @see java.util.Arrays#sort(Object[], java.util.Comparator)
 */
public class OrderComparator implements Comparator<Object> {

	/**
	 * OrderComparator的共享默认实例
	 *
	 * Shared default instance of {@code OrderComparator}.
	 */
	public static final OrderComparator INSTANCE = new OrderComparator();


	/**
	 * 与给定的源提供商建立一个适合的Order比较器
	 *
	 * Build an adapted order comparator with the given source provider.
	 * @param sourceProvider the order source provider to use
	 * @return the adapted comparator
	 * @since 4.1
	 */
	public Comparator<Object> withSourceProvider(OrderSourceProvider sourceProvider) {
		return (o1, o2) -> doCompare(o1, o2, sourceProvider);
	}

	@Override
	public int compare(@Nullable Object o1, @Nullable Object o2) {
		return doCompare(o1, o2, null);
	}

	private int doCompare(@Nullable Object o1, @Nullable Object o2, @Nullable OrderSourceProvider sourceProvider) {
		// 判断o1是否实现了PriorityOrdered接口
		boolean p1 = (o1 instanceof PriorityOrdered);
		// 判断o2是否实现了PriorityOrdered接口
		boolean p2 = (o2 instanceof PriorityOrdered);
		// 如果o1实现了PriorityOrdered接口，o2没有，则o1排前面
		if (p1 && !p2) {
			return -1;
		}
		// 如果o2实现了PriorityOrdered接口，而o1没有，o2排前面
		else if (p2 && !p1) {
			return 1;
		}
		// 如果o1和o2都实现或者都没有实现PriorityOrdered接口
		// 拿到o1的order值，如果没有实现Ordered接口，值为Ordered.LOWEST_PRECEDENCE
		int i1 = getOrder(o1, sourceProvider);
		// 拿到o2的order值，如果没有实现Ordered接口，值为Ordered.LOWEST_PRECEDENCE
		int i2 = getOrder(o2, sourceProvider);
		// 通过order值排序(order值越小，优先级越高)
		return Integer.compare(i1, i2);
	}

	/**
	 * 获取从sourceProvider中获取obj的源对象的优先级值，如果获取不到源对象时，直接从o1,o2中获取优先级值
	 *
	 * Determine the order value for the given object.
	 * <p>The default implementation checks against the given {@link OrderSourceProvider}
	 * using {@link #findOrder} and falls back to a regular {@link #getOrder(Object)} call.
	 * @param obj the object to check
	 * @return the order value, or {@code Ordered.LOWEST_PRECEDENCE} as fallback
	 */
	private int getOrder(@Nullable Object obj, @Nullable OrderSourceProvider sourceProvider) {
		// 定义保存优先级值的变量
		Integer order = null;
		// 如果obj不为null且sourceProvider不为null
		if (obj != null && sourceProvider != null) {
			// 获取obj的Order来源
			Object orderSource = sourceProvider.getOrderSource(obj);
			// 如果order来源不为null
			if (orderSource != null) {
				// 如果orderSource是数组，会遍历找到第一个有order值的元素，而剩下的元素即使有Order值都会忽略
				// 如果orderSource是数组
				if (orderSource.getClass().isArray()) {
					// 将orderSource转换成数组对象
					Object[] sources = ObjectUtils.toObjectArray(orderSource);
					// 遍历源对象
					for (Object source : sources) {
						// 获取obj的order值
						order = findOrder(source);
						// 如果order不为null,跳出循环
						if (order != null) {
							break;
						}
					}
				}
				else {
					// 获取orderSource的order值
					order = findOrder(orderSource);
				}
			}
		}
		// 如果order有值，就返回order；否则再尝试obj的优先级值并将结果返回出去
		return (order != null ? order : getOrder(obj));
	}

	/**
	 * 获取obj的优先级值
	 *
	 * Determine the order value for the given object.
	 * <p>The default implementation checks against the {@link Ordered} interface
	 * through delegating to {@link #findOrder}. Can be overridden in subclasses.
	 * @param obj the object to check
	 * @return the order value, or {@code Ordered.LOWEST_PRECEDENCE} as fallback
	 */
	protected int getOrder(@Nullable Object obj) {
		// 如果obj不为null
		if (obj != null) {
			// 获取obj的优先级值
			Integer order = findOrder(obj);
			// order有值
			if (order != null) {
				// 返回order
				return order;
			}
		}
		// 在没有获取到指定优先级值时，返回最低优先级值
		return Ordered.LOWEST_PRECEDENCE;
	}

	/**
	 * 获取obj的优先级值，用于供Comparator比较
	 *
	 * Find an order value indicated by the given object.
	 * <p>The default implementation checks against the {@link Ordered} interface.
	 * Can be overridden in subclasses.
	 * @param obj the object to check
	 * @return the order value, or {@code null} if none found
	 */
	@Nullable
	protected Integer findOrder(Object obj) {
		// 如果obj是Ordered实例,获取obj的优先级值；否则返回null
		return (obj instanceof Ordered ? ((Ordered) obj).getOrder() : null);
	}

	/**
	 * 确定给定对象的优先级值(如果有)
	 *
	 * Determine a priority value for the given object, if any.
	 * <p>The default implementation always returns {@code null}.
	 * Subclasses may override this to give specific kinds of values a
	 * 'priority' characteristic, in addition to their 'order' semantics.
	 * A priority indicates that it may be used for selecting one object over
	 * another, in addition to serving for ordering purposes in a list/array.
	 * @param obj the object to check
	 * @return the priority value, or {@code null} if none
	 * @since 4.1
	 */
	@Nullable
	public Integer getPriority(Object obj) {
		return null;
	}


	/**
	 * 使用默认的OrderComparator对给定的列表进行排序
	 *
	 * Sort the given List with a default OrderComparator.
	 * <p>Optimized to skip sorting for lists with size 0 or 1,
	 * in order to avoid unnecessary array extraction.
	 * @param list the List to sort
	 * @see java.util.List#sort(java.util.Comparator)
	 */
	public static void sort(List<?> list) {
		// 如果list至少有一个元素
		if (list.size() > 1) {
			// 使用默认的OrderComparator进行排序
			list.sort(INSTANCE);
		}
	}

	/**
	 * 使用默认的OrderComparator对给定的数组进行排序
	 *
	 * Sort the given array with a default OrderComparator.
	 * <p>Optimized to skip sorting for lists with size 0 or 1,
	 * in order to avoid unnecessary array extraction.
	 * @param array the array to sort
	 * @see java.util.Arrays#sort(Object[], java.util.Comparator)
	 */
	public static void sort(Object[] array) {
		// 如果list至少有一个元素
		if (array.length > 1) {
			// 使用默认的OrderComparator进行排序
			Arrays.sort(array, INSTANCE);
		}
	}

	/**
	 * 如果有必要，使用默认的OrderCompatator对给定的数组或列表进行排序。给定其他任何值时，只需跳过排序
	 *
	 * Sort the given array or List with a default OrderComparator,
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


	/**
	 * 策略接口,用于为给定对象提供订单来源
	 *
	 * Strategy interface to provide an order source for a given object.
	 * @since 4.1
	 */
	@FunctionalInterface
	public interface OrderSourceProvider {

		/**
		 * 返回指定对象的Order来源，即应检查优先级值的对象,以替换给定对象
		 *
		 * Return an order source for the specified object, i.e. an object that
		 * should be checked for an order value as a replacement to the given object.
		 * <p>Can also be an array of order source objects.
		 * <p>If the returned object does not indicate any order, the comparator
		 * will fall back to checking the original object.
		 * @param obj the object to find an order source for
		 * @return the order source for that object, or {@code null} if none found
		 */
		@Nullable
		Object getOrderSource(Object obj);
	}

}
