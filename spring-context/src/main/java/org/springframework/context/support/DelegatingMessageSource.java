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

package org.springframework.context.support;

import java.util.Locale;

import org.springframework.context.HierarchicalMessageSource;
import org.springframework.context.MessageSource;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.context.NoSuchMessageException;
import org.springframework.lang.Nullable;

/**
 * 消息源解析委派类，当用户未指定时，springContext默认使用当前类
 *
 * Empty {@link MessageSource} that delegates all calls to the parent MessageSource.
 * If no parent is available, it simply won't resolve any message.
 *
 * <p>Used as placeholder by AbstractApplicationContext, if the context doesn't
 * define its own MessageSource. Not intended for direct use in applications.
 *
 * @author Juergen Hoeller
 * @since 1.1.5
 * @see AbstractApplicationContext
 */
public class DelegatingMessageSource extends MessageSourceSupport implements HierarchicalMessageSource {

	// 父消息解析源
	@Nullable
	private MessageSource parentMessageSource;


	@Override
	public void setParentMessageSource(@Nullable MessageSource parent) {
		this.parentMessageSource = parent;
	}

	@Override
	@Nullable
	public MessageSource getParentMessageSource() {
		return this.parentMessageSource;
	}


	/**
	 * 解析消息，父消息解析源不为null时，则采用父消息源解析消息，否则有自身解析
	 *
	 * @param code the message code to look up, e.g. 'calculator.noRateSet'.
	 * MessageSource users are encouraged to base message names on qualified class
	 * or package names, avoiding potential conflicts and ensuring maximum clarity.
	 * @param args an array of arguments that will be filled in for params within
	 * the message (params look like "{0}", "{1,date}", "{2,time}" within a message),
	 * or {@code null} if none
	 * @param defaultMessage a default message to return if the lookup fails
	 * @param locale the locale in which to do the lookup
	 * @return
	 */
	@Override
	@Nullable
	public String getMessage(String code, @Nullable Object[] args, @Nullable String defaultMessage, Locale locale) {
		if (this.parentMessageSource != null) {
			return this.parentMessageSource.getMessage(code, args, defaultMessage, locale);
		}
		else if (defaultMessage != null) {
			return renderDefaultMessage(defaultMessage, args, locale);
		}
		else {
			return null;
		}
	}

	/**
	 * 解析消息，父消息解析源不为空时，则采用父消息源解析消息，否则抛出异常
	 *
	 * @param code the message code to look up, e.g. 'calculator.noRateSet'.
	 * MessageSource users are encouraged to base message names on qualified class
	 * or package names, avoiding potential conflicts and ensuring maximum clarity.
	 * @param args an array of arguments that will be filled in for params within
	 * the message (params look like "{0}", "{1,date}", "{2,time}" within a message),
	 * or {@code null} if none
	 * @param locale the locale in which to do the lookup
	 * @return
	 * @throws NoSuchMessageException
	 */
	@Override
	public String getMessage(String code, @Nullable Object[] args, Locale locale) throws NoSuchMessageException {
		if (this.parentMessageSource != null) {
			return this.parentMessageSource.getMessage(code, args, locale);
		}
		else {
			throw new NoSuchMessageException(code, locale);
		}
	}

	/**
	 * 解析消息，父消息解析源不为空时，则采用父消息源解析消息，否则有自身解析
	 *
	 * @param resolvable the value object storing attributes required to resolve a message
	 * (may include a default message)
	 * @param locale the locale in which to do the lookup
	 * @return
	 * @throws NoSuchMessageException
	 */
	@Override
	public String getMessage(MessageSourceResolvable resolvable, Locale locale) throws NoSuchMessageException {
		if (this.parentMessageSource != null) {
			return this.parentMessageSource.getMessage(resolvable, locale);
		}
		else {
			if (resolvable.getDefaultMessage() != null) {
				return renderDefaultMessage(resolvable.getDefaultMessage(), resolvable.getArguments(), locale);
			}
			String[] codes = resolvable.getCodes();
			String code = (codes != null && codes.length > 0 ? codes[0] : "");
			throw new NoSuchMessageException(code, locale);
		}
	}


	@Override
	public String toString() {
		return this.parentMessageSource != null ? this.parentMessageSource.toString() : "Empty MessageSource";
	}

}
