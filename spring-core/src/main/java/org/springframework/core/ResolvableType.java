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

import java.io.Serializable;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Arrays;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.StringJoiner;

import org.springframework.core.SerializableTypeWrapper.FieldTypeProvider;
import org.springframework.core.SerializableTypeWrapper.MethodParameterTypeProvider;
import org.springframework.core.SerializableTypeWrapper.TypeProvider;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ConcurrentReferenceHashMap;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

/**
 * 可以看作是封装JavaType的元信息类
 *
 * Encapsulates a Java {@link java.lang.reflect.Type}, providing access to
 * {@link #getSuperType() supertypes}, {@link #getInterfaces() interfaces}, and
 * {@link #getGeneric(int...) generic parameters} along with the ability to ultimately
 * {@link #resolve() resolve} to a {@link java.lang.Class}.
 *
 * <p>{@code ResolvableTypes} may be obtained from {@link #forField(Field) fields},
 * {@link #forMethodParameter(Method, int) method parameters},
 * {@link #forMethodReturnType(Method) method returns} or
 * {@link #forClass(Class) classes}. Most methods on this class will themselves return
 * {@link ResolvableType ResolvableTypes}, allowing easy navigation. For example:
 * <pre class="code">
 * private HashMap&lt;Integer, List&lt;String&gt;&gt; myMap;
 *
 * public void example() {
 *     ResolvableType t = ResolvableType.forField(getClass().getDeclaredField("myMap"));
 *     t.getSuperType(); // AbstractMap&lt;Integer, List&lt;String&gt;&gt;
 *     t.asMap(); // Map&lt;Integer, List&lt;String&gt;&gt;
 *     t.getGeneric(0).resolve(); // Integer
 *     t.getGeneric(1).resolve(); // List
 *     t.getGeneric(1); // List&lt;String&gt;
 *     t.resolveGeneric(1, 0); // String
 * }
 * </pre>
 *
 * @author Phillip Webb
 * @author Juergen Hoeller
 * @author Stephane Nicoll
 * @since 4.0
 * @see #forField(Field)
 * @see #forMethodParameter(Method, int)
 * @see #forMethodReturnType(Method)
 * @see #forConstructorParameter(Constructor, int)
 * @see #forClass(Class)
 * @see #forType(Type)
 * @see #forInstance(Object)
 * @see ResolvableTypeProvider
 */
@SuppressWarnings("serial")
public class ResolvableType implements Serializable {

	/**
	 * 如果没有可用的值，就返回 {@code ResolvableType} 。{@code NONE}比{@code null}更好，以便多个方法可以被安全的链条时调用
	 *
	 * {@code ResolvableType} returned when no value is available. {@code NONE} is used
	 * in preference to {@code null} so that multiple method calls can be safely chained.
	 */
	public static final ResolvableType NONE = new ResolvableType(EmptyType.INSTANCE, null, null, 0);

	/**
	 * 空类型数组
	 */
	private static final ResolvableType[] EMPTY_TYPES_ARRAY = new ResolvableType[0];

	// ResolvableType对象映射缓存缓存
	private static final ConcurrentReferenceHashMap<ResolvableType, ResolvableType> cache =
			new ConcurrentReferenceHashMap<>(256);


	/**
	 * 被管理的底层类型
	 * The underlying Java type being managed.
	 */
	private final Type type;

	/**
	 * 类型的可选提供提供者
	 * Optional provider for the type.
	 */
	@Nullable
	private final TypeProvider typeProvider;

	/**
	 * 要使用的{@code VariableResolve}或如果没有可用的解析器则为{@code null}
	 * The {@code VariableResolver} to use or {@code null} if no resolver is available.
	 */
	@Nullable
	private final VariableResolver variableResolver;

	/**
	 * 数组的元素类型；如果应该推到类型，则为{@code null}
	 * The component type for an array or {@code null} if the type should be deduced.
	 */
	@Nullable
	private final ResolvableType componentType;

	// 本类的哈希值
	@Nullable
	private final Integer hash;

	// 将{@link #type}解析成Class对象
	@Nullable
	private Class<?> resolved;

	// 表示{@link #resolved}的父类的ResolvableType对象
	@Nullable
	private volatile ResolvableType superType;

	// 表示{@link #type} 的所有接口的ResolvableType数组
	@Nullable
	private volatile ResolvableType[] interfaces;

	// 表示{@link #type}的所有泛型参数类型的ResolvableType数组
	@Nullable
	private volatile ResolvableType[] generics;


	/**
	 * 私有构造函数，用于创建新的{@link ResolvableType}以用于缓存密钥，无需预先解析
	 *
	 * Private constructor used to create a new {@link ResolvableType} for cache key purposes,
	 * with no upfront resolution.
	 */
	private ResolvableType(
			Type type, @Nullable TypeProvider typeProvider, @Nullable VariableResolver variableResolver) {

		this.type = type;
		this.typeProvider = typeProvider;
		this.variableResolver = variableResolver;
		this.componentType = null;
		this.hash = calculateHashCode();
		this.resolved = null;
	}

	/**
	 * 使用构造函数，用于创建新的{@link ResolvableType}以用于缓存密钥，无需预先解析或者预计算哈希值
	 *
	 * Private constructor used to create a new {@link ResolvableType} for cache value purposes,
	 * with upfront resolution and a pre-calculated hash.
	 * @since 4.2
	 */
	private ResolvableType(Type type, @Nullable TypeProvider typeProvider,
			@Nullable VariableResolver variableResolver, @Nullable Integer hash) {

		this.type = type;
		this.typeProvider = typeProvider;
		this.variableResolver = variableResolver;
		this.componentType = null;
		this.hash = hash;
		// 将type解析成Class赋值给resolved
		this.resolved = resolveClass();
	}

	/**
	 *  私有构造函数，用于创建一个新的{@link ResolvableType}用于未缓存目标，具有前期解析方案，但是以懒汉式形式计算哈希值
	 *
	 * Private constructor used to create a new {@link ResolvableType} for uncached purposes,
	 * with upfront resolution but lazily calculated hash.
	 */
	private ResolvableType(Type type, @Nullable TypeProvider typeProvider,
			@Nullable VariableResolver variableResolver, @Nullable ResolvableType componentType) {

		this.type = type;
		this.typeProvider = typeProvider;
		this.variableResolver = variableResolver;
		this.componentType = componentType;
		this.hash = null;
		this.resolved = resolveClass();
	}

	/**
	 * 私有构造函数，用于在{@link Class}的基础上创建新的{@link ResolvableType}。避免为了创建直接的{@link Class}包装类而进行所有{@code instanceof}检查
	 *
	 * Private constructor used to create a new {@link ResolvableType} on a {@link Class} basis.
	 * Avoids all {@code instanceof} checks in order to create a straight {@link Class} wrapper.
	 * @since 4.2
	 */
	private ResolvableType(@Nullable Class<?> clazz) {
		this.resolved = (clazz != null ? clazz : Object.class);
		this.type = this.resolved;
		this.typeProvider = null;
		this.variableResolver = null;
		this.componentType = null;
		this.hash = null;
	}


	/**
	 * 返回受管理的基础Java{@link Type}
	 *
	 * Return the underling Java {@link Type} being managed.
	 */
	public Type getType() {
		return SerializableTypeWrapper.unwrap(this.type);
	}

	/**
	 * 返回受管理的基础Java{@link Class}(如果有)；否则返回{@ocde null}
	 *
	 * Return the underlying Java {@link Class} being managed, if available;
	 * otherwise {@code null}.
	 */
	@Nullable
	public Class<?> getRawClass() {
		// 如果已解析类型等于受管理的基础Java{@link Type}
		if (this.type == this.resolved) {
			// 返回已解析类型
			return this.resolved;
		}
		// 将type作为原始类型
		Type rawType = this.type;
		// ParameterizedType:具有<>符号的变量
		// 如果rawType是ParameterizedType的子类或本身
		if (rawType instanceof ParameterizedType) {
			// ParameterizeType.getRowType:返回最外层<>前面那个类型，即Map<K ,V>的Map。
			rawType = ((ParameterizedType) rawType).getRawType();
		}
		// 如果rawType是Class的子类或本身，就将rawType强转为Class对象并返回，否则返回null
		return (rawType instanceof Class ? (Class<?>) rawType : null);
	}

	/**
	 * 返回可解析类型的基础源。将返回{@link Field},{@link MethodParameter}或者{@link Type}具体取决于{@link ResolvableType}的构造方式。
	 * 除了{@link #NONE}常量外，这个方法将永远不会返回{@code null}.此方法主要用于提供对其他类型信息或替代JVM语言可能提供的元数据的访问
	 *
	 * Return the underlying source of the resolvable type. Will return a {@link Field},
	 * {@link MethodParameter} or {@link Type} depending on how the {@link ResolvableType}
	 * was constructed. With the exception of the {@link #NONE} constant, this method will
	 * never return {@code null}. This method is primarily to provide access to additional
	 * type information or meta-data that alternative JVM languages may provide.
	 */
	public Object getSource() {
		// 如果类型的可选提供者对象不为null，就获取类型的可选提供者对象的source属性值，否则返回null
		Object source = (this.typeProvider != null ? this.typeProvider.getSource() : null);
		// 如果source不为null，返回source；否则返回type属性值
		return (source != null ? source : this.type);
	}

	/**
	 * 返回此类型作为解析的{@code Class},如果没有特定的类可以解析，则返回{@link java.lang.Object}
	 *
	 * Return this type as a resolved {@code Class}, falling back to
	 * {@link java.lang.Object} if no specific class can be resolved.
	 * @return the resolved {@link Class} or the {@code Object} fallback
	 * @since 5.1
	 * @see #getRawClass()
	 * @see #resolve(Class)
	 */
	public Class<?> toClass() {
		return resolve(Object.class);
	}

	/**
	 * 确定给定的对象是否是此{@code ResolvableType}的实例
	 *
	 * Determine whether the given object is an instance of this {@code ResolvableType}.
	 * @param obj the object to check
	 * @since 4.2
	 * @see #isAssignableFrom(Class)
	 */
	public boolean isInstance(@Nullable Object obj) {
		// 如果obj不为null且obj是本类的子类或本身
		return (obj != null && isAssignableFrom(obj.getClass()));
	}

	/**
	 * 确定是否可以从指定的其他类型分配此{@code ResolvableType}
	 *
	 * Determine whether this {@code ResolvableType} is assignable from the
	 * specified other type.
	 * @param other the type to be checked against (as a {@code Class})
	 * @since 4.2
	 * @see #isAssignableFrom(ResolvableType)
	 */
	public boolean isAssignableFrom(Class<?> other) {
		return isAssignableFrom(forClass(other), null);
	}

	/**
	 * 确定是否可以从指定的其他类型分配此{@code ResolvableType}
	 *
	 * Determine whether this {@code ResolvableType} is assignable from the
	 * specified other type.
	 * <p>Attempts to follow the same rules as the Java compiler, considering
	 * whether both the {@link #resolve() resolved} {@code Class} is
	 * {@link Class#isAssignableFrom(Class) assignable from} the given type
	 * as well as whether all {@link #getGenerics() generics} are assignable.
	 * @param other the type to be checked against (as a {@code ResolvableType})
	 * @return {@code true} if the specified other type can be assigned to this
	 * {@code ResolvableType}; {@code false} otherwise
	 */
	public boolean isAssignableFrom(ResolvableType other) {
		return isAssignableFrom(other, null);
	}

	/**
	 * 确定是否可以从指定的其他类型分配此{@code ResolvableType}
	 * @param other
	 * @param matchedBefore
	 * @return
	 */
	private boolean isAssignableFrom(ResolvableType other, @Nullable Map<Type, Type> matchedBefore) {
		Assert.notNull(other, "ResolvableType must not be null");

		// If we cannot resolve types, we are not assignable
		// 如果无法解析类型，则无法分配
		// 如果本对象为NONE，或者另一个ResolvableType对象为NONE
		if (this == NONE || other == NONE) {
			// 返回false，表示不能分配
			return false;
		}

		// Deal with array by delegating to the component type
		// 通过委派组件类型来处理数组
		// 如果是本类对象是数组类型
		if (isArray()) {
			// 如果other是数组类型，且 本类对象获取的组件类型是other的组件类型或本身，就返回true，表示能分配；否则返回false，表示不能分配
			return (other.isArray() && getComponentType().isAssignableFrom(other.getComponentType()));
		}

		// 如果matchcedBefore不为null且从matchedBefore获取本类对象的type属性值对应的ResolvableType对象
		// 等于other的type属性时
		if (matchedBefore != null && matchedBefore.get(this.type) == other.type) {
			// 返回true，表示可分配
			return true;
		}

		// Deal with wildcard bounds 处理通配符范围
		// WildcardBounds:用于处理WildcardTypes的范围
		// 获取本类对象的ResolvableType.WildcardBounds实例
		WildcardBounds ourBounds = WildcardBounds.get(this);
		// 获取other的ResolvableType.WildcardBounds实例
		WildcardBounds typeBounds = WildcardBounds.get(other);

		// In the form X is assignable to <? extends Number>
		// X的形式可分配给 <? extends Number>
		// 如果typeBounds不为null
		if (typeBounds != null) {
			// (如果ourBounds不为null且ourBounds与typeBound的界限相同) 且（ourBound是可分配
			// typeBounds的界限，返回true,表示本类对象可分配给other；否则返回false，表示本类对象不可分配给other
			return (ourBounds != null && ourBounds.isSameKind(typeBounds) &&
					ourBounds.isAssignableFrom(typeBounds.getBounds()));
		}

		// In the form <? extends Number> is assignable to X...
		// 以<? extends Number>可分配给X
		// 如果ourBounds不为null
		if (ourBounds != null) {
			// 如果ourBounds可分配给other,返回true；否则返回false
			return ourBounds.isAssignableFrom(other);
		}

		// Main assignability check about to follow
		// 要遵的主要可分配性检查
		// 如果mathedBefore不为null，完成匹配标记设置为true；否则为false
		boolean exactMatch = (matchedBefore != null);  // We're checking nested generic variables now...
		// 初始化检查泛型标记为true
		boolean checkGenerics = true;
		// 初始化本类对象的已解析类为null
		Class<?> ourResolved = null;
		// 如果本类对象被管理的底层类型是TypeVariable的子类或本类
		if (this.type instanceof TypeVariable) {
			// 将type强转为TypeVariable对象
			TypeVariable<?> variable = (TypeVariable<?>) this.type;
			// Try default variable resolution 尝试默认遍历解析
			// 如果遍历解析器不为null
			if (this.variableResolver != null) {
				// 使用变量解析解析variable得到ResolvableType对象
				ResolvableType resolved = this.variableResolver.resolveVariable(variable);
				// 如果resolved对象不为null
				if (resolved != null) {
					// 获取resolved的type属性值解析为Class对象并设置到ourResolved
					ourResolved = resolved.resolve();
				}
			}
			// 如果ourResolved为null
			if (ourResolved == null) {
				// Try variable resolution against target type 尝试针对目标类型进行变量解析
				// 如果other的变量解析器不为null
				if (other.variableResolver != null) {
					// 通过other的遍历解析器解析variable得到ResolvableType对象
					ResolvableType resolved = other.variableResolver.resolveVariable(variable);
					// 如果resolved不为null
					if (resolved != null) {
						// 获取resolved的type属性值解析为Class对象并设置到ourResolved
						ourResolved = resolved.resolve();
						// 设置检查泛型标记为false
						checkGenerics = false;
					}
				}
			}
			// 如果ourResolved为null
			if (ourResolved == null) {
				// Unresolved type variable, potentially nested -> never insist on exact match
				// 未解析的类型变量，可能嵌套 -> 从不坚持完全匹配
				// 设置完成匹配标记为false
				exactMatch = false;
			}
		}
		// 如果ourResolved为null
		if (ourResolved == null) {
			// 将本类对象的type属性值解析为Class,如果无法解析该类型，则返回Object.Class
			ourResolved = resolve(Object.class);
		}
		// 获取other的type属性值作为解析的Class,如果没有特定的类可以解析，则返回 Object
		Class<?> otherResolved = other.toClass();

		// We need an exact type match for generics
		// List<CharSequence> is not assignable from List<String>
		// 如果需要完全匹配，就判断ourResolved等于otherResolved对象 ；否则判断是否可以将otherResolved分配给ourResolved，
		// 假设可以通过反射进行设置。将原始包装类视为可分配给相应的原始类型。
		if (exactMatch ? !ourResolved.equals(otherResolved) : !ClassUtils.isAssignable(ourResolved, otherResolved)) {
			// 返回false，表示ourResolved不可分配给otherResolved
			return false;
		}

		// 如果需要检查泛型
		if (checkGenerics) {
			// Recursively check each generic
			// 获取表现本类对象的泛型参数的ResolvableTypes数组。
			ResolvableType[] ourGenerics = getGenerics();
			// 将other作为ourResoulved的ResolvableType对象，获取其表示泛型参数的ResolvableType数组
			ResolvableType[] typeGenerics = other.as(ourResolved).getGenerics();
			// 如果ourGenerics的长度不等于typeGenerics的长度
			if (ourGenerics.length != typeGenerics.length) {
				// 返回false，表示ourResolved不可分配给otherResolved
				return false;
			}
			// 如果matchedBefore为null
			if (matchedBefore == null) {
				// IdentityHashMap:它添加保存重复的键名，并不会覆盖原有的对，其判断值是否
				// 相等是比较对象的地址而不是hashCode和equal方法
				// 初始化一个最大size为1的IdentityHashMap对象
				matchedBefore = new IdentityHashMap<>(1);
			}
			// 将本类对象的type属性和other的type属性添加到matchedBefore集合中
			matchedBefore.put(this.type, other.type);
			// 遍历ourGenerics
			for (int i = 0; i < ourGenerics.length; i++) {
				// 如果第i个ourGenerics元素不能分配给第i个typeGenerics元素
				if (!ourGenerics[i].isAssignableFrom(typeGenerics[i], matchedBefore)) {
					// 返回false，表示ourResolved不可分配给otherResolved
					return false;
				}
			}
		}
		// 默认返回true。表示ourResolved可分配给otherResolved
		return true;
	}

	/**
	 * 如果此类型解析为表示数组的Class,则返回{@code true}
	 * Return {@code true} if this type resolves to a Class that represents an array.
	 * @see #getComponentType()
	 */
	public boolean isArray() {
		// 如果本类对象为NONE
		if (this == NONE) {
			// 返回false，表示不是数组
			return false;
		}
		// GenericArrayType是Type的子接口，用于表示“泛型数组”，描述的是形如：A<T>[]或T[]的类型。
		// 其实也就是描述ParameterizedType类型以及TypeVariable类型的数组，即形如：classA<T>[][]、T[]等。
		// (如果本类对象的type属性是Class的子类或本身且(将type属性值强转为Class对象时，isArray方法返回true)或者(如果本类对象的type属性是Class的子类或本身且
		// 通过单级解析此类型的ResolveType对象的isArray方法返回true) 就返回true，表示属于本类对象是数组类型，否则返回false，表示本类对象不是数组类型
		return ((this.type instanceof Class && ((Class<?>) this.type).isArray()) ||
				this.type instanceof GenericArrayType || resolveType().isArray());
	}

	/**
	 * 返回表示数组的组件类型的ResolvableType;如果此类型不能表示数组，就返回{@link #NONE}
	 *
	 * Return the ResolvableType representing the component type of the array or
	 * {@link #NONE} if this type does not represent an array.
	 * @see #isArray()
	 */
	public ResolvableType getComponentType() {
		// 如果本对象是NONE
		if (this == NONE) {
			// 直接返回NONE，因为NONE表示没有可用的值，相当与null
			return NONE;
		}
		// 如果本对象组件类型不为null
		if (this.componentType != null) {
			// 执行返回该组件对象
			return this.componentType;
		}
		// 如果type是Class的子类或者本身
		if (this.type instanceof Class) {
			// 将type强转为Class对象来获取组件类型
			Class<?> componentType = ((Class<?>) this.type).getComponentType();
			// 返回由variableResolver支持的componentType的ResolvableType对象
			return forType(componentType, this.variableResolver);
		}
		// 如果type是GenericArrayType的子类或本身
		if (this.type instanceof GenericArrayType) {
			// 返回由给定variableResolver支持的指定Type的ResolvableType
			return forType(((GenericArrayType) this.type).getGenericComponentType(), this.variableResolver);
		}
		// 通过单级解析本类的tyep，返回ResovleType对象，然后再返回表示数组的组件类型的ResolvableType;
		// 如果此类型不能表示数组，就返回NONE
		return resolveType().getComponentType();
	}

	/**
	 * 一种方便的方法，用于将此类型返回可解析的{@link Collection}类型。如果此类型未实现或未继承，则返回{@link #NONE}
	 *
	 * Convenience method to return this type as a resolvable {@link Collection} type.
	 * Returns {@link #NONE} if this type does not implement or extend
	 * {@link Collection}.
	 * @see #as(Class)
	 * @see #asMap()
	 */
	public ResolvableType asCollection() {
		return as(Collection.class);
	}

	/**
	 * 一种方便方法，用于将此类型返回可解析的{@link Map}类型。如果此类型未实现或未继承，则返回{@link #NONE}
	 *
	 * Convenience method to return this type as a resolvable {@link Map} type.
	 * Returns {@link #NONE} if this type does not implement or extend
	 * {@link Map}.
	 * @see #as(Class)
	 * @see #asCollection()
	 */
	public ResolvableType asMap() {
		return as(Map.class);
	}

	/**
	 * 转换为 type 的ResolvableType对象
	 *
	 * Return this type as a {@link ResolvableType} of the specified class. Searches
	 * {@link #getSuperType() supertype} and {@link #getInterfaces() interface}
	 * hierarchies to find a match, returning {@link #NONE} if this type does not
	 * implement or extend the specified class.
	 * @param type the required type (typically narrowed)
	 * @return a {@link ResolvableType} representing this object as the specified
	 * type, or {@link #NONE} if not resolvable as that type
	 * @see #asCollection()
	 * @see #asMap()
	 * @see #getSuperType()
	 * @see #getInterfaces()
	 */
	public ResolvableType as(Class<?> type) {
		// 如果本类对象为NONE
		if (this == NONE) {
			// 还是返回NONE
			return NONE;
		}
		// 将type属性值解析为Class,如果无法解析，则返回null
		Class<?> resolved = resolve();
		// 如果resolved为null或者resolved就是传进来的type
		if (resolved == null || resolved == type) {
			// 直接返回本类对象
			return this;
		}
		// 遍历所有本类对象表示此type属性实现的直接接口的ResolvableType数组
		for (ResolvableType interfaceType : getInterfaces()) {
			// 将interfaceType作为传进来的type的ResolvableType对象
			ResolvableType interfaceAsType = interfaceType.as(type);
			// 如果interfaceAsType不为NONE
			if (interfaceAsType != NONE) {
				// 直接返回interfaceAsType
				return interfaceAsType;
			}
		}
		// 将表示resolved的父类的ResolvableType对象，作为传进来的type的ResolvableType对象
		return getSuperType().as(type);
	}

	/**
	 * 返回表示此类型的直接父类的{@link ResolvableType}.如果没有父类可以用，此方法返回{@link #NONE}
	 *
	 * Return a {@link ResolvableType} representing the direct supertype of this type.
	 * If no supertype is available this method returns {@link #NONE}.
	 * <p>Note: The resulting {@link ResolvableType} instance may not be {@link Serializable}.
	 * @see #getInterfaces()
	 */
	public ResolvableType getSuperType() {
		// 将type属性值解析为Class,如果无法解析，则返回null
		Class<?> resolved = resolve();
		// 如果resolved为null
		if (resolved == null) {
			// 直接返回NONE
			return NONE;
		}
		try {
			// 返回本类的父类，包含泛型参数信息
			Type superclass = resolved.getGenericSuperclass();
			if (superclass == null) {
				return NONE;
			}
			// 获取本类对象的superType属性值
			ResolvableType superType = this.superType;
			// 如果supertype为null
			if (superType == null) {
				// 获取resolve的父类，将其包装成ResolvableType对象
				superType = forType(superclass, this);
				// 缓存解析出来的ResolvableType数组到本类对象的superType属性，以防止下次调用此方法时，重新解析
				this.superType = superType;
			}
			// 返回表示resolved的父类的ResolvableType对象
			return superType;
		}
		catch (TypeNotPresentException ex) {
			// Ignore non-present types in generic signature
			return NONE;
		}
	}

	/**
	 * 返回一个{@link ResolvableType}数组，该数组表示此类型的实现的直接接口。如果此类型不会实现任何接口，则返回空数组
	 *
	 * Return a {@link ResolvableType} array representing the direct interfaces
	 * implemented by this type. If this type does not implement any interfaces an
	 * empty array is returned.
	 * <p>Note: The resulting {@link ResolvableType} instances may not be {@link Serializable}.
	 * @see #getSuperType()
	 */
	public ResolvableType[] getInterfaces() {
		// 将本类对象的type属性解析为Class对象，如果无法解析，则返回null
		Class<?> resolved = resolve();
		// 如果resolved为null
		if (resolved == null) {
			// 返回空数组
			return EMPTY_TYPES_ARRAY;
		}
		// 获取本类对象的interfaces属性值
		ResolvableType[] interfaces = this.interfaces;
		// 如果interfaces为null
		if (interfaces == null) {
			// getGenericInterfaces：返回实现接口信息的Type数组，包含泛型信息
			// getInterfaces:返回实现接口信息的Class数组，不包含泛型信息
			Type[] genericIfcs = resolved.getGenericInterfaces();
			// 初始化insterfaces为长度为genericIfcs长度的ResolvableType数组
			interfaces = new ResolvableType[genericIfcs.length];
			// 遍历genericIfcs
			for (int i = 0; i < genericIfcs.length; i++) {
				// 将genericIfcs[i]的第i个TypeVariable对象封装成ResolvableType对象，并赋值给
				// interfaces的第i个ResolvableType对象
				interfaces[i] = forType(genericIfcs[i], this);
			}
			// 缓存解析出来的ResolvableType数组到本类对象的interfaces属性，以防止下次调用此方法时，重新解析
			this.interfaces = interfaces;
		}
		// 返回解析出来的表示type的接口的ResolvableType数组
		return interfaces;
	}

	/**
	 * 如果此类型包含泛型参数，则返回{@code true}
	 *
	 * Return {@code true} if this type contains generic parameters.
	 * @see #getGeneric(int...)
	 * @see #getGenerics()
	 */
	public boolean hasGenerics() {
		// 获取此类的的泛型参数，如果长度大于0，表示包含泛型参数，返回true；否则，返回false
		return (getGenerics().length > 0);
	}

	/**
	 * 如果此类型只包含不可解析的泛型，则返回{@code true},即不能替代其声明的任何类型变量
	 *
	 * Return {@code true} if this type contains unresolvable generics only,
	 * that is, no substitute for any of its declared type variables.
	 */
	boolean isEntirelyUnresolvable() {
		// 如果本类对象为NONE
		if (this == NONE) {
			// 直接返回false，表示此类型不只包含不可解析的泛型
			return false;
		}
		// 获取表示本类对象的泛型的ResolvableType数组
		ResolvableType[] generics = getGenerics();
		// 遍历generics
		for (ResolvableType generic : generics) {
			// 如果generic不是无法通过关联变量解析器解析的类型变量 且 generic不是表示无特点边界的通配符
			if (!generic.isUnresolvableTypeVariable() && !generic.isWildcardWithoutBounds()) {
				// 返回false，表示此类型不只包含不可解析的泛型
				return false;
			}
		}
		// 返回true，表示此类型只包含不可解析的泛型
		return true;
	}

	/**
	 * 是否具有无法解析的泛型:如果本类对象【this】为NONE,返回false，表示不具有任何不可解析的泛型
	 *
	 * Determine whether the underlying type has any unresolvable generics:
	 * either through an unresolvable type variable on the type itself
	 * or through implementing a generic interface in a raw fashion,
	 * i.e. without substituting that interface's type variables.
	 * The result will be {@code true} only in those two scenarios.
	 */
	public boolean hasUnresolvableGenerics() {
		// 如果本类对象为NONE
		if (this == NONE) {
			// 返回false，表示不具有任何不可解析的泛型
			return false;
		}
		// 获取表示本类对象的泛型参数的ResolvableType数组
		ResolvableType[] generics = getGenerics();
		// 遍历ResolvableType数组
		for (ResolvableType generic : generics) {
			// 如果generic是无法通过关联变量解析器解析的类型变量或者generic是表示无特点边界的通配符
			if (generic.isUnresolvableTypeVariable() || generic.isWildcardWithoutBounds()) {
				// 返回true，表示具有不可解析的泛型
				return true;
			}
		}
		// 将本类对象的type属性解析成Class对象，如果无法解析，则返回null
		Class<?> resolved = resolve();
		// 如果resolved不为null
		if (resolved != null) {
			try {
				// getGenericInterfaces：返回实现接口信息的Type数组，包含泛型信息
				// getInterfaces:返回实现接口信息的Class数组，不包含泛型信息
				for (Type genericInterface : resolved.getGenericInterfaces()) {
					// 如果genericInterface是Class的子类或本身
					if (genericInterface instanceof Class) {
						// 获取genericInterface的ResolvableType对象，并使用完整泛型 类型信息进行可分配性检查
						// 然后判断ResolvableType对象是否包含泛型参数
						if (forClass((Class<?>) genericInterface).hasGenerics()) {
							// 返回true，表示具有不可解析的泛型
							return true;
						}
					}
				}
			}
			catch (TypeNotPresentException ex) {
				// Ignore non-present types in generic signature
			}
			// 获取表示本类对象的父类的ResolveType对象，确定其是否具有任何不可解析的泛型：
			return getSuperType().hasUnresolvableGenerics();
		}
		// 返回false，表示不具有任何不可解析的泛型
		return false;
	}

	/**
	 * 确定基础类型是否是无法通过关联变量解析器解析的类型变量
	 *
	 * Determine whether the underlying type is a type variable that
	 * cannot be resolved through the associated variable resolver.
	 */
	private boolean isUnresolvableTypeVariable() {
		// 如果type是TypeVariable的子类或本身
		if (this.type instanceof TypeVariable) {
			// 如果variableResolver为null
			if (this.variableResolver == null) {
				// 返回true，表示基础类型是无法通过关联变量解析器解析的类型变量
				return true;
			}
			// 将type强转为TypeVariable对象
			TypeVariable<?> variable = (TypeVariable<?>) this.type;
			// 通过variableResolver解析variable得到ResolvableType对象
			ResolvableType resolved = this.variableResolver.resolveVariable(variable);
			// 如果resolved为null或者resovled是无法通过关联变量解析器解析的类型变量
			if (resolved == null || resolved.isUnresolvableTypeVariable()) {
				// 返回true，表示基础类型是无法通过关联变量解析器解析的类型变量
				return true;
			}
		}
		// 返回false，默认情况下，type不是无法通过关联变量解析器解析的类型变量
		return false;
	}

	/**
	 * 确定基本类型是否表示无特点边界的通配符(即等于{@code ? extends Object})
	 *
	 * Determine whether the underlying type represents a wildcard
	 * without specific bounds (i.e., equal to {@code ? extends Object}).
	 */
	private boolean isWildcardWithoutBounds() {
		// 如果type是TypeVariable的子类或本身
		if (this.type instanceof WildcardType) {
			// 将type强转为WildcardType对象
			WildcardType wt = (WildcardType) this.type;
			// 如果wt的下限的所有Type对象不存在
			if (wt.getLowerBounds().length == 0) {
				// 获取wt上限的所有Type对象
				Type[] upperBounds = wt.getUpperBounds();
				// 如果wt上限的所有Type对象不存在或者(wt上限的所有Type对象只有一个且那一个元素为Object)
				if (upperBounds.length == 0 || (upperBounds.length == 1 && Object.class == upperBounds[0])) {
					// 返回true，表示该类对象为无特点边界的通配符
					return true;
				}
			}
		}
		// 返回false，表示该类对象不为无特点边界的通配符
		return false;
	}

	/**
	 * 返回指定嵌套等级的{@link ResolvableType}对象
	 *
	 * Return a {@link ResolvableType} for the specified nesting level.
	 * See {@link #getNested(int, Map)} for details.
	 * @param nestingLevel the nesting level
	 * @return the {@link ResolvableType} type, or {@code #NONE}
	 */
	public ResolvableType getNested(int nestingLevel) {
		return getNested(nestingLevel, null);
	}

	/**
	 * 返回指定嵌套等级的{@link ResolvableType}对象
	 *
	 * Return a {@link ResolvableType} for the specified nesting level.
	 * <p>The nesting level refers to the specific generic parameter that should be returned.
	 * A nesting level of 1 indicates this type; 2 indicates the first nested generic;
	 * 3 the second; and so on. For example, given {@code List<Set<Integer>>} level 1 refers
	 * to the {@code List}, level 2 the {@code Set}, and level 3 the {@code Integer}.
	 * <p>The {@code typeIndexesPerLevel} map can be used to reference a specific generic
	 * for the given level. For example, an index of 0 would refer to a {@code Map} key;
	 * whereas, 1 would refer to the value. If the map does not contain a value for a
	 * specific level the last generic will be used (e.g. a {@code Map} value).
	 * <p>Nesting levels may also apply to array types; for example given
	 * {@code String[]}, a nesting level of 2 refers to {@code String}.
	 * <p>If a type does not {@link #hasGenerics() contain} generics the
	 * {@link #getSuperType() supertype} hierarchy will be considered.
	 * @param nestingLevel the required nesting level, indexed from 1 for the
	 * current type, 2 for the first nested generic, 3 for the second and so on
	 * @param typeIndexesPerLevel a map containing the generic index for a given
	 * nesting level (may be {@code null})
	 * @return a {@link ResolvableType} for the nested level, or {@link #NONE}
	 */
	public ResolvableType getNested(int nestingLevel, @Nullable Map<Integer, Integer> typeIndexesPerLevel) {
		// 初始化返回结果为本类对象
		ResolvableType result = this;
		// 从2开始遍历传进来的嵌套等级,因为1表示其本身，所以从2开始
		for (int i = 2; i <= nestingLevel; i++) {
			// 如果result是数组
			if (result.isArray()) {
				// 获取表示result的元素的ResolvableType对象重新赋值给result
				result = result.getComponentType();
			}
			else {
				// Handle derived types
				// result不是数组类型
				// Handle derived types处理派生类型
				// 如果result不是NONE且result不包含泛型参数
				while (result != ResolvableType.NONE && !result.hasGenerics()) {
					// 获取表示result的父类的ResolvableType对象重新赋值给result
					result = result.getSuperType();
				}
				// 如果typeIndexPrelevel不为null,获取在typeIndexsPerLevel的key为i的值；否则返回null
				Integer index = (typeIndexesPerLevel != null ? typeIndexesPerLevel.get(i) : null);
				// 如果index为null，获取表示reuslt的泛型参数的ResolvableType数组的最后一个对象索引作为index
				index = (index == null ? result.getGenerics().length - 1 : index);
				// 获取表示result的泛型参数的ResolvableType数组的第index个对象作为result
				result = result.getGeneric(index);
			}
		}
		// 返回匹配参数信息的泛型的ResolvableType对象
		return result;
	}

	/**
	 * 返回表示给定索引的通用参数的{@link ResolvableType}.索引从零开始;例如，给定类型
	 * {@code Map<Integer,List<String>>},{@code getGeneric(0)}将访问{@code Integer}.
	 * 嵌套泛型可以通过指定多个索引来访问，例如{@code getGeneric(1,0)从嵌套{@code List}
	 * 中访问{@code String}.为了方便起见，如果没有指定索引，则返回第一个泛型
	 *
	 * Return a {@link ResolvableType} representing the generic parameter for the
	 * given indexes. Indexes are zero based; for example given the type
	 * {@code Map<Integer, List<String>>}, {@code getGeneric(0)} will access the
	 * {@code Integer}. Nested generics can be accessed by specifying multiple indexes;
	 * for example {@code getGeneric(1, 0)} will access the {@code String} from the
	 * nested {@code List}. For convenience, if no indexes are specified the first
	 * generic is returned.
	 * <p>If no generic is available at the specified indexes {@link #NONE} is returned.
	 * @param indexes the indexes that refer to the generic parameter
	 * (may be omitted to return the first generic)
	 * @return a {@link ResolvableType} for the specified generic, or {@link #NONE}
	 * @see #hasGenerics()
	 * @see #getGenerics()
	 * @see #resolveGeneric(int...)
	 * @see #resolveGenerics()
	 */
	public ResolvableType getGeneric(@Nullable int... indexes) {
		// 获取表示本类对象的泛型参数的ResolvableType数组
		ResolvableType[] generics = getGenerics();
		// 如果索引为null或者索引长度为0
		if (indexes == null || indexes.length == 0) {
			// 如果generics的长度为0，返回NONE；否则返回generics的第一个ResolvableType对象
			return (generics.length == 0 ? NONE : generics[0]);
		}
		// 初始化generic为本类对象
		ResolvableType generic = this;
		// 遍历indexs
		for (int index : indexes) {
			// 获取表示generic的泛型参数的ResolvableType数组，重新赋值给generics
			generics = generic.getGenerics();
			// 如果index小于0或者index大于generics的长度
			if (index < 0 || index >= generics.length) {
				// 返回NONE
				return NONE;
			}
			// 获取generics的第i个ResolveType对象作为generic
			generic = generics[index];
		}
		// 返回匹配的泛型的ResolvableType对象
		return generic;
	}

	/**
	 * 返回表现此类型的泛型参数的{@link ResolvableType ResolvableTypes}数组。如果没有可用的泛型，
	 * 则返回一个空的数组。如果需要访问特定的泛型，请考虑使用{@link #getGeneric(int...)} 方法，
	 * 因为它允许访问嵌套的泛型并防止{@code IndexOutBoundsExceptions}
	 *
	 * Return an array of {@link ResolvableType ResolvableTypes} representing the generic parameters of
	 * this type. If no generics are available an empty array is returned. If you need to
	 * access a specific generic consider using the {@link #getGeneric(int...)} method as
	 * it allows access to nested generics and protects against
	 * {@code IndexOutOfBoundsExceptions}.
	 * @return an array of {@link ResolvableType ResolvableTypes} representing the generic parameters
	 * (never {@code null})
	 * @see #hasGenerics()
	 * @see #getGeneric(int...)
	 * @see #resolveGeneric(int...)
	 * @see #resolveGenerics()
	 */
	public ResolvableType[] getGenerics() {
		// 如果本类对象为NONE
		if (this == NONE) {
			// 返回空类型数组
			return EMPTY_TYPES_ARRAY;
		}
		// 将本类对象的generics属性作为要返回出去的ResolvableType数组
		ResolvableType[] generics = this.generics;
		// 如果generics为null
		if (generics == null) {
			// 如果本类对象的type属性值是Class的子类或本类
			if (this.type instanceof Class) {
				// 从type中获取一个代表该泛型声明中声明的类型变量TypeVariable对象的数组
				Type[] typeParams = ((Class<?>) this.type).getTypeParameters();
				// 初始化generics为长度为typeParams长度的ResolvableType类型数组
				generics = new ResolvableType[typeParams.length];
				// 遍历generics
				for (int i = 0; i < generics.length; i++) {
					// 将typeParams[i]的第i个TypeVariable对象封装成ResolvableType对象，并赋值给generics的第i个ResolvableTyp对象
					generics[i] = ResolvableType.forType(typeParams[i], this);
				}
			}
			// ParameterizedType:具有<>符号的变量
			// 如果本类对象的type属性值是ParameterizedType
			else if (this.type instanceof ParameterizedType) {
				// ParameterizedType.getActualTypeArgments:获取泛型中的实际类型，可能会存在多个泛型，例如Map<K,V>,所以会返回Type[]数组；
				Type[] actualTypeArguments = ((ParameterizedType) this.type).getActualTypeArguments();
				// 初始化generics为长度为typeParams长度的ResolvableType类型数组
				generics = new ResolvableType[actualTypeArguments.length];
				// 遍历actualTypeArguments
				for (int i = 0; i < actualTypeArguments.length; i++) {
					// 将actualTypeArguments[i]的第i个TypeVariable对象封装成ResolvableType对象，并赋值给generics的第i个ResolvableType对象
					generics[i] = forType(actualTypeArguments[i], this.variableResolver);
				}
			}
			else {
				// 重新解析本类对象的type属性值，得到新的ResolvableType对象，然后再重新获取该对象的泛型参数的ResovlableTypes素组
				generics = resolveType().getGenerics();
			}
			// 缓存解析出来的ResolvableType数组到本类对象的generics属性，以防止下次调用此方法时，重新解析
			this.generics = generics;
		}
		// 返回解析出来的ResolvableType数组
		return generics;
	}

	/**
	 * 将{@link #getGenerics() 获取} 和{@link #resolve() 解析}泛型参数的方便方法
	 *
	 * Convenience method that will {@link #getGenerics() get} and
	 * {@link #resolve() resolve} generic parameters.
	 * @return an array of resolved generic parameters (the resulting array
	 * will never be {@code null}, but it may contain {@code null} elements})
	 * @see #getGenerics()
	 * @see #resolve()
	 */
	public Class<?>[] resolveGenerics() {
		// 获取表示本类对象的泛型参数的ResolvableType数组，重新赋值给generics
		ResolvableType[] generics = getGenerics();
		// 定义一个长度为generics长度的Class数组
		Class<?>[] resolvedGenerics = new Class<?>[generics.length];
		// 遍历generics
		for (int i = 0; i < generics.length; i++) {
			//将第i个generics的ResolvableType对象的type属性解析为Class对象，并赋值给resolvedGenerics的第i个Class对象
			resolvedGenerics[i] = generics[i].resolve();
		}
		// 解析的泛型参数Class数组
		return resolvedGenerics;
	}

	/**
	 * 将{@link #getGenerics() 获取}和{@link #resolve() 解析}泛型参数的方便方法，如果任何类型不能被解析，则返回指定的{@code fallback}
	 *
	 * Convenience method that will {@link #getGenerics() get} and {@link #resolve()
	 * resolve} generic parameters, using the specified {@code fallback} if any type
	 * cannot be resolved.
	 * @param fallback the fallback class to use if resolution fails
	 * @return an array of resolved generic parameters
	 * @see #getGenerics()
	 * @see #resolve()
	 */
	public Class<?>[] resolveGenerics(Class<?> fallback) {
		// 获取表示本类对象的泛型参数的ResolvableType数组，重新赋值给generics
		ResolvableType[] generics = getGenerics();
		// 定义一个长度为generics长度的Class数组
		Class<?>[] resolvedGenerics = new Class<?>[generics.length];
		// 遍历generics
		for (int i = 0; i < generics.length; i++) {
			// 将第i个generics的ResolvableType对象的type属性解析为Class对象，如果无法解析就返回fallback，并赋值给resolvedGenerics的第i个Class对象
			resolvedGenerics[i] = generics[i].resolve(fallback);
		}
		// 解析的泛型参数Class数组
		return resolvedGenerics;
	}

	/**
	 * 将{@link #getGeneric(int...) 获取}和{@link #resolve() 解析}指定泛型参数
	 *
	 * Convenience method that will {@link #getGeneric(int...) get} and
	 * {@link #resolve() resolve} a specific generic parameters.
	 * @param indexes the indexes that refer to the generic parameter
	 * (may be omitted to return the first generic)
	 * @return a resolved {@link Class} or {@code null}
	 * @see #getGeneric(int...)
	 * @see #resolve()
	 */
	@Nullable
	public Class<?> resolveGeneric(int... indexes) {
		// 将indexs的通用参数的ResolvableType对象的type属性解析成Class对象
		return getGeneric(indexes).resolve();
	}

	/**
	 * 将此类型解析为{@link java.lang.Class},如果无法解析，则返回{@code null}.如果直接解析失败，
	 * 则此方法将考虑{@link TypeVariable TypeVariables}和{@link WildcardType WildcardTypes}的范围；
	 * 但是，{@code Object.class}的边界将被忽略
	 *
	 * Resolve this type to a {@link java.lang.Class}, returning {@code null}
	 * if the type cannot be resolved. This method will consider bounds of
	 * {@link TypeVariable TypeVariables} and {@link WildcardType WildcardTypes} if
	 * direct resolution fails; however, bounds of {@code Object.class} will be ignored.
	 * <p>If this method returns a non-null {@code Class} and {@link #hasGenerics()}
	 * returns {@code false}, the given type effectively wraps a plain {@code Class},
	 * allowing for plain {@code Class} processing if desirable.
	 * @return the resolved {@link Class}, or {@code null} if not resolvable
	 * @see #resolve(Class)
	 * @see #resolveGeneric(int...)
	 * @see #resolveGenerics()
	 */
	@Nullable
	public Class<?> resolve() {
		return this.resolved;
	}

	/**
	 * 将此类型解析为{@link java.lang.Class}，如果无法解析该类型，则返回指定的{@code fallback}
	 * 如果直接解析失败，则此方法考虑{@Code TypeVariable TypeVariables}和{@link WildcardType WildcardTypes}的bounds，
	 * 但是{@code Object.class}的bounds将被忽略
	 *
	 * Resolve this type to a {@link java.lang.Class}, returning the specified
	 * {@code fallback} if the type cannot be resolved. This method will consider bounds
	 * of {@link TypeVariable TypeVariables} and {@link WildcardType WildcardTypes} if
	 * direct resolution fails; however, bounds of {@code Object.class} will be ignored.
	 * @param fallback the fallback class to use if resolution fails
	 * @return the resolved {@link Class} or the {@code fallback}
	 * @see #resolve()
	 * @see #resolveGeneric(int...)
	 * @see #resolveGenerics()
	 */
	public Class<?> resolve(Class<?> fallback) {
		// 如果resolved不为null，就返回resolved，否则返回fallback
		return (this.resolved != null ? this.resolved : fallback);
	}

	/**
	 * 解析类
	 */
	@Nullable
	private Class<?> resolveClass() {
		// type为空类型对象
		if (this.type == EmptyType.INSTANCE) {
			// 返回null
			return null;
		}
		// 如果type是Class的子类或本身
		if (this.type instanceof Class) {
			// 强转type为Class<?>并返回出去
			return (Class<?>) this.type;
		}
		// 如果type是GenericArrayType的子类或本类
		// GenericArrayType是Type的子接口，用于表示“泛型数组”，描述的是形如：A<T>[]或T[]的类型。
		// 其实也就是描述ParameterizedType类型以及TypeVariable类型的数组，即形如：classA<T>[][]、T[]等。
		if (this.type instanceof GenericArrayType) {
			// 获取type的表示数组的组件类型的ResolvableType;如果此类型不能表示数组，就返回NONE
			Class<?> resolvedComponent = getComponentType().resolve();
			return (resolvedComponent != null ? Array.newInstance(resolvedComponent, 0).getClass() : null);
		}
		// 通过单级解析type，返回ResolveType对象，然后将type解析为Class,如果无法解析，则返回null.
		// 如果直接解析失败，则此方法将考虑TypeVariables和WildcardTypes的范围；但是，Object.class的边界将被忽略
		return resolveType().resolve();
	}

	/**
	 * 通过单级解析此类型，返回解析值或{@link #NONE}
	 *
	 * Resolve this type by a single level, returning the resolved value or {@link #NONE}.
	 * <p>Note: The returned {@link ResolvableType} should only be used as an intermediary
	 * as it cannot be serialized.
	 */
	ResolvableType resolveType() {
		// ParameterizedType:具有<>符号的变量
		// 如果type是ParameterizedType的子类或本身
		if (this.type instanceof ParameterizedType) {
			// ParameterizeType.getRowType:返回最外层<>前面那个类型，即Map<K ,V>的Map。
			// 返回由给定variableResolver支持的指定type.getRawType的ResolvableType对象
			return forType(((ParameterizedType) this.type).getRawType(), this.variableResolver);
		}
		// WildcardType：通配符表达式，泛型表达式，也可以说是，限定性的泛型，形如：? extends classA、？super classB
		// 如果type是WildcardType的子类或本身
		if (this.type instanceof WildcardType) {
			// WildcardType.getUppperBounds：获得泛型表达式上界（上限），即父类
			Type resolved = resolveBounds(((WildcardType) this.type).getUpperBounds());
			// 如果没有找到上限，就找下限
			if (resolved == null) {
				// WildcardType.getLowerBounds： 获得泛型表达式下界（下限），即子类
				resolved = resolveBounds(((WildcardType) this.type).getLowerBounds());
			}
			// 给定variableResolver支持的指定resolved的ResolvableType
			return forType(resolved, this.variableResolver);
		}
		// TypeVariable：Type接口是java编程语言中所有类型的公共高级接口、参数化类型、数组类型、类型变量和基本类型。
		// 如果type是TypeVariable的子类或本身
		if (this.type instanceof TypeVariable) {
			// 将type强转为TypeVariable对象
			TypeVariable<?> variable = (TypeVariable<?>) this.type;
			// Try default variable resolution
			// 尝试默认变量解析
			if (this.variableResolver != null) {
				// 解析variable
				ResolvableType resolved = this.variableResolver.resolveVariable(variable);
				// 如果resolved不为null
				if (resolved != null) {
					// 直接返回结果
					return resolved;
				}
			}
			// Fallback to bounds
			// Fallback to bounds 退回到Bounds
			// TypeVariable.getBound(): 获得类型变量的上边界，若无显式的定义（extends）,默认为Object;类型变量的上边界可能不止一个，
			//             因为可以用&符号限定多个（这其中有且只能有一个为类或抽象类，且必须放在extends后的第一个，
			//             即若有多个上边界，则第一个&后必须为接口）
			// resolveBound(Type[]): 解析typeVariable的bounds, 返回给定bounds的第一个元素类型，如果没有找到，返回null
			// 返回由variableResolver支持的ypeVariable的bounds的第一个Type的ResolvableType
			return forType(resolveBounds(variable.getBounds()), this.variableResolver);
		}
		return NONE;
	}

	/**
	 * 解析typeVariable的上边界类型
	 * @param bounds
	 * @return
	 */
	@Nullable
	private Type resolveBounds(Type[] bounds) {
		// 如果bounds长度为0或者第一个bounds的第一个元素类型为Object
		if (bounds.length == 0 || bounds[0] == Object.class) {
			// 直接返回null，表示没有找到上边界类型
			return null;
		}
		// 返回bounds的第一个元素类型
		return bounds[0];
	}

	/**
	 * 将{@code variable}解析包装成ResolvableType对象
	 * @param variable
	 * @return
	 */
	@Nullable
	private ResolvableType resolveVariable(TypeVariable<?> variable) {
		// 如果type是TypeVariable的子类或本身
		if (this.type instanceof TypeVariable) {
			// 通过单级解析本类对象的type，得到ResolvableType对象，再将variable解析包装成ResolvableType对象
			return resolveType().resolveVariable(variable);
		}
		// 如果type是ParameterizedType的子类或本身
		if (this.type instanceof ParameterizedType) {
			// 强转type为ParameterizedType对象
			ParameterizedType parameterizedType = (ParameterizedType) this.type;
			// 将type解析成Class对象，如果无法解析，则返回null
			Class<?> resolved = resolve();
			// 如果resolved为null
			if (resolved == null) {
				// 返回null
				return null;
			}
			// 从resolved中获取一个代表该泛型声明中声明的类型变量TypeVariable对象的数组。
			TypeVariable<?>[] variables = resolved.getTypeParameters();
			// 遍历variables
			for (int i = 0; i < variables.length; i++) {
				// 如果第i个variables元素对象的名称等于传进来的variable名称
				if (ObjectUtils.nullSafeEquals(variables[i].getName(), variable.getName())) {
					// ParameterizedType.getActualTypeArgments:获取泛型中的实际类型，可能会存在多个泛型，
					// 例如Map<K,V>,所以会返回Type[]数组；
					// 获取第i个parameterizedType泛型中的实际类型
					Type actualType = parameterizedType.getActualTypeArguments()[i];
					// 返回由variableResolver支持的指定Type的ResolvableType对象
					return forType(actualType, this.variableResolver);
				}
			}
			// Type getOwnerType()返回 Type 对象，表示此类型是其成员之一的类型。例如，如果此类型为 O.I，
			// 则返回O的表示形式。如果此类型为顶层类型，则返回 null
			Type ownerType = parameterizedType.getOwnerType();
			// 如果ownerType不为null
			if (ownerType != null) {
				// 由给定variableResolver支持的指定Type的ResolvableType对象，再将variable解析包装成ResolvableType对象
				return forType(ownerType, this.variableResolver).resolveVariable(variable);
			}
		}
		if (this.type instanceof WildcardType) {
			ResolvableType resolved = resolveType().resolveVariable(variable);
			if (resolved != null) {
				return resolved;
			}
		}
		// 如果variableReosolver不为null
		if (this.variableResolver != null) {
			// 使用本类对象的variableResolver属性对variable解析包装成ResolvableType对象
			return this.variableResolver.resolveVariable(variable);
		}
		// 无法解析，返回null
		return null;
	}


	@Override
	public boolean equals(@Nullable Object other) {
		// 如果本类对象与other的地址相等
		if (this == other) {
			// 返回true，表示相等
			return true;
		}
		// 如果other不是ResolvableType的子类或者本类
		if (!(other instanceof ResolvableType)) {
			// 返回false，表示不相等
			return false;
		}

		// 将other强转成ResolvableType对象
		ResolvableType otherType = (ResolvableType) other;
		// 如果本类对象的type属性与otherType的type属性不相等
		if (!ObjectUtils.nullSafeEquals(this.type, otherType.type)) {
			// 返回false，表示不相等
			return false;
		}
		// 如果本类对象的typeProvider属性与otherType的typeProvider属性 地址不相等
		// 且(本类对象的typeProvider属性为null或者otherType的typeProvider属性为null或者
		// 本类对象的typeProvider属性的type属性与otherType的typeProvider属性的type属性不相等)
		if (this.typeProvider != otherType.typeProvider &&
				(this.typeProvider == null || otherType.typeProvider == null ||
				!ObjectUtils.nullSafeEquals(this.typeProvider.getType(), otherType.typeProvider.getType()))) {
			// 返回false，表示不相等
			return false;
		}
		// 如果本类对象的variableResolver属性与otherType的variableResolver属性地址不相等
		// 且(本类对象的variableResolver属性为null或者otherType的variableResolver属性为null或者
		// 本类对象的variableResolver属性的source属性与otherType的variableResolver属性的source属性不相等)
		if (this.variableResolver != otherType.variableResolver &&
				(this.variableResolver == null || otherType.variableResolver == null ||
				!ObjectUtils.nullSafeEquals(this.variableResolver.getSource(), otherType.variableResolver.getSource()))) {
			// 返回false，表示不相等
			return false;
		}
		// 如果本类对象的componentType属性与otherType的componentType属性不相等
		if (!ObjectUtils.nullSafeEquals(this.componentType, otherType.componentType)) {
			// 返回false，表示不相等
			return false;
		}
		// 返回true，表示相等
		return true;
	}

	@Override
	public int hashCode() {
		// 如果本类的hash属性不为null，就返回该属性值；否则计算本类对象的哈希值
		return (this.hash != null ? this.hash : calculateHashCode());
	}

	/**
	 * 计算本类的哈希值
	 * @return
	 */
	private int calculateHashCode() {
		// 获取被管理的基础类型
		int hashCode = ObjectUtils.nullSafeHashCode(this.type);
		// 如果当前类型的可选提供者不为null
		if (this.typeProvider != null) {
			// 叠加计算哈希值
			hashCode = 31 * hashCode + ObjectUtils.nullSafeHashCode(this.typeProvider.getType());
		}
		// 如果要使用的遍历解析器不为null
		if (this.variableResolver != null) {
			// 叠加计算哈希值
			hashCode = 31 * hashCode + ObjectUtils.nullSafeHashCode(this.variableResolver.getSource());
		}
		// 数组的元素类型不为null
		if (this.componentType != null) {
			// 叠加计算哈希值
			hashCode = 31 * hashCode + ObjectUtils.nullSafeHashCode(this.componentType);
		}
		// 返回经过层层叠加计算后的哈希值，该哈希值表示本类哈希值
		return hashCode;
	}

	/**
	 * 将此{@link ResolvableType}修改为{@link VariableResolver}
	 *
	 * Adapts this {@link ResolvableType} to a {@link VariableResolver}.
	 */
	@Nullable
	VariableResolver asVariableResolver() {
		// 如果本类对象为NONE
		if (this == NONE) {
			// 返回null
			return null;
		}
		// DefaultVariableResolver：默认的变量解析器，使用ResolvableType对象对类型变量进行解析
		// 新建一个默认的VariableResolver实例，
		return new DefaultVariableResolver(this);
	}

	/**
	 * 对{@link #NONE}自定义序列化支持
	 *
	 * Custom serialization support for {@link #NONE}.
	 */
	private Object readResolve() {
		// 如果本类对象的type属性为EmptyType对象，返回NONE；否则返回本类对象
		return (this.type == EmptyType.INSTANCE ? NONE : this);
	}

	/**
	 * 以完全解析的形式(包括任何泛型参数)返回此类型的String表示形式
	 *
	 * Return a String representation of this type in its fully resolved form
	 * (including any generic parameters).
	 */
	@Override
	public String toString() {
		// 如果本类对象为数组
		if (isArray()) {
			// 获取表示元素类型的ResolvableType对象,拼接'[]'返回出去
			return getComponentType() + "[]";
		}
		// 如果type解析成Class对象为null
		if (this.resolved == null) {
			// 返回'?'
			return "?";
		}
		// 如果type是TypeVariable的子类或本类
		if (this.type instanceof TypeVariable) {
			// 将type强转为TypeVariable对象
			TypeVariable<?> variable = (TypeVariable<?>) this.type;
			// 如果variableResolver为null或者variableResolver解析variable为null
			if (this.variableResolver == null || this.variableResolver.resolveVariable(variable) == null) {
				// Don't bother with variable boundaries for toString()...
				// 不要为toString()的变量边界而烦劳
				// Can cause infinite recursions in case of self-references
				// 自我引用可能导致无限递归
				// 直接返回'?'
				return "?";
			}
		}
		// 如果本类对象具有泛型
		if (hasGenerics()) {
			// 获取resolved的全类名称，拼接'<',拼接获取表示本类对象泛型参数的ResolvableTyp数组转换为定界的String,拼接'>'
			return this.resolved.getName() + '<' + StringUtils.arrayToDelimitedString(getGenerics(), ", ") + '>';
		}
		// 直接返回resolved的全类名称
		return this.resolved.getName();
	}


	// Factory methods

	/**
	 * 返回指定{@link Class}的{@link ResolvableType}对象，使用完整泛型类型信息进行可分配性检查
	 *
	 * Return a {@link ResolvableType} for the specified {@link Class},
	 * using the full generic type information for assignability checks.
	 * For example: {@code ResolvableType.forClass(MyArrayList.class)}.
	 * @param clazz the class to introspect ({@code null} is semantically
	 * equivalent to {@code Object.class} for typical use cases here)
	 * @return a {@link ResolvableType} for the specified class
	 * @see #forClass(Class, Class)
	 * @see #forClassWithGenerics(Class, Class...)
	 */
	public static ResolvableType forClass(@Nullable Class<?> clazz) {
		// 在Class的基础上创建新的ResolvableType。避免为了 创建直接的Class包装类而进行所有instanceof检查
		return new ResolvableType(clazz);
	}

	/**
	 * 为指定的{@link Class}返回一个{@link ResolvableType}，仅针对原始类型进行可分配性检查(类似于{@link Class#isAssignableFrom},它用作包装器
	 *
	 * Return a {@link ResolvableType} for the specified {@link Class},
	 * doing assignability checks against the raw class only (analogous to
	 * {@link Class#isAssignableFrom}, which this serves as a wrapper for.
	 * For example: {@code ResolvableType.forRawClass(List.class)}.
	 * @param clazz the class to introspect ({@code null} is semantically
	 * equivalent to {@code Object.class} for typical use cases here)
	 * @return a {@link ResolvableType} for the specified class
	 * @since 4.2
	 * @see #forClass(Class)
	 * @see #getRawClass()
	 */
	public static ResolvableType forRawClass(@Nullable Class<?> clazz) {
		// 用于在Class的基础上创建新的ResolvableType,这里重写了一些方法，再返回出去
		return new ResolvableType(clazz) {
			@Override
			public ResolvableType[] getGenerics() {
				// 返回空类型数组
				return EMPTY_TYPES_ARRAY;
			}
			@Override
			public boolean isAssignableFrom(Class<?> other) {
				// 如果clazz为null或者clazz分配给other,返回true，表示可分配给other
				return (clazz == null || ClassUtils.isAssignable(clazz, other));
			}
			@Override
			public boolean isAssignableFrom(ResolvableType other) {
				// 将other的type属性解析成Class对象
				Class<?> otherClass = other.resolve();
				// 如果otherClass不为null(clazz为null或者clazz分配给other),返回true，表示可分配给other
				return (otherClass != null && (clazz == null || ClassUtils.isAssignable(clazz, otherClass)));
			}
		};
	}

	/**
	 * 返回给定实现类的指定基础类型(接口或基类)的{@link ResolvableType}对象
	 *
	 * Return a {@link ResolvableType} for the specified base type
	 * (interface or base class) with a given implementation class.
	 * For example: {@code ResolvableType.forClass(List.class, MyArrayList.class)}.
	 * @param baseType the base type (must not be {@code null})
	 * @param implementationClass the implementation class
	 * @return a {@link ResolvableType} for the specified base type backed by the
	 * given implementation class
	 * @see #forClass(Class)
	 * @see #forClassWithGenerics(Class, Class...)
	 */
	public static ResolvableType forClass(Class<?> baseType, Class<?> implementationClass) {
		Assert.notNull(baseType, "Base type must not be null");
		// 获取implementationClass的ResolvableType对象，然后将此ResolvableType对象的type属性作为baseType的ResolvableType返回
		ResolvableType asType = forType(implementationClass).as(baseType);
		// 如果asType为NONE，返回baseType的ResolvableType；否则返回asType
		return (asType == NONE ? forType(baseType) : asType);
	}

	/**
	 * 使用预先声明的泛型为指定{@link Class}返回一个{@link ResolvableType}对象
	 *
	 * Return a {@link ResolvableType} for the specified {@link Class} with pre-declared generics.
	 * @param clazz the class (or interface) to introspect
	 * @param generics the generics of the class
	 * @return a {@link ResolvableType} for the specific class and generics
	 * @see #forClassWithGenerics(Class, ResolvableType...)
	 */
	public static ResolvableType forClassWithGenerics(Class<?> clazz, Class<?>... generics) {
		Assert.notNull(clazz, "Class must not be null");
		Assert.notNull(generics, "Generics array must not be null");
		// 初始化长度为generics的长度的ResolvableType数组
		ResolvableType[] resolvableGenerics = new ResolvableType[generics.length];
		// 遍历generics
		for (int i = 0; i < generics.length; i++) {
			// 获取第i个generics元素的ResolvableType对象，使用完整泛型类型信息进行可分配性检查，然后赋值给第一个resolvableGenerics元素
			resolvableGenerics[i] = forClass(generics[i]);
		}
		return forClassWithGenerics(clazz, resolvableGenerics);
	}

	/**
	 * 使用预先声明的泛型为指定{@link Class}返回一个{@link ResolvableType}对象
	 *
	 * Return a {@link ResolvableType} for the specified {@link Class} with pre-declared generics.
	 * @param clazz the class (or interface) to introspect
	 * @param generics the generics of the class
	 * @return a {@link ResolvableType} for the specific class and generics
	 * @see #forClassWithGenerics(Class, Class...)
	 */
	public static ResolvableType forClassWithGenerics(Class<?> clazz, ResolvableType... generics) {
		Assert.notNull(clazz, "Class must not be null");
		Assert.notNull(generics, "Generics array must not be null");
		// 从clazz中获取一个代表该泛型声明中声明的类型变量TypeVariable对象的数组。
		TypeVariable<?>[] variables = clazz.getTypeParameters();
		// 如果variable的长度不等于generics的长度，抛出异常
		Assert.isTrue(variables.length == generics.length, "Mismatched number of generics specified");

		// 定义一个长度为generics长度的Type类型数组
		Type[] arguments = new Type[generics.length];
		// 遍历generics
		for (int i = 0; i < generics.length; i++) {
			// 获取generics的第i个ResolvableType对象
			ResolvableType generic = generics[i];
			// 如果generic不为null,获取generic的受管理的基础Java Type；否则为null
			Type argument = (generic != null ? generic.getType() : null);
			// 如果arguments不为null 且 argument不是TypeVariable的子类或本身,则第i个argument元素就为argument，否则就为第i个variables元素
			arguments[i] = (argument != null && !(argument instanceof TypeVariable) ? argument : variables[i]);
		}
		// 构建一个 SyntheticParameterizedType
		ParameterizedType syntheticType = new SyntheticParameterizedType(clazz, arguments);
		// 返回由TypeVariablesVariableResolver支持的指定Type的ResolvableType对象
		return forType(syntheticType, new TypeVariablesVariableResolver(variables, generics));
	}

	/**
	 *  返回一个指定实例的{@link ResolvableType}对象。该实例不会传达泛型信息但是如果它实现了{@link ResolvableTypeProvider}
	 *  则可以使用比基于{@link #forClass(Class) 类实例}的简单实例更精确的{@link ResolvableType}
	 *
	 * Return a {@link ResolvableType} for the specified instance. The instance does not
	 * convey generic information but if it implements {@link ResolvableTypeProvider} a
	 * more precise {@link ResolvableType} can be used than the simple one based on
	 * the {@link #forClass(Class) Class instance}.
	 * @param instance the instance
	 * @return a {@link ResolvableType} for the specified instance
	 * @since 4.2
	 * @see ResolvableTypeProvider
	 */
	public static ResolvableType forInstance(Object instance) {
		Assert.notNull(instance, "Instance must not be null");
		// 如果instance是ResolvableTypeProvider的子类或本身
		if (instance instanceof ResolvableTypeProvider) {
			// 强转instance为ResolvableTypeProvider对象后，通过getResolvableType获取指定的ResolvableType对象
			ResolvableType type = ((ResolvableTypeProvider) instance).getResolvableType();
			// 如果type不为null
			if (type != null) {
				// 返回type
				return type;
			}
		}
		// 返回instance的类对象的ResolvableType对象，使用完整泛型类型信息进行可分配性检查
		return ResolvableType.forClass(instance.getClass());
	}

	/**
	 * 返回指定{@link Field}的{@link ResolvableType}
	 *
	 * Return a {@link ResolvableType} for the specified {@link Field}.
	 * @param field the source field
	 * @return a {@link ResolvableType} for the specified field
	 * @see #forField(Field, Class)
	 */
	public static ResolvableType forField(Field field) {
		Assert.notNull(field, "Field must not be null");
		// FieldTypeProvider:从Field中获取的类型的SerializableTypeWrapper.TypeProvider
		// 返回由默认的VariableResolver支持的FieldTypeProvider的ResolvableType对象
		return forType(null, new FieldTypeProvider(field), null);
	}

	/**
	 * 使用给定的时间，为指定的{@link Field}返回一个{@link ResolvableType}
	 *
	 * Return a {@link ResolvableType} for the specified {@link Field} with a given
	 * implementation.
	 * <p>Use this variant when the class that declares the field includes generic
	 * parameter variables that are satisfied by the implementation class.
	 * @param field the source field
	 * @param implementationClass the implementation class
	 * @return a {@link ResolvableType} for the specified field
	 * @see #forField(Field)
	 */
	public static ResolvableType forField(Field field, Class<?> implementationClass) {
		Assert.notNull(field, "Field must not be null");
		// 获取implementationClass的ResolvableType对象，并作为field的声明类的ResolvableType对象返回
		ResolvableType owner = forType(implementationClass).as(field.getDeclaringClass());
		// owner.asVariableResolver:将owner修改为DefaultVariableResolver,因为每个ResolvableType对象都具有VariableResolver的能力，
		// 通过DefaultVariableResolver调用
		// FieldTypeProvider:从Field中获取的类型的SerializableTypeWrapper.TypeProvider
		// 返回由通过DefaultVariableResolver支持的FieldTypeProvider的ResolvableType对象
		return forType(null, new FieldTypeProvider(field), owner.asVariableResolver());
	}

	/**
	 * 使用给定的时间，为指定的{@link Field}返回一个{@link ResolvableType}
	 *
	 * Return a {@link ResolvableType} for the specified {@link Field} with a given
	 * implementation.
	 * <p>Use this variant when the class that declares the field includes generic
	 * parameter variables that are satisfied by the implementation type.
	 * @param field the source field
	 * @param implementationType the implementation type
	 * @return a {@link ResolvableType} for the specified field
	 * @see #forField(Field)
	 */
	public static ResolvableType forField(Field field, @Nullable ResolvableType implementationType) {
		Assert.notNull(field, "Field must not be null");
		// 如果implementationType为null，就使用NONE
		ResolvableType owner = (implementationType != null ? implementationType : NONE);
		// 将owner作为field的声明类的ResolvableType对象重新赋值给owner
		owner = owner.as(field.getDeclaringClass());
		// owner.asVariableResolver:将owner修改为DefaultVariableResolver,因为每个ResolvableType对象都具有VariableResolver的能力，
		// 通过DefaultVariableResolver调用
		// FieldTypeProvider:从Field中获取的类型的SerializableTypeWrapper.TypeProvider
		// 返回由通过DefaultVariableResolver支持的FieldTypeProvider的ResolvableType对象
		return forType(null, new FieldTypeProvider(field), owner.asVariableResolver());
	}

	/**
	 * 使用给定嵌套等级返回指定的{@link Field}的{@link ResolvableType}
	 *
	 * Return a {@link ResolvableType} for the specified {@link Field} with the
	 * given nesting level.
	 * @param field the source field
	 * @param nestingLevel the nesting level (1 for the outer level; 2 for a nested
	 * generic type; etc)
	 * @see #forField(Field)
	 */
	public static ResolvableType forField(Field field, int nestingLevel) {
		Assert.notNull(field, "Field must not be null");
		// FieldTypeProvider:从Field中获取的类型的SerializableTypeWrapper.TypeProvider
		// 获取由通过DefaultVariableResolver支持的FieldTypeProvider的ResolvableType对象,返回获取其nestingLevel的
		// ResolvableType对象并返回出去
		return forType(null, new FieldTypeProvider(field), null).getNested(nestingLevel);
	}

	/**
	 * 使用给定嵌套等级返回指定的{@link Field}的{@link ResolvableType}
	 *
	 * Return a {@link ResolvableType} for the specified {@link Field} with a given
	 * implementation and the given nesting level.
	 * <p>Use this variant when the class that declares the field includes generic
	 * parameter variables that are satisfied by the implementation class.
	 * @param field the source field
	 * @param nestingLevel the nesting level (1 for the outer level; 2 for a nested
	 * generic type; etc)
	 * @param implementationClass the implementation class
	 * @return a {@link ResolvableType} for the specified field
	 * @see #forField(Field)
	 */
	public static ResolvableType forField(Field field, int nestingLevel, @Nullable Class<?> implementationClass) {
		Assert.notNull(field, "Field must not be null");
		// 获取implementationClass的ResolvableType对象，并作为field的声明类的ResolvableType对象返回
		ResolvableType owner = forType(implementationClass).as(field.getDeclaringClass());
		// owner.asVariableResolver:将owner修改为DefaultVariableResolver,因为每个ResolvableType对象都具有VariableResolver的能力，
		// 通过DefaultVariableResolver调用
		// FieldTypeProvider:从Field中获取的类型的SerializableTypeWrapper.TypeProvider
		// 返回由通过DefaultVariableResolver支持的FieldTypeProvider的ResolvableType对象
		return forType(null, new FieldTypeProvider(field), owner.asVariableResolver()).getNested(nestingLevel);
	}

	/**
	 * 返回指定{@link Constructor} 参数的{@link ResolvableType}
	 *
	 * Return a {@link ResolvableType} for the specified {@link Constructor} parameter.
	 * @param constructor the source constructor (must not be {@code null})
	 * @param parameterIndex the parameter index
	 * @return a {@link ResolvableType} for the specified constructor parameter
	 * @see #forConstructorParameter(Constructor, int, Class)
	 */
	public static ResolvableType forConstructorParameter(Constructor<?> constructor, int parameterIndex) {
		Assert.notNull(constructor, "Constructor must not be null");
		// 使用嵌套级别1为constructor创建一个新的MethodParameter,然后获取该MethodParameter对象的ResolvableType对象
		return forMethodParameter(new MethodParameter(constructor, parameterIndex));
	}

	/**
	 * 使用给定的实现返回指定{@link Constructor}参数的{@link ResolvableType}.当声明field的类包含实现类满足的泛型参数时，请使用此变体
	 *
	 * Return a {@link ResolvableType} for the specified {@link Constructor} parameter
	 * with a given implementation. Use this variant when the class that declares the
	 * constructor includes generic parameter variables that are satisfied by the
	 * implementation class.
	 * @param constructor the source constructor (must not be {@code null})
	 * @param parameterIndex the parameter index
	 * @param implementationClass the implementation class
	 * @return a {@link ResolvableType} for the specified constructor parameter
	 * @see #forConstructorParameter(Constructor, int)
	 */
	public static ResolvableType forConstructorParameter(Constructor<?> constructor, int parameterIndex,
			Class<?> implementationClass) {

		Assert.notNull(constructor, "Constructor must not be null");
		// 创建带有已设置的类的MethodParameter对象
		MethodParameter methodParameter = new MethodParameter(constructor, parameterIndex, implementationClass);
		// 返回methodParameter的ResolvableType
		return forMethodParameter(methodParameter);
	}

	/**
	 * 返回指定{@link Method}的返回类型的ResolvableType对象
	 *
	 * Return a {@link ResolvableType} for the specified {@link Method} return type.
	 * @param method the source for the method return type
	 * @return a {@link ResolvableType} for the specified method return
	 * @see #forMethodReturnType(Method, Class)
	 */
	public static ResolvableType forMethodReturnType(Method method) {
		Assert.notNull(method, "Method must not be null");
		// 使用嵌套级别1为给定方法创建一个新的MethodParameter,parameterIndex为-1表示方法的返回类型
		// 再返回methodParameter的ResolvableType对象
		return forMethodParameter(new MethodParameter(method, -1));
	}

	/**
	 * 使用给定嵌套等级返回指定的{@link Field}的{@link ResolvableType},当声明field的类包含实现类满足的泛型参数时，请使用此变体
	 *
	 * Return a {@link ResolvableType} for the specified {@link Method} return type.
	 * Use this variant when the class that declares the method includes generic
	 * parameter variables that are satisfied by the implementation class.
	 * @param method the source for the method return type
	 * @param implementationClass the implementation class
	 * @return a {@link ResolvableType} for the specified method return
	 * @see #forMethodReturnType(Method)
	 */
	public static ResolvableType forMethodReturnType(Method method, Class<?> implementationClass) {
		Assert.notNull(method, "Method must not be null");
		// 使用嵌套级别1为method创建一个新的MethodParameter对象,parameterIndex为-1表示方法的返回类型
		MethodParameter methodParameter = new MethodParameter(method, -1, implementationClass);
		// 返回methodParameter的ResolvableType对象
		return forMethodParameter(methodParameter);
	}

	/**
	 * 返回指定{@link Method}参数的{@link ResolvableType}
	 *
	 * Return a {@link ResolvableType} for the specified {@link Method} parameter.
	 * @param method the source method (must not be {@code null})
	 * @param parameterIndex the parameter index
	 * @return a {@link ResolvableType} for the specified method parameter
	 * @see #forMethodParameter(Method, int, Class)
	 * @see #forMethodParameter(MethodParameter)
	 */
	public static ResolvableType forMethodParameter(Method method, int parameterIndex) {
		Assert.notNull(method, "Method must not be null");
		// 使用嵌套级别1为method创建一个新的MethodParameter对象,再返回methodParameter对象的ResolvableType对象
		return forMethodParameter(new MethodParameter(method, parameterIndex));
	}

	/**
	 * 使用给定实现类返回指定的{@link Method}参数的{@link ResolvableType},当声明method的类包含实现类满足的泛型参数时，请使用此变体
	 *
	 * Return a {@link ResolvableType} for the specified {@link Method} parameter with a
	 * given implementation. Use this variant when the class that declares the method
	 * includes generic parameter variables that are satisfied by the implementation class.
	 * @param method the source method (must not be {@code null})
	 * @param parameterIndex the parameter index
	 * @param implementationClass the implementation class
	 * @return a {@link ResolvableType} for the specified method parameter
	 * @see #forMethodParameter(Method, int, Class)
	 * @see #forMethodParameter(MethodParameter)
	 */
	public static ResolvableType forMethodParameter(Method method, int parameterIndex, Class<?> implementationClass) {
		Assert.notNull(method, "Method must not be null");
		// 使用嵌套级别1为method创建一个新的MethodParameter对象,parameterIndex为-1表示方法的返回类型
		MethodParameter methodParameter = new MethodParameter(method, parameterIndex, implementationClass);
		// 返回methodParameter的ResolvableType对象
		return forMethodParameter(methodParameter);
	}

	/**
	 * 返回指定{@link MethodParameter}的{@link ResolvableType}
	 *
	 * Return a {@link ResolvableType} for the specified {@link MethodParameter}.
	 * @param methodParameter the source method parameter (must not be {@code null})
	 * @return a {@link ResolvableType} for the specified method parameter
	 * @see #forMethodParameter(Method, int)
	 */
	public static ResolvableType forMethodParameter(MethodParameter methodParameter) {
		return forMethodParameter(methodParameter, (Type) null);
	}

	/**
	 * 使用给定实现类型返回指定{@link MethodParameter}的{@link ResolvableType},当声明field的类包含实现类满足的泛型参数时，请使用此变体
	 *
	 * Return a {@link ResolvableType} for the specified {@link MethodParameter} with a
	 * given implementation type. Use this variant when the class that declares the method
	 * includes generic parameter variables that are satisfied by the implementation type.
	 * @param methodParameter the source method parameter (must not be {@code null})
	 * @param implementationType the implementation type
	 * @return a {@link ResolvableType} for the specified method parameter
	 * @see #forMethodParameter(MethodParameter)
	 */
	public static ResolvableType forMethodParameter(MethodParameter methodParameter,
			@Nullable ResolvableType implementationType) {

		Assert.notNull(methodParameter, "MethodParameter must not be null");
		// 如果实现类型为null,获取构造表示methodParameterr的包含类（默认情况下是声明method的类)的ResolvableType对象
		implementationType = (implementationType != null ? implementationType :
				forType(methodParameter.getContainingClass()));
		// 将implementationType作为methodParameter的声明类的ResolvableType返回。搜索supertype和interface层次结构以找到匹配项，
		// 如果此类不会实现或者继承指定类， 返回NONE
		ResolvableType owner = implementationType.as(methodParameter.getDeclaringClass());
		// MethodParameterTypeProvider:从MethodParameter中获得的类型的SerializableTypeWrapper.TypeProvider
		// owner.asVariableResolver:将owner修改为DefaultVariableResolver,因为每个ResolvableType对象都具有VariableResolver的能力，
		// 通过DefaultVariableResolver调用
		// FieldTypeProvider:从Field中获取的类型的SerializableTypeWrapper.TypeProvider
		// 返回由给定ResolvableType.VariableResolver支持的指定Type的ResolvableType
		// getNested:返回methodParameter的嵌套等级的ResolvableType对象,methodParameter.typeIndexesPerLevel:从整数嵌套等级到整数类
		// 型索引的映射.如 key=1，value=2,表示第 1级的第2个索引位置的泛型
		// 返回由DefaultVariableResolver支持的MethodParameterTypeProvider对象的type属性的ResolvableType对象
		return forType(null, new MethodParameterTypeProvider(methodParameter), owner.asVariableResolver()).
				getNested(methodParameter.getNestingLevel(), methodParameter.typeIndexesPerLevel);
	}

	/**
	 * 返回指定{@link MethodParameter}的{@link ResolvableType}对象，覆盖目标类型以使用特定的给定类型进行解析
	 *
	 * Return a {@link ResolvableType} for the specified {@link MethodParameter},
	 * overriding the target type to resolve with a specific given type.
	 * @param methodParameter the source method parameter (must not be {@code null})
	 * @param targetType the type to resolve (a part of the method parameter's type)
	 * @return a {@link ResolvableType} for the specified method parameter
	 * @see #forMethodParameter(Method, int)
	 */
	public static ResolvableType forMethodParameter(MethodParameter methodParameter, @Nullable Type targetType) {
		Assert.notNull(methodParameter, "MethodParameter must not be null");
		// 在methodParameter的嵌套级别为methodParameter返回一个ResolvableType对象,覆盖目标类类型以使用targetType进行解析
		return forMethodParameter(methodParameter, targetType, methodParameter.getNestingLevel());
	}

	/**
	 * 在特定的嵌套级别为指定的{@link MethodParameter}返回一个{@link ResolvableType},覆盖目标类类型以使用特定的给定类型进行解析
	 *
	 * Return a {@link ResolvableType} for the specified {@link MethodParameter} at
	 * a specific nesting level, overriding the target type to resolve with a specific
	 * given type.
	 * @param methodParameter the source method parameter (must not be {@code null})
	 * @param targetType the type to resolve (a part of the method parameter's type)
	 * @param nestingLevel the nesting level to use
	 * @return a {@link ResolvableType} for the specified method parameter
	 * @since 5.2
	 * @see #forMethodParameter(Method, int)
	 */
	static ResolvableType forMethodParameter(
			MethodParameter methodParameter, @Nullable Type targetType, int nestingLevel) {

		// 获取构造表示methodParameterr的包含类（默认情况下是声明method的类)的ResolvableType对象,
		// 将ResolvableType对象作为methodParameter的声明类的ResolvableType对象
		ResolvableType owner = forType(methodParameter.getContainingClass()).as(methodParameter.getDeclaringClass());
		// MethodParameterTypeProvider:从MethodParameter中获得的类型的SerializableTypeWrapper.TypeProvider
		// owner.asVariableResolver:将owner修改为DefaultVariableResolver,因为每个ResolvableType对象都具有VariableResolver的能力，
		// 通过DefaultVariableResolver调用
		// FieldTypeProvider:从Field中获取的类型的SerializableTypeWrapper.TypeProvider
		// 返回由给定ResolvableType.VariableResolver支持的指定Type的ResolvableType
		// getNested:返回methodParameter的嵌套等级的ResolvableType对象,methodParameter.typeIndexesPerLevel:从整数嵌套等级到整数类
		// 型索引的映射.如 key=1，value=2,表示第 1级的第2个索引位置的泛型
		// 返回由DefaultVariableResolver支持的MethodParameterTypeProvider对象的type属性的ResolvableType对象
		return forType(targetType, new MethodParameterTypeProvider(methodParameter), owner.asVariableResolver()).
				getNested(nestingLevel, methodParameter.typeIndexesPerLevel);
	}

	/**
	 * 返回给定{@code conmponentType}数组的{@link ResolvableType}
	 *
	 * Return a {@link ResolvableType} as a array of the specified {@code componentType}.
	 * @param componentType the component type
	 * @return a {@link ResolvableType} as an array of the specified component type
	 */
	public static ResolvableType forArrayComponent(ResolvableType componentType) {
		Assert.notNull(componentType, "Component type must not be null");
		// 将componentType的type属性解析为Class,构建出其类型数组，长度为0，然后获取这个数组的类对象
		Class<?> arrayClass = Array.newInstance(componentType.resolve(), 0).getClass();
		// 创建一个新的ResolvableType用于未缓存目标
		return new ResolvableType(arrayClass, null, null, componentType);
	}

	/**
	 * 返回指定{@link Type}的{@link ResolvableType}
	 *
	 * Return a {@link ResolvableType} for the specified {@link Type}.
	 * <p>Note: The resulting {@link ResolvableType} instance may not be {@link Serializable}.
	 * @param type the source type (potentially {@code null})
	 * @return a {@link ResolvableType} for the specified {@link Type}
	 * @see #forType(Type, ResolvableType)
	 */
	public static ResolvableType forType(@Nullable Type type) {
		return forType(type, null, null);
	}

	/**
	 * 返回支持所有者类型的指定{@link Type}的{@link ResolvableType}
	 *
	 * Return a {@link ResolvableType} for the specified {@link Type} backed by the given
	 * owner type.
	 * <p>Note: The resulting {@link ResolvableType} instance may not be {@link Serializable}.
	 * @param type the source type or {@code null}
	 * @param owner the owner type used to resolve variables
	 * @return a {@link ResolvableType} for the specified {@link Type} and owner
	 * @see #forType(Type)
	 */
	public static ResolvableType forType(@Nullable Type type, @Nullable ResolvableType owner) {
		// 初始化variableResolver为null
		VariableResolver variableResolver = null;
		// 如果所有者不为null
		if (owner != null) {
			// owner.asVariableResolver:将owner修改为DefaultVariableResolver,因为每个ResolvableType对象都具有VariableResolver的能力，
			// 通过DefaultVariableResolver调用
			variableResolver = owner.asVariableResolver();
		}
		// 返回由variableResolver支持的type的ResolvableType
		return forType(type, variableResolver);
	}


	/**
	 * 返回指定{@link ParameterizedTypeReference}的{@link ResolvableType}
	 *
	 * Return a {@link ResolvableType} for the specified {@link ParameterizedTypeReference}.
	 * <p>Note: The resulting {@link ResolvableType} instance may not be {@link Serializable}.
	 * @param typeReference the reference to obtain the source type from
	 * @return a {@link ResolvableType} for the specified {@link ParameterizedTypeReference}
	 * @since 4.3.12
	 * @see #forType(Type)
	 */
	public static ResolvableType forType(ParameterizedTypeReference<?> typeReference) {
		// 返回由DefaultVariableResolver支持的typeReference的type属性的ResolvableType
		return forType(typeReference.getType(), null, null);
	}

	/**
	 * 返回由给定{@link VariableResolver}支持的指定{@link Type}的{@link ResolvableType}
	 *
	 * Return a {@link ResolvableType} for the specified {@link Type} backed by a given
	 * {@link VariableResolver}.
	 * @param type the source type or {@code null}
	 * @param variableResolver the variable resolver or {@code null}
	 * @return a {@link ResolvableType} for the specified {@link Type} and {@link VariableResolver}
	 */
	static ResolvableType forType(@Nullable Type type, @Nullable VariableResolver variableResolver) {
		return forType(type, null, variableResolver);
	}

	/**
	 * 返回由给定{@link VariableResolver}支持的指定{@link Type}的{@link ResolvableType}
	 *
	 * Return a {@link ResolvableType} for the specified {@link Type} backed by a given
	 * {@link VariableResolver}.
	 * @param type the source type or {@code null}
	 * @param typeProvider the type provider or {@code null}
	 * @param variableResolver the variable resolver or {@code null}
	 * @return a {@link ResolvableType} for the specified {@link Type} and {@link VariableResolver}
	 */
	static ResolvableType forType(
			@Nullable Type type, @Nullable TypeProvider typeProvider, @Nullable VariableResolver variableResolver) {

		// 如果type为null且typeProvider不为null
		if (type == null && typeProvider != null) {
			// 获取由typeProvider支持的序列化Type(代理对象)
			type = SerializableTypeWrapper.forTypeProvider(typeProvider);
		}
		// 如果type为null
		if (type == null) {
			// 返回NONE，表示没有可用的值
			return NONE;
		}

		// For simple Class references, build the wrapper right away -
		// no expensive resolution necessary, so not worth caching...
		// 对应简单的Class引用，立即构建包装器 - 不需要昂贵的解析，因此不知道缓存
		// 如果type是Class的子类或本身
		if (type instanceof Class) {
			// 创建一个新的ResolvableType用于未缓存目标,其具有前期解析方案，但是以懒汉式形式计算哈希值
			return new ResolvableType(type, typeProvider, variableResolver, (ResolvableType) null);
		}

		// Purge empty entries on access since we don't have a clean-up thread or the like.
		// 由于我们没有清理线程等，因此清除访问时的空entries
		// purgeUnreferencedEntries:删除所有已被垃圾回收且不再被引用的条目。在正常情况下，随着项目从映射中添加或删除，
		// 垃圾收集条目将自动清除。此方法可用于强制清除，当频繁读取Map当更新批量较低时
		cache.purgeUnreferencedEntries();

		// Check the cache - we may have a ResolvableType which has been resolved before...
		// 检查缓存-我们可能有一个ResolvableType，它已经在...之前解析了
		// 创建新的ResolvableType以用于缓存密钥，无需预先解决
		ResolvableType resultType = new ResolvableType(type, typeProvider, variableResolver);
		// 从缓存中获取resultType对应的ResolvableType对象
		ResolvableType cachedType = cache.get(resultType);
		// 如果cacheTyoe为null，表示缓存中没有
		if (cachedType == null) {
			// 重新创建一个ResolvableType对象作为cacheType
			cachedType = new ResolvableType(type, typeProvider, variableResolver, resultType.hash);
			// 将cachedType添加到cache中
			cache.put(cachedType, cachedType);
		}
		// 设置resultType的已解析类为cachedType的已解析类
		resultType.resolved = cachedType.resolved;
		// type和variableResolver的ResolvableType对象
		return resultType;
	}

	/**
	 * 清除内部的{@code ResolvableType}缓存和{@code SerializableTypeWrapper}缓存
	 *
	 * Clear the internal {@code ResolvableType}/{@code SerializableTypeWrapper} cache.
	 * @since 4.2
	 */
	public static void clearCache() {
		// 清空resolvableType对象映射缓存
		cache.clear();
		// 清空序列化类型包装对象缓存
		SerializableTypeWrapper.cache.clear();
	}


	/**
	 *  解析{@link TypeVariable}的策略接口
	 *
	 * Strategy interface used to resolve {@link TypeVariable TypeVariables}.
	 */
	interface VariableResolver extends Serializable {

		/**
		 * 返回解析的源对象（用于hashCode和equals）
		 *
		 * Return the source of the resolver (used for hashCode and equals).
		 */
		Object getSource();

		/**
		 * 解析指定的变量
		 *
		 * Resolve the specified variable.
		 * @param variable the variable to resolve
		 * @return the resolved variable, or {@code null} if not found
		 */
		@Nullable
		ResolvableType resolveVariable(TypeVariable<?> variable);
	}


	@SuppressWarnings("serial")
	/**
	 * 默认的变量解析器，使用ResolvableType对象对类型变量进行解析
	 */
	private static class DefaultVariableResolver implements VariableResolver {

		// 可解析类型源对象
		private final ResolvableType source;

		DefaultVariableResolver(ResolvableType resolvableType) {
			this.source = resolvableType;
		}

		@Override
		@Nullable
		public ResolvableType resolveVariable(TypeVariable<?> variable) {
			return this.source.resolveVariable(variable);
		}

		/**
		 * 获取可解析类型源对象
		 */
		@Override
		public Object getSource() {
			return this.source;
		}
	}


	@SuppressWarnings("serial")
	private static class TypeVariablesVariableResolver implements VariableResolver {

		// 泛型数组
		private final TypeVariable<?>[] variables;

		// 表示泛型数组的ResolvablType数组
		private final ResolvableType[] generics;

		public TypeVariablesVariableResolver(TypeVariable<?>[] variables, ResolvableType[] generics) {
			this.variables = variables;
			this.generics = generics;
		}

		@Override
		@Nullable
		public ResolvableType resolveVariable(TypeVariable<?> variable) {
			TypeVariable<?> variableToCompare = SerializableTypeWrapper.unwrap(variable);
			for (int i = 0; i < this.variables.length; i++) {
				// 解开variables[i]，有效地返回原始的不可序列化类型。
				TypeVariable<?> resolvedVariable = SerializableTypeWrapper.unwrap(this.variables[i]);
				if (ObjectUtils.nullSafeEquals(resolvedVariable, variableToCompare)) {
					return this.generics[i];
				}
			}
			return null;
		}

		@Override
		public Object getSource() {
			return this.generics;
		}
	}


	/**
	 * 综合参数化类型
	 */
	private static final class SyntheticParameterizedType implements ParameterizedType, Serializable {

		/**
		 * 原始类型
		 */
		private final Type rawType;

		/**
		 * 类型参数
		 */
		private final Type[] typeArguments;

		// 构建一个综合参数化类型实例
		public SyntheticParameterizedType(Type rawType, Type[] typeArguments) {
			this.rawType = rawType;
			this.typeArguments = typeArguments;
		}

		@Override
		public String getTypeName() {
			// getTypeName:返回此类型名称的信息字符串,可以理解为返回的是该类类型的名称
			// 获取rawType的类型名
			String typeName = this.rawType.getTypeName();
			// 如果存在类型参数
			if (this.typeArguments.length > 0) {
				// StringJoiner是Java8新出的一个类，用于构造由分隔符分隔的字符序列，并可选择性地从提供的前缀开
				// 始和以提供的后缀结尾。省的我们开发人员再次通过StringBuffer或者StingBuilder拼接。
				// 定义一个分割符为', ',开头为'<',结尾为'>'的StringJoiner对象
				StringJoiner stringJoiner = new StringJoiner(", ", "<", ">");
				// 遍历typeArguments
				for (Type argument : this.typeArguments) {
					// 获取argument的类型名,并添加到stringJoiner中
					stringJoiner.add(argument.getTypeName());
				}
				// 类型名拼接stringJoiner
				return typeName + stringJoiner;
			}
			// 返回rawType的类型名
			return typeName;
		}

		@Override
		@Nullable
		public Type getOwnerType() {
			// 不管是否是其成员之一的类型，都返回null
			return null;
		}

		@Override
		public Type getRawType() {
			return this.rawType;
		}

		@Override
		public Type[] getActualTypeArguments() {
			return this.typeArguments;
		}

		@Override
		public boolean equals(@Nullable Object other) {
			// 如果本类对象与other的地址相等
			if (this == other) {
				// 返回true，表示相等
				return true;
			}
			// 如果other不是ParameterizedType的子类或本类
			if (!(other instanceof ParameterizedType)) {
				// 返回false，表示不相等
				return false;
			}
			// 强转other为ParameterizedType对象
			ParameterizedType otherType = (ParameterizedType) other;
			// getOwnerType()返回Type对象，表示此类型是其成员之一的类型。例如，如果此类型为 O.I，
			// 则返回O的表示形式。如果此类型为顶层类型，则返回 null。
			// ParameterizedType.getActualTypeArgments:获取泛型中的实际类型，可能会存在多个泛型，
			// 例如Map<K,V>,所以会返回Type[]数组；
			// 如果otherType的顶层类型为null 且 本类对象的原始类型等于otherType的原始类型 且 本类对象的typeArgumnents
			// 与otherType的泛型中的实际类型 相等
			return (otherType.getOwnerType() == null && this.rawType.equals(otherType.getRawType()) &&
					Arrays.equals(this.typeArguments, otherType.getActualTypeArguments()));
		}

		@Override
		public int hashCode() {
			// 以本类对象的rawType属性和typeArgument属性的哈希值作为特征值进行计算本类对象哈希值
			return (this.rawType.hashCode() * 31 + Arrays.hashCode(this.typeArguments));
		}

		@Override
		public String toString() {
			return getTypeName();
		}
	}


	/**
	 * 内部辅助程序，用于处理{@link WildcardType WildcardTypes}的范围
	 *
	 * Internal helper to handle bounds from {@link WildcardType WildcardTypes}.
	 */
	private static class WildcardBounds {

		/**
		 * 界限枚举对象
		 */
		private final Kind kind;

		/**
		 * 范围中的ResolvableType对象
		 */
		private final ResolvableType[] bounds;

		/**
		 * 内部构造函数，用于创建新的{@link WildcardBounds}实例
		 *
		 * Internal constructor to create a new {@link WildcardBounds} instance.
		 * @param kind the kind of bounds
		 * @param bounds the bounds
		 * @see #get(ResolvableType)
		 */
		public WildcardBounds(Kind kind, ResolvableType[] bounds) {
			this.kind = kind;
			this.bounds = bounds;
		}

		/**
		 * 如果此界限与指定界限相同，则返回{@code true}
		 *
		 * Return {@code true} if this bounds is the same kind as the specified bounds.
		 */
		public boolean isSameKind(WildcardBounds bounds) {
			return this.kind == bounds.kind;
		}

		/**
		 * 如果此范围可分配给所有指导的类型，则返回{@code true}
		 *
		 * Return {@code true} if this bounds is assignable to all the specified types.
		 * @param types the types to test against
		 * @return {@code true} if this bounds is assignable to all types
		 */
		public boolean isAssignableFrom(ResolvableType... types) {
			// 遍历本类对象所保存范围中的ResolvableType对象
			for (ResolvableType bound : this.bounds) {
				// 遍历传入的ResovlableType对象数组
				for (ResolvableType type : types) {
					// 如果其中一个type不是bond的子类或本身
					if (!isAssignable(bound, type)) {
						// 直接返回false，表示此界限不可分配给所有类型
						return false;
					}
				}
			}
			// 直接返回true，表示此界限可分配给所有类型
			return true;
		}

		/**
		 * 给定的类是否可分配
		 * @param source
		 * @param from
		 * @return
		 */
		private boolean isAssignable(ResolvableType source, ResolvableType from) {
			// 如果本类对象的界限枚举对象是上界限，判断source是否是from的父类或本身，并返回结果；否则判断from是否是source的父类或本身
			return (this.kind == Kind.UPPER ? source.isAssignableFrom(from) : from.isAssignableFrom(source));
		}

		/**
		 * 返回底层的界限
		 *
		 * Return the underlying bounds.
		 */
		public ResolvableType[] getBounds() {
			return this.bounds;
		}

		/**
		 * 获取指定类型的{@link WildcardBounds}实例，如果给定类型不能解析成{@link WildcardType}返回null
		 *
		 * Get a {@link WildcardBounds} instance for the specified type, returning
		 * {@code null} if the specified type cannot be resolved to a {@link WildcardType}.
		 * @param type the source type
		 * @return a {@link WildcardBounds} instance or {@code null}
		 */
		@Nullable
		public static WildcardBounds get(ResolvableType type) {
			// 将传入的type作为要解析成Wildcard的ResovlableType对象
			ResolvableType resolveToWildcard = type;
			// 如果resolveToWildcard的受管理的基础JavaType是WildcardType的子类
			while (!(resolveToWildcard.getType() instanceof WildcardType)) {
				// 如果resolvedWildcard为NONE
				if (resolveToWildcard == NONE) {
					// 返回null
					return null;
				}
				// 通过单级解析重新解析resolvedToWildcard对象
				resolveToWildcard = resolveToWildcard.resolveType();
			}
			// 将resolvedToWildcard对象的type强转为WildcardType对象
			WildcardType wildcardType = (WildcardType) resolveToWildcard.type;
			// 如果wildcardType存在下边界，设置范围类型为下边界，否则为上边界
			Kind boundsType = (wildcardType.getLowerBounds().length > 0 ? Kind.LOWER : Kind.UPPER);
			// 如果边界类型是上边界，就获取上边界的类型；否则获取下边界的类型
			Type[] bounds = (boundsType == Kind.UPPER ? wildcardType.getUpperBounds() : wildcardType.getLowerBounds());
			// 定义一个存放ResolvableType对象，长度为bounds的长度的数组，用于对bounds的每个元素进行包装成ResolvableType对象
			ResolvableType[] resolvableBounds = new ResolvableType[bounds.length];
			// 遍历bounds
			for (int i = 0; i < bounds.length; i++) {
				// 取出bounds中第i个元素，对其进行包装成ResolvableType对象，然后设置到resolvableBounds的第i个元素上
				resolvableBounds[i] = ResolvableType.forType(bounds[i], type.variableResolver);
			}
			// 创建新的ResolvableType.WildcardBounds实例
			return new WildcardBounds(boundsType, resolvableBounds);
		}

		/**
		 * 各种界限
		 * The various kinds of bounds.
		 */
		enum Kind {UPPER, LOWER}
	}


	/**
	 * 内部{@link Type}用于表示一个空值，内部类式单例模式
	 *
	 * Internal {@link Type} used to represent an empty value.
	 */
	@SuppressWarnings("serial")
	static class EmptyType implements Type, Serializable {

		/**
		 * 定义一个单例EmptyType对象
		 */
		static final Type INSTANCE = new EmptyType();

		/**
		 * 阅读解析，返回EmtptyType单例对象
		 * @return EmtptyType单例对象
		 */
		Object readResolve() {
			return INSTANCE;
		}
	}

}
