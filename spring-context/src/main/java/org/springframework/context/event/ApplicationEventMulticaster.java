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

package org.springframework.context.event;

import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.ResolvableType;
import org.springframework.lang.Nullable;

/**
 * ApplicationEventMulticaster接口的实现类可以管理多个ApplicationListener监听器对象，并且发布事件到监听器
 *
 * Interface to be implemented by objects that can manage a number of
 * {@link ApplicationListener} objects and publish events to them.
 *
 * <p>An {@link org.springframework.context.ApplicationEventPublisher}, typically
 * a Spring {@link org.springframework.context.ApplicationContext}, can use an
 * {@code ApplicationEventMulticaster} as a delegate for actually publishing events.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @author Stephane Nicoll
 * @see ApplicationListener
 */
public interface ApplicationEventMulticaster {

	/**
	 * 添加监听器以接受所有的事件通知
	 *
	 * Add a listener to be notified of all events.
	 * @param listener the listener to add
	 */
	void addApplicationListener(ApplicationListener<?> listener);

	/**
	 * 添加监听器以接收所有的事件通知，参数是监听器bean名称
	 *
	 * Add a listener bean to be notified of all events.
	 * @param listenerBeanName the name of the listener bean to add
	 */
	void addApplicationListenerBean(String listenerBeanName);

	/**
	 * 从通知列表中删除监听器
	 *
	 * Remove a listener from the notification list.
	 * @param listener the listener to remove
	 */
	void removeApplicationListener(ApplicationListener<?> listener);

	/**
	 * 删除注册到代理上的所有监听器，在remove调用之后，代理不会对事件通知执行任何操作直到有新的监听器注册
	 *
	 * Remove a listener bean from the notification list.
	 * @param listenerBeanName the name of the listener bean to remove
	 */
	void removeApplicationListenerBean(String listenerBeanName);

	/**
	 * 删除注册到代理上的所有监听器，在remove调用之后，代理不会对事件通知执行任何操作直到有新的监听器注册
	 *
	 * Remove all listeners registered with this multicaster.
	 * <p>After a remove call, the multicaster will perform no action
	 * on event notification until new listeners are registered.
	 */
	void removeAllListeners();

	/**
	 * 将给定的事件广播到对应的监听器上
	 *
	 * Multicast the given application event to appropriate listeners.
	 * <p>Consider using {@link #multicastEvent(ApplicationEvent, ResolvableType)}
	 * if possible as it provides better support for generics-based events.
	 * @param event the event to multicast
	 */
	void multicastEvent(ApplicationEvent event);

	/**
	 * 将应用程序事件广播到对应的监听器上
	 *
	 * Multicast the given application event to appropriate listeners.
	 * <p>If the {@code eventType} is {@code null}, a default type is built
	 * based on the {@code event} instance.
	 * @param event the event to multicast
	 * @param eventType the type of event (can be {@code null})
	 * @since 4.2
	 */
	void multicastEvent(ApplicationEvent event, @Nullable ResolvableType eventType);

}
