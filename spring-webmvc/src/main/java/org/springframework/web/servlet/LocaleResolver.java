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

import java.util.Locale;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.lang.Nullable;

/**
 * 从request中解析出Locale，LocaleResolver有两个用处：
 * 1、在ViewResolver解析视图的时候，
 * 2，使用到国际化资源或者主题的时候，主要使用RequestContext的getMessage和getThemeMessage方法，在<spring:message>标签中就是使用RequestContext，
 * 		先调用getMessageSource然后内部调用getMessage方法，可以看MessageTag
 *
 * 在很多应用场景里有很多人为设置语言的操作，此步骤在进行实现的时候，不需要在controller中修改Locale,而是通过Interceptor来进行实现
 * 例如:LocaleChangeInterceptor,可以通过http://localhost:8080?locale=zh_CN来进行操作
 * <mvc:interceptors>
 *     <mvc:interceptor>
 *         <mvc:mapping path="/*"/>
 *         <bean class="org.springframe.wen.servlet.i18n.LocaleChangeInterceptor></bean>
 *     </mvc:interceptor>
 * </mvc:interceptors>
 *
 * Interface for web-based locale resolution strategies that allows for
 * both locale resolution via the request and locale modification via
 * request and response.
 *
 * <p>This interface allows for implementations based on request, session,
 * cookies, etc. The default implementation is
 * {@link org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver},
 * simply using the request's locale provided by the respective HTTP header.
 *
 * <p>Use {@link org.springframework.web.servlet.support.RequestContext#getLocale()}
 * to retrieve the current locale in controllers or views, independent
 * of the actual resolution strategy.
 *
 * <p>Note: As of Spring 4.0, there is an extended strategy interface
 * called {@link LocaleContextResolver}, allowing for resolution of
 * a {@link org.springframework.context.i18n.LocaleContext} object,
 * potentially including associated time zone information. Spring's
 * provided resolver implementations implement the extended
 * {@link LocaleContextResolver} interface wherever appropriate.
 *
 * @author Juergen Hoeller
 * @since 27.02.2003
 * @see LocaleContextResolver
 * @see org.springframework.context.i18n.LocaleContextHolder
 * @see org.springframework.web.servlet.support.RequestContext#getLocale
 * @see org.springframework.web.servlet.support.RequestContextUtils#getLocale
 */
public interface LocaleResolver {

	/**
	 * 从请求中，解析出要使用的语言，例如，请求投中的Accept-Language
	 *
	 * Resolve the current locale via the given request.
	 * Can return a default locale as fallback in any case.
	 * @param request the request to resolve the locale for
	 * @return the current locale (never {@code null})
	 */
	Locale resolveLocale(HttpServletRequest request);

	/**
	 * 设置请求所使用的语言
	 *
	 * Set the current locale to the given one.
	 * @param request the request to be used for locale modification
	 * @param response the response to be used for locale modification
	 * @param locale the new locale, or {@code null} to clear the locale
	 * @throws UnsupportedOperationException if the LocaleResolver
	 * implementation does not support dynamic changing of the locale
	 */
	void setLocale(HttpServletRequest request, @Nullable HttpServletResponse response, @Nullable Locale locale);

}
