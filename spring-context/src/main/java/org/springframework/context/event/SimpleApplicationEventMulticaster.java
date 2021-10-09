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

import java.util.concurrent.Executor;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.ResolvableType;
import org.springframework.lang.Nullable;
import org.springframework.util.ErrorHandler;

/**
 * Simple implementation of the {@link ApplicationEventMulticaster} interface.
 * 简单实现的ApplicationEventMulticaster接口
 *
 * <p>Multicasts all events to all registered listeners, leaving it up to
 * the listeners to ignore events that they are not interested in.
 * Listeners will usually perform corresponding {@code instanceof}
 * checks on the passed-in event object.
 * 将所有事件多播给所有注册的监听器，让监听器忽略它们不感兴趣的事件。监听器通常会对传入的事件对象执行相应的 instanceof 检查
 *
 * <p>By default, all listeners are invoked in the calling thread.
 * This allows the danger of a rogue listener blocking the entire application,
 * but adds minimal overhead. Specify an alternative task executor to have
 * listeners executed in different threads, for example from a thread pool.
 * 默认情况下，所有监听器都在调用线程中调用。这允许恶意监听器阻塞整个应用程序的危险，但只增加最小的开销。指定另一个任务执行器，让监听器在不同的线程中执行
 * 例如 从线程池中执行
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @author Stephane Nicoll
 * @see #setTaskExecutor
 */
public class SimpleApplicationEventMulticaster extends AbstractApplicationEventMulticaster {

	//当前任务线程池
	@Nullable
	private Executor taskExecutor;

	@Nullable
	private ErrorHandler errorHandler;


	/**
	 * 创建一个新的SimpleApplicationEventMulticaster
	 *
	 * Create a new SimpleApplicationEventMulticaster.
	 */
	public SimpleApplicationEventMulticaster() {
	}

	/**
	 * 为给定的BeanFactory创建一个新的SimpleApplicationEventMulticaster
	 *
	 * Create a new SimpleApplicationEventMulticaster for the given BeanFactory.
	 */
	public SimpleApplicationEventMulticaster(BeanFactory beanFactory) {
		setBeanFactory(beanFactory);
	}


	/**
	 * Set a custom executor (typically a {@link org.springframework.core.task.TaskExecutor})
	 * to invoke each listener with.
	 * <p>Default is equivalent to {@link org.springframework.core.task.SyncTaskExecutor},
	 * executing all listeners synchronously in the calling thread.
	 * <p>Consider specifying an asynchronous task executor here to not block the
	 * caller until all listeners have been executed. However, note that asynchronous
	 * execution will not participate in the caller's thread context (class loader,
	 * transaction association) unless the TaskExecutor explicitly supports this.
	 * @see org.springframework.core.task.SyncTaskExecutor
	 * @see org.springframework.core.task.SimpleAsyncTaskExecutor
	 */
	public void setTaskExecutor(@Nullable Executor taskExecutor) {
		this.taskExecutor = taskExecutor;
	}

	/**
	 * 返回此多播器的当前任务线程池
	 *
	 * Return the current task executor for this multicaster.
	 */
	@Nullable
	protected Executor getTaskExecutor() {
		return this.taskExecutor;
	}

	/**
	 * Set the {@link ErrorHandler} to invoke in case an exception is thrown
	 * from a listener.
	 * <p>Default is none, with a listener exception stopping the current
	 * multicast and getting propagated to the publisher of the current event.
	 * If a {@linkplain #setTaskExecutor task executor} is specified, each
	 * individual listener exception will get propagated to the executor but
	 * won't necessarily stop execution of other listeners.
	 * <p>Consider setting an {@link ErrorHandler} implementation that catches
	 * and logs exceptions (a la
	 * {@link org.springframework.scheduling.support.TaskUtils#LOG_AND_SUPPRESS_ERROR_HANDLER})
	 * or an implementation that logs exceptions while nevertheless propagating them
	 * (e.g. {@link org.springframework.scheduling.support.TaskUtils#LOG_AND_PROPAGATE_ERROR_HANDLER}).
	 * @since 4.1
	 */
	public void setErrorHandler(@Nullable ErrorHandler errorHandler) {
		this.errorHandler = errorHandler;
	}

	/**
	 * 返回此多播器的当前错误处理程序
	 *
	 * Return the current error handler for this multicaster.
	 * @since 4.1
	 */
	@Nullable
	protected ErrorHandler getErrorHandler() {
		return this.errorHandler;
	}


	@Override
	public void multicastEvent(ApplicationEvent event) {
		multicastEvent(event, resolveDefaultEventType(event));
	}

	/**
	 * 广播事件
	 * @param event the event to multicast
	 * @param eventType the type of event (can be {@code null})
	 */
	@Override
	public void multicastEvent(final ApplicationEvent event, @Nullable ResolvableType eventType) {
		// 如果eventType不为null就引用eventType;否则将event转换为ResolvableType对象再引用
		ResolvableType type = (eventType != null ? eventType : resolveDefaultEventType(event));
		// 获取此多播器的当前任务线程池
		Executor executor = getTaskExecutor();
		// getApplicationListeners方法是返回与给定事件类型匹配的应用监听器集合
		// 遍历获取所有支持event的监听器
		for (ApplicationListener<?> listener : getApplicationListeners(event, type)) {
			//如果executor不为null
			if (executor != null) {
				//使用executor回调listener的onApplicationEvent方法，传入event
				executor.execute(() -> invokeListener(listener, event));
			}
			else {
				//回调listener的onApplicationEvent方法，传入event
				invokeListener(listener, event);
			}
		}
	}

	/**
	 * 将event转换为ResolvableType对象
	 * @param event
	 * @return
	 */
	private ResolvableType resolveDefaultEventType(ApplicationEvent event) {
		return ResolvableType.forInstance(event);
	}

	/**
	 * 使用给定的事件调用给定的监听器
	 *
	 * Invoke the given listener with the given event.
	 * @param listener the ApplicationListener to invoke
	 * @param event the current event to propagate
	 * @since 4.1
	 */
	protected void invokeListener(ApplicationListener<?> listener, ApplicationEvent event) {
		// 获取此多播器的当前错误处理程序
		ErrorHandler errorHandler = getErrorHandler();
		// 如果errorHandler不为null
		if (errorHandler != null) {
			try {
				// 回调listener的onApplicationEvent方法，传入event
				doInvokeListener(listener, event);
			}
			catch (Throwable err) {
				// 交给errorHandler接收处理err
				errorHandler.handleError(err);
			}
		}
		else {
			// 回调listener的onApplicationEvent方法，传入event
			doInvokeListener(listener, event);
		}
	}

	/**
	 * 回调listener的onApplicationEvent方法，传入 event
	 * @param listener
	 * @param event
	 */
	@SuppressWarnings({"rawtypes", "unchecked"})
	private void doInvokeListener(ApplicationListener listener, ApplicationEvent event) {
		try {
			//回调listener的onApplicationEvent方法，传入event:contextrefreshListener:onapplicaitonEvent:FrameworkServlet.this.onApplicationEvent()
			listener.onApplicationEvent(event);
		}
		catch (ClassCastException ex) {
			//获取异常信息
			String msg = ex.getMessage();
			if (msg == null || matchesClassCastMessage(msg, event.getClass())) {
				// Possibly a lambda-defined listener which we could not resolve the generic event type for
				// -> let's suppress the exception and just log a debug message.
				Log logger = LogFactory.getLog(getClass());
				if (logger.isTraceEnabled()) {
					logger.trace("Non-matching event type for listener: " + listener, ex);
				}
			}
			else {
				//抛出异常
				throw ex;
			}
		}
	}

	/**
	 * 匹配类转换消息，以保证抛出类转换异常是因eventClass引起的
	 *
	 * @param classCastMessage
	 * @param eventClass
	 * @return
	 */
	private boolean matchesClassCastMessage(String classCastMessage, Class<?> eventClass) {
		// On Java 8, the message starts with the class name: "java.lang.String cannot be cast..."
		// 在JAVA8中，消息以类名开始：'java.lang.String不能被转换..'
		// 如果classCastMessage是以eventClass类名开头，返回true
		if (classCastMessage.startsWith(eventClass.getName())) {
			return true;
		}
		// On Java 11, the message starts with "class ..." a.k.a. Class.toString()
		// 在JAVA11中，消息是以'class ...' 开始，选择Class.toString
		// 如果classCastMessage是以eventClass.toString()开头，返回true
		if (classCastMessage.startsWith(eventClass.toString())) {
			return true;
		}
		// On Java 9, the message used to contain the module name: "java.base/java.lang.String cannot be cast..."
		// 在Java 9, 用于包含模块名的消息：'java.base/java.lang.String 不能被转换'
		// 找出classCastMessage的'/'第一个索引位置
		int moduleSeparatorIndex = classCastMessage.indexOf('/');
		// 如果找到了'/'位置 && '/'后面的字符串是以eventClass类名开头
		if (moduleSeparatorIndex != -1 && classCastMessage.startsWith(eventClass.getName(), moduleSeparatorIndex + 1)) {
			return true;
		}
		// Assuming an unrelated class cast failure...
		// 假设一个不相关的类转换失败
		// 返回false
		return false;
	}

}
