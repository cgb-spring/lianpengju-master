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

package org.springframework.beans;

import java.beans.PropertyEditor;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.core.CollectionFactory;
import org.springframework.core.convert.ConversionFailedException;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.lang.Nullable;
import org.springframework.util.ClassUtils;
import org.springframework.util.NumberUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

/**
 * 类型转换的委托类，所有类型的转换工作都由该类完成，转换属性值到目标的类型
 *
 * Internal helper class for converting property values to target types.
 *
 * <p>Works on a given {@link PropertyEditorRegistrySupport} instance.
 * Used as a delegate by {@link BeanWrapperImpl} and {@link SimpleTypeConverter}.
 *
 * @author Juergen Hoeller
 * @author Rob Harrop
 * @author Dave Syer
 * @since 2.0
 * @see BeanWrapperImpl
 * @see SimpleTypeConverter
 */
class TypeConverterDelegate {

	private static final Log logger = LogFactory.getLog(TypeConverterDelegate.class);

	// 属性编辑器注册器，用来管理属性编辑器（默认的和自定义的）
	private final PropertyEditorRegistrySupport propertyEditorRegistry;

	/**
	 * 要处理的目标对象（作为可传递给编辑器的上下文）
	 */
	@Nullable
	private final Object targetObject;


	/**
	 * 创建一个新的 TypeConverterDelegate 实例
	 *
	 * Create a new TypeConverterDelegate for the given editor registry.
	 * @param propertyEditorRegistry the editor registry to use
	 */
	public TypeConverterDelegate(PropertyEditorRegistrySupport propertyEditorRegistry) {
		this(propertyEditorRegistry, null);
	}

	/**
	 * 为给定的编辑器注册表和Bean实例创建一个新的TypeConverterDelegate
	 *
	 * Create a new TypeConverterDelegate for the given editor registry and bean instance.
	 * @param propertyEditorRegistry the editor registry to use
	 * @param targetObject the target object to work on (as context that can be passed to editors)
	 */
	public TypeConverterDelegate(PropertyEditorRegistrySupport propertyEditorRegistry, @Nullable Object targetObject) {
		this.propertyEditorRegistry = propertyEditorRegistry;
		this.targetObject = targetObject;
	}


	/**
	 * 将该值转换为指定属性所需的类型
	 *
	 * Convert the value to the required type for the specified property.
	 * @param propertyName name of the property
	 * @param oldValue the previous value, if available (may be {@code null})
	 * @param newValue the proposed new value
	 * @param requiredType the type we must convert to
	 * (or {@code null} if not known, for example in case of a collection element)
	 * @return the new value, possibly the result of type conversion
	 * @throws IllegalArgumentException if type conversion failed
	 */
	@Nullable
	public <T> T convertIfNecessary(@Nullable String propertyName, @Nullable Object oldValue,
			Object newValue, @Nullable Class<T> requiredType) throws IllegalArgumentException {

		// 将requiredType封装成TypeDescriptor对象，调用另外一个重载方法
		return convertIfNecessary(propertyName, oldValue, newValue, requiredType, TypeDescriptor.valueOf(requiredType));
	}

	/**
	 * 将指定属性的值转换为所需的类型(如果需要从字符串)
	 *
	 * Convert the value to the required type (if necessary from a String),
	 * for the specified property.
	 * @param propertyName name of the property
	 * @param oldValue the previous value, if available (may be {@code null})
	 * @param newValue the proposed new value
	 * @param requiredType the type we must convert to
	 * (or {@code null} if not known, for example in case of a collection element)
	 * @param typeDescriptor the descriptor for the target property or field
	 * @return the new value, possibly the result of type conversion
	 * @throws IllegalArgumentException if type conversion failed
	 */
	@SuppressWarnings("unchecked")
	@Nullable
	public <T> T convertIfNecessary(@Nullable String propertyName, @Nullable Object oldValue, @Nullable Object newValue,
			@Nullable Class<T> requiredType, @Nullable TypeDescriptor typeDescriptor) throws IllegalArgumentException {

		// Custom editor for this type?
		// 自定义编辑这个类型吗？
		// PropertyEditor是属性编辑器的接口，它规定了将外部设置值转换为内部JavaBean属性值的转换接口方法。
		// 为requiredType和propertyName找到一个自定义属性编辑器
		PropertyEditor editor = this.propertyEditorRegistry.findCustomEditor(requiredType, propertyName);

		// 尝试使用自定义ConversionService转换newValue转换失败后抛出的异常
		ConversionFailedException conversionAttemptEx = null;

		// No custom editor but custom ConversionService specified?
		// 没有自定以编辑器，但自定以 ConversionService 指定了？
		// ConversionService :  一个类型转换的服务接口。这个转换系统的入口。
		// 获取类型转换服务
		ConversionService conversionService = this.propertyEditorRegistry.getConversionService();
		// 如果editor为null且cnversionService不为null&&新值不为null&&类型描述符不为null
		if (editor == null && conversionService != null && newValue != null && typeDescriptor != null) {
			// 将newValue封装成TypeDescriptor对象
			TypeDescriptor sourceTypeDesc = TypeDescriptor.forObject(newValue);
			// 如果sourceTypeDesc的对象能被转换成typeDescriptor.
			if (conversionService.canConvert(sourceTypeDesc, typeDescriptor)) {
				try {
					// 从conversionService 中找到 sourceTypeDesc,typeDesriptor对于的转换器进行对newValue的转换成符合typeDesciptor类型的对象，并返回出去
					return (T) conversionService.convert(newValue, sourceTypeDesc, typeDescriptor);
				}
				catch (ConversionFailedException ex) {
					// fallback to default conversion logic below
					// 返回到下面的默认转换逻辑
					conversionAttemptEx = ex;
				}
			}
		}

		// 默认转换后的值为newValue
		Object convertedValue = newValue;

		// Value not of required type?
		// 值不是必需的类型
		// 如果editor不为null||(requiredType不为null&&convertedValue不是requiredType的实例)
		if (editor != null || (requiredType != null && !ClassUtils.isAssignableValue(requiredType, convertedValue))) {
			// 如果typeDescriptor不为null&&requiredType不为null&&requiredType是Collection的子类或实现&&conventedValue是String类型
			if (typeDescriptor != null && requiredType != null && Collection.class.isAssignableFrom(requiredType) &&
					convertedValue instanceof String) {
				// 获取该typeDescriptor的元素TypeDescriptor
				TypeDescriptor elementTypeDesc = typeDescriptor.getElementTypeDescriptor();
				// 如果elementTypeDesc不为null
				if (elementTypeDesc != null) {
					// 获取elementTypeDesc的类型
					Class<?> elementType = elementTypeDesc.getType();
					// 如果elementType是Class类||elementType是Enum的子类或实现
					if (Class.class == elementType || Enum.class.isAssignableFrom(elementType)) {
						// 将convertedValue强转为String，以逗号分割convertedValue返回空字符串
						convertedValue = StringUtils.commaDelimitedListToStringArray((String) convertedValue);
					}
				}
			}
			// 如果editor为null
			if (editor == null) {
				// 找到requiredType的默认编辑器
				editor = findDefaultEditor(requiredType);
			}
			// 使用editor将convertedValue转换为requiredType
			convertedValue = doConvertValue(oldValue, convertedValue, requiredType, editor);
		}

		// 标准转换标记，convertedValue是Collection类型，Map类型，数组类型，可转换成Enum类型的String对象，Number类型并成功进行转换后即为true
		boolean standardConversion = false;

		// 如果requiredType不为null
		if (requiredType != null) {
			// Try to apply some standard type conversion rules if appropriate.
			// 如果合适，尝试应用一些标准类型转换规则
			// convertedValue不为null
			if (convertedValue != null) {
				// 如果requiredType是Object类型
				if (Object.class == requiredType) {
					// 直接返回convertedValue
					return (T) convertedValue;
				}
				// 如果requiredType是数组
				else if (requiredType.isArray()) {
					// Array required -> apply appropriate conversion of elements.
					// 数组所需 -> 应用适当的元素转换
					// 如果convertedValue是String的实例&&requiredType的元素类型是Enum的子类或实现
					if (convertedValue instanceof String && Enum.class.isAssignableFrom(requiredType.getComponentType())) {
						// 将逗号分割的列表(例如 csv 文件中的一行)转换为字符串数组
						convertedValue = StringUtils.commaDelimitedListToStringArray((String) convertedValue);
					}
					// 将convertedValue转换为componentType类型数组对象
					return (T) convertToTypedArray(convertedValue, propertyName, requiredType.getComponentType());
				}
				// 如果convertedValue是Collection对象
				else if (convertedValue instanceof Collection) {
					// Convert elements to target type, if determined.
					// 如果确定，则将元素转换为目标类型
					// 将convertedValue转换为Collection类型对象
					convertedValue = convertToTypedCollection(
							(Collection<?>) convertedValue, propertyName, requiredType, typeDescriptor);
					// 更新standardConversion标记
					standardConversion = true;
				}
				// 如果convertedValue是Map对象
				else if (convertedValue instanceof Map) {
					// Convert keys and values to respective target type, if determined.
					// 如果确定了，则将建和值转换为相应的目标类型
					convertedValue = convertToTypedMap(
							(Map<?, ?>) convertedValue, propertyName, requiredType, typeDescriptor);
					// 更新standardConversion标记
					standardConversion = true;
				}
				// 如果convertedValue是数组类型，并且长度为1，那么就把get(0)赋值给本身
				if (convertedValue.getClass().isArray() && Array.getLength(convertedValue) == 1) {
					// 获取convertedValue的第一个元素对象
					convertedValue = Array.get(convertedValue, 0);
					// 更新standardConversion标记
					standardConversion = true;
				}
				// 如果需要的类型是String，并且convertedValue的类型是基本类型或者装箱类型，那就直接toString 后强行转换
				if (String.class == requiredType && ClassUtils.isPrimitiveOrWrapper(convertedValue.getClass())) {
					// We can stringify any primitive value...
					// 将convertedValue转换为字符转返回出去
					return (T) convertedValue.toString();
				}
				// 如果convertedValue是String类型&& convertedValue不是requiredType类型
				else if (convertedValue instanceof String && !requiredType.isInstance(convertedValue)) {
					// conversionAttemptEx为null意味着自定义ConversionService转换newValue转换失败或者没有自定义ConversionService
					// 如果conversionAttemptEx为null&&requiredType不是接口&&requireType不是枚举类
					if (conversionAttemptEx == null && !requiredType.isInterface() && !requiredType.isEnum()) {
						try {
							// 获取requiredType的接收一个String类型参数的构造函数对象
							Constructor<T> strCtor = requiredType.getConstructor(String.class);
							// 使用strCtor构造函数，传入convertedValue实例化对象并返回出去
							return BeanUtils.instantiateClass(strCtor, convertedValue);
						}
						catch (NoSuchMethodException ex) {
							// proceed with field lookup
							if (logger.isTraceEnabled()) {
								logger.trace("No String constructor found on type [" + requiredType.getName() + "]", ex);
							}
						}
						catch (Exception ex) {
							if (logger.isDebugEnabled()) {
								logger.debug("Construction via String failed for type [" + requiredType.getName() + "]", ex);
							}
						}
					}
					// 将convertedValue强转为字符串，并去掉前后的空格
					String trimmedValue = ((String) convertedValue).trim();
					// 如果requireType是枚举&&trimmedValue是空字符串
					if (requiredType.isEnum() && trimmedValue.isEmpty()) {
						// It's an empty enum identifier: reset the enum value to null.
						// 这个一个空枚举标识符：重置枚举值为null
						return null;
					}
					// 尝试转换String对象为Enum对象
					convertedValue = attemptToConvertStringToEnum(requiredType, trimmedValue, convertedValue);
					// 更新standardConversion标记
					standardConversion = true;
				}
				// 如果convertedValue是Number实例&&requiredType是Number的实现或子类
				else if (convertedValue instanceof Number && Number.class.isAssignableFrom(requiredType)) {
					// NumberUtils.convertNumberToTargetClass：将convertedValue为requiredType的实例
					convertedValue = NumberUtils.convertNumberToTargetClass(
							(Number) convertedValue, (Class<Number>) requiredType);
					// 更新standardConversion标记
					standardConversion = true;
				}
			}
			else {
				// convertedValue == null
				// 如果requiredType为Optional类
				if (requiredType == Optional.class) {
					// 将convertedValue设置Optional空对象
					convertedValue = Optional.empty();
				}
			}

			// 如果convertedValue不是requiredType的实例
			if (!ClassUtils.isAssignableValue(requiredType, convertedValue)) {
				// conversionAttemptEx：尝试使用自定义ConversionService转换newValue转换失败后抛出的异常
				// conversionAttemptEx不为null
				if (conversionAttemptEx != null) {
					// Original exception from former ConversionService call above...
					// 从前面的ConversionService调用的原始异常
					// 重新抛出conversionAttemptEx
					throw conversionAttemptEx;
				}
				// 如果conversionService不为null&&typeDescriptor不为null
				else if (conversionService != null && typeDescriptor != null) {
					// ConversionService not tried before, probably custom editor found
					// but editor couldn't produce the required type...
					// ConversionService之前没有尝试过，可能找到了自定义编辑器，但编辑器不能产生所需的类型获取newValue的类型描述符
					TypeDescriptor sourceTypeDesc = TypeDescriptor.forObject(newValue);
					// 如果sourceTypeDesc的对象能被转换成typeDescriptor
					if (conversionService.canConvert(sourceTypeDesc, typeDescriptor)) {
						// 将newValue转换为typeDescriptor对应类型的对象
						return (T) conversionService.convert(newValue, sourceTypeDesc, typeDescriptor);
					}
				}

				// Definitely doesn't match: throw IllegalArgumentException/IllegalStateException
				// 绝对不匹配：抛出IllegalArgumentException/IllegalStateException
				// 拼接异常信息
				StringBuilder msg = new StringBuilder();
				msg.append("Cannot convert value of type '").append(ClassUtils.getDescriptiveType(newValue));
				msg.append("' to required type '").append(ClassUtils.getQualifiedName(requiredType)).append("'");
				if (propertyName != null) {
					msg.append(" for property '").append(propertyName).append("'");
				}
				if (editor != null) {
					msg.append(": PropertyEditor [").append(editor.getClass().getName()).append(
							"] returned inappropriate value of type '").append(
							ClassUtils.getDescriptiveType(convertedValue)).append("'");
					throw new IllegalArgumentException(msg.toString());
				}
				else {
					msg.append(": no matching editors or conversion strategy found");
					throw new IllegalStateException(msg.toString());
				}
			}
		}

		// conversionAttemptEx：尝试使用自定义ConversionService转换newValue转换失败后抛出的异常
		// conversionAttemptEx不为null
		if (conversionAttemptEx != null) {
			// editor：requiredType和propertyName对应一个自定义属性编辑器
			// standardConversion:标准转换标记，convertedValue是Collection类型，Map类型，数组类型，
			// 可转换成Enum类型的String对象，Number类型并成功进行转换后即为true
			// editor为null&&不是标准转换&&要转换的类型不为null&&requiedType不是Object类
			if (editor == null && !standardConversion && requiredType != null && Object.class != requiredType) {
				// 重新抛出conversionAttemptEx
				throw conversionAttemptEx;
			}
			logger.debug("Original ConversionService attempt failed - ignored since " +
					"PropertyEditor based conversion eventually succeeded", conversionAttemptEx);
		}

		// 返回转换后的值
		return (T) convertedValue;
	}

	/**
	 * 尝试转换String对象为Enum对象
	 * @param requiredType
	 * @param trimmedValue
	 * @param currentConvertedValue
	 * @return
	 */
	private Object attemptToConvertStringToEnum(Class<?> requiredType, String trimmedValue, Object currentConvertedValue) {
		// 当前转换后的对象，默认是currentConvertedValue
		Object convertedValue = currentConvertedValue;

		// 如果requiredType是Enum类&&目标对象不为null
		if (Enum.class == requiredType && this.targetObject != null) {
			// target type is declared as raw enum, treat the trimmed value as <enum.fqn>.FIELD_NAME
			// 目标类型被声明为原始枚举，处理修减值为 <enum.fqn>.FIELD_NAME将trimmedValue的最后一个'.'位置
			int index = trimmedValue.lastIndexOf('.');
			// 如果找到'.'的位置
			if (index > - 1) {
				// 截取出trimmedValue的index前面的字符传作为枚举类名
				String enumType = trimmedValue.substring(0, index);
				// 截取出trimmedValue的index+1后面的字符传为枚举类的属性名
				String fieldName = trimmedValue.substring(index + 1);
				// 获取targetObject的类加载器
				ClassLoader cl = this.targetObject.getClass().getClassLoader();
				try {
					// 从cl中获取enumType的Class对象
					Class<?> enumValueType = ClassUtils.forName(enumType, cl);
					// 获取fieldName对应enumValueType属性对象
					Field enumField = enumValueType.getField(fieldName);
					// 取出该属性对象的值作为convertedValue
					convertedValue = enumField.get(null);
				}
				catch (ClassNotFoundException ex) {
					if (logger.isTraceEnabled()) {
						logger.trace("Enum class [" + enumType + "] cannot be loaded", ex);
					}
				}
				catch (Throwable ex) {
					if (logger.isTraceEnabled()) {
						logger.trace("Field [" + fieldName + "] isn't an enum value for type [" + enumType + "]", ex);
					}
				}
			}
		}

		// 如果convertedValue与currentConvertedValue是同一个对象
		if (convertedValue == currentConvertedValue) {
			// Try field lookup as fallback: for JDK 1.5 enum or custom enum
			// with values defined as static fields. Resulting value still needs
			// to be checked, hence we don't return it right away.
			// 尝试字段查找作为回退：对于JDK 1.5枚举或值定义为静态字段的自定义枚举。结果值仍然需要检查
			// 因此我们不能立即返回它
			try {
				// 获取requiredType中trimmedValue属性名的属性对象
				Field enumField = requiredType.getField(trimmedValue);
				// 使enumField可访问，并在需要时显示设置enumField的可访问性
				ReflectionUtils.makeAccessible(enumField);
				// 取出该enumField的值作为convertedValue
				convertedValue = enumField.get(null);
			}
			catch (Throwable ex) {
				if (logger.isTraceEnabled()) {
					logger.trace("Field [" + convertedValue + "] isn't an enum value", ex);
				}
			}
		}

		// 返回转换后的值
		return convertedValue;
	}
	/**
	 * 找到给定类型的默认编辑器
	 *
	 * Find a default editor for the given type.
	 * @param requiredType the type to find an editor for
	 * @return the corresponding editor, or {@code null} if none
	 */
	@Nullable
	private PropertyEditor findDefaultEditor(@Nullable Class<?> requiredType) {
		// 如果requireType不为null
		PropertyEditor editor = null;
		if (requiredType != null) {
			// No custom editor -> check BeanWrapperImpl's default editors.
			// 没有自定义编辑器 -> 检查BeanWrapperImpl 的默认编辑器
			editor = this.propertyEditorRegistry.getDefaultEditor(requiredType);
			if (editor == null && String.class != requiredType) {
				// No BeanWrapper default editor -> check standard JavaBean editor.
				editor = BeanUtils.findEditorByConvention(requiredType);
			}
		}
		return editor;
	}

	/**
	 * 使用给定的属性编辑器将值转换为所需的类型(如果需要从String)
	 *
	 * Convert the value to the required type (if necessary from a String),
	 * using the given property editor.
	 * @param oldValue the previous value, if available (may be {@code null})
	 * @param newValue the proposed new value
	 * @param requiredType the type we must convert to
	 * (or {@code null} if not known, for example in case of a collection element)
	 * @param editor the PropertyEditor to use
	 * @return the new value, possibly the result of type conversion
	 * @throws IllegalArgumentException if type conversion failed
	 */
	@Nullable
	private Object doConvertValue(@Nullable Object oldValue, @Nullable Object newValue,
			@Nullable Class<?> requiredType, @Nullable PropertyEditor editor) {

		// 默认转换后的值为newValue
		Object convertedValue = newValue;

		// 如果editor不为null&&convertedValue不是字符换
		if (editor != null && !(convertedValue instanceof String)) {
			// Not a String -> use PropertyEditor's setValue.
			// 使用 PropertyEditor 的 setValue
			// With standard PropertyEditors, this will return the very same object;
			// 使用标准的 PropertyEditors,这将返回完全相同的对象
			// we just want to allow special PropertyEditors to override setValue
			// for type conversion from non-String values to the required type.
			// 我们只是想允许特殊的PropertyEditors覆盖setValue来进行从非字符串值所需类型的类型转换
			try {
				// PropertyEditor.setValue:设置属性的值，基本类型以包装类传入（自动装箱）；
				// 设置editor要编辑的对象为convertedValue
				editor.setValue(convertedValue);
				// 重新获取editor的属性值
				Object newConvertedValue = editor.getValue();
				// 如果newConvertedValue与convertedValue不是同一个对象
				if (newConvertedValue != convertedValue) {
					// 让convertedValue引用该newConvertedValue
					convertedValue = newConvertedValue;
					// Reset PropertyEditor: It already did a proper conversion.
					// 重置PropertyEditor:它已经做了一个适当的转换
					// Don't use it again for a setAsText call.
					// 不要在调用setAsText时再次使用它
					editor = null;
				}
			}
			catch (Exception ex) {
				if (logger.isDebugEnabled()) {
					logger.debug("PropertyEditor [" + editor.getClass().getName() + "] does not support setValue call", ex);
				}
				// Swallow and proceed.
			}
		}

		// 默认返回值为转换后的值
		Object returnValue = convertedValue;

		// 如果requireType不为null&&requiredType不是数组&&convertedValue是String 数组
		if (requiredType != null && !requiredType.isArray() && convertedValue instanceof String[]) {
			// Convert String array to a comma-separated String.
			// 将字符串数组转换为逗号分割的字符串
			// Only applies if no PropertyEditor converted the String array before.
			// 只有在之前没有PropertyEditor转换字符串数组是才适用
			// The CSV String will be passed into a PropertyEditor's setAsText method, if any.
			// CSV字符串将被传递到PropertyEditor的setAsText方法中(如果有的话)
			// 如果是跟踪模式
			if (logger.isTraceEnabled()) {
				// 将字符串数组转换为以逗号分割的字符串
				logger.trace("Converting String array to comma-delimited String [" + convertedValue + "]");
			}
			// 将convertedValue转换为以逗号分隔的String(即CSV).
			convertedValue = StringUtils.arrayToCommaDelimitedString((String[]) convertedValue);
		}

		// 如果convertedValue是String实例
		if (convertedValue instanceof String) {
			// 如果编辑器不为null
			if (editor != null) {
				// Use PropertyEditor's setAsText in case of a String value.
				// 如果是字符值，请使用PropertyEditor的setAsText
				// 如果是跟踪模式
				if (logger.isTraceEnabled()) {
					// 转换字符串为[requiredType]适用属性编辑器[editor]
					logger.trace("Converting String to [" + requiredType + "] using property editor [" + editor + "]");
				}
				// 将convertedValue强转为String对象
				String newTextValue = (String) convertedValue;
				// 使用editor转换newTextValue，并将转换后的值返回出去
				return doConvertTextValue(oldValue, newTextValue, editor);
			}
			// 如果requiredType是String类型
			else if (String.class == requiredType) {
				// 返回值就是convertedValue
				returnValue = convertedValue;
			}
		}

		// 将返回值返回出去
		return returnValue;
	}

	/**
	 * 使用给定属性编辑器转换给定的文本值
	 *
	 * Convert the given text value using the given property editor.
	 * @param oldValue the previous value, if available (may be {@code null})
	 * @param newTextValue the proposed text value
	 * @param editor the PropertyEditor to use
	 * @return the converted value
	 */
	private Object doConvertTextValue(@Nullable Object oldValue, String newTextValue, PropertyEditor editor) {
		try {
			// PropertyEditor.setValue:设置属性的值，基本类型以包装类传入（自动装箱）
			// 设置editor的属性值为oldValue
			editor.setValue(oldValue);
		}
		catch (Exception ex) {
			// 捕捉所有设置属性值时抛出的异常
			// 如果是跟踪模式
			if (logger.isDebugEnabled()) {
				logger.debug("PropertyEditor [" + editor.getClass().getName() + "] does not support setValue call", ex);
			}
			// Swallow and proceed.
		}
		// PropertyEditor.setAsText:用一个字符串去更新属性的内部值，这个字符串一般从外部属性编辑器传入；
		// 使用newTextValue更新内部属性值
		editor.setAsText(newTextValue);
		// 获取属性值
		return editor.getValue();
	}

	/**
	 * 将input转换为 commpentedType类型数组对象
	 * @param input
	 * @param propertyName
	 * @param componentType
	 * @return
	 */
	private Object convertToTypedArray(Object input, @Nullable String propertyName, Class<?> componentType) {
		// 如果input是Collection实例
		if (input instanceof Collection) {
			// Convert Collection elements to array elements.
			// 将集合元素转换为数组元素
			// 将input强转为Collection对象
			Collection<?> coll = (Collection<?>) input;
			// 新建一个元素类型为componentType,长度为coll.size的列表
			Object result = Array.newInstance(componentType, coll.size());
			int i = 0;
			// 遍历coll的元素
			for (Iterator<?> it = coll.iterator(); it.hasNext(); i++) {
				// 将it的元素转换为componentType类型对象
				Object value = convertIfNecessary(
						buildIndexedPropertyName(propertyName, i), null, it.next(), componentType);
				// 将value设置到第i个result元素位置上
				Array.set(result, i, value);
			}
			// 返回result
			return result;
		}
		// 如果input是数组类型
		else if (input.getClass().isArray()) {
			// Convert array elements, if necessary.
			// 如果需要，转换数组元素,如果componentType与input的元素类型相同 && propertyEditorRegistry不包含指定数组/集合元素的自定义编辑器
			if (componentType.equals(input.getClass().getComponentType()) &&
					!this.propertyEditorRegistry.hasCustomEditorForElement(componentType, propertyName)) {
				// 返回input
				return input;
			}
			// 获取input的数组长度
			int arrayLength = Array.getLength(input);
			// 构建出数组长度为arrayLength，元素类型为componentType的数组
			Object result = Array.newInstance(componentType, arrayLength);
			// 遍历result
			for (int i = 0; i < arrayLength; i++) {
				// Array.get(input, i)：获取input的第i个元素对象
				// 将该值转换为componentType对象
				Object value = convertIfNecessary(
						buildIndexedPropertyName(propertyName, i), null, Array.get(input, i), componentType);
				// 将value放到result数组的第i个位置里
				Array.set(result, i, value);
			}
			// 返回result
			return result;
		}
		else {
			// A plain value: convert it to an array with a single component.
			// 纯值：将其转换为具有单个组件的数组
			// 构建一个长度为1，元素类型为componentType的数组对象
			Object result = Array.newInstance(componentType, 1);
			// 将input转换为componentType类型的对象
			Object value = convertIfNecessary(
					buildIndexedPropertyName(propertyName, 0), null, input, componentType);
			// 将value设置到result第0个索引位置
			Array.set(result, 0, value);
			// 返回result
			return result;
		}
	}

	/**
	 * 将original转换为Collection类型对象
	 * @param original
	 * @param propertyName
	 * @param requiredType
	 * @param typeDescriptor
	 * @return
	 */
	@SuppressWarnings("unchecked")
	private Collection<?> convertToTypedCollection(Collection<?> original, @Nullable String propertyName,
			Class<?> requiredType, @Nullable TypeDescriptor typeDescriptor) {

		// 如果requiredType不是Collection类型
		if (!Collection.class.isAssignableFrom(requiredType)) {
			// 返回original
			return original;
		}

		// collectionType是否是常见的Collection类的标记
		boolean approximable = CollectionFactory.isApproximableCollectionType(requiredType);
		// 如果不是常见Collection类且不可以requiredTyped的实例
		if (!approximable && !canCreateCopy(requiredType)) {
			if (logger.isDebugEnabled()) {
				logger.debug("Custom Collection type [" + original.getClass().getName() +
						"] does not allow for creating a copy - injecting original Collection as-is");
			}
			// 返回原始对象
			return original;
		}

		// 如果original是requiredType的实例
		boolean originalAllowed = requiredType.isInstance(original);
		// 获取typeDescriptor的元素类型描述符
		TypeDescriptor elementType = (typeDescriptor != null ? typeDescriptor.getElementTypeDescriptor() : null);
		// 如果elementType为null&&orginal是requiredType的实例&&propertyEditorRegistry不包含propertyName的null对象的自定义编辑器
		if (elementType == null && originalAllowed &&
				!this.propertyEditorRegistry.hasCustomEditorForElement(null, propertyName)) {
			// 返回原始对象
			return original;
		}

		// original的迭代器
		Iterator<?> it;
		try {
			// 获取original的迭代器
			it = original.iterator();
		}
		// 捕捉获取迭代器发生的所有异常
		catch (Throwable ex) {
			if (logger.isDebugEnabled()) {
				logger.debug("Cannot access Collection of type [" + original.getClass().getName() +
						"] - injecting original Collection as-is: " + ex);
			}
			return original;
		}

		// 转换后的Collection对象
		Collection<Object> convertedCopy;
		try {
			// 如果requiredType是常见Collection类
			if (approximable) {
				// 为original创建最近似的Collection对象，初始容量与original保持一致
				convertedCopy = CollectionFactory.createApproximateCollection(original, original.size());
			}
			else {
				// 获取requiredType的无参构造函数，然后创建一个实例
				convertedCopy = (Collection<Object>)
						ReflectionUtils.accessibleConstructor(requiredType).newInstance();
			}
		}
		catch (Throwable ex) {
			if (logger.isDebugEnabled()) {
				logger.debug("Cannot create copy of Collection type [" + original.getClass().getName() +
						"] - injecting original Collection as-is: " + ex);
			}
			return original;
		}

		// 遍历original的迭代器
		for (int i = 0; it.hasNext(); i++) {
			// 获取元素对象
			Object element = it.next();
			// 构建索引形式的属性名。格式：propertyName[i]
			String indexedPropertyName = buildIndexedPropertyName(propertyName, i);
			// 将element转换为elementType类型
			Object convertedElement = convertIfNecessary(indexedPropertyName, null, element,
					(elementType != null ? elementType.getType() : null) , elementType);
			try {
				// 将convertedElement添加到convertedCopy中
				convertedCopy.add(convertedElement);
			}
			catch (Throwable ex) {
				if (logger.isDebugEnabled()) {
					logger.debug("Collection type [" + original.getClass().getName() +
							"] seems to be read-only - injecting original Collection as-is: " + ex);
				}
				return original;
			}
			// 更新originalAllowed：只要element与convertedElement是同一个对象，就一直为true
			originalAllowed = originalAllowed && (element == convertedElement);
		}
		// 如果originalAllowed为true，就返回original；否则返回convertedCopy
		return (originalAllowed ? original : convertedCopy);
	}

	/**
	 * 将original转换为Map类型对象
	 * @param original
	 * @param propertyName
	 * @param requiredType
	 * @param typeDescriptor
	 * @return
	 */
	@SuppressWarnings("unchecked")
	private Map<?, ?> convertToTypedMap(Map<?, ?> original, @Nullable String propertyName,
			Class<?> requiredType, @Nullable TypeDescriptor typeDescriptor) {

		// 如果requiredType不是Collection类型
		if (!Map.class.isAssignableFrom(requiredType)) {
			// 返回original
			return original;
		}

		// collectionType是否是常见的Collection类的标记
		boolean approximable = CollectionFactory.isApproximableMapType(requiredType);
		// 如果不是常见Collection类且不可以requiredTyped的实例
		if (!approximable && !canCreateCopy(requiredType)) {
			if (logger.isDebugEnabled()) {
				logger.debug("Custom Map type [" + original.getClass().getName() +
						"] does not allow for creating a copy - injecting original Map as-is");
			}
			return original;
		}

		// 如果original是requiredType的实例
		boolean originalAllowed = requiredType.isInstance(original);
		// 如果此TypeDescriptor是Map类型，则获取其Key的类型。如果typeDescriptor不是Map类型，将会抛出异常
		TypeDescriptor keyType = (typeDescriptor != null ? typeDescriptor.getMapKeyTypeDescriptor() : null);
		// 如果此TypeDescriptor是Map类型，则获取其Value的类型。如果typeDescriptor不是Map类型，将会抛出异常
		TypeDescriptor valueType = (typeDescriptor != null ? typeDescriptor.getMapValueTypeDescriptor() : null);
		// 如果keyType为null&&value为null && original是requiredType的实例&&propertyEditorRegistry不包含
		// propertyName的null对象的自定义编辑器
		if (keyType == null && valueType == null && originalAllowed &&
				!this.propertyEditorRegistry.hasCustomEditorForElement(null, propertyName)) {
			// 返回原始对象
			return original;
		}

		// original的迭代器
		Iterator<?> it;
		try {
			// 获取original的迭代器
			it = original.entrySet().iterator();
		}
		catch (Throwable ex) {
			if (logger.isDebugEnabled()) {
				logger.debug("Cannot access Map of type [" + original.getClass().getName() +
						"] - injecting original Map as-is: " + ex);
			}
			return original;
		}

		// 转换后的Collection对象
		Map<Object, Object> convertedCopy;
		try {
			// 如果requiredType是常见Collection类
			if (approximable) {
				// 为original创建最近似的Collection对象，初始容量与original保持一致
				convertedCopy = CollectionFactory.createApproximateMap(original, original.size());
			}
			else {
				// 获取requiredType的无参构造函数，然后创建一个实例
				convertedCopy = (Map<Object, Object>)
						ReflectionUtils.accessibleConstructor(requiredType).newInstance();
			}
		}
		// 捕捉创建convertedCopy对象时抛出的异常
		catch (Throwable ex) {
			if (logger.isDebugEnabled()) {
				logger.debug("Cannot create copy of Map type [" + original.getClass().getName() +
						"] - injecting original Map as-is: " + ex);
			}
			return original;
		}

		// 遍历original的迭代器
		while (it.hasNext()) {
			// 获取元素对象
			Map.Entry<?, ?> entry = (Map.Entry<?, ?>) it.next();
			// 获取entry的key对象
			Object key = entry.getKey();
			// 获取entry的value对象
			Object value = entry.getValue();
			// 构建键名形式的属性名。格式：propertyName[key]
			String keyedPropertyName = buildKeyedPropertyName(propertyName, key);
			// 将key转换为keyType类型
			Object convertedKey = convertIfNecessary(keyedPropertyName, null, key,
					(keyType != null ? keyType.getType() : null), keyType);
			// 将value转换为valueType类型
			Object convertedValue = convertIfNecessary(keyedPropertyName, null, value,
					(valueType!= null ? valueType.getType() : null), valueType);
			try {
				// 将convertedKey,convertedValue添加到convertedCopy中
				convertedCopy.put(convertedKey, convertedValue);
			}
			catch (Throwable ex) {
				if (logger.isDebugEnabled()) {
					logger.debug("Map type [" + original.getClass().getName() +
							"] seems to be read-only - injecting original Map as-is: " + ex);
				}
				// 返回原始对象
				return original;
			}
			// 更新originalAllowed：只要element与convertedKey是同一个对象&&value与convertedValue是同一个对象 ，就一直为true
			originalAllowed = originalAllowed && (key == convertedKey) && (value == convertedValue);
		}
		// 如果originalAllowed为true，就返回original；否则返回convertedCopy
		return (originalAllowed ? original : convertedCopy);
	}

	/**
	 * 构建索引形式的属性名。格式：propertyName[index]
	 * @param propertyName
	 * @param index
	 * @return
	 */
	@Nullable
	private String buildIndexedPropertyName(@Nullable String propertyName, int index) {
		return (propertyName != null ?
				propertyName + PropertyAccessor.PROPERTY_KEY_PREFIX + index + PropertyAccessor.PROPERTY_KEY_SUFFIX :
				null);
	}

	/**
	 * 构建键名形式的属性名。格式：propertyName[key]
	 * @param propertyName
	 * @param key
	 * @return
	 */
	@Nullable
	private String buildKeyedPropertyName(@Nullable String propertyName, Object key) {
		return (propertyName != null ?
				propertyName + PropertyAccessor.PROPERTY_KEY_PREFIX + key + PropertyAccessor.PROPERTY_KEY_SUFFIX :
				null);
	}

	/**
	 * 是否可以requiredTyped的实例
	 * @param requiredType
	 * @return
	 */
	private boolean canCreateCopy(Class<?> requiredType) {
		// 如果requiredType不是接口&&requiredType不是抽象&&requiredType是Public&&requiredType是无参构造方法就返回true
		return (!requiredType.isInterface() && !Modifier.isAbstract(requiredType.getModifiers()) &&
				Modifier.isPublic(requiredType.getModifiers()) && ClassUtils.hasConstructor(requiredType));
	}

}
