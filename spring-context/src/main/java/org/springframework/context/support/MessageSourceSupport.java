/*
 * Copyright 2002-2018 the original author or authors.
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

import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.lang.Nullable;
import org.springframework.util.ObjectUtils;

/**
 * 用于支持消息源解析的抽象类
 *
 * Base class for message source implementations, providing support infrastructure
 * such as {@link java.text.MessageFormat} handling but not implementing concrete
 * methods defined in the {@link org.springframework.context.MessageSource}.
 *
 * <p>{@link AbstractMessageSource} derives from this class, providing concrete
 * {@code getMessage} implementations that delegate to a central template
 * method for message code resolution.
 *
 * @author Juergen Hoeller
 * @since 2.5.5
 */
public abstract class MessageSourceSupport {

	// 默认的消息格式组件
	private static final MessageFormat INVALID_MESSAGE_FORMAT = new MessageFormat("");

	/** Logger available to subclasses. */
	protected final Log logger = LogFactory.getLog(getClass());

	// 是否始终应用“消息格式组件”，解析没有参数的消息（如：AbstractMessageSource中的commonMessages中的消息）
	private boolean alwaysUseMessageFormat = false;

	/**
	 * 缓存来保存已解决消息的消息格式组件，用于传入的默认消息
	 *
	 * Cache to hold already generated MessageFormats per message.
	 * Used for passed-in default messages. MessageFormats for resolved
	 * codes are cached on a specific basis in subclasses.
	 */
	private final Map<String, Map<Locale, MessageFormat>> messageFormatsPerMessage = new HashMap<>();


	/**
	 * 设置是否始终应用消息格式组件，解析没有参数的消息
	 *
	 * Set whether to always apply the {@code MessageFormat} rules,
	 * parsing even messages without arguments.
	 * <p>Default is "false": Messages without arguments are by default
	 * returned as-is, without parsing them through MessageFormat.
	 * Set this to "true" to enforce MessageFormat for all messages,
	 * expecting all message texts to be written with MessageFormat escaping.
	 * <p>For example, MessageFormat expects a single quote to be escaped
	 * as "''". If your message texts are all written with such escaping,
	 * even when not defining argument placeholders, you need to set this
	 * flag to "true". Else, only message texts with actual arguments
	 * are supposed to be written with MessageFormat escaping.
	 * @see java.text.MessageFormat
	 */
	public void setAlwaysUseMessageFormat(boolean alwaysUseMessageFormat) {
		this.alwaysUseMessageFormat = alwaysUseMessageFormat;
	}

	/**
	 * 返回是否始终应用消息格式组件，解析没有参数的消息
	 *
	 * Return whether to always apply the MessageFormat rules, parsing even
	 * messages without arguments.
	 */
	protected boolean isAlwaysUseMessageFormat() {
		return this.alwaysUseMessageFormat;
	}


	/**
	 * 渲染给定的默认消息字符串
	 *
	 * Render the given default message String. The default message is
	 * passed in as specified by the caller and can be rendered into
	 * a fully formatted default message shown to the user.
	 * <p>The default implementation passes the String to {@code formatMessage},
	 * resolving any argument placeholders found in them. Subclasses may override
	 * this method to plug in custom processing of default messages.
	 * @param defaultMessage the passed-in default message String
	 * @param args array of arguments that will be filled in for params within
	 * the message, or {@code null} if none.
	 * @param locale the Locale used for formatting
	 * @return the rendered default message (with resolved arguments)
	 * @see #formatMessage(String, Object[], java.util.Locale)
	 */
	protected String renderDefaultMessage(String defaultMessage, @Nullable Object[] args, Locale locale) {
		return formatMessage(defaultMessage, args, locale);
	}

	/**
	 * 渲染给定的消息字符串
	 *
	 * Format the given message String, using cached MessageFormats.
	 * By default invoked for passed-in default messages, to resolve
	 * any argument placeholders found in them.
	 * @param msg the message to format
	 * @param args array of arguments that will be filled in for params within
	 * the message, or {@code null} if none
	 * @param locale the Locale used for formatting
	 * @return the formatted message (with resolved arguments)
	 */
	protected String formatMessage(String msg, @Nullable Object[] args, Locale locale) {
		if (!isAlwaysUseMessageFormat() && ObjectUtils.isEmpty(args)) {
			return msg;
		}
		MessageFormat messageFormat = null;
		synchronized (this.messageFormatsPerMessage) {
			// 先尝试获取缓存消息格式组件
			Map<Locale, MessageFormat> messageFormatsPerLocale = this.messageFormatsPerMessage.get(msg);
			if (messageFormatsPerLocale != null) {
				messageFormat = messageFormatsPerLocale.get(locale);
			}
			else {
				// 缓存为空，设置初始化空map值
				messageFormatsPerLocale = new HashMap<>();
				this.messageFormatsPerMessage.put(msg, messageFormatsPerLocale);
			}
			// 消息格式组件为空，则初始化一个messageFormat
			if (messageFormat == null) {
				try {
					messageFormat = createMessageFormat(msg, locale);
				}
				catch (IllegalArgumentException ex) {
					// Invalid message format - probably not intended for formatting,
					// rather using a message structure with no arguments involved...
					// 如果alwaysUseMessageFormat为true，则抛出异常
					if (isAlwaysUseMessageFormat()) {
						throw ex;
					}
					// Silently proceed with raw message if format not enforced...
					// 如果格式未被强制执行，则默认继续处理原始消息
					messageFormat = INVALID_MESSAGE_FORMAT;
				}
				// 缓存当前消息的消息格式组件
				messageFormatsPerLocale.put(locale, messageFormat);
			}
		}
		// 消息解析对象为默认消息解析器，直接返回msg
		if (messageFormat == INVALID_MESSAGE_FORMAT) {
			return msg;
		}
		// 使用messageFormat解析消息
		synchronized (messageFormat) {
			return messageFormat.format(resolveArguments(args, locale));
		}
	}

	/**
	 * 为给定的消息和区域设置创建一个MessageFormat
	 *
	 * Create a MessageFormat for the given message and Locale.
	 * @param msg the message to create a MessageFormat for
	 * @param locale the Locale to create a MessageFormat for
	 * @return the MessageFormat instance
	 */
	protected MessageFormat createMessageFormat(String msg, Locale locale) {
		return new MessageFormat(msg, locale);
	}

	/**
	 * 子类可以覆盖，否则只是按原样返回给定的参数数组
	 *
	 * Template method for resolving argument objects.
	 * <p>The default implementation simply returns the given argument array as-is.
	 * Can be overridden in subclasses in order to resolve special argument types.
	 * @param args the original argument array
	 * @param locale the Locale to resolve against
	 * @return the resolved argument array
	 */
	protected Object[] resolveArguments(@Nullable Object[] args, Locale locale) {
		return (args != null ? args : new Object[0]);
	}

}
