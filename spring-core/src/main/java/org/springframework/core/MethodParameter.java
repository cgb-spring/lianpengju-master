/*
 * Copyright 2002-2020 the original author or authors.
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

package org.springframework.core;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

import kotlin.Unit;
import kotlin.reflect.KFunction;
import kotlin.reflect.KParameter;
import kotlin.reflect.jvm.ReflectJvmMapping;

import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ObjectUtils;

/**
 * 封装方法参数说明的帮助类，即一个{@link Method} 或者{@link Constructor}以及已声明的泛型类型的参数索引和嵌套类型索引。用作传递的规范对象
 *
 * Helper class that encapsulates the specification of a method parameter, i.e. a {@link Method}
 * or {@link Constructor} plus a parameter index and a nested type index for a declared generic
 * type. Useful as a specification object to pass along.
 *
 * <p>As of 4.2, there is a {@link org.springframework.core.annotation.SynthesizingMethodParameter}
 * subclass available which synthesizes annotations with attribute aliases. That subclass is used
 * for web and message endpoint processing, in particular.
 *
 * @author Juergen Hoeller
 * @author Rob Harrop
 * @author Andy Clement
 * @author Sam Brannen
 * @author Sebastien Deleuze
 * @author Phillip Webb
 * @since 2.0
 * @see org.springframework.core.annotation.SynthesizingMethodParameter
 */
public class MethodParameter {

	/**
	 * 空注解数组
	 */
	private static final Annotation[] EMPTY_ANNOTATION_ARRAY = new Annotation[0];

	/**
	 * 当前Method或构造函数对象
	 * <p>Executable类是Method和Constructor的父类，一个抽象类，直接继承AccessibleObject类，并实现Membet与GenericDeclaration接口，
	 *  主要作用是实现类型变量的相关操作以及展示方法的信息。</p>
	 *
	 */
	private final Executable executable;

	/**
	 * 通过检查后的参数索引
	 */
	private final int parameterIndex;

	/**
	 * 当前方法/构造函数 {@link #parameterIndex} 位置的参数
	 */
	@Nullable
	private volatile Parameter parameter;

	/**
	 * 目标类型的嵌套等级（通常为1；比如，在列表的列表的情况下，则1表示嵌套列表，而2表示嵌套列表的元素）
	 */
	private int nestingLevel;

	/**
	 * 从整数嵌套等级到整数类型索引的映射
	 * Map from Integer level to Integer type index. */
	@Nullable
	Map<Integer, Integer> typeIndexesPerLevel;

	/**
	 * 包含的类。也可以通过覆盖{@link #getContainingClass()}来提供
	 * The containing class. Could also be supplied by overriding {@link #getContainingClass()} */
	@Nullable
	private volatile Class<?> containingClass;

	/**
	 * 当前参数类型
	 */
	@Nullable
	private volatile Class<?> parameterType;

	/**
	 * 当前泛型参数类型
	 */
	@Nullable
	private volatile Type genericParameterType;

	/**
	 * 当前参数的注解
	 */
	@Nullable
	private volatile Annotation[] parameterAnnotations;

	/**
	 * 用于发现方法和构造函数的参数名的发现器
	 */
	@Nullable
	private volatile ParameterNameDiscoverer parameterNameDiscoverer;

	/**
	 * 当前参数名
	 */
	@Nullable
	private volatile String parameterName;

	/**
	 * 内嵌的方法参数
	 */
	@Nullable
	private volatile MethodParameter nestedMethodParameter;


	/**
	 * 使用嵌套级别1为给定方法创建一个新的{@code MethodParameter}
	 * Create a new {@code MethodParameter} for the given method, with nesting level 1.
	 * @param method the Method to specify a parameter for
	 * @param parameterIndex the index of the parameter: -1 for the method
	 * return type; 0 for the first method parameter; 1 for the second method
	 * parameter, etc.
	 *      参数的索引位置：-1表示方法的返回类型；0表示第一个方法参数,1表示第二个方法参数，以此类推
	 *
	 */
	public MethodParameter(Method method, int parameterIndex) {
		this(method, parameterIndex, 1);
	}

	/**
	 * Create a new {@code MethodParameter} for the given method.
	 * @param method the Method to specify a parameter for
	 * @param parameterIndex the index of the parameter: -1 for the method
	 * return type; 0 for the first method parameter; 1 for the second method
	 * parameter, etc.
	 * @param nestingLevel the nesting level of the target type
	 * (typically 1; e.g. in case of a List of Lists, 1 would indicate the
	 * nested List, whereas 2 would indicate the element of the nested List)
	 */
	public MethodParameter(Method method, int parameterIndex, int nestingLevel) {
		Assert.notNull(method, "Method must not be null");
		this.executable = method;
		// 将通过检查后的parameterIndex赋值给parameterIndex
		this.parameterIndex = validateIndex(method, parameterIndex);
		this.nestingLevel = nestingLevel;
	}

	/**
	 * 使用嵌套级别1为给定构造函数创建一个新的{@code MethodParameter}
	 *
	 * Create a new MethodParameter for the given constructor, with nesting level 1.
	 * @param constructor the Constructor to specify a parameter for
	 * @param parameterIndex the index of the parameter
	 */
	public MethodParameter(Constructor<?> constructor, int parameterIndex) {
		this(constructor, parameterIndex, 1);
	}

	/**
	 * Create a new MethodParameter for the given constructor.
	 * @param constructor the Constructor to specify a parameter for
	 * @param parameterIndex the index of the parameter
	 * @param nestingLevel the nesting level of the target type
	 * (typically 1; e.g. in case of a List of Lists, 1 would indicate the
	 * nested List, whereas 2 would indicate the element of the nested List)
	 */
	public MethodParameter(Constructor<?> constructor, int parameterIndex, int nestingLevel) {
		Assert.notNull(constructor, "Constructor must not be null");
		this.executable = constructor;
		this.parameterIndex = validateIndex(constructor, parameterIndex);
		this.nestingLevel = nestingLevel;
	}

	/**
	 * 内部构造函数，用于创建带有已设置的类的{@link MethodParameter}
	 *
	 * Internal constructor used to create a {@link MethodParameter} with a
	 * containing class already set.
	 * @param executable the Executable to specify a parameter for
	 * @param parameterIndex the index of the parameter
	 * @param containingClass the containing class
	 * @since 5.2
	 */
	MethodParameter(Executable executable, int parameterIndex, @Nullable Class<?> containingClass) {
		Assert.notNull(executable, "Executable must not be null");
		this.executable = executable;
		// 将通过检查后的parameterIndex赋值给parameterIndex
		this.parameterIndex = validateIndex(executable, parameterIndex);
		this.nestingLevel = 1;
		this.containingClass = containingClass;
	}

	/**
	 * 赋值构造函数，根据与原始对象相同的元数据和缓存状态，生成一个独立的MethodParameter对象
	 *
	 * Copy constructor, resulting in an independent MethodParameter object
	 * based on the same metadata and cache state that the original object was in.
	 * @param original the original MethodParameter object to copy from
	 */
	public MethodParameter(MethodParameter original) {
		Assert.notNull(original, "Original must not be null");
		this.executable = original.executable;
		this.parameterIndex = original.parameterIndex;
		this.parameter = original.parameter;
		this.nestingLevel = original.nestingLevel;
		this.typeIndexesPerLevel = original.typeIndexesPerLevel;
		this.containingClass = original.containingClass;
		this.parameterType = original.parameterType;
		this.genericParameterType = original.genericParameterType;
		this.parameterAnnotations = original.parameterAnnotations;
		this.parameterNameDiscoverer = original.parameterNameDiscoverer;
		this.parameterName = original.parameterName;
	}


	/**
	 * 返回包装的方法，如果有
	 *
	 * Return the wrapped Method, if any.
	 * <p>Note: Either Method or Constructor is available.
	 * @return the Method, or {@code null} if none
	 */
	@Nullable
	public Method getMethod() {
		// 如果executable是Method的子类或者本身就强转executable类型为Method再返回出去，否则返回null
		return (this.executable instanceof Method ? (Method) this.executable : null);
	}

	/**
	 * 返回包装的构造函数，如果有
	 *
	 * Return the wrapped Constructor, if any.
	 * <p>Note: Either Method or Constructor is available.
	 * @return the Constructor, or {@code null} if none
	 */
	@Nullable
	public Constructor<?> getConstructor() {
		// 如果executable是Constructor的子类或者本身就强转executable类型为Method再返回出去，否则返回null
		return (this.executable instanceof Constructor ? (Constructor<?>) this.executable : null);
	}

	/**
	 * 返回声明底层方法或者构造函数的类
	 * Return the class that declares the underlying Method or Constructor.
	 */
	public Class<?> getDeclaringClass() {
		// 获取声明该executable的类型并返回
		return this.executable.getDeclaringClass();
	}

	/**
	 * 返回包装的成员
	 *
	 * Return the wrapped member.
	 * @return the Method or Constructor as Member
	 */
	public Member getMember() {
		return this.executable;
	}

	/**
	 * 返回包装的注解元素
	 *
	 * Return the wrapped annotated element.
	 * <p>Note: This method exposes the annotations declared on the method/constructor
	 * itself (i.e. at the method/constructor level, not at the parameter level).
	 * @return the Method or Constructor as AnnotatedElement
	 */
	public AnnotatedElement getAnnotatedElement() {
		return this.executable;
	}

	/**
	 * 回包装的可执行程序
	 *
	 * Return the wrapped executable.
	 * @return the Method or Constructor as Executable
	 * @since 5.0
	 */
	public Executable getExecutable() {
		return this.executable;
	}

	/**
	 * 返回方法或构造函数参数的{@link Parameter}描述符
	 *
	 * Return the {@link Parameter} descriptor for method/constructor parameter.
	 * @since 5.0
	 */
	public Parameter getParameter() {
		// 如果参数索引小于0
		if (this.parameterIndex < 0) {
			// 抛出异常，说明无法检索方法返回类型的参数描述符
			throw new IllegalStateException("Cannot retrieve Parameter descriptor for method return type");
		}
		Parameter parameter = this.parameter;
		// 如果当前参数为null
		if (parameter == null) {
			// 获取可执行程序的的第parameterIndex个参数
			parameter = getExecutable().getParameters()[this.parameterIndex];
			// 设置当前参数
			this.parameter = parameter;
		}
		return parameter;
	}

	/**
	 * 返回方法/构造函数参数的索引
	 * Return the index of the method/constructor parameter.
	 * @return the parameter index (-1 in case of the return type)
	 */
	public int getParameterIndex() {
		return this.parameterIndex;
	}

	/**
	 * 增加参数的嵌套等级
	 *
	 * Increase this parameter's nesting level.
	 * @see #getNestingLevel()
	 * @deprecated since 5.2 in favor of {@link #nested(Integer)}
	 */
	@Deprecated
	public void increaseNestingLevel() {
		this.nestingLevel++;
	}

	/**
	 * 降低此参数的嵌套等级
	 *
	 * Decrease this parameter's nesting level.
	 * @see #getNestingLevel()
	 * @deprecated since 5.2 in favor of retaining the original MethodParameter and
	 * using {@link #nested(Integer)} if nesting is required
	 */
	@Deprecated
	public void decreaseNestingLevel() {
		// 移除从整数嵌套等级到整数类型索引的映射关系
		getTypeIndexesPerLevel().remove(this.nestingLevel);
		// 嵌套等级-1
		this.nestingLevel--;
	}

	/**
	 * 返回目标类型的嵌套等级(通常为1；比如，在列表的列表的情况下，则1表示嵌套列表，而2表示嵌套列表的元素）)
	 *
	 * Return the nesting level of the target type
	 * (typically 1; e.g. in case of a List of Lists, 1 would indicate the
	 * nested List, whereas 2 would indicate the element of the nested List).
	 */
	public int getNestingLevel() {
		return this.nestingLevel;
	}

	/**
	 * 返回此{@code MethodParameter}的变体，类型为当前级别设置的指定值
	 *
	 * Return a variant of this {@code MethodParameter} with the type
	 * for the current level set to the specified value.
	 * @param typeIndex the new type index
	 * @since 5.2
	 */
	public MethodParameter withTypeIndex(int typeIndex) {
		return nested(this.nestingLevel, typeIndex);
	}

	/**
	 * 设置当前嵌套等级的类型索引
	 *
	 * Set the type index for the current nesting level.
	 * @param typeIndex the corresponding type index
	 * (or {@code null} for the default type index)
	 * @see #getNestingLevel()
	 * @deprecated since 5.2 in favor of {@link #withTypeIndex}
	 */
	@Deprecated
	public void setTypeIndexForCurrentLevel(int typeIndex) {
		// 将嵌套等级和类型索引添加到typeIndexPerLevel中
		getTypeIndexesPerLevel().put(this.nestingLevel, typeIndex);
	}

	/**
	 * 返回当前嵌套等级的类型索引
	 *
	 * Return the type index for the current nesting level.
	 * @return the corresponding type index, or {@code null}
	 * if none specified (indicating the default type index)
	 * @see #getNestingLevel()
	 */
	@Nullable
	public Integer getTypeIndexForCurrentLevel() {
		// 从typeIndexPerLevel中获取当前nestingLevel对应的类型索引
		return getTypeIndexForLevel(this.nestingLevel);
	}

	/**
	 * 返回指定嵌套等级的类型索引
	 *
	 * Return the type index for the specified nesting level.
	 * @param nestingLevel the nesting level to check
	 * @return the corresponding type index, or {@code null}
	 * if none specified (indicating the default type index)
	 */
	@Nullable
	public Integer getTypeIndexForLevel(int nestingLevel) {
		// 从typeIndexPerLevel中获取传入的nestingLevel对应的类型索引
		return getTypeIndexesPerLevel().get(nestingLevel);
	}

	/**
	 * 获取(延迟构造的) 从整数嵌套等级到整数类型索引的映射
	 *
	 * Obtain the (lazily constructed) type-indexes-per-level Map.
	 */
	private Map<Integer, Integer> getTypeIndexesPerLevel() {
		// 如果typeIndexesPerLevel为null
		if (this.typeIndexesPerLevel == null) {
			// 初始化typeIndexesPerLevel,容量为4
			this.typeIndexesPerLevel = new HashMap<>(4);
		}
		// 从整数嵌套等级到整数类型索引的映射
		return this.typeIndexesPerLevel;
	}

	/**
	 * 返回此{@code MethodParameter}的变体,它指向同一个参数，但嵌套层次更深
	 *
	 * Return a variant of this {@code MethodParameter} which points to the
	 * same parameter but one nesting level deeper.
	 * @since 4.3
	 */
	public MethodParameter nested() {
		return nested(null);
	}

	/**
	 * 返回此{@code MethodParameter}的变体,它指向同一个参数，但嵌套层次更深
	 *
	 * Return a variant of this {@code MethodParameter} which points to the
	 * same parameter but one nesting level deeper.
	 * @param typeIndex the type index for the new nesting level
	 * @since 5.2
	 */
	public MethodParameter nested(@Nullable Integer typeIndex) {
		// 获取当前嵌套MethodParameter
		MethodParameter nestedParam = this.nestedMethodParameter;
		// 如果nestedParam不为null且类型索引为null
		if (nestedParam != null && typeIndex == null) {
			// 返回嵌套的MethodParameter
			return nestedParam;
		}
		// 获取嵌套等级多1级的MethodParameter
		nestedParam = nested(this.nestingLevel + 1, typeIndex);
		// 如果类型索引为null
		if (typeIndex == null) {
			//设置当前嵌套MethodParameter
			this.nestedMethodParameter = nestedParam;
		}
		// 返回嵌套等级多1级嵌套的MethodParameter
		return nestedParam;
	}

	/**
	 * 返回此{@code MethodParameter}的变体,它指向给定嵌套等级，以及给定的类型索引
	 * @param nestingLevel
	 * @param typeIndex
	 * @return
	 */
	private MethodParameter nested(int nestingLevel, @Nullable Integer typeIndex) {
		// 克隆当前类对象得到一份副本进行操作
		MethodParameter copy = clone();
		// 设置嵌套等级为nestinglevel
		copy.nestingLevel = nestingLevel;
		// 如果从整数嵌套等级到整数类型索引的映射不为null
		if (this.typeIndexesPerLevel != null) {
			// 设置一个从整数嵌套等级到整数类型索引的映射
			copy.typeIndexesPerLevel = new HashMap<>(this.typeIndexesPerLevel);
		}
		// 如果类型索引不为null
		if (typeIndex != null) {
			// 将嵌套等级和类型索引添加的哦typeIndexPerLevel中
			copy.getTypeIndexesPerLevel().put(copy.nestingLevel, typeIndex);
		}
		// 设置副本的当前参数类型为null
		copy.parameterType = null;
		// 设置副本的通用参数类型为null
		copy.genericParameterType = null;
		// 返回修整好的副本
		return copy;
	}

	/**
	 * 返回此方法是否表明不需要参数：以Java8的{@link java.util.Optional}形式，
	 * 参数级{@code Nullable}注解的任何变体(例如来自JSR-305或FindBugs注释集),
	 * 或者Kotlin中的语言集可为null的类型声明。
	 *
	 * Return whether this method indicates a parameter which is not required:
	 * either in the form of Java 8's {@link java.util.Optional}, any variant
	 * of a parameter-level {@code Nullable} annotation (such as from JSR-305
	 * or the FindBugs set of annotations), or a language-level nullable type
	 * declaration or {@code Continuation} parameter in Kotlin.
	 * @since 4.3
	 */
	public boolean isOptional() {
		// 如果当前参数索引的参数类型为Optional类或者此方法的参数注解是否带有Nullable注解的任何变体
		// 或者(存在Kotlin反射且当前包含类是Kotlin类型(上面带有Kotlin元数据)且)
		return (getParameterType() == Optional.class || hasNullableAnnotation() ||
				(KotlinDetector.isKotlinReflectPresent() &&
						KotlinDetector.isKotlinType(getContainingClass()) &&
						KotlinDelegate.isOptional(this)));
	}

	/**
	 * 检查此方法的参数注解是否带有{@code Nullable}注解的任何变体
	 *
	 * Check whether this method parameter is annotated with any variant of a
	 * {@code Nullable} annotation, e.g. {@code javax.annotation.Nullable} or
	 * {@code edu.umd.cs.findbugs.annotations.Nullable}.
	 */
	private boolean hasNullableAnnotation() {
		// 遍历返回与指定(当前)method/constructor参数关联的注解数组
		for (Annotation ann : getParameterAnnotations()) {
			// 如果ann的简单注解类型名为'Nullable'
			if ("Nullable".equals(ann.annotationType().getSimpleName())) {
				// 返回true，表示此方法的参数注解带有{@code Nullable}注解的任何变体
				return true;
			}
		}
		// 执行到这里，表示在当前参数的注解数组没有找到带有{@code Nullable}注解的任何变体
		return false;
	}

	/**
	 * 返回{@code MethodParameter}的变体，该变体执行同一个参数但是在声明{@link java.util.Optional}的情况下，嵌套级别更深
	 *
	 * Return a variant of this {@code MethodParameter} which points to
	 * the same parameter but one nesting level deeper in case of a
	 * {@link java.util.Optional} declaration.
	 * @since 4.3
	 * @see #isOptional()
	 * @see #nested()
	 */
	public MethodParameter nestedIfOptional() {
		// 获取当前的参数类型如果是Optional，返回同一个参数但是嵌套级别更深的{@code MethodParameter}变体
		return (getParameterType() == Optional.class ? nested() : this);
	}

	/**
	 * 返回{@code MethodParameter}的变体，该变体指向给定的包含类
	 *
	 * Return a variant of this {@code MethodParameter} which refers to the
	 * given containing class.
	 * @param containingClass a specific containing class (potentially a
	 * subclass of the declaring class, e.g. substituting a type variable)
	 * @since 5.2
	 * @see #getParameterType()
	 */
	public MethodParameter withContainingClass(@Nullable Class<?> containingClass) {
		// 克隆当前类对象得到一份副本进行操作
		MethodParameter result = clone();
		// 设置副本的包含类
		result.containingClass = containingClass;
		// 设置副本的当前参数类型
		result.parameterType = null;
		// 返回副本
		return result;
	}

	/**
	 * 设置一个包含类来解析参数类型
	 *
	 * Set a containing class to resolve the parameter type against.
	 */
	@Deprecated
	void setContainingClass(Class<?> containingClass) {
		// 设置当前包含类
		this.containingClass = containingClass;
		// 将当前参数类型置为null
		this.parameterType = null;
	}

	/**
	 * 返回方法参数的包含类
	 *
	 * Return the containing class for this method parameter.
	 * @return a specific containing class (potentially a subclass of the
	 * declaring class), or otherwise simply the declaring class itself
	 * @see #getDeclaringClass()
	 */
	public Class<?> getContainingClass() {
		// 获取当前包含类
		Class<?> containingClass = this.containingClass;
		// 如果containingClass不为null，返回containingClass;否则返回声明该方法的类
		return (containingClass != null ? containingClass : getDeclaringClass());
	}

	/**
	 * 设置解析的（通用）参数类型
	 *
	 * Set a resolved (generic) parameter type.
	 */
	@Deprecated
	void setParameterType(@Nullable Class<?> parameterType) {
		this.parameterType = parameterType;
	}

	/**
	 * 返回方法或构造函数的当前参数索引参数类型
	 *
	 * Return the type of the method/constructor parameter.
	 * @return the parameter type (never {@code null})
	 */
	public Class<?> getParameterType() {
		// 获取当前参数类型
		Class<?> paramType = this.parameterType;
		// 如果paramType不为null
		if (paramType != null) {
			// 返回当前参数类型
			return paramType;
		}
		if (getContainingClass() != getDeclaringClass()) {

			// 在嵌套级别1为本类对象返回一个ResolvableType对象,将此ResolvableType对象的type属性解析为Class,如果无法解析，则返回nul
			paramType = ResolvableType.forMethodParameter(this, null, 1).resolve();
		}
		// 如果paramType为null
		if (paramType == null) {
			// 计算参数类型，如果当前参数索引小于0，返回method的返回类型；
			// 否则返回executable的参数类型 数组中当前参数索引的参数类型
			paramType = computeParameterType();
		}
		// 设置当前参数类型,缓存起来，避免下次调用该方法时需要再次解析
		this.parameterType = paramType;
		return paramType;
	}

	/**
	 * 返回Method或Constructor的当前参数索引的泛型参数类型
	 *
	 * Return the generic type of the method/constructor parameter.
	 * @return the parameter type (never {@code null})
	 * @since 3.0
	 */
	public Type getGenericParameterType() {
		// 获取当前泛型参数类型
		Type paramType = this.genericParameterType;
		// 如果paramType为null
		if (paramType == null) {
			// 如果当前参数索引小于0
			if (this.parameterIndex < 0) {
				// 获取当前Method对象
				Method method = getMethod();
				// 如果method不为null, 则(如果存在Kotlin反射且声明当前Method对象的类为Kotlin类型,
				// 则得到返回方法的泛型返回类型,通过Kotlin反射支持暂停功能;否则得到一个Type对象，该对象表示此Method
				// 对象表示的方法的正式返回类型);否则为void类型
				paramType = (method != null ?
						(KotlinDetector.isKotlinReflectPresent() && KotlinDetector.isKotlinType(getContainingClass()) ?
						KotlinDelegate.getGenericReturnType(method) : method.getGenericReturnType()) : void.class);
			}
			else {
				// Method.getGenericParameterTypes()方法返回一个Type对象的数组，它以声明顺序表示此
				// Method对象表示的方法的形式参数类型。如果底层方法没有参数，则返回长度为0的数组
				Type[] genericParameterTypes = this.executable.getGenericParameterTypes();
				// 获取当前参数索引
				int index = this.parameterIndex;
				// 如果executable是Constructor的实例且声明executable的类是内部类且genericParameterTypes的长度等于executable的参数数量-1
				if (this.executable instanceof Constructor &&
						ClassUtils.isInnerClass(this.executable.getDeclaringClass()) &&
						genericParameterTypes.length == this.executable.getParameterCount() - 1) {
					// Bug in javac: type array excludes enclosing instance parameter
					// for inner classes with at least one generic constructor parameter,
					// so access it with the actual parameter index lowered by 1
					// JDK<9中的javac bug：注解数组不包含内部类的实例参数，所以以实际参数索引降低1的
					// 的方式进行访问
					index = this.parameterIndex - 1;
				}
				// 如果index属于[0,genericParameterTypes长度)范围内，获取第index个genericParameterTypes元素对象
				// 赋值给paramType；否则计算参数类型，如果当前参数索引小于0，返回method的返回类型；否则返回executable的参数类型
				// 数组中当前参数索引的参数类型
				paramType = (index >= 0 && index < genericParameterTypes.length ?
						genericParameterTypes[index] : computeParameterType());
			}
			// 设置当前泛型参数类型,缓存起来，避免下次调用该方法时需要再次解析
			this.genericParameterType = paramType;
		}
		return paramType;
	}

	/**
	 * 计算参数类型，如果当前参数索引小于0，返回method的返回类型；否则返回executable的参数类型
	 * 数组中当前参数索引的参数类型
	 */
	private Class<?> computeParameterType() {
		// 如果当前参数索引小于0
		if (this.parameterIndex < 0) {
			// 获取当前Method对象
			Method method = getMethod();
			// 如果method为null
			if (method == null) {
				// 返回void
				return void.class;
			}
			// 如果存在Kotlin反射且方法参数的包含类为Kotlin类型(上面带有Kotlin元数据)
			if (KotlinDetector.isKotlinReflectPresent() && KotlinDetector.isKotlinType(getContainingClass())) {
				// 返回method的返回类型,通过Kotlin反射支持暂停功能
				return KotlinDelegate.getReturnType(method);
			}
			// 返回method的返回类型
			return method.getReturnType();
		}
		// 返回executable的参数类型数组中当前参数索引的参数类型
		return this.executable.getParameterTypes()[this.parameterIndex];
	}

	/**
	 * 返回method/constructor参数嵌套类型
	 * Return the nested type of the method/constructor parameter.
	 * @return the parameter type (never {@code null})
	 * @since 3.1
	 * @see #getNestingLevel()
	 */
	public Class<?> getNestedParameterType() {
		// 嵌套级别大于1
		if (this.nestingLevel > 1) {
			// 返回Method或Contructor的泛型参数类型
			Type type = getGenericParameterType();
			// 从2开始遍历传进来的嵌套等级,因为1表示其本身，所以从2开始
			for (int i = 2; i <= this.nestingLevel; i++) {
				// 如果type是ParameterizedType对象
				if (type instanceof ParameterizedType) {
					// ParameterizedType.getActualTypeArgments:获取泛型中的实际类型，可能会存在多个泛型，
					// 例如Map<K,V>,所以会返回Type[]数组；
					Type[] args = ((ParameterizedType) type).getActualTypeArguments();
					// 返回i的类型索引
					Integer index = getTypeIndexForLevel(i);
					// 如果index不为null,获取第index个args元素；否则取最后一个元素
					type = args[index != null ? index : args.length - 1];
				}
				// TODO: Object.class if unresolvable 如果Object不可解析
			}
			// 如果type是Class实例
			if (type instanceof Class) {
				// 强转type为Class对象
				return (Class<?>) type;
			}
			// 如果type是ParameterizedType实例
			else if (type instanceof ParameterizedType) {
				// ParameterizeType.getRowType:返回最外层<>前面那个类型，即Map<K ,V>的Map。
				Type arg = ((ParameterizedType) type).getRawType();
				// 如果arg是Class的子类
				if (arg instanceof Class) {
					// 强转成arg的为Class对象
					return (Class<?>) arg;
				}
			}
			// 所有条件不满足，直接返回Object.class，因为java的所有类需要继承Object
			return Object.class;
		}
		else {
			// 返回方法或构造函数的当前参数索引参数类型
			return getParameterType();
		}
	}

	/**
	 * 返回method/constructor参数的嵌套泛型类型
	 *
	 * Return the nested generic type of the method/constructor parameter.
	 * @return the parameter type (never {@code null})
	 * @since 4.2
	 * @see #getNestingLevel()
	 */
	public Type getNestedGenericParameterType() {
		// 如果当前嵌套等级大于1
		if (this.nestingLevel > 1) {
			// 返回Method或Contructor的当前参数索引的泛型参数类型
			Type type = getGenericParameterType();
			// 从2开始遍历传进来的嵌套等级,因为1表示其本身，所以从2开始
			for (int i = 2; i <= this.nestingLevel; i++) {
				// 如果type是ParameterizedType实例
				if (type instanceof ParameterizedType) {
					// ParameterizedType.getActualTypeArgments:获取泛型中的实际类型，可能会存在多个泛型，
					// 例如Map<K,V>,所以会返回Type[]数组；
					Type[] args = ((ParameterizedType) type).getActualTypeArguments();
					// 返回i的类型索引
					Integer index = getTypeIndexForLevel(i);
					// 如果index不为null,获取第index个args元素；否则取最后一个元素
					type = args[index != null ? index : args.length - 1];
				}
			}
			// 返回当前参数索引的泛型参数类型的嵌套泛型类型
			return type;
		} else {
			// 返回Method或Contructor的当前参数索引的泛型参数类型
			return getGenericParameterType();
		}
	}
	/**
	 * 返回与目标method或constructor本身关联的经过后处理的注解
	 * Return the annotations associated with the target method/constructor itself.
	 */
	public Annotation[] getMethodAnnotations() {
		// getAnnotatedElement:返回包装的注解元素,
		// AnnotatedElement.getAnnotations:获取method或constructor的注解数组
		// 将获取到注解数组返回给调用方之前对他进行后处理的模板方法,默认实例简单照原样返回给定的注解数组
		return adaptAnnotationArray(getAnnotatedElement().getAnnotations());
	}

	/**
	 * 返回给定类型的method/constructor注解,(如果有)
	 *
	 * Return the method/constructor annotation of the given type, if available.
	 * @param annotationType the annotation type to look for
	 * @return the annotation object, or {@code null} if not found
	 */
	@Nullable
	public <A extends Annotation> A getMethodAnnotation(Class<A> annotationType) {
		// 获取包装的注解元素,从其中获取annotationType对应的注解对象
		A annotation = getAnnotatedElement().getAnnotation(annotationType);
		// 如果annotation不为null,
		return (annotation != null ? adaptAnnotation(annotation) : null);
	}

	/**
	 * 返回method/constructor是否使用给定类型进行注释
	 * Return whether the method/constructor is annotated with the given type.
	 * @param annotationType the annotation type to look for
	 * @since 4.3
	 * @see #getMethodAnnotation(Class)
	 */
	public <A extends Annotation> boolean hasMethodAnnotation(Class<A> annotationType) {
		// 获取包装的注解元素，从中查找annotationType类型的注解对象，并返回出去
		return getAnnotatedElement().isAnnotationPresent(annotationType);
	}

	/**
	 * 返回与指定(当前)method/constructor参数关联的注解数组
	 *
	 * Return the annotations associated with the specific method/constructor parameter.
	 */
	public Annotation[] getParameterAnnotations() {
		// 获取当前参数注解数组
		Annotation[] paramAnns = this.parameterAnnotations;
		// 如果paramAnns为null
		if (paramAnns == null) {
			// 从方法或构造函数中获取参数注解
			Annotation[][] annotationArray = this.executable.getParameterAnnotations();
			// 获取当前参数索引
			int index = this.parameterIndex;
			// 如果executable是构造函数且声明executable的类不是内部类且参数注解数组长度等于executable的参数数量-1
			if (this.executable instanceof Constructor &&
					ClassUtils.isInnerClass(this.executable.getDeclaringClass()) &&
					annotationArray.length == this.executable.getParameterCount() - 1) {
				// Bug in javac in JDK <9: annotation array excludes enclosing instance parameter
				// for inner classes, so access it with the actual parameter index lowered by 1
				// JDK<9中的javac bug：注解数组不包含内部类的实例参数，所以以实际参数索引降低1的
				// 的方式进行访问
				index = this.parameterIndex - 1;
			}
			// 如果index属于[0,annotationArray长度)范围内，就将annotationArray[index]的注解数组
			// 交给adaptAnnotationArray方法进行处理后返回，adaptAnnotationArray方法是给模板方法，
			// 可用于供给子类重写，本类的adaptAnnotationArray方法实现是个空方法，直接返回annotationArray[index]
			// 不作任何处理；否则返回空注解数组
			paramAnns = (index >= 0 && index < annotationArray.length ?
					adaptAnnotationArray(annotationArray[index]) : EMPTY_ANNOTATION_ARRAY);
			// 设置当前参数的注解数组
			this.parameterAnnotations = paramAnns;
		}
		// 返回当前参数的注解数组
		return paramAnns;
	}

	/**
	 * 如果参数至少有一个注解，返回ture;如果没有，返回false
	 *
	 * Return {@code true} if the parameter has at least one annotation,
	 * {@code false} if it has none.
	 * @see #getParameterAnnotations()
	 */
	public boolean hasParameterAnnotations() {
		// 遍历返回与指定(当前)method/constructor参数关联的注解数组，如果长度不为0,表示至少
		// 有一个参数，返回true；否则返回false
		return (getParameterAnnotations().length != 0);
	}

	/**
	 * 返回给定类型的参数注解,如果可用
	 *
	 * Return the parameter annotation of the given type, if available.
	 * @param annotationType the annotation type to look for
	 * @return the annotation object, or {@code null} if not found
	 */
	@SuppressWarnings("unchecked")
	@Nullable
	public <A extends Annotation> A getParameterAnnotation(Class<A> annotationType) {
		// 遍历返回与指定(当前)method/constructor参数关联的注解数组
		Annotation[] anns = getParameterAnnotations();
		// 遍历注解数组
		for (Annotation ann : anns) {
			// 如果annotationType的ann的实例
			if (annotationType.isInstance(ann)) {
				// 返回ann,并强转成传进来的注解类型
				return (A) ann;
			}
		}
		// 返回null，表示没有找到
		return null;
	}

	/**
	 * 返回参数是否具有给定的注解类型声明
	 *
	 * Return whether the parameter is declared with the given annotation type.
	 * @param annotationType the annotation type to look for
	 * @see #getParameterAnnotation(Class)
	 */
	public <A extends Annotation> boolean hasParameterAnnotation(Class<A> annotationType) {
		// 获取annotationType的参数注解,如果不为null，表示具有给定的注解类型声明，返回true；否则为false
		return (getParameterAnnotation(annotationType) != null);
	}

	/**
	 * 初始化此方法参数的参数名称发现器
	 *
	 * Initialize parameter name discovery for this method parameter.
	 * <p>This method does not actually try to retrieve the parameter name at
	 * this point; it just allows discovery to happen when the application calls
	 * {@link #getParameterName()} (if ever).
	 */
	public void initParameterNameDiscovery(@Nullable ParameterNameDiscoverer parameterNameDiscoverer) {
		this.parameterNameDiscoverer = parameterNameDiscoverer;
	}

	/**
	 * 返回method/constructor参数的名称
	 *
	 * Return the name of the method/constructor parameter.
	 * @return the parameter name (may be {@code null} if no
	 * parameter name metadata is contained in the class file or no
	 * {@link #initParameterNameDiscovery ParameterNameDiscoverer}
	 * has been set to begin with)
	 */
	@Nullable
	public String getParameterName() {
		// 如果当前参数索引小于0
		if (this.parameterIndex < 0) {
			// 返回null
			return null;
		}
		// 获取当前参数名发现器
		ParameterNameDiscoverer discoverer = this.parameterNameDiscoverer;
		// 如果发现器不为null
		if (discoverer != null) {
			String[] parameterNames = null;
			// 如果executable是Method的实例
			if (this.executable instanceof Method) {
				// 获取方法的参数名，如果不能确定就返回null,如果参数名称仅可用于给定方法的某些参数，
				// 而不适用于其他参数，则数组的各个条目 可能为null.但是，建议在可行的地方使用存根参数名名代替。
				parameterNames = discoverer.getParameterNames((Method) this.executable);
			}
			// 如果executable是Constructor的实例
			else if (this.executable instanceof Constructor) {
				// 获取方法的参数名，如果不能确定就返回null,如果参数名称仅可用于给定方法的某些参数，
				// 而不适用于其他参数，则数组的各个条目 可能为null.但是，建议在可行的地方使用存根参数名名代替。
				parameterNames = discoverer.getParameterNames((Constructor<?>) this.executable);
			}
			// 如果参数名数组不为null
			if (parameterNames != null) {
				// 取出当前参数索引在parameterNames中对应的参数名作为当前参数名
				this.parameterName = parameterNames[this.parameterIndex];
			}
			// 将参数名发现器设置为null，因为已经解析完并成功获取了参数名，参数名发现器就不再起作用，
			// 将其置null，能让GC回收该对象
			this.parameterNameDiscoverer = null;
		}
		// 返回当前参数名
		return this.parameterName;
	}


	/**
	 * 将给定的注解返回给调用方之前对他进行后处理的模板方法
	 *
	 * A template method to post-process a given annotation instance before
	 * returning it to the caller.
	 * <p>The default implementation simply returns the given annotation as-is.
	 * @param annotation the annotation about to be returned
	 * @return the post-processed annotation (or simply the original one)
	 * @since 4.2
	 */
	protected <A extends Annotation> A adaptAnnotation(A annotation) {
		return annotation;
	}

	/**
	 * 将给定的注解数组返回给调用方之前对他进行后处理的模板方法
	 *
	 * A template method to post-process a given annotation array before
	 * returning it to the caller.
	 * <p>The default implementation simply returns the given annotation array as-is.
	 * @param annotations the annotation array about to be returned
	 * @return the post-processed annotation array (or simply the original one)
	 * @since 4.2
	 */
	protected Annotation[] adaptAnnotationArray(Annotation[] annotations) {
		return annotations;
	}


	@Override
	public boolean equals(@Nullable Object other) {
		// 如果本类对象地址与other相等
		if (this == other) {
			// 返回true，表示相等
			return true;
		}
		// 如果other是MethodParameter的实例
		if (!(other instanceof MethodParameter)) {
			// 返回false，表示不相等
			return false;
		}
		// 强转other为MethodParameter对象
		MethodParameter otherParam = (MethodParameter) other;
		// 如果当前容器类与otherParam的容器类是同一个 且 当前整数嵌套等级到整数类型索引的映射
		// 与otherParam的otherParam从整数嵌套等级到整数类型索引的映射 相等 且 当前嵌套等级与otherParam的嵌套等级相等
		// 且 当前参数索引与otherParam的参数索引相等 且 当前Method或构造函数对象与otherParam的Method或构造函数对象相等则返回true，表示相等
		return (getContainingClass() == otherParam.getContainingClass() &&
				ObjectUtils.nullSafeEquals(this.typeIndexesPerLevel, otherParam.typeIndexesPerLevel) &&
				this.nestingLevel == otherParam.nestingLevel &&
				this.parameterIndex == otherParam.parameterIndex &&
				this.executable.equals(otherParam.executable));
	}

	@Override
	public int hashCode() {
		// 取当前Method或构造函数对象加上当前参数索引计算出本类对象的哈希值
		return (31 * this.executable.hashCode() + this.parameterIndex);
	}

	@Override
	public String toString() {
		// 获取当前Method对象
		Method method = getMethod();
		// 如果method不为null,拼装方法名；否则为'constructor',再拼装当前参数索引
		return (method != null ? "method '" + method.getName() + "'" : "constructor") +
				" parameter " + this.parameterIndex;
	}

	/**
	 * 克隆本类对象
	 * @return
	 */
	@Override
	public MethodParameter clone() {
		// 赋值构造函数，根据与原始对象相同的元数据和缓存状态，生成一个独立的MethodParameter对象
		return new MethodParameter(this);
	}

	/**
	 * 为给定的method或者constructor创建一个新的MethodParameter对象
	 * Create a new MethodParameter for the given method or constructor.
	 * <p>This is a convenience factory method for scenarios where a
	 * Method or Constructor reference is treated in a generic fashion.
	 * @param methodOrConstructor the Method or Constructor to specify a parameter for
	 * @param parameterIndex the index of the parameter
	 * @return the corresponding MethodParameter instance
	 * @deprecated as of 5.0, in favor of {@link #forExecutable}
	 */
	@Deprecated
	public static MethodParameter forMethodOrConstructor(Object methodOrConstructor, int parameterIndex) {
		// 如果methodOrConstructor不是Executable的实例
		if (!(methodOrConstructor instanceof Executable)) {
			// 抛出非法异常，异常信息：给定的对象[methodOrConstructor]既不是Method又不是Constructor
			throw new IllegalArgumentException(
					"Given object [" + methodOrConstructor + "] is neither a Method nor a Constructor");
		}
		// 为methodOrConstructor创建一个新的MethodParameter对象
		return forExecutable((Executable) methodOrConstructor, parameterIndex);
	}

	/**
	 * 为给定的method或者constructor创建一个新的MethodParameter对象
	 *
	 * Create a new MethodParameter for the given method or constructor.
	 * <p>This is a convenience factory method for scenarios where a
	 * Method or Constructor reference is treated in a generic fashion.
	 * @param executable the Method or Constructor to specify a parameter for
	 * @param parameterIndex the index of the parameter
	 * @return the corresponding MethodParameter instance
	 * @since 5.0
	 */
	public static MethodParameter forExecutable(Executable executable, int parameterIndex) {
		// 如果executable是Method实例
		if (executable instanceof Method) {
			// 强转executable为Method对象,使用嵌套级别1为Method对象创建一个新的MethodParameter
			return new MethodParameter((Method) executable, parameterIndex);
		}
		// 如果executable是Constructor实例
		else if (executable instanceof Constructor) {
			// 强转executable为Constructor对象,使用嵌套级别1为Constructor对象创建一个新的MethodParameter
			return new MethodParameter((Constructor<?>) executable, parameterIndex);
		}
		else {
			// 抛出非法参数异常，异常信息：不是Method/Constructor:executable
			throw new IllegalArgumentException("Not a Method/Constructor: " + executable);
		}
	}

	/**
	 * 为给定的参数描述符创建一个新的MethodParameter对象
	 *
	 * Create a new MethodParameter for the given parameter descriptor.
	 * <p>This is a convenience factory method for scenarios where a
	 * Java 8 {@link Parameter} descriptor is already available.
	 * @param parameter the parameter descriptor
	 * @return the corresponding MethodParameter instance
	 * @since 5.0
	 */
	public static MethodParameter forParameter(Parameter parameter) {
		return forExecutable(parameter.getDeclaringExecutable(), findParameterIndex(parameter));
	}

	/**
	 * 找出参数索引
	 * @param parameter
	 * @return
	 */
	protected static int findParameterIndex(Parameter parameter) {
		// 获取parameter的声明方法
		Executable executable = parameter.getDeclaringExecutable();
		// 获取方法的参数数组
		Parameter[] allParams = executable.getParameters();
		// Try first with identity checks for greater performance.
		// 遍历方法的参数数组
		for (int i = 0; i < allParams.length; i++) {
			// 如果parameter与第i个allParams的Parameter对象相同
			if (parameter == allParams[i]) {
				// 返回i，表示该parameter对应的参数索引
				return i;
			}
		}
		// Potentially try again with object equality checks in order to avoid race
		// conditions while invoking java.lang.reflect.Executable.getParameters().
		// 可能在调用java.lang.reflect.Executable.getParameters().时再次尝试相等性检查,
		// 以避免出现竞态条件
		// 竞态:指多线程情况下计算的正确性依赖于相对时间顺序或线程的交错;
		// 遍历方法的参数数组
		for (int i = 0; i < allParams.length; i++) {
			// 如果parameter与第i个allParams的Parameter对象相同
			if (parameter.equals(allParams[i])) {
				// 返回i，表示该parameter对应的参数索引
				return i;
			}
		}
		// 抛出非法参数异常，异常信息：给定参数[parameter]再声明的方法中不匹配任何参数
		throw new IllegalArgumentException("Given parameter [" + parameter +
				"] does not match any parameter in the declaring executable");
	}

	/**
	 * 验证参数索引位置
	 * @param executable
	 * @param parameterIndex
	 * @return
	 */
	private static int validateIndex(Executable executable, int parameterIndex) {
		// 获取方法的参数数量
		int count = executable.getParameterCount();
		// 如果parameterIndex不在[-1,count)范围内，抛出IllegalArgumentException
		Assert.isTrue(parameterIndex >= -1 && parameterIndex < count,
				() -> "Parameter index needs to be between -1 and " + (count - 1));
		// 返回通过检查后的parameterIndex
		return parameterIndex;
	}


	/**
	 * 内部类，以避免在运行时严重依赖Kotlin
	 *
	 * Inner class to avoid a hard dependency on Kotlin at runtime.
	 */
	private static class KotlinDelegate {

		/**
		 * 检查给定的{@link MethodParameter}是否表示nullable Kotlin 类型 或者 optional 参数(在Kontlin声明中具有默认值)
		 *
		 * Check whether the specified {@link MethodParameter} represents a nullable Kotlin type,
		 * an optional parameter (with a default value in the Kotlin declaration) or a
		 * {@code Continuation} parameter used in suspending functions.
		 */
		public static boolean isOptional(MethodParameter param) {
			// 获取param的Method对象
			Method method = param.getMethod();
			// 获取param的当前参数索引
			int index = param.getParameterIndex();
			// 如果method不为null且index等于 -1
			if (method != null && index == -1) {
				// 获取与method相应的KFunction实例；如果此方法不能由Kotlin函数表示，则返回null
				KFunction<?> function = ReflectJvmMapping.getKotlinFunction(method);
				// 如果function不为null且function的返回类型在源代码中标记为nullable ,则返回true
				return (function != null && function.getReturnType().isMarkedNullable());
			}
			KFunction<?> function;
			// Predicate接口主要用来判断一个参数是否符合要求,类似于Junit的assert.
			Predicate<KParameter> predicate;
			// 如果method不为null
			if (method != null) {
				if (param.getParameterType().getName().equals("kotlin.coroutines.Continuation")) {
					return true;
				}
				// 获取与method相应的KFunction实例；如果此方法不能由Kotlin函数表示，则返回null
				function = ReflectJvmMapping.getKotlinFunction(method);
				// KParameter.Kind.VALUE表示普通命名参数
				// 设置判断条件为：如果p的参数种类是普通命名参数
				predicate = p -> KParameter.Kind.VALUE.equals(p.getKind());
			}
			else {
				// 获取构造器
				Constructor<?> ctor = param.getConstructor();
				Assert.state(ctor != null, "Neither method nor constructor found");
				// 获取与method相应的KFunction实例；如果此方法不能由Kotlin函数表示，则返回null
				function = ReflectJvmMapping.getKotlinFunction(ctor);
				// KParameter.Kind.INSTANCE表示调用成员需的实例，或者内部类构造函数的外部类实例
				// 设置判断条件为：如果p的参数种类是普通命名参数 或者是调用成员需的实例，或者内部类构造函数的外部类实例
				predicate = p -> (KParameter.Kind.VALUE.equals(p.getKind()) ||
						KParameter.Kind.INSTANCE.equals(p.getKind()));
			}
			// 如果function不为null
			if (function != null) {
				int i = 0;
				// 调用此可调用项所需要的参数，如果此可调用项需要this实例或者扩展接收者参数，则他们
				// 以该顺序在列表中排在第一位
				for (KParameter kParameter : function.getParameters()) {
					// 收集parameters中符合predicate条件的KParameter对象到List,然后获取第index个KParameter对象
					if (predicate.test(kParameter)) {
						if (index == i++) {
							// 如果参数类型在源码中标记为可空且参数类型是可选的,并且通过KCallable.calBy进行调用时可以省略
							return (kParameter.getType().isMarkedNullable() || kParameter.isOptional());
						}
					}
				}
			}
			return false;
		}

		/**
		 * 返回方法的泛型返回类型,通过Kotlin反射支持暂停功能
		 *
		 * Return the generic return type of the method, with support of suspending
		 * functions via Kotlin reflection.
		 */
		static private Type getGenericReturnType(Method method) {
			try {
				// 获取与method相应的KFunction实例；如果此方法不能由Kotlin函数表示，则返回null
				KFunction<?> function = ReflectJvmMapping.getKotlinFunction(method);
				// 如果function不为null且function是挂起
				if (function != null && function.isSuspend()) {
					// 获取与给定Kotlin类型相对应的Java Type实例。请注意，根据出现的位置，一种
					// Kotlin类型可能对应于不同的JVM类型。例如,当Unit是参数的类型时,它对应于
					// JVM类Unit；当它是函数的返回类型时，它对应于void
					return ReflectJvmMapping.getJavaType(function.getReturnType());
				}
			}
			catch (UnsupportedOperationException ex) {
				// probably a synthetic class - let's use java reflection instead
			}
			// 返回一个Type对象，该对象表示此Method对象表示的方法的正式返回类型。
			return method.getGenericReturnType();
		}

		/**
		 * 返回method的返回类型,通过Kotlin反射支持暂停功能
		 *
		 * Return the return type of the method, with support of suspending
		 * functions via Kotlin reflection.
		 */
		static private Class<?> getReturnType(Method method) {
			try {
				// 获取与method相应的KFunction实例；如果此方法不能由Kotlin函数表示，则返回null
				KFunction<?> function = ReflectJvmMapping.getKotlinFunction(method);
				// 如果function不为null且function是挂起
				if (function != null && function.isSuspend()) {
					// 获取与给定Kotlin类型相对应的Java Type实例。请注意，根据出现的位置，一种
					// Kotlin类型可能对应于不同的JVM类型。例如,当Unit是参数的类型时,它对应于
					// JVM类Unit；当它是函数的返回类型时，它对应于void
					Type paramType = ReflectJvmMapping.getJavaType(function.getReturnType());
					if (paramType == Unit.class) {
						paramType = void.class;
					}
					// 获取paramType的ResolvableType对象，将此类型解析为Class，如果无法解析该类型，
					// 则返回method的返回类型 如果直接解析失败，则此方法考虑和WildcardTypes的bounds，
					// 但是Object.class的bounds将被忽略
					return ResolvableType.forType(paramType).resolve(method.getReturnType());
				}
			}
			catch (UnsupportedOperationException ex) {
				// probably a synthetic class - let's use java reflection instead
			}
			// 返回method的返回类型
			return method.getReturnType();
		}
	}

}
