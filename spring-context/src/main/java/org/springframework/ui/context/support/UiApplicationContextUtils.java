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

package org.springframework.ui.context.support;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.context.ApplicationContext;
import org.springframework.ui.context.HierarchicalThemeSource;
import org.springframework.ui.context.ThemeSource;

/**
 * 用于UI应用程序上下文实现的实用程序类。提供对名为'themeSource'
 *  * 的特殊Bean的支持，该Bean类型为 themeSource
 *
 * Utility class for UI application context implementations.
 * Provides support for a special bean named "themeSource",
 * of type {@link org.springframework.ui.context.ThemeSource}.
 *
 * @author Jean-Pierre Pawlak
 * @author Juergen Hoeller
 * @since 17.06.2003
 */
public abstract class UiApplicationContextUtils {

	/**
	 * Name of the ThemeSource bean in the factory.
	 * If none is supplied, theme resolution is delegated to the parent.
	 * @see org.springframework.ui.context.ThemeSource
	 */
	public static final String THEME_SOURCE_BEAN_NAME = "themeSource";


	private static final Log logger = LogFactory.getLog(UiApplicationContextUtils.class);


	/**
	 * 初始化给定应用程序上下文的 ThemeSource,自动检查名为 'ThemeSource'的bean。如果没有找到这样的Bean，将使用默认的'空'主题源
	 *
	 * Initialize the ThemeSource for the given application context,
	 * autodetecting a bean with the name "themeSource". If no such
	 * bean is found, a default (empty) ThemeSource will be used.
	 * @param context current application context
	 * @return the initialized theme source (will never be {@code null})
	 * @see #THEME_SOURCE_BEAN_NAME
	 */
	public static ThemeSource initThemeSource(ApplicationContext context) {
		//ThemeSource接口，由能够解决主题的对象实现。这使得给定'Theme'的消息能够参数化和国际化
		//如果context的BeanFactory包含'themeSource'的bean，忽略父工厂
		if (context.containsLocalBean(THEME_SOURCE_BEAN_NAME)) {
			//从beanFactory中获取名为 themeSource 的 ThemeSource 类型的Bean对象
			ThemeSource themeSource = context.getBean(THEME_SOURCE_BEAN_NAME, ThemeSource.class);
			// Make ThemeSource aware of parent ThemeSource.
			// 使ThemeSource知道父ThemeSource
			// HierarchicalThemeSource:主题源的子接口，由能够分层解决主题消息的对象实现
			// 如果context的父级上下文是ThemeSource对象 && themeSource是HierarchicalThemeSource对象
			if (context.getParent() instanceof ThemeSource && themeSource instanceof HierarchicalThemeSource) {
				//将themeSource强转为HierarchicalThemeSource对象
				HierarchicalThemeSource hts = (HierarchicalThemeSource) themeSource;
				//如果hts的父级ThemeSource为null
				if (hts.getParentThemeSource() == null) {
					// Only set parent context as parent ThemeSource if no parent ThemeSource
					// registered already.
					// 如果没有父ThemeSource已经注册，只能将父上下文设置为父主题资源
					// 将父级上下文作为hts的父级ThemeSource
					hts.setParentThemeSource((ThemeSource) context.getParent());
				}
			}
			//如果当前日志级别是调试
			if (logger.isDebugEnabled()) {
				logger.debug("Using ThemeSource [" + themeSource + "]");
			}
			//将themeSource返回出去
			return themeSource;
		}
		else {
			// Use default ThemeSource to be able to accept getTheme calls, either
			// delegating to parent context's default or to local ResourceBundleThemeSource.
			// 使用默认的ThemeSource能够接受getTheme调用，或者委托给父上下文的默认值，或者委托给本地的ResourceBundleThemeSource
			HierarchicalThemeSource themeSource = null;
			//如果context的父级上下文是ThemeSource对象
			if (context.getParent() instanceof ThemeSource) {
				// DeletgatingThemeSource : 清空委托所有调用父ThemeSource的ThemeSource。如果没有可用的父元素，他就不能解决任何主题
				// 新建一个DelegatingThemeSource对象
				themeSource = new DelegatingThemeSource();
				//将父级上线问作为themeSource的父级ThemeSource
				themeSource.setParentThemeSource((ThemeSource) context.getParent());
			}
			else {
				// ResourceBundleThemeSource：查找个人信息的资源实现java.util.ResourceBundle每个主题。主题名称被解释为ResourceBundle basename ,支持所有主题的公共 basename 前缀
				// 新建一个ResourceBundleThemeSource对象
				themeSource = new ResourceBundleThemeSource();
			}
			//如果当前日志级别是调试
			if (logger.isDebugEnabled()) {
				logger.debug("Unable to locate ThemeSource with name '" + THEME_SOURCE_BEAN_NAME +
						"': using default [" + themeSource + "]");
			}
			//返回themeSource
			return themeSource;
		}
	}

}
