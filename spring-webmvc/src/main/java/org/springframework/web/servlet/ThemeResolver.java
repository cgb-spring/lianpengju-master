/*
 * Copyright 2002-2017 the original author or authors.
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

package org.springframework.web.servlet;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.lang.Nullable;

/**
 * themeResolver是用来做主题解析使用的，不同的主题其实就是换一套图片、显示效果以及样式等，在springmvc中一套主题对应着一个properties文件，
 * 存储着跟主题相关的所有资源，例如：theme.properties
 * 	companyName=mashibing
 * 	在使用的时候可以用<spring:theme code="companyName"></>那么在显示的时候就会出现mashibing的字符串了
 * 	默认实现的子类是FixedThemeResolver,获取资源的名称是ThemeResolver的工作，而根据资源名称找到主题就是ThemeSource的作用了
 *
 * 	我们可以自己配置ThemeResolver和ThemeSource对象
 * 	<bean id="themeSource" class="org.springframework.ui.context.support.ResourceBundleThemeSource" p:basenamePrefix="com.mashibing.theme."></bean>
 *	<bean id="themeResolver" class="org.springframework.web.servlet.theme.CookieThemeResolver" p:defaultThemeName="default"></bean>
 *
 *	在进行主题切换的时候，同样也是通过拦截器来实现的
 *	<mvc:interceptors>
 *	   <mvc:interceptor>
 *	       <mvc:mapping path="/*“></mvc:mapping>
 *	       <bean class="org.springframework.web.servlet.theme.ThemeChangeInterceptor" p:paramName="theme"></bean>
 *	   </mvc:interceptor>
 *	</mvc:interceptors>
 *	http://localhost:8080?theme=default
 *
 * Interface for web-based theme resolution strategies that allows for
 * both theme resolution via the request and theme modification via
 * request and response.
 *
 * <p>This interface allows for implementations based on session,
 * cookies, etc. The default implementation is
 * {@link org.springframework.web.servlet.theme.FixedThemeResolver},
 * simply using a configured default theme.
 *
 * <p>Note that this resolver is only responsible for determining the
 * current theme name. The Theme instance for the resolved theme name
 * gets looked up by DispatcherServlet via the respective ThemeSource,
 * i.e. the current WebApplicationContext.
 *
 * <p>Use {@link org.springframework.web.servlet.support.RequestContext#getTheme()}
 * to retrieve the current theme in controllers or views, independent
 * of the actual resolution strategy.
 *
 * @author Jean-Pierre Pawlak
 * @author Juergen Hoeller
 * @since 17.06.2003
 * @see org.springframework.ui.context.Theme
 * @see org.springframework.ui.context.ThemeSource
 */
public interface ThemeResolver {

	/**
	 * 从请求中，解析出使用的主题
	 *
	 * Resolve the current theme name via the given request.
	 * Should return a default theme as fallback in any case.
	 * @param request the request to be used for resolution
	 * @return the current theme name
	 */
	String resolveThemeName(HttpServletRequest request);

	/**
	 * 设置请求，所使用的主题
	 *
	 * Set the current theme name to the given one.
	 * @param request the request to be used for theme name modification
	 * @param response the response to be used for theme name modification
	 * @param themeName the new theme name ({@code null} or empty to reset it)
	 * @throws UnsupportedOperationException if the ThemeResolver implementation
	 * does not support dynamic changing of the theme
	 */
	void setThemeName(HttpServletRequest request, @Nullable HttpServletResponse response, @Nullable String themeName);

}
