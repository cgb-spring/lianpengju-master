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

package org.springframework.web.servlet.handler;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.springframework.beans.BeansException;
import org.springframework.util.CollectionUtils;

/**
 * SimpleUrlHandlerMapping在内部定义了一个map变量，有两个作用，一个是方便进行配置，第二个是在注册钱可以做预处理工作，确保所有的url都是以/开头
 * 然后将所有的url和handler的对应关系存储起来，最后注册到父类的map中
 *
 * 此对象在进行创建的时候会通过重写的父类的initApplicationContext方法调用registerHandler来完成handler的注册，
 *
 * Implementation of the {@link org.springframework.web.servlet.HandlerMapping}
 * interface that maps from URLs to request handler beans. Supports both mapping to bean
 * instances and mapping to bean names; the latter is required for non-singleton handlers.
 *
 * <p>The "urlMap" property is suitable for populating the handler map with
 * bean references, e.g. via the map element in XML bean definitions.
 *
 * <p>Mappings to bean names can be set via the "mappings" property, in a form
 * accepted by the {@code java.util.Properties} class, as follows:
 *
 * <pre class="code">
 * /welcome.html=ticketController
 * /show.html=ticketController</pre>
 *
 * <p>The syntax is {@code PATH=HANDLER_BEAN_NAME}. If the path doesn't begin
 * with a slash, one is prepended.
 *
 * <p>Supports direct matches (given "/test" -&gt; registered "/test") and "*"
 * pattern matches (given "/test" -&gt; registered "/t*"). Note that the default
 * is to map within the current servlet mapping if applicable; see the
 * {@link #setAlwaysUseFullPath "alwaysUseFullPath"} property. For details on the
 * pattern options, see the {@link org.springframework.util.AntPathMatcher} javadoc.

 * @author Rod Johnson
 * @author Juergen Hoeller
 * @author Sam Brannen
 * @see #setMappings
 * @see #setUrlMap
 * @see BeanNameUrlHandlerMapping
 */
public class SimpleUrlHandlerMapping extends AbstractUrlHandlerMapping {

	/**
	 * 配置的URL与处理器的映射
	 *
	 * 最终，会调用registerHandlers(Map)进行注册到AbstractUrlHandlerMapping#handlerMap中
	 */
	private final Map<String, Object> urlMap = new LinkedHashMap<>();


	/**
	 * Create a {@code SimpleUrlHandlerMapping} with default settings.
	 */
	public SimpleUrlHandlerMapping() {
	}

	/**
	 * Create a {@code SimpleUrlHandlerMapping} using the supplied URL map.
	 * @param urlMap map with URL paths as keys and handler beans (or handler
	 * bean names) as values
	 * @since 5.2
	 * @see #setUrlMap(Map)
	 */
	public SimpleUrlHandlerMapping(Map<String, ?> urlMap) {
		setUrlMap(urlMap);
	}

	/**
	 * Create a {@code SimpleUrlHandlerMapping} using the supplied URL map and order.
	 * @param urlMap map with URL paths as keys and handler beans (or handler
	 * bean names) as values
	 * @param order the order value for this {@code SimpleUrlHandlerMapping}
	 * @since 5.2
	 * @see #setUrlMap(Map)
	 * @see #setOrder(int)
	 */
	public SimpleUrlHandlerMapping(Map<String, ?> urlMap, int order) {
		setUrlMap(urlMap);
		setOrder(order);
	}


	/**
	 * 通过属性配置URL到Bean名的映射
	 *
	 * Map URL paths to handler bean names.
	 * This is the typical way of configuring this HandlerMapping.
	 * <p>Supports direct URL matches and Ant-style pattern matches. For syntax
	 * details, see the {@link org.springframework.util.AntPathMatcher} javadoc.
	 * @param mappings properties with URLs as keys and bean names as values
	 * @see #setUrlMap
	 */
	public void setMappings(Properties mappings) {
		CollectionUtils.mergePropertiesIntoMap(mappings, this.urlMap);
	}

	/**
	 * 配置URL到Bean的映射
	 *
	 * Set a Map with URL paths as keys and handler beans (or handler bean names)
	 * as values. Convenient for population with bean references.
	 * <p>Supports direct URL matches and Ant-style pattern matches. For syntax
	 * details, see the {@link org.springframework.util.AntPathMatcher} javadoc.
	 * @param urlMap map with URLs as keys and beans as values
	 * @see #setMappings
	 */
	public void setUrlMap(Map<String, ?> urlMap) {
		this.urlMap.putAll(urlMap);
	}

	/**
	 * Allow Map access to the URL path mappings, with the option to add or
	 * override specific entries.
	 * <p>Useful for specifying entries directly, for example via "urlMap[myKey]".
	 * This is particularly useful for adding or overriding entries in child
	 * bean definitions.
	 */
	public Map<String, ?> getUrlMap() {
		return this.urlMap;
	}


	/**
	 * Calls the {@link #registerHandlers} method in addition to the
	 * superclass's initialization.
	 */
	@Override
	public void initApplicationContext() throws BeansException {
		super.initApplicationContext();
		// 初始化的时候注册处理器
		registerHandlers(this.urlMap);
	}

	/**
	 * Register all handlers specified in the URL map for the corresponding paths.
	 * @param urlMap a Map with URL paths as keys and handler beans or bean names as values
	 * @throws BeansException if a handler couldn't be registered
	 * @throws IllegalStateException if there is a conflicting handler registered
	 */
	protected void registerHandlers(Map<String, Object> urlMap) throws BeansException {
		// 如果配置的处理器映射为空，则警告
		if (urlMap.isEmpty()) {
			logger.trace("No patterns in " + formatMappingName());
		}
		else {
			// 对于每一个配置的URL到处理器的映射，如果URL不是以斜线/开头，则追加斜线开头，则注册处理器
			urlMap.forEach((url, handler) -> {
				// Prepend with slash if not already present.
				if (!url.startsWith("/")) {
					url = "/" + url;
				}
				// Remove whitespace from handler bean name.
				if (handler instanceof String) {
					handler = ((String) handler).trim();
				}
				// 【核心代码】注册处理器
				registerHandler(url, handler);
			});
			if (logger.isDebugEnabled()) {
				List<String> patterns = new ArrayList<>();
				if (getRootHandler() != null) {
					patterns.add("/");
				}
				if (getDefaultHandler() != null) {
					patterns.add("/**");
				}
				patterns.addAll(getHandlerMap().keySet());
				logger.debug("Patterns " + patterns + " in " + formatMappingName());
			}
		}
	}

}
