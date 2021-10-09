/*
 * Copyright 2002-2012 the original author or authors.
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

package org.springframework.web.servlet.handler;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextException;
import org.springframework.util.ObjectUtils;

/**
 * 将容器所有的bean都拿出来，按一定规则注册到父类的map中
 * 此实现类也是通过重写initApplicationContext方法来注册handler，内部调用了detectHandlers方法
 * Abstract implementation of the {@link org.springframework.web.servlet.HandlerMapping}
 * interface, detecting URL mappings for handler beans through introspection of all
 * defined beans in the application context.
 *
 * @author Juergen Hoeller
 * @since 2.5
 * @see #determineUrlsForHandler
 */
public abstract class AbstractDetectingUrlHandlerMapping extends AbstractUrlHandlerMapping {

	private boolean detectHandlersInAncestorContexts = false;


	/**
	 * 是否只扫描可访问的handler
	 *
	 * Set whether to detect handler beans in ancestor ApplicationContexts.
	 * <p>Default is "false": Only handler beans in the current ApplicationContext
	 * will be detected, i.e. only in the context that this HandlerMapping itself
	 * is defined in (typically the current DispatcherServlet's context).
	 * <p>Switch this flag on to detect handler beans in ancestor contexts
	 * (typically the Spring root WebApplicationContext) as well.
	 */
	public void setDetectHandlersInAncestorContexts(boolean detectHandlersInAncestorContexts) {
		this.detectHandlersInAncestorContexts = detectHandlersInAncestorContexts;
	}


	/**
	 * Calls the {@link #detectHandlers()} method in addition to the
	 * superclass's initialization.
	 */
	@Override
	public void initApplicationContext() throws ApplicationContextException {
		super.initApplicationContext();
		// 自动探测处理器
		detectHandlers();
	}

	/**
	 * 根据配置的detectHandlersInAncestorContexts参数从springmvc容器或者父容器中找到所有bean的beanName，然后使用determineUrlsForHandler方法
	 * 对每个beanName解析出对应的urls，如果解析结果不为空，则解析出urls和beanName注册到父类的map中，
	 *
	 * Register all handlers found in the current ApplicationContext.
	 * <p>The actual URL determination for a handler is up to the concrete
	 * {@link #determineUrlsForHandler(String)} implementation. A bean for
	 * which no such URLs could be determined is simply not considered a handler.
	 * @throws org.springframework.beans.BeansException if the handler couldn't be registered
	 * @see #determineUrlsForHandler(String)
	 */
	protected void detectHandlers() throws BeansException {
		// 从spring上下文获取所有Object类型的bean名称
		ApplicationContext applicationContext = obtainApplicationContext();
		// 获取容器中所有bean的名字
		String[] beanNames = (this.detectHandlersInAncestorContexts ?
				BeanFactoryUtils.beanNamesForTypeIncludingAncestors(applicationContext, Object.class) :
				applicationContext.getBeanNamesForType(Object.class));

		// Take any bean name that we can determine URLs for.
		// 对每一个beanName解析url，如果能解析到就注册到父类的map中
		for (String beanName : beanNames) {
			// 使用beanName解析url，模板方法，有子类实现
			String[] urls = determineUrlsForHandler(beanName);
			// 如果该bean存在对应的url，则添加该处理器
			if (!ObjectUtils.isEmpty(urls)) {
				// URL paths found: Let's consider it a handler.
				// 调用父类的方法，往handlerMap中添加注册器
				registerHandler(urls, beanName);
			}
		}

		if ((logger.isDebugEnabled() && !getHandlerMap().isEmpty()) || logger.isTraceEnabled()) {
			logger.debug("Detected " + getHandlerMap().size() + " mappings in " + formatMappingName());
		}
	}


	/**
	 * Determine the URLs for the given handler bean.
	 * @param beanName the name of the candidate bean
	 * @return the URLs determined for the bean, or an empty array if none
	 */
	protected abstract String[] determineUrlsForHandler(String beanName);

}
