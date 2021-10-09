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

package org.springframework.beans.factory.support;

import java.beans.PropertyEditor;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeansException;
import org.springframework.beans.PropertyEditorRegistrar;
import org.springframework.beans.PropertyEditorRegistry;
import org.springframework.beans.PropertyEditorRegistrySupport;
import org.springframework.beans.SimpleTypeConverter;
import org.springframework.beans.TypeConverter;
import org.springframework.beans.TypeMismatchException;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.BeanCurrentlyInCreationException;
import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.BeanIsAbstractException;
import org.springframework.beans.factory.BeanIsNotAFactoryException;
import org.springframework.beans.factory.BeanNotOfRequiredTypeException;
import org.springframework.beans.factory.CannotLoadBeanClassException;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.SmartFactoryBean;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.config.BeanExpressionContext;
import org.springframework.beans.factory.config.BeanExpressionResolver;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.beans.factory.config.DestructionAwareBeanPostProcessor;
import org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessor;
import org.springframework.beans.factory.config.Scope;
import org.springframework.core.AttributeAccessor;
import org.springframework.core.DecoratingClassLoader;
import org.springframework.core.NamedThreadLocal;
import org.springframework.core.ResolvableType;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.log.LogMessage;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.util.StringValueResolver;

/**
 * BeanFactory实现的抽象基类，提供了ConfigurableBeanFactory SPI的全部功能。
 * 不会假设有一个可列出的bean工厂:因此也可以用作bean工厂实现的基类，这些实现从某个后端资源获取bean
 * 定义(其中bean定义访问是一个昂贵的操作)
 *
 * Abstract base class for {@link org.springframework.beans.factory.BeanFactory}
 * implementations, providing the full capabilities of the
 * {@link org.springframework.beans.factory.config.ConfigurableBeanFactory} SPI.
 * Does <i>not</i> assume a listable bean factory: can therefore also be used
 * as base class for bean factory implementations which obtain bean definitions
 * from some backend resource (where bean definition access is an expensive operation).
 *
 * <p>This class provides a singleton cache (through its base class
 * {@link org.springframework.beans.factory.support.DefaultSingletonBeanRegistry},
 * singleton/prototype determination, {@link org.springframework.beans.factory.FactoryBean}
 * handling, aliases, bean definition merging for child bean definitions,
 * and bean destruction ({@link org.springframework.beans.factory.DisposableBean}
 * interface, custom destroy methods). Furthermore, it can manage a bean factory
 * hierarchy (delegating to the parent in case of an unknown bean), through implementing
 * the {@link org.springframework.beans.factory.HierarchicalBeanFactory} interface.
 *
 * <p>The main template methods to be implemented by subclasses are
 * {@link #getBeanDefinition} and {@link #createBean}, retrieving a bean definition
 * for a given bean name and creating a bean instance for a given bean definition,
 * respectively. Default implementations of those operations can be found in
 * {@link DefaultListableBeanFactory} and {@link AbstractAutowireCapableBeanFactory}.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @author Costin Leau
 * @author Chris Beams
 * @author Phillip Webb
 * @since 15 April 2001
 * @see #getBeanDefinition
 * @see #createBean
 * @see AbstractAutowireCapableBeanFactory#createBean
 * @see DefaultListableBeanFactory#getBeanDefinition
 */
public abstract class AbstractBeanFactory extends FactoryBeanRegistrySupport implements ConfigurableBeanFactory {

	/** Parent bean factory, for bean inheritance support. */
	@Nullable
	private BeanFactory parentBeanFactory;

	/**
	 * 必要时使用ClassLoader解析Bean类名称,默认使用线程上下文类加载器
	 *
	 * ClassLoader to resolve bean class names with, if necessary. */
	@Nullable
	private ClassLoader beanClassLoader = ClassUtils.getDefaultClassLoader();

	/**
	 * 必要时使用ClassLoader临时解析Bean类名称
	 *
	 * ClassLoader to temporarily resolve bean class names with, if necessary. */
	@Nullable
	private ClassLoader tempClassLoader;

	/**
	 * 是否缓存bean元数据还是每次访问重新获取它
	 *
	 * Whether to cache bean metadata or rather reobtain it for every access. */
	private boolean cacheBeanMetadata = true;

	/**
	 * bean定义值中表达式的解析策略,SpringBoot默认使用的是StandardBeanExpressionResolver
	 *
	 * Resolution strategy for expressions in bean definition values. */
	@Nullable
	private BeanExpressionResolver beanExpressionResolver;

	/**
	 * ConversionService:一个类型转换的服务接口。这个转换系统的入口。 调用convert(Object, Class)去执行一个线程安全类型转换器使用此系统。
	 *
	 * Spring ConversionService to use instead of PropertyEditors. */
	@Nullable
	private ConversionService conversionService;

	/**
	 * 定制PropertyEditorRegistrars应用于此工厂的bean
	 *
	 * Custom PropertyEditorRegistrars to apply to the beans of this factory. */
	private final Set<PropertyEditorRegistrar> propertyEditorRegistrars = new LinkedHashSet<>(4);

	/**
	 * 定制PropertyEditor应用于该工厂的bean
	 *
	 * Custom PropertyEditors to apply to the beans of this factory. */
	private final Map<Class<?>, Class<? extends PropertyEditor>> customEditors = new HashMap<>(4);

	/**
	 * 要使用的自定义类型转换器，覆盖默认的PropertyEditor机制
	 *
	 * A custom TypeConverter to use, overriding the default PropertyEditor mechanism. */
	@Nullable
	private TypeConverter typeConverter;

	/**
	 * 字符串解析器适用于注解属性值
	 *
	 * String resolvers to apply e.g. to annotation attribute values. */
	private final List<StringValueResolver> embeddedValueResolvers = new CopyOnWriteArrayList<>();

	/**
	 * BeanPosProcessor应用于createBean
	 *
	 * BeanPostProcessors to apply. */
	private final List<BeanPostProcessor> beanPostProcessors = new CopyOnWriteArrayList<>();

	/**
	 * 指示是否已经注册了任何 InstantiationAwareBeanPostProcessors 对象
	 *
	 * Indicates whether any InstantiationAwareBeanPostProcessors have been registered. */
	private volatile boolean hasInstantiationAwareBeanPostProcessors;

	/**
	 * 表明DestructionAwareBeanPostProcessors是否被注册
	 *
	 * Indicates whether any DestructionAwareBeanPostProcessors have been registered. */
	private volatile boolean hasDestructionAwareBeanPostProcessors;

	/**
	 * 从作用域表示符String映射到相应的作用域
	 *
	 * Map from scope identifier String to corresponding Scope. */
	private final Map<String, Scope> scopes = new LinkedHashMap<>(8);

	/**
	 * 与SecurityManager一起运行时使用的安全上下文
	 *
	 * Security context used when running with a SecurityManager. */
	@Nullable
	private SecurityContextProvider securityContextProvider;

	/**
	 * 从bean名称映射到合并的RootBeanDefinition
	 *
	 * Map from bean name to merged RootBeanDefinition. */
	private final Map<String, RootBeanDefinition> mergedBeanDefinitions = new ConcurrentHashMap<>(256);

	/**
	 * 至少已经创建一次的bean名称
	 *
	 * Names of beans that have already been created at least once. */
	private final Set<String> alreadyCreated = Collections.newSetFromMap(new ConcurrentHashMap<>(256));

	/**
	 * 当前正在创建的bean名称
	 *
	 * Names of beans that are currently in creation. */
	private final ThreadLocal<Object> prototypesCurrentlyInCreation =
			new NamedThreadLocal<>("Prototype beans currently in creation");


	/**
	 * 创建一个新的AbstractBeanFactory
	 *
	 * Create a new AbstractBeanFactory.
	 */
	public AbstractBeanFactory() {
	}

	/**
	 * Create a new AbstractBeanFactory with the given parent.
	 * @param parentBeanFactory parent bean factory, or {@code null} if none
	 * @see #getBean
	 */
	public AbstractBeanFactory(@Nullable BeanFactory parentBeanFactory) {
		this.parentBeanFactory = parentBeanFactory;
	}


	//---------------------------------------------------------------------
	// Implementation of BeanFactory interface
	//---------------------------------------------------------------------

	@Override
	public Object getBean(String name) throws BeansException {
		// 此方法是实际获取bean的方法，也是触发依赖注入的方法
		return doGetBean(name, null, null, false);
	}

	@Override
	public <T> T getBean(String name, Class<T> requiredType) throws BeansException {
		return doGetBean(name, requiredType, null, false);
	}

	@Override
	public Object getBean(String name, Object... args) throws BeansException {
		return doGetBean(name, null, args, false);
	}

	/**
	 * 返回一个实例，该实例可以指定bean的共享或独立
	 *
	 * Return an instance, which may be shared or independent, of the specified bean.
	 * @param name the name of the bean to retrieve
	 * @param requiredType the required type of the bean to retrieve
	 * @param args arguments to use when creating a bean instance using explicit arguments
	 * (only applied when creating a new instance as opposed to retrieving an existing one)
	 * @return an instance of the bean
	 * @throws BeansException if the bean could not be created
	 */
	public <T> T getBean(String name, @Nullable Class<T> requiredType, @Nullable Object... args)
			throws BeansException {

		// 返回一个实例，该实例可以指定bean的共享或独立
		return doGetBean(name, requiredType, args, false);
	}

	/**
	 * 返回一个实例，该实例可以指定bean的共享或独立
	 *
	 * Return an instance, which may be shared or independent, of the specified bean.
	 * @param name the name of the bean to retrieve
	 * @param requiredType the required type of the bean to retrieve
	 * @param args arguments to use when creating a bean instance using explicit arguments
	 * (only applied when creating a new instance as opposed to retrieving an existing one)
	 * @param typeCheckOnly whether the instance is obtained for a type check,
	 * not for actual use
	 * @return an instance of the bean
	 * @throws BeansException if the bean could not be created
	 */
	@SuppressWarnings("unchecked")
	protected <T> T doGetBean(
			String name, @Nullable Class<T> requiredType, @Nullable Object[] args, boolean typeCheckOnly)
			throws BeansException {

		/**
		 * 提取对应的beanName，很多同学可能会认为此处直接使用即可，为什么还要进行转换呢，原因在于当bean对象实现FactoryBean接口之后就会变成&beanName，同时如果存在别名，也需要把别名进行转换*/
		String beanName = transformedBeanName(name);
		Object bean;

		// Eagerly check singleton cache for manually registered singletons.
		/**提前检查单例缓存中是否有手动注册的单例对象，跟循环依赖有关联*/
		Object sharedInstance = getSingleton(beanName);
		// 如果bean的单例对象找到了，且没有创建bean实例时要使用的参数
		if (sharedInstance != null && args == null) {
			if (logger.isTraceEnabled()) {
				if (isSingletonCurrentlyInCreation(beanName)) {
					logger.trace("Returning eagerly cached instance of singleton bean '" + beanName +
							"' that is not fully initialized yet - a consequence of a circular reference");
				}
				else {
					logger.trace("Returning cached instance of singleton bean '" + beanName + "'");
				}
			}
			// 返回对象的实例，很多同学会理解不了这句话存在的意义，当你实现了FactoryBean接口的对象，需要获取具体的对象的时候就需要此方法来进行获取了
			bean = getObjectForBeanInstance(sharedInstance, name, beanName, null);
		}

		else {
			// Fail if we're already creating this bean instance:
			// We're assumably within a circular reference.
			// 当对象都是单例的时候会尝试解决循环依赖的问题，但是原型模式下如果存在循环依赖的情况，那么直接抛出异常
			if (isPrototypeCurrentlyInCreation(beanName)) {
				throw new BeanCurrentlyInCreationException(beanName);
			}

			// Check if bean definition exists in this factory.
			// 如果bean定义不存在，就检查父工厂是否有
			BeanFactory parentBeanFactory = getParentBeanFactory();
			// 如果beanDefinitionMap中也就是在所有已经加载的类中不包含beanName，那么就尝试从父容器中获取
			if (parentBeanFactory != null && !containsBeanDefinition(beanName)) {
				// Not found -> check parent.
				// 获取name对应的规范名称【全类名】，如果name前面有'&'，则会返回'&'+规范名称【全类名】
				String nameToLookup = originalBeanName(name);
				// 如果父工厂是AbstractBeanFactory的实例
				if (parentBeanFactory instanceof AbstractBeanFactory) {
					// 调用父工厂的doGetBean方法，就是该方法。【递归】
					return ((AbstractBeanFactory) parentBeanFactory).doGetBean(
							nameToLookup, requiredType, args, typeCheckOnly);
				}
				else if (args != null) {
					// Delegation to parent with explicit args.
					// 如果有创建bean实例时要使用的参数
					// Delegation to parent with explicit args. 使用显示参数委派给父工厂
					// 使用父工厂获取该bean对象,通bean全类名和创建bean实例时要使用的参数
					return (T) parentBeanFactory.getBean(nameToLookup, args);
				}
				else if (requiredType != null) {
					// No args -> delegate to standard getBean method.
					// 没有创建bean实例时要使用的参数 -> 委托给标准的getBean方法。
					// 使用父工厂获取该bean对象,通bean全类名和所需的bean类型
					return parentBeanFactory.getBean(nameToLookup, requiredType);
				}
				else {
					// 使用父工厂获取bean，通过bean全类名
					return (T) parentBeanFactory.getBean(nameToLookup);
				}
			}
			// 如果不是做类型检查，那么表示要创建bean，此处在集合中做一个记录
			if (!typeCheckOnly) {
				// 为beanName标记为已经创建（或将要创建）
				markBeanAsCreated(beanName);
			}

			try {
				// 此处做了BeanDefinition对象的转换，当我们从xml文件中加载beandefinition对象的时候，封装的对象是GenericBeanDefinition,
				// 此处要做类型转换，如果是子类bean的话，会合并父类的相关属性
				RootBeanDefinition mbd = getMergedLocalBeanDefinition(beanName);
				// 检查mbd的合法性，不合格会引发验证异常
				checkMergedBeanDefinition(mbd, beanName, args);

				// Guarantee initialization of beans that the current bean depends on.
				// 如果存在依赖的bean的话，那么则优先实例化依赖的bean
				String[] dependsOn = mbd.getDependsOn();
				if (dependsOn != null) {
					// 如果存在依赖，则需要递归实例化依赖的bean
					for (String dep : dependsOn) {
						// 如果beanName已注册依赖于dependentBeanName的关系
						if (isDependent(beanName, dep)) {
							throw new BeanCreationException(mbd.getResourceDescription(), beanName,
									"Circular depends-on relationship between '" + beanName + "' and '" + dep + "'");
						}
						// 注册各个bean的依赖关系，方便进行销毁
						registerDependentBean(dep, beanName);
						try {
							// 递归优先实例化被依赖的Bean
							getBean(dep);
						}
						// 捕捉为找到BeanDefinition异常：'beanName'依赖于缺少的bean'dep'
						catch (NoSuchBeanDefinitionException ex) {
							throw new BeanCreationException(mbd.getResourceDescription(), beanName,
									"'" + beanName + "' depends on missing bean '" + dep + "'", ex);
						}
					}
				}

				// Create bean instance.
				// 创建bean的实例对象
				if (mbd.isSingleton()) {
					// 返回以beanName的(原始)单例对象，如果尚未注册，则使用singletonFactory创建并注册一个对象:
					sharedInstance = getSingleton(beanName, () -> {
						try {
							// 为给定的合并后BeanDefinition(和参数)创建一个bean实例
							return createBean(beanName, mbd, args);
						}
						catch (BeansException ex) {
							// Explicitly remove instance from singleton cache: It might have been put there
							// eagerly by the creation process, to allow for circular reference resolution.
							// Also remove any beans that received a temporary reference to the bean.
							// 显示地从单例缓存中删除实例：它可能是由创建过程急切地放在那里，以允许循环引用解析。还要删除
							// 接收到该Bean临时引用的任何Bean
							// 销毁给定的bean。如果找到相应的一次性Bean实例，则委托给destoryBean
							destroySingleton(beanName);
							// 重新抛出ex
							throw ex;
						}
					});
					// 从beanInstance中获取公开的Bean对象，主要处理beanInstance是FactoryBean对象的情况，如果不是
					// FactoryBean会直接返回beanInstance实例
					bean = getObjectForBeanInstance(sharedInstance, name, beanName, mbd);
				}
				// 原型模式的bean对象创建
				else if (mbd.isPrototype()) {
					// It's a prototype -> create a new instance.
					// 它是一个原型 -> 创建一个新实例
					// 定义prototype实例
					Object prototypeInstance = null;
					try {
						// 创建Prototype对象前的准备工作，默认实现将beanName添加到prototypesCurrentlyInCreation中
						beforePrototypeCreation(beanName);
						// 为mbd(和参数)创建一个bean实例
						prototypeInstance = createBean(beanName, mbd, args);
					}
					finally {
						// 创建完prototype实例后的回调，默认是将beanName从prototypesCurrentlyInCreation移除
						afterPrototypeCreation(beanName);
					}
					// 从beanInstance中获取公开的Bean对象，主要处理beanInstance是FactoryBean对象的情况，如果不是
					// FactoryBean会直接返回beanInstance实例
					bean = getObjectForBeanInstance(prototypeInstance, name, beanName, mbd);
				}

				else {
					// 指定的scope上实例化bean
					String scopeName = mbd.getScope();
					if (!StringUtils.hasLength(scopeName)) {
						throw new IllegalStateException("No scope name defined for bean ´" + beanName + "'");
					}
					// 从scopes中获取scopeName对于的Scope对象
					Scope scope = this.scopes.get(scopeName);
					// 如果scope为null
					if (scope == null) {
						// 抛出非法状态异常：没有名为'scopeName'的scope注册
						throw new IllegalStateException("No Scope registered for scope name '" + scopeName + "'");
					}
					try {
						// 从scope中获取beanName对应的实例对象
						Object scopedInstance = scope.get(beanName, () -> {
							// 创建Prototype对象前的准备工作，默认实现 将beanName添加到prototypesCurrentlyInCreation中
							beforePrototypeCreation(beanName);
							try {
								// 为mbd(和参数)创建一个bean实例
								return createBean(beanName, mbd, args);
							}
							finally {
								// 创建完prototype实例后的回调，默认是将beanName从prototypesCurrentlyInCreation移除
								afterPrototypeCreation(beanName);
							}
						});
						// 从beanInstance中获取公开的Bean对象，主要处理beanInstance是FactoryBean对象的情况，如果不是
						// FactoryBean会直接返回beanInstance实例
						bean = getObjectForBeanInstance(scopedInstance, name, beanName, mbd);
					}
					catch (IllegalStateException ex) {
						// 捕捉非法状态异常
						// 抛出Bean创建异常：作用域 'scopeName' 对于当前线程是不活动的；如果您打算从单个实例引用它，请考虑为此
						// beanDefinition一个作用域代理
						throw new BeanCreationException(beanName,
								"Scope '" + scopeName + "' is not active for the current thread; consider " +
								"defining a scoped proxy for this bean if you intend to refer to it from a singleton",
								ex);
					}
				}
			}
			catch (BeansException ex) {
				// 捕捉获取Bean对象抛出的Bean异常
				// 在Bean创建失败后，对缓存的元数据执行适当的清理
				cleanupAfterBeanCreationFailure(beanName);
				// 重新抛出ex
				throw ex;
			}
		}

		// Check if required type matches the type of the actual bean instance.
		// 检查requiredType是否与实际Bean实例的类型匹配
		// 如果requiredType不为null&&bean不是requiredType的实例
		if (requiredType != null && !requiredType.isInstance(bean)) {
			try {
				// 获取此BeanFactory使用的类型转换器，将bean转换为requiredType
				T convertedBean = getTypeConverter().convertIfNecessary(bean, requiredType);
				// 如果convertedBean为null
				if (convertedBean == null) {
					// 抛出Bean不是必要类型的异常
					throw new BeanNotOfRequiredTypeException(name, requiredType, bean.getClass());
				}
				// 返回convertedBean
				return convertedBean;
			}
			catch (TypeMismatchException ex) {
				if (logger.isTraceEnabled()) {
					logger.trace("Failed to convert bean '" + name + "' to required type '" +
							ClassUtils.getQualifiedName(requiredType) + "'", ex);
				}
				throw new BeanNotOfRequiredTypeException(name, requiredType, bean.getClass());
			}
		}
		// 将bean返回出去
		return (T) bean;
	}

	/**
	 * 该bean工厂是否包含具有给定名称的bean定义或外部注册的singleton实例
	 * @param name the name of the bean to query
	 * @return
	 */
	@Override
	public boolean containsBean(String name) {
		// 获取name最终的规范名称【最终别名】
		String beanName = transformedBeanName(name);
		// 如果beanName存在于singletonObjects【单例对象的高速缓存Map集合】中，
		// 或者从beanDefinitionMap【Bean定义对象映射】中存在该beanName的BeanDefinition对象
		if (containsSingleton(beanName) || containsBeanDefinition(beanName)) {
			return (!BeanFactoryUtils.isFactoryDereference(name) || isFactoryBean(name));
		}
		// Not found -> check parent.
		// 获取父工厂
		BeanFactory parentBeanFactory = getParentBeanFactory();
		// 如果父工厂不为null 则递归形式查询该name是否存在于父工厂，并返回执行结果；为null时直接返回false
		// 因为经过上面步骤，已经确定当前工厂不存在该bean的BeanDefinition对象以及singleton实例
		return (parentBeanFactory != null && parentBeanFactory.containsBean(originalBeanName(name)));
	}

	/**
	 * 判断给定的name所指的对象是否为单例
	 * @param name the name of the bean to query
	 * @return
	 * @throws NoSuchBeanDefinitionException
	 */
	@Override
	public boolean isSingleton(String name) throws NoSuchBeanDefinitionException {
		// 去除name开头的'&'字符,获取name最终的规范名称【最终别名或者是全类名】
		String beanName = transformedBeanName(name);

		// 在不允许创建早期引用的情况下，获取beanName所指的对象
		Object beanInstance = getSingleton(beanName, false);
		// 如果成功获取beanInstance
		if (beanInstance != null) {
			// 如果beanInstance是FactoryBean实例
			if (beanInstance instanceof FactoryBean) {
				// 将name是否是FactoryBean的解引用的结果返回出去或者将beanInstance强转成FactoryBean对象后，调用isSingleton()得到是否为
				// 单例的结果返回出去
				return (BeanFactoryUtils.isFactoryDereference(name) || ((FactoryBean<?>) beanInstance).isSingleton());
			}
			else {
				// 获取name是否是FactoryBean的解引用的结果，是就返回表示不是单例，否则表示单例
				return !BeanFactoryUtils.isFactoryDereference(name);
			}
		}

		// No singleton instance found -> check bean definition.
		// 找不到单例实例 -> 检查bean定义
		// 获取父工厂
		BeanFactory parentBeanFactory = getParentBeanFactory();
		//如果成功获取到父工厂 且 当前工厂没有beanName所指的BeanDefinition对象
		if (parentBeanFactory != null && !containsBeanDefinition(beanName)) {
			// No bean definition found in this factory -> delegate to parent.
			// 在此工厂中找不到bean定义 -> 委托给父对象。
			// 使用父工厂调用该方法判断name，将结果返回出去
			return parentBeanFactory.isSingleton(originalBeanName(name));
		}

		// 获取bean的合并后的RootBeanDefinition对象
		RootBeanDefinition mbd = getMergedLocalBeanDefinition(beanName);

		// In case of FactoryBean, return singleton status of created object if not a dereference.
		// 对于FactoryBean，如果不取消引用，则返回创建对象的单例状态
		// 如果mbd配置的作用域是单例
		if (mbd.isSingleton()) {
			// 如果beanName,mbd所指的bean是FactoryBean
			if (isFactoryBean(beanName, mbd)) {
				// 获取name是FactoryBean的解引用的则认为是单例，返回true
				if (BeanFactoryUtils.isFactoryDereference(name)) {
					return true;
				}
				// 获取name所指的FactoryBean对象
				FactoryBean<?> factoryBean = (FactoryBean<?>) getBean(FACTORY_BEAN_PREFIX + beanName);
				// 将factoryBean所创建的Bean对象是否为单例的结果返回出去
				return factoryBean.isSingleton();
			}
			else {
				// 获取name是否是FactoryBean的解引用的结果，是就返回表示不是单例，否则表示单例
				return !BeanFactoryUtils.isFactoryDereference(name);
			}
		}
		else {
			// 如果mbd配置的作用域不是单例，返回false
			return false;
		}
	}

	@Override
	public boolean isPrototype(String name) throws NoSuchBeanDefinitionException {
		String beanName = transformedBeanName(name);

		BeanFactory parentBeanFactory = getParentBeanFactory();
		if (parentBeanFactory != null && !containsBeanDefinition(beanName)) {
			// No bean definition found in this factory -> delegate to parent.
			return parentBeanFactory.isPrototype(originalBeanName(name));
		}

		RootBeanDefinition mbd = getMergedLocalBeanDefinition(beanName);
		if (mbd.isPrototype()) {
			// In case of FactoryBean, return singleton status of created object if not a dereference.
			return (!BeanFactoryUtils.isFactoryDereference(name) || isFactoryBean(beanName, mbd));
		}

		// Singleton or scoped - not a prototype.
		// However, FactoryBean may still produce a prototype object...
		if (BeanFactoryUtils.isFactoryDereference(name)) {
			return false;
		}
		if (isFactoryBean(beanName, mbd)) {
			FactoryBean<?> fb = (FactoryBean<?>) getBean(FACTORY_BEAN_PREFIX + beanName);
			if (System.getSecurityManager() != null) {
				return AccessController.doPrivileged(
						(PrivilegedAction<Boolean>) () ->
								((fb instanceof SmartFactoryBean && ((SmartFactoryBean<?>) fb).isPrototype()) ||
										!fb.isSingleton()),
						getAccessControlContext());
			}
			else {
				return ((fb instanceof SmartFactoryBean && ((SmartFactoryBean<?>) fb).isPrototype()) ||
						!fb.isSingleton());
			}
		}
		else {
			return false;
		}
	}

	@Override
	public boolean isTypeMatch(String name, ResolvableType typeToMatch) throws NoSuchBeanDefinitionException {
		return isTypeMatch(name, typeToMatch, true);
	}

	/**
	 * 检查具有给定名称的bean是否与指定的类型匹配
	 *
	 * Internal extended variant of {@link #isTypeMatch(String, ResolvableType)}
	 * to check whether the bean with the given name matches the specified type. Allow
	 * additional constraints to be applied to ensure that beans are not created early.
	 * @param name the name of the bean to query
	 * @param typeToMatch the type to match against (as a
	 * {@code ResolvableType})
	 * @return {@code true} if the bean type matches, {@code false} if it
	 * doesn't match or cannot be determined yet
	 * @throws NoSuchBeanDefinitionException if there is no bean with the given name
	 * @since 5.2
	 * @see #getBean
	 * @see #getType
	 */
	protected boolean isTypeMatch(String name, ResolvableType typeToMatch, boolean allowFactoryBeanInit)
			throws NoSuchBeanDefinitionException {

		// 去除name开头的'&'字符,获取name最终的规范名称【最终别名或者是全类名】
		String beanName = transformedBeanName(name);
		// 判断name是否为FactoryBean的解引用名
		// name是以'&'开头，就是FactoryBean的解引用
		boolean isFactoryDereference = BeanFactoryUtils.isFactoryDereference(name);

		// Check manually registered singletons.
		// 检查手动注册的单例
		// 获取beanName的单例对象，但不允许创建引用
		Object beanInstance = getSingleton(beanName, false);
		// 如果成功获取到单例对象而且该单例对象的类型又不是NullBean
		if (beanInstance != null && beanInstance.getClass() != NullBean.class) {
			// 如果单例对象是FactoryBean的实例
			if (beanInstance instanceof FactoryBean) {
				// 如果name不是FactoryBean的解引用名
				if (!isFactoryDereference) {
					// 获取beanInstance的创建出来的对象的类型。
					Class<?> type = getTypeForFactoryBean((FactoryBean<?>) beanInstance);
					// 如果成功获取到beanInstance的创建出来的对象的类型而且属于要匹配的类型
					return (type != null && typeToMatch.isAssignableFrom(type));
				}
				else {
					// 返回单例对象是否属于要匹配的类型的实例
					return typeToMatch.isInstance(beanInstance);
				}
			}
			// 如果name不是FactoryBean的解引用名
			else if (!isFactoryDereference) {
				// 如果单例对象属于要匹配的类型的实例
				if (typeToMatch.isInstance(beanInstance)) {
					// Direct match for exposed instance?
					// 直接匹配暴露的实例？
					return true;
				}
				// 如果要匹配的类型包含泛型参数而且此bean工厂包含beanName所指的BeanDefinition定义
				else if (typeToMatch.hasGenerics() && containsBeanDefinition(beanName)) {
					// Generics potentially only match on the target class, not on the proxy...
					// 泛型可能仅在目标类上匹配，而在代理上不匹配
					// 获取beanName所对应的合并RootBeanDefinition
					RootBeanDefinition mbd = getMergedLocalBeanDefinition(beanName);
					// 获取mbd的目标类型
					Class<?> targetType = mbd.getTargetType();
					// 如果成功获取到了mbd的目标类型而且目标类型与单例对象的类型不同
					if (targetType != null && targetType != ClassUtils.getUserClass(beanInstance)) {
						// Check raw class match as well, making sure it's exposed on the proxy.
						// 同时检查原始类匹配，确保它在代理中公开
						// 获取TypeToMatch的封装Class对象
						Class<?> classToMatch = typeToMatch.resolve();
						// 如果成功获取Class对象而且单例对象不是该Class对象的实例
						if (classToMatch != null && !classToMatch.isInstance(beanInstance)) {
							// 表示要查询的Bean名与要匹配的类型不匹配
							return false;
						}
						// 如果mbd的目标类型属于要匹配的类型
						if (typeToMatch.isAssignableFrom(targetType)) {
							// 表示要查询的Bean名与要匹配的类型匹配
							return true;
						}
					}
					// 获取mbd的目标类型
					ResolvableType resolvableType = mbd.targetType;
					// 如果获取mbd的目标类型失败
					if (resolvableType == null) {
						// 获取mbd的工厂方法返回类型作为mbd的目标类型
						resolvableType = mbd.factoryMethodReturnType;
					}
					// 如果成功获取到了mbd的目标类型而且该目标类型属于要匹配的类型 就返回true，否则返回false。
					return (resolvableType != null && typeToMatch.isAssignableFrom(resolvableType));
				}
			}
			// 如果beanName的单例对象不是FactoryBean的实例或者name是FactoryBean的解引用名
			return false;
		}
		// 如果该工厂的单例对象注册器包含beanName所指的单例对象 但该工厂没有beanName对应的BeanDefinition对象
		else if (containsSingleton(beanName) && !containsBeanDefinition(beanName)) {
			// null instance registered
			// 注册了null实例,即 beanName对应的实例是NullBean实例，因前面已经处理了beanName不是NullBean的情况，
			// 再加上该工厂没有对应beanName的BeanDefinition对象
			return false;
		}

		// No singleton instance found -> check bean definition.
		// 找不到单例实例 -> 检查bean定义
		// 获取该工厂的父级工厂
		BeanFactory parentBeanFactory = getParentBeanFactory();
		// 如果父级工厂不为null且该工厂没有包含beanName的BeanDefinition
		if (parentBeanFactory != null && !containsBeanDefinition(beanName)) {
			// No bean definition found in this factory -> delegate to parent.
			// 在该工厂中找不到BeanDefinition -> 委托给父对象
			// 递归交给父工厂判断，将判断结果返回出去
			return parentBeanFactory.isTypeMatch(originalBeanName(name), typeToMatch);
		}

		// Retrieve corresponding bean definition.
		// 检索相应的bean定义
		// 获取beanName合并后的本地RootBeanDefinition
		RootBeanDefinition mbd = getMergedLocalBeanDefinition(beanName);
		// 获取mbd的BeanDefinitionHolder
		// BeanDefinitionHolder就是对BeanDefinition的持有，同时持有的包括BeanDefinition的名称和别名
		BeanDefinitionHolder dbd = mbd.getDecoratedDefinition();

		// Setup the types that we want to match against
		// 设置我们要匹配的类型
		// 获取我们要匹配的class对象
		Class<?> classToMatch = typeToMatch.resolve();
		// 如果classToMatch为null
		if (classToMatch == null) {
			// 默认使用FactoryBean作为要匹配的class对象
			classToMatch = FactoryBean.class;
		}
		// 如果factoryBean不是要匹配的class对象，要匹配的类数组会加上FactoryBean.class
		Class<?>[] typesToMatch = (FactoryBean.class == classToMatch ?
				new Class<?>[] {classToMatch} : new Class<?>[] {FactoryBean.class, classToMatch});


		// Attempt to predict the bean type
		// 尝试预测bean类型
		Class<?> predictedType = null;

		// We're looking for a regular reference but we're a factory bean that has
		// a decorated bean definition. The target bean should be the same type
		// as FactoryBean would ultimately return.
		// 我们正在寻找常规参考，但是我们是具有修饰的BeanDefinition的FactoryBean.目标bean类型
		// 应与factoryBean最终返回的类型相同
		// 如果不是FactoryBean解引用且mbd有配置BeanDefinitionHolder且beanName,mbd所指的bean是FactoryBean
		if (!isFactoryDereference && dbd != null && isFactoryBean(beanName, mbd)) {
			// We should only attempt if the user explicitly set lazy-init to true
			// and we know the merged bean definition is for a factory bean.
			// 只有在用户将lazy-init显示设置为true并且我们知道合并的BeanDefinition是针对FactoryBean的情况下，才应该尝试
			// 如果mbd没有设置lazy-init或者允许FactoryBean初始化
			if (!mbd.isLazyInit() || allowFactoryBeanInit) {
				// 获取dbd的beanName，dbd的BeanDefinition，mbd所对应的合并后RootBeanDefinition
				RootBeanDefinition tbd = getMergedBeanDefinition(dbd.getBeanName(), dbd.getBeanDefinition(), mbd);
				// 预测dbd的beanName,tbd,typesToMatch的Bean类型
				Class<?> targetType = predictBeanType(dbd.getBeanName(), tbd, typesToMatch);
				// 如果目标类型不为null，且targetType不属于FactoryBean
				if (targetType != null && !FactoryBean.class.isAssignableFrom(targetType)) {
					// 预测bean类型就为该目标类型
					predictedType = targetType;
				}
			}
		}

		// If we couldn't use the target type, try regular prediction.
		// 如果我们无法使用目标类型，请尝试常规预测
		// 如果无法获得预测bean类型
		if (predictedType == null) {
			// 获取beanName，mbd，typeToMatch所对应的Bean类型作为预测bean类型
			predictedType = predictBeanType(beanName, mbd, typesToMatch);
			// 如果没有成功获取到预测bean类型，返回false，表示不匹配
			if (predictedType == null) {
				return false;
			}
		}

		// Attempt to get the actual ResolvableType for the bean.
		// 尝试获取Bean的实际ResolvableType
		// ResolvableType：可以看作是封装JavaType的元信息类
		ResolvableType beanType = null;

		// If it's a FactoryBean, we want to look at what it creates, not the factory class.
		// 如果是FactoryBean,我们要查看它创建的内容，而不是工厂类
		// 如果predictedType属于FactoryBean
		if (FactoryBean.class.isAssignableFrom(predictedType)) {
			// 如果没有beanName的单例对象且beanName不是指FactoryBean解引用
			if (beanInstance == null && !isFactoryDereference) {
				// 获取beanName,mbd的FactoryBean定义的bean类型赋值给beanType
				beanType = getTypeForFactoryBean(beanName, mbd, allowFactoryBeanInit);
				// 解析beanType以得到predictedType
				predictedType = beanType.resolve();
				// 如果得到predictedType为null
				if (predictedType == null) {
					// 返回false，表示不匹配
					return false;
				}
			}
		}
		// beanName是指FactoryBean解引用
		else if (isFactoryDereference) {
			// Special case: A SmartInstantiationAwareBeanPostProcessor returned a non-FactoryBean
			// type but we nevertheless are being asked to dereference a FactoryBean...
			// Let's check the original bean class and proceed with it if it is a FactoryBean.
			// 特殊情况：SmartInstantiationAwareBeanPostProcessor返回非FactoryBean类型，但是仍然要求我们
			// 取消引用FactoryBean... 让我们检查原始bean类，如果它是FactoryBean，则继续进行处理
			// 预测mdb所指的bean的最终bean类型
			predictedType = predictBeanType(beanName, mbd, FactoryBean.class);
			// 如果预测不到或者得到的预测类型属于FactoryBean
			if (predictedType == null || !FactoryBean.class.isAssignableFrom(predictedType)) {
				// 返回false，表示不匹配
				return false;
			}
		}

		// We don't have an exact type but if bean definition target type or the factory
		// method return type matches the predicted type then we can use that.
		// 我们没有确切的类型，但是如果bean定义目标类型或者工厂方法返回类型与预测的类型匹配，则可以使用它
		// 如果没有拿到beanType
		if (beanType == null) {
			// 声明一个已定义类型，默认使用mbd的目标类型
			ResolvableType definedType = mbd.targetType;
			// 如果没有拿到definedType
			if (definedType == null) {
				// 获取mbd的工厂方法的返回类型
				definedType = mbd.factoryMethodReturnType;
			}
			// 如果拿到了definedType且definedType所封装的Class对象与预测类型相同
			if (definedType != null && definedType.resolve() == predictedType) {
				// beanType就为definedType
				beanType = definedType;
			}
		}

		// If we have a bean type use it so that generics are considered
		// 如果我们有一个bean类型，请使用它，以便将泛型考虑在内
		// 如果拿到了beanType
		if (beanType != null) {
			// 返回beanType是否属于typeToMatch的结果
			return typeToMatch.isAssignableFrom(beanType);
		}

		// If we don't have a bean type, fallback to the predicted type
		// 如果我们没有bean类型，则回退到预测类型
		// 如果我们没有bean类型，返回predictedType否属于typeToMatch的结果
		return typeToMatch.isAssignableFrom(predictedType);
	}

	@Override
	public boolean isTypeMatch(String name, Class<?> typeToMatch) throws NoSuchBeanDefinitionException {
		return isTypeMatch(name, ResolvableType.forRawClass(typeToMatch));
	}

	/**
	 * 确定具有给定名称的bean类型(为了确定其对象类型，默认让FactoryBean以初始化)
	 * @param name the name of the bean to query
	 * @return
	 * @throws NoSuchBeanDefinitionException
	 */
	@Override
	@Nullable
	public Class<?> getType(String name) throws NoSuchBeanDefinitionException {
		return getType(name, true);
	}

	/**
	 * 确定具有给定名称的bean类型。更具体地说，确定getBean将针对给定名称返回的对象的类型
	 * @param name the name of the bean to query
	 * @param allowFactoryBeanInit whether a {@code FactoryBean} may get initialized
	 * just for the purpose of determining its object type
	 * @return
	 * @throws NoSuchBeanDefinitionException
	 */
	@Override
	@Nullable
	public Class<?> getType(String name, boolean allowFactoryBeanInit) throws NoSuchBeanDefinitionException {
		// 获取name对应的规范名称【全类名】,包括以'&'开头的name
		String beanName = transformedBeanName(name);

		// Check manually registered singletons.
		// 检查手动注册的单例,获取beanName注册的单例对象，但不会创建早期引用
		Object beanInstance = getSingleton(beanName, false);
		// 如果成功获取到beanName的单例对象，且该单例对象又不是NullBean,NullBean用于表示null
		if (beanInstance != null && beanInstance.getClass() != NullBean.class) {
			// 如果bean的单例对象是FactoryBean的实例且name不是FactoryBean的解引用
			if (beanInstance instanceof FactoryBean && !BeanFactoryUtils.isFactoryDereference(name)) {
				// 将beanInstance强转为FactoryBean,获取其创建出来的对象的类型并返回
				return getTypeForFactoryBean((FactoryBean<?>) beanInstance);
			}
			else {
				// 获取beanInstance的类型并返回
				return beanInstance.getClass();
			}
		}

		// No singleton instance found -> check bean definition.
		// 找不到单例实例 -> 检查bean定义
		// 获取该工厂的父级bean工厂
		BeanFactory parentBeanFactory = getParentBeanFactory();
		// 如果成功获取到了父级bean工厂且该bean工厂包含具有beanName的bean定义
		if (parentBeanFactory != null && !containsBeanDefinition(beanName)) {
			// No bean definition found in this factory -> delegate to parent.
			// 在该工厂中找不到bean定义 -> 委托给父对象
			// originalBeanName:获取name对应的规范名称【全类名】，如果name前面有'&'，则会返回'&'+规范名称【全类名】
			// 从父级bean工厂中获取name的全类名的bean类型，【递归】
			return parentBeanFactory.getType(originalBeanName(name));
		}

		// 获取beanName对应的合并RootBeanDefinition，如果bean对应于子bean定义，则遍历父bean定义
		RootBeanDefinition mbd = getMergedLocalBeanDefinition(beanName);

		// Check decorated bean definition, if any: We assume it'll be easier
		// to determine the decorated bean's type than the proxy's type.
		// 检查修饰的bean定义(如果有):我们假设确定修饰的bean类型比代理类型更容易
		// 获取mbd的Bean定义持有者
		BeanDefinitionHolder dbd = mbd.getDecoratedDefinition();
		// 如果成功获取到mbd的Bean定义持有者且name不是FactoryBean的解引用
		// FactoryBean的解引用指的是FactoryBean使用getObject方法得到的对象
		if (dbd != null && !BeanFactoryUtils.isFactoryDereference(name)) {
			// 获取合并的RootBeanDefinition
			RootBeanDefinition tbd = getMergedBeanDefinition(dbd.getBeanName(), dbd.getBeanDefinition(), mbd);
			// 尝试预测dbd的bean名的最终bean类型
			Class<?> targetClass = predictBeanType(dbd.getBeanName(), tbd);
			// 如果成功预测到了bdd的bean名的最终bean类型且targetClass不属于FactoryBean类型
			if (targetClass != null && !FactoryBean.class.isAssignableFrom(targetClass)) {
				// 返回该预测到的dbd的bean名的最终bean类型
				return targetClass;
			}
		}

		// 尝试预测beanName的最终bean类型
		Class<?> beanClass = predictBeanType(beanName, mbd);

		// Check bean class whether we're dealing with a FactoryBean.
		// 检查bean类是否正在处理FactoryBean
		// 如果成功预测到beanName的最终bean类型且该类属于FactoryBean类型
		if (beanClass != null && FactoryBean.class.isAssignableFrom(beanClass)) {
			// 如果name不是FactoryBean的解引用名
			if (!BeanFactoryUtils.isFactoryDereference(name)) {
				// If it's a FactoryBean, we want to look at what it creates, not at the factory class.
				// 如果是FactoryBean，则我们要查看它创建的内容，而不是工厂类
				// 尽可能的使用beanName和mbd去获取FactoryBean定义的bean类型
				return getTypeForFactoryBean(beanName, mbd, allowFactoryBeanInit).resolve();
			}
			else {
				//如果是FactoryBean的解引用，就直接返回该beanName的最终bean类型
				return beanClass;
			}
		}
		else {
			// 如果没有成功预测到beanName的最终bean类型 或者 最终bean类型不属于FactoryBean类型
			// 如果name不是FactoryBean的解引用名,就直接返回该beanName的最终bean类型，否则返回null
			return (!BeanFactoryUtils.isFactoryDereference(name) ? beanClass : null);
		}
	}

	/**
	 * 返回给定bean名称的别名(如果有)
	 * @param name the bean name to check for aliases
	 * @return
	 */
	@Override
	public String[] getAliases(String name) {
		// 去除name开头的'&'字符,获取name最终的规范名称【最终别名或者是全类名】
		String beanName = transformedBeanName(name);
		// 定义用于存放别名的集合
		List<String> aliases = new ArrayList<>();
		// 定义保存name是否以'&'开头的结果标记,让有'&'开头时，表示name是一个FactoryBean名
		boolean factoryPrefix = name.startsWith(FACTORY_BEAN_PREFIX);
		// 定义一个完整bean名初始引用beanName
		String fullBeanName = beanName;
		// 如果name是以'&'开头
		if (factoryPrefix) {
			// 完整bean名开头就加上'&'
			fullBeanName = FACTORY_BEAN_PREFIX + beanName;
		}
		// 如果完整bean名与name不相同
		if (!fullBeanName.equals(name)) {
			// 将fullBeanName添加到aliases中
			aliases.add(fullBeanName);
		}
		// 获取beanName的别名
		String[] retrievedAliases = super.getAliases(beanName);
		// 获取前缀
		String prefix = factoryPrefix ? FACTORY_BEAN_PREFIX : "";
		// 遍历所有所有别名
		for (String retrievedAlias : retrievedAliases) {
			// 引用retrievedAlias,如果有'&'前缀,就加上'&'
			String alias = prefix + retrievedAlias;
			// 如果alias与name不同
			if (!alias.equals(name)) {
				// 将alias添加到aliases中
				aliases.add(alias);
			}
		}
		// 如果beanName不在该BeanFactory的单例对象的高速缓存Map集合【DefaultListableBeanFactory.singletonObjects】中,
		// 且该BeanFactory不包含beanName的BeanDefinition对象
		if (!containsSingleton(beanName) && !containsBeanDefinition(beanName)) {
			// 获取父工厂
			BeanFactory parentBeanFactory = getParentBeanFactory();
			// 如果父工厂不为null
			if (parentBeanFactory != null) {
				// 使用父工厂获取fullBeanName的所有别名，然后添加到aliases中
				aliases.addAll(Arrays.asList(parentBeanFactory.getAliases(fullBeanName)));
			}
		}
		// 将aliases转换成String数组返回出去
		return StringUtils.toStringArray(aliases);
	}


	//---------------------------------------------------------------------
	// Implementation of HierarchicalBeanFactory interface
	//---------------------------------------------------------------------

	@Override
	@Nullable
	public BeanFactory getParentBeanFactory() {
		return this.parentBeanFactory;
	}

	/**
	 * 本地BeanFactory是否包含给定名称的bean.满足以下全部条件才认为本地bean工厂包含name的bean:
	 * @param name the name of the bean to query
	 * @return
	 */
	@Override
	public boolean containsLocalBean(String name) {
		// 获取name最终的规范名称【最终别名】
		String beanName = transformedBeanName(name);
		// 满足以下全部条件则认为本地bean工厂包含name的bean：
		// 1. beanName存在于该BeanFactory的singletonObjects【单例对象的高速缓存Map集合】中
		// 	  或者该BeanFactory包含beanName的BeanDefinition对象
		// 2. 该BeanFactory包含beanName的BeanDefinition对象
		// 3. name为该工厂的FactoryBean的解引用.判断依据：name是以'&'开头，就是FactoryBean的解引用
		// 4. beanName是FactoryBean
		return ((containsSingleton(beanName) || containsBeanDefinition(beanName)) &&
				(!BeanFactoryUtils.isFactoryDereference(name) || isFactoryBean(beanName)));
	}


	//---------------------------------------------------------------------
	// Implementation of ConfigurableBeanFactory interface
	//---------------------------------------------------------------------

	@Override
	public void setParentBeanFactory(@Nullable BeanFactory parentBeanFactory) {
		// 如果当前已经有一个父级bean工厂，且传进来的父级bean工厂与当前父级bean工厂不是同一个
		if (this.parentBeanFactory != null && this.parentBeanFactory != parentBeanFactory) {
			// 抛出异常
			throw new IllegalStateException("Already associated with parent BeanFactory: " + this.parentBeanFactory);
		}
		if (this == parentBeanFactory) {
			throw new IllegalStateException("Cannot set parent bean factory to self");
		}
		this.parentBeanFactory = parentBeanFactory;
	}

	@Override
	public void setBeanClassLoader(@Nullable ClassLoader beanClassLoader) {
		this.beanClassLoader = (beanClassLoader != null ? beanClassLoader : ClassUtils.getDefaultClassLoader());
	}

	@Override
	@Nullable
	public ClassLoader getBeanClassLoader() {
		return this.beanClassLoader;
	}

	@Override
	public void setTempClassLoader(@Nullable ClassLoader tempClassLoader) {
		this.tempClassLoader = tempClassLoader;
	}

	@Override
	@Nullable
	public ClassLoader getTempClassLoader() {
		return this.tempClassLoader;
	}

	@Override
	public void setCacheBeanMetadata(boolean cacheBeanMetadata) {
		this.cacheBeanMetadata = cacheBeanMetadata;
	}

	@Override
	public boolean isCacheBeanMetadata() {
		return this.cacheBeanMetadata;
	}

	@Override
	public void setBeanExpressionResolver(@Nullable BeanExpressionResolver resolver) {
		this.beanExpressionResolver = resolver;
	}

	@Override
	@Nullable
	public BeanExpressionResolver getBeanExpressionResolver() {
		return this.beanExpressionResolver;
	}

	@Override
	public void setConversionService(@Nullable ConversionService conversionService) {
		this.conversionService = conversionService;
	}

	@Override
	@Nullable
	public ConversionService getConversionService() {
		return this.conversionService;
	}

	@Override
	public void addPropertyEditorRegistrar(PropertyEditorRegistrar registrar) {
		Assert.notNull(registrar, "PropertyEditorRegistrar must not be null");
		this.propertyEditorRegistrars.add(registrar);
	}

	/**
	 * Return the set of PropertyEditorRegistrars.
	 */
	public Set<PropertyEditorRegistrar> getPropertyEditorRegistrars() {
		return this.propertyEditorRegistrars;
	}

	@Override
	public void registerCustomEditor(Class<?> requiredType, Class<? extends PropertyEditor> propertyEditorClass) {
		Assert.notNull(requiredType, "Required type must not be null");
		Assert.notNull(propertyEditorClass, "PropertyEditor class must not be null");
		this.customEditors.put(requiredType, propertyEditorClass);
	}

	@Override
	public void copyRegisteredEditorsTo(PropertyEditorRegistry registry) {
		registerCustomEditors(registry);
	}

	/**
	 * Return the map of custom editors, with Classes as keys and PropertyEditor classes as values.
	 */
	public Map<Class<?>, Class<? extends PropertyEditor>> getCustomEditors() {
		return this.customEditors;
	}

	@Override
	public void setTypeConverter(TypeConverter typeConverter) {
		this.typeConverter = typeConverter;
	}

	/**
	 * 返回自定义的TypeConverter以使用(如果有)
	 *
	 * Return the custom TypeConverter to use, if any.
	 * @return the custom TypeConverter, or {@code null} if none specified
	 */
	@Nullable
	protected TypeConverter getCustomTypeConverter() {
		return this.typeConverter;
	}

	/**
	 * 获取此BeanFactory使用的类型转换器。这可能是每次调用都有新实例，因TypeConverters通常 不是线程安全的.
	 * @return 此BeanFactory使用的类型转换器:默认情况下优先返回自定义的类型转换器【{@link #getCustomTypeConverter()}】;
	 * 	获取不到时,返回一个新的SimpleTypeConverter对象
	 */
	@Override
	public TypeConverter getTypeConverter() {
		// 获取自定义的TypeConverter
		TypeConverter customConverter = getCustomTypeConverter();
		// 如果有自定义的TypeConverter
		if (customConverter != null) {
			// 返回该自定义的TypeConverter
			return customConverter;
		}
		else {
			// Build default TypeConverter, registering custom editors.
			// 构建默认的TypeConverter，注册自定义编辑器
			// SimpleTypeConverter:不在特定目标对象上运行的TypeConverter接口的简单实现。
			// 这是使用完整的BeanWrapperImpl实例来实现 任意类型转换需求的替代方法，同时
			// 使用相同的转换算法（包括委托给PropertyEditor和ConversionService）。
			// 每次调用该方法都会新建一个类型转换器，因为SimpleTypeConverter不是线程安全的
			// 新建一个SimpleTypeConverter对象
			SimpleTypeConverter typeConverter = new SimpleTypeConverter();
			// 让typeConverter引用该工厂的类型转换的服务接口
			typeConverter.setConversionService(getConversionService());
			// 将工厂中所有PropertyEditor注册到typeConverter中
			registerCustomEditors(typeConverter);
			// 返回SimpleTypeConverter作为该工厂的默认类型转换器。
			return typeConverter;
		}
	}

	@Override
	public void addEmbeddedValueResolver(StringValueResolver valueResolver) {
		Assert.notNull(valueResolver, "StringValueResolver must not be null");
		// 将valueResolver添加到embeddedValueResolvers中
		this.embeddedValueResolvers.add(valueResolver);
	}

	@Override
	public boolean hasEmbeddedValueResolver() {
		// 返回embeddedValueResolvers是否为空集的结果
		return !this.embeddedValueResolvers.isEmpty();
	}

	/**
	 * 解析嵌套的值(如果value是表达式会解析出该表达式的值)
	 * @param value the value to resolve
	 * @return
	 */
	@Override
	@Nullable
	public String resolveEmbeddedValue(@Nullable String value) {
		// 如果value为null，返回null
		if (value == null) {
			return null;
		}
		// 定义返回结果，默认引用value
		String result = value;
		// SpringBoot默认存放一个PropertySourcesPlaceholderConfigurer，该类注意用于针对当前
		// Spring Environment 及其PropertySource解析bean定义属性值和@Value注释中的${...}占位符
		// 遍历该工厂的所有字符串解析器
		for (StringValueResolver resolver : this.embeddedValueResolvers) {
			// 解析result，将解析后的值重新赋值给result
			result = resolver.resolveStringValue(result);
			// 如果result为null,结束该循环，并返回null
			if (result == null) {
				return null;
			}
		}
		// 将解析后的结果返回出去
		return result;
	}

	@Override
	public void addBeanPostProcessor(BeanPostProcessor beanPostProcessor) {
		Assert.notNull(beanPostProcessor, "BeanPostProcessor must not be null");
		// 后添加的BeanPostProcessor会覆盖之前的，先删除，然后在添加
		// Remove from old position, if any
		// 从老的位置移除此beanPostProcessor
		this.beanPostProcessors.remove(beanPostProcessor);
		// Track whether it is instantiation/destruction aware
		// 此处是为了设置某些状态变量，这些状态变量会影响后续的执行流程，只需要判断是否是指定的类型，然后设置标志位即可
		if (beanPostProcessor instanceof InstantiationAwareBeanPostProcessor) {
			// 该变量表示beanfactory是否已注册过InstantiationAwareBeanPostProcessor
			this.hasInstantiationAwareBeanPostProcessors = true;
		}
		if (beanPostProcessor instanceof DestructionAwareBeanPostProcessor) {
			// 该变量表示beanfactory是否已注册过DestructionAwareBeanPostProcessor
			this.hasDestructionAwareBeanPostProcessors = true;
		}
		// Add to end of list
		// 将beanPostProcessor添加到beanPostProcessors缓存中
		this.beanPostProcessors.add(beanPostProcessor);
	}

	@Override
	public int getBeanPostProcessorCount() {
		return this.beanPostProcessors.size();
	}

	/**
	 * Return the list of BeanPostProcessors that will get applied
	 * to beans created with this factory.
	 */
	public List<BeanPostProcessor> getBeanPostProcessors() {
		return this.beanPostProcessors;
	}

	/**
	 * 返回此工厂是否拥有InstiationAwareBeanPostProcessor，它将在关闭时应用于单例bean
	 *
	 * Return whether this factory holds a InstantiationAwareBeanPostProcessor
	 * that will get applied to singleton beans on creation.
	 * @see #addBeanPostProcessor
	 * @see org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessor
	 */
	protected boolean hasInstantiationAwareBeanPostProcessors() {
		return this.hasInstantiationAwareBeanPostProcessors;
	}

	/**
	 * 返回该工厂是否持有一个 DestructionAwareBeanPostProcessor ,该处理器将在关闭时应用于单例Bean
	 *
	 * Return whether this factory holds a DestructionAwareBeanPostProcessor
	 * that will get applied to singleton beans on shutdown.
	 * @see #addBeanPostProcessor
	 * @see org.springframework.beans.factory.config.DestructionAwareBeanPostProcessor
	 */
	protected boolean hasDestructionAwareBeanPostProcessors() {
		return this.hasDestructionAwareBeanPostProcessors;
	}

	@Override
	public void registerScope(String scopeName, Scope scope) {
		Assert.notNull(scopeName, "Scope identifier must not be null");
		Assert.notNull(scope, "Scope must not be null");
		if (SCOPE_SINGLETON.equals(scopeName) || SCOPE_PROTOTYPE.equals(scopeName)) {
			throw new IllegalArgumentException("Cannot replace existing scopes 'singleton' and 'prototype'");
		}
		Scope previous = this.scopes.put(scopeName, scope);
		if (previous != null && previous != scope) {
			if (logger.isDebugEnabled()) {
				logger.debug("Replacing scope '" + scopeName + "' from [" + previous + "] to [" + scope + "]");
			}
		}
		else {
			if (logger.isTraceEnabled()) {
				logger.trace("Registering scope '" + scopeName + "' with implementation [" + scope + "]");
			}
		}
	}

	@Override
	public String[] getRegisteredScopeNames() {
		return StringUtils.toStringArray(this.scopes.keySet());
	}

	// 获取给定作用域名称对应的作用域对象（如果有）
	@Override
	@Nullable
	public Scope getRegisteredScope(String scopeName) {
		// 如果传入的作用域名为null，抛出异常
		Assert.notNull(scopeName, "Scope identifier must not be null");
		// 从映射的linkedHashMap中获取传入的作用域名对应的作用域对象并返回
		return this.scopes.get(scopeName);
	}

	/**
	 * 设置此bean工厂的安全上下文提供者。如果设置了安全管理器，则将使用提供的安全上下文的特权执行与用户代码的交互
	 *
	 * Set the security context provider for this bean factory. If a security manager
	 * is set, interaction with the user code will be executed using the privileged
	 * of the provided security context.
	 */
	public void setSecurityContextProvider(SecurityContextProvider securityProvider) {
		this.securityContextProvider = securityProvider;
	}

	/**
	 * 将访问控制上下文的创建委托给 {@link #setSecurityContextProvider SecurityContextProvider}
	 *
	 * Delegate the creation of the access control context to the
	 * {@link #setSecurityContextProvider SecurityContextProvider}.
	 */
	@Override
	public AccessControlContext getAccessControlContext() {
		// 如果没有bean工厂的安全上下文提供者，就使用jdk提供的访问控制上下文
		// 否则使用该bean工厂的安全上下文提供者来获取访问控制上下文
		return (this.securityContextProvider != null ?
				this.securityContextProvider.getAccessControlContext() :
				AccessController.getContext());
	}

	@Override
	public void copyConfigurationFrom(ConfigurableBeanFactory otherFactory) {
		Assert.notNull(otherFactory, "BeanFactory must not be null");
		setBeanClassLoader(otherFactory.getBeanClassLoader());
		setCacheBeanMetadata(otherFactory.isCacheBeanMetadata());
		setBeanExpressionResolver(otherFactory.getBeanExpressionResolver());
		setConversionService(otherFactory.getConversionService());
		if (otherFactory instanceof AbstractBeanFactory) {
			AbstractBeanFactory otherAbstractFactory = (AbstractBeanFactory) otherFactory;
			this.propertyEditorRegistrars.addAll(otherAbstractFactory.propertyEditorRegistrars);
			this.customEditors.putAll(otherAbstractFactory.customEditors);
			this.typeConverter = otherAbstractFactory.typeConverter;
			this.beanPostProcessors.addAll(otherAbstractFactory.beanPostProcessors);
			this.hasInstantiationAwareBeanPostProcessors = this.hasInstantiationAwareBeanPostProcessors ||
					otherAbstractFactory.hasInstantiationAwareBeanPostProcessors;
			this.hasDestructionAwareBeanPostProcessors = this.hasDestructionAwareBeanPostProcessors ||
					otherAbstractFactory.hasDestructionAwareBeanPostProcessors;
			this.scopes.putAll(otherAbstractFactory.scopes);
			this.securityContextProvider = otherAbstractFactory.securityContextProvider;
		}
		else {
			setTypeConverter(otherFactory.getTypeConverter());
			String[] otherScopeNames = otherFactory.getRegisteredScopeNames();
			for (String scopeName : otherScopeNames) {
				this.scopes.put(scopeName, otherFactory.getRegisteredScope(scopeName));
			}
		}
	}

	/**
	 * 返回给定bean名的"合并的"BeanDefinition，如有必要，将子bean定义与其父对象合并
	 *
	 * Return a 'merged' BeanDefinition for the given bean name,
	 * merging a child bean definition with its parent if necessary.
	 * <p>This {@code getMergedBeanDefinition} considers bean definition
	 * in ancestors as well.
	 * @param name the name of the bean to retrieve the merged definition for
	 * (may be an alias)
	 * @return a (potentially merged) RootBeanDefinition for the given bean
	 * @throws NoSuchBeanDefinitionException if there is no bean with the given name
	 * @throws BeanDefinitionStoreException in case of an invalid bean definition
	 */
	@Override
	public BeanDefinition getMergedBeanDefinition(String name) throws BeansException {
		// 获取name对应的规范名称【全类名】
		String beanName = transformedBeanName(name);
		// Efficiently check whether bean definition exists in this factory.
		// 有效检查该工厂中是否存在bean定义。
		// 如果当前bean工厂不包含具有beanName的bean定义且父工厂是ConfigurableBeanFactory的实例
		if (!containsBeanDefinition(beanName) && getParentBeanFactory() instanceof ConfigurableBeanFactory) {
			// 使用父工厂返回beanName的合并BeanDefinition【如有必要，将子bean定义与其父级合并】
			return ((ConfigurableBeanFactory) getParentBeanFactory()).getMergedBeanDefinition(beanName);
		}
		// Resolve merged bean definition locally.
		// 本地解决合并的bean定义
		return getMergedLocalBeanDefinition(beanName);
	}

	/**
	 * 确定name的Bean是否为FactoryBean
	 * @param name the name of the bean to check
	 * @return
	 * @throws NoSuchBeanDefinitionException
	 */
	@Override
	public boolean isFactoryBean(String name) throws NoSuchBeanDefinitionException {
		//去除name开头的'&'字符,获取name最终的规范名称【最终别名或者是全类名】：
		String beanName = transformedBeanName(name);
		//获取beanName注册的（原始）单例对象，如果单例对象没有找到，并且beanName存在
		// 	正在创建的Set集合中
		Object beanInstance = getSingleton(beanName, false);
		//如果beanInstance能获取到
		if (beanInstance != null) {
			// 如果 beanInstance是FactoryBean的实例 则返回true，否则返回false。
			return (beanInstance instanceof FactoryBean);
		}
		// No singleton instance found -> check bean definition.
		// 如果缓存中不存在此beanName && 父beanFactory是ConfigurableBeanFactory，则调用父BeanFactory判断是否为FactoryBean
		if (!containsBeanDefinition(beanName) && getParentBeanFactory() instanceof ConfigurableBeanFactory) {
			// No bean definition found in this factory -> delegate to parent.
			// 在该工厂中找不到bean定义 -> 委托给父对象
			// 尝试在父工厂中确定name是否为FactoryBean，如果不是返回false，否则返回true【递归】
			return ((ConfigurableBeanFactory) getParentBeanFactory()).isFactoryBean(name);
		}
		// 如果该bean对应于子bean定义，则遍历父bean定义。
		// 判断(beanName和beanName对应的合并后BeanDefinition)所指的bean是否FactoryBean并将结果返回出去
		return isFactoryBean(beanName, getMergedLocalBeanDefinition(beanName));
	}

	/**
	 * 是否实际上正在创建
	 * @param beanName
	 * @return
	 */
	@Override
	public boolean isActuallyInCreation(String beanName) {
		// 如果beanName是单例，且当前正在创建（在整个工厂内）或者如果beanName是否原型，且当前正在创建中（在当前线程内）都会返回true
		return (isSingletonCurrentlyInCreation(beanName) || isPrototypeCurrentlyInCreation(beanName));
	}

	/**
	 * 返回指定的原型bean是否当前正在创建中（在当前线程内）
	 *
	 * Return whether the specified prototype bean is currently in creation
	 * (within the current thread).
	 * @param beanName the name of the bean
	 */
	protected boolean isPrototypeCurrentlyInCreation(String beanName) {
		// 获取当前正在创建的bean名称【线程本地】
		Object curVal = this.prototypesCurrentlyInCreation.get();
		// 如果当前正在创建的bean名称不为null，且 （当前正在创建的bean名称等于beanName或者当前正在创建的bean名称是Set集合，并包含该beanName）
		// 就返回true，表示在当前线程内，beanName当前正在创建中。
		return (curVal != null &&
				(curVal.equals(beanName) || (curVal instanceof Set && ((Set<?>) curVal).contains(beanName))));
	}

	/**
	 * 创建ProtoPype对象前的准备工作，默认实现将beanName添加到prototypesCurrentlyInCreation中
	 *
	 * Callback before prototype creation.
	 * <p>The default implementation register the prototype as currently in creation.
	 * @param beanName the name of the prototype about to be created
	 * @see #isPrototypeCurrentlyInCreation
	 */
	@SuppressWarnings("unchecked")
	protected void beforePrototypeCreation(String beanName) {
		// prototypesCurrentlyInCreation：当前正在创建的bean名称
		// 从prototypesCurrentlyInCreation中获取线程安全的当前正在创建的Bean对象名
		Object curVal = this.prototypesCurrentlyInCreation.get();
		// 如果curlVal为null
		if (curVal == null) {
			// 将beanName设置到prototypesCurrentlyInCreation中
			this.prototypesCurrentlyInCreation.set(beanName);
		}
		// 如果curlValue是Stringl类型
		else if (curVal instanceof String) {
			// 定义一个HashSet对象存放prototypesCurrentlyInCreation原有Bean名和beanName
			Set<String> beanNameSet = new HashSet<>(2);
			// 将curlVal添加beanNameSet中
			beanNameSet.add((String) curVal);
			// 将beanName添加到beanNameSet中
			beanNameSet.add(beanName);
			// 将beanNameSet设置到prototypesCurrentlyInCreation中
			this.prototypesCurrentlyInCreation.set(beanNameSet);
		}
		else {
			// 否则，curlValue就只会是HashSet对象将curlVal强转为Set对象
			Set<String> beanNameSet = (Set<String>) curVal;
			// 将beanName添加到beanNameSet中
			beanNameSet.add(beanName);
		}
	}

	/**
	 * 创建完prototype实例后的回调，默认是将beanName从prototypesCurrentlyInCreation移除
	 *
	 * Callback after prototype creation.
	 * <p>The default implementation marks the prototype as not in creation anymore.
	 * @param beanName the name of the prototype that has been created
	 * @see #isPrototypeCurrentlyInCreation
	 */
	@SuppressWarnings("unchecked")
	protected void afterPrototypeCreation(String beanName) {
		// 从prototypesCurrentlyInCreation获取当前正在创建的Bean名
		Object curVal = this.prototypesCurrentlyInCreation.get();
		// 将curlVal是String对象
		if (curVal instanceof String) {
			// 将curlVal从prototypesCurrentlyInCreation中移除
			this.prototypesCurrentlyInCreation.remove();
		}
		// 如果curlVal是Set对象
		else if (curVal instanceof Set) {
			// 将curValue强转为Set对象
			Set<String> beanNameSet = (Set<String>) curVal;
			// 将beanName从beanNameSet中移除
			beanNameSet.remove(beanName);
			// 如果beanNameSet已经没有元素了
			if (beanNameSet.isEmpty()) {
				// 将beanNameSet从prototypesCurrentlyInCreation中移除
				this.prototypesCurrentlyInCreation.remove();
			}
		}
	}

	@Override
	public void destroyBean(String beanName, Object beanInstance) {
		destroyBean(beanName, beanInstance, getMergedLocalBeanDefinition(beanName));
	}

	/**
	 * Destroy the given bean instance (usually a prototype instance
	 * obtained from this factory) according to the given bean definition.
	 * @param beanName the name of the bean definition
	 * @param bean the bean instance to destroy
	 * @param mbd the merged bean definition
	 */
	protected void destroyBean(String beanName, Object bean, RootBeanDefinition mbd) {
		new DisposableBeanAdapter(bean, beanName, mbd, getBeanPostProcessors(), getAccessControlContext()).destroy();
	}

	@Override
	public void destroyScopedBean(String beanName) {
		RootBeanDefinition mbd = getMergedLocalBeanDefinition(beanName);
		if (mbd.isSingleton() || mbd.isPrototype()) {
			throw new IllegalArgumentException(
					"Bean name '" + beanName + "' does not correspond to an object in a mutable scope");
		}
		String scopeName = mbd.getScope();
		Scope scope = this.scopes.get(scopeName);
		if (scope == null) {
			throw new IllegalStateException("No Scope SPI registered for scope name '" + scopeName + "'");
		}
		Object bean = scope.remove(beanName);
		if (bean != null) {
			destroyBean(beanName, bean, mbd);
		}
	}


	//---------------------------------------------------------------------
	// Implementation methods
	//---------------------------------------------------------------------

	/**
	 * 去除name开头的'&'字符,获取name最终的规范名称【最终别名或者是全类名】：去除开头的'&'字符，返回剩余的字符串得到转换后的Bean名称,
	 * 然后通过递归形式在 aliasMap【别名映射到规范名称集合】中得到最终的规范名称
	 *
	 * Return the bean name, stripping out the factory dereference prefix if necessary,
	 * and resolving aliases to canonical names.
	 * @param name the user-specified name
	 * @return the transformed bean name
	 */
	protected String transformedBeanName(String name) {
		// 去除开头的'&'字符，返回剩余的字符串得到转换后的Bean名称，然后通过递归形式在aliasMap【别名映射到规范名称集合】中得到最终的规范名称
		return canonicalName(BeanFactoryUtils.transformedBeanName(name));
	}

	/**
	 * 获取name对应的规范名称【全类名/最终别名】，如果name前面有'&'，则会返回'&'+规范名称【全类名】
	 *
	 * Determine the original bean name, resolving locally defined aliases to canonical names.
	 * @param name the user-specified name
	 * @return the original bean name
	 */
	protected String originalBeanName(String name) {
		// 获取name对应的规范名称【全类名】,包括以'&'开头的name
		String beanName = transformedBeanName(name);
		// 如果name的开头有'&'
		if (name.startsWith(FACTORY_BEAN_PREFIX)) {
			// 对beanName拼接上'&'在开头位置
			beanName = FACTORY_BEAN_PREFIX + beanName;
		}
		// 返回规范的beanName
		return beanName;
	}

	/**
	 * 初始化BeanWrapper
	 *
	 * Initialize the given BeanWrapper with the custom editors registered
	 * with this factory. To be called for BeanWrappers that will create
	 * and populate bean instances.
	 * <p>The default implementation delegates to {@link #registerCustomEditors}.
	 * Can be overridden in subclasses.
	 * @param bw the BeanWrapper to initialize
	 */
	protected void initBeanWrapper(BeanWrapper bw) {
		// 使用该工厂的ConversionService来作为bw的ConversionService，用于转换属性值，以替换JavaBeans PropertyEditor
		bw.setConversionService(getConversionService());
		// 将工厂中所有PropertyEditor注册到bw中
		registerCustomEditors(bw);
	}

	/**
	 * 将工厂中所有PropertyEditor注册到PropertyEditorRegistry中
	 *
	 * Initialize the given PropertyEditorRegistry with the custom editors
	 * that have been registered with this BeanFactory.
	 * <p>To be called for BeanWrappers that will create and populate bean
	 * instances, and for SimpleTypeConverter used for constructor argument
	 * and factory method type conversion.
	 * @param registry the PropertyEditorRegistry to initialize
	 */
	protected void registerCustomEditors(PropertyEditorRegistry registry) {
		// PropertyEditorRegistrySupport是PropertyEditorRegistry接口的默认实现
		// 将registry强转成PropertyEditorRegistrySupport对象，如果registry不能强转则为null
		PropertyEditorRegistrySupport registrySupport =
				(registry instanceof PropertyEditorRegistrySupport ? (PropertyEditorRegistrySupport) registry : null);
		// 如果成功获取PropertyEditorRegistrySupport对象
		if (registrySupport != null) {
			// 激活仅用于配置目的的配置值编辑器
			registrySupport.useConfigValueEditors();
		}
		// PropertyEditorRegistrar：各种业务的PropertyEditorSupport一般都会先注册到PropertyEditorRegistrar中，再通过PropertyEditorRegistrar
		// 将PropertyEditorSupport注册到PropertyEditorRegistry中
		// 如果该工厂的propertyEditorRegistrar列表不为空
		if (!this.propertyEditorRegistrars.isEmpty()) {
			// propertyEditorRegistrars默认情况下只有一个元素对象，该对象为ResourceEditorRegistrar。
			// 遍历propertyEditorRegistrars
			for (PropertyEditorRegistrar registrar : this.propertyEditorRegistrars) {
				try {
					// ResourceEditorRegistrar会将ResourceEditor, InputStreamEditor, InputSourceEditor,
					// FileEditor, URLEditor, URIEditor, ClassEditor, ClassArrayEditor注册到registry中，
					// 如果registry已配置了ResourcePatternResolver,则还将注册ResourceArrayPropertyEditor
					// 将registrar中的所有PropertyEditor注册到PropertyEditorRegistry中
					registrar.registerCustomEditors(registry);
				}
				// 捕捉Bean创建异常
				catch (BeanCreationException ex) {
					Throwable rootCause = ex.getMostSpecificCause();
					if (rootCause instanceof BeanCurrentlyInCreationException) {
						BeanCreationException bce = (BeanCreationException) rootCause;
						String bceBeanName = bce.getBeanName();
						if (bceBeanName != null && isCurrentlyInCreation(bceBeanName)) {
							if (logger.isDebugEnabled()) {
								logger.debug("PropertyEditorRegistrar [" + registrar.getClass().getName() +
										"] failed because it tried to obtain currently created bean '" +
										ex.getBeanName() + "': " + ex.getMessage());
							}
							onSuppressedException(ex);
							continue;
						}
					}
					// 重写抛出ex
					throw ex;
				}
			}
		}
		// 如果该工厂的自定义PropertyEditor集合有元素，在SpringBoot中，customEditors默认是空的
		if (!this.customEditors.isEmpty()) {
			// 遍历自定义PropertyEditor集合,将其元素注册到registry中
			this.customEditors.forEach((requiredType, editorClass) ->
					registry.registerCustomEditor(requiredType, BeanUtils.instantiateClass(editorClass)));
		}
	}


	/**
	 * 获取beanName合并后的本地RootBeanDefinition
	 *
	 * Return a merged RootBeanDefinition, traversing the parent bean definition
	 * if the specified bean corresponds to a child bean definition.
	 * @param beanName the name of the bean to retrieve the merged definition for
	 * @return a (potentially merged) RootBeanDefinition for the given bean
	 * @throws NoSuchBeanDefinitionException if there is no bean with the given name
	 * @throws BeanDefinitionStoreException in case of an invalid bean definition
	 */
	protected RootBeanDefinition getMergedLocalBeanDefinition(String beanName) throws BeansException {
		// Quick check on the concurrent map first, with minimal locking.
		// 首先以最小的锁定快速检测并发映射。
		// 从bean名称映射到合并的RootBeanDefinition的集合中获取beanName对应的RootBeanDefinition
		RootBeanDefinition mbd = this.mergedBeanDefinitions.get(beanName);
		// 如果mbd不为null 且 不需要重新合并定义
		if (mbd != null && !mbd.stale) {
			// 返回对应的RootBeanDefinition
			return mbd;
		}
		// 获取beanName对应的合并Bean定义，如果beanName对应的BeanDefinition是子BeanDefinition,
		// 则通过与父级合并返回RootBeanDefinition
		return getMergedBeanDefinition(beanName, getBeanDefinition(beanName));
	}

	/**
	 *  获取beanName对应的合并后的RootBeanDefinition:直接交给
	 * 	getMergedBeanDefinition(String, BeanDefinition,BeanDefinition)处理，第三个参数传null
	 *
	 * Return a RootBeanDefinition for the given top-level bean, by merging with
	 * the parent if the given bean's definition is a child bean definition.
	 * @param beanName the name of the bean definition
	 * @param bd the original bean definition (Root/ChildBeanDefinition)
	 * @return a (potentially merged) RootBeanDefinition for the given bean
	 * @throws BeanDefinitionStoreException in case of an invalid bean definition
	 */
	protected RootBeanDefinition getMergedBeanDefinition(String beanName, BeanDefinition bd)
			throws BeanDefinitionStoreException {

		return getMergedBeanDefinition(beanName, bd, null);
	}

	/**
	 * 获取beanName对应的合并后的RootBeanDefinition
	 *
	 * Return a RootBeanDefinition for the given bean, by merging with the
	 * parent if the given bean's definition is a child bean definition.
	 * @param beanName the name of the bean definition
	 * @param bd the original bean definition (Root/ChildBeanDefinition)
	 * @param containingBd the containing bean definition in case of inner bean,
	 * or {@code null} in case of a top-level bean
	 * @return a (potentially merged) RootBeanDefinition for the given bean
	 * @throws BeanDefinitionStoreException in case of an invalid bean definition
	 */
	protected RootBeanDefinition getMergedBeanDefinition(
			String beanName, BeanDefinition bd, @Nullable BeanDefinition containingBd)
			throws BeanDefinitionStoreException {

		// 同步：使用从bean名称映射到合并的RootBeanDefinition集合进行加锁
		synchronized (this.mergedBeanDefinitions) {
			// 用于存储bd的MergedBeanDefinition
			RootBeanDefinition mbd = null;
			//该变量表示从bean名称映射到合并的RootBeanDefinition集合中取到的mbd且该mbd需要重新合并定义
			RootBeanDefinition previous = null;

			// Check with full lock now in order to enforce the same merged instance.
			// 立即检查完全锁定，以强制执行相同的合并实例,如果没有包含bean定义
			if (containingBd == null) {
				// 从bean名称映射到合并的RootBeanDefinition集合中获取beanName对应的BeanDefinition作为mbd
				mbd = this.mergedBeanDefinitions.get(beanName);
			}

			// 如果mbd为null或者mdb需要重新合并定义
			if (mbd == null || mbd.stale) {
				// 将mdn作为previous
				previous = mbd;
				// 如果获取不到原始BeanDefinition的父Bean名
				if (bd.getParentName() == null) {
					// Use copy of given root bean definition.
					// 使用给定的RootBeanDefinition的副本,如果原始BeanDefinition是RootBeanDefinition对象
					if (bd instanceof RootBeanDefinition) {
						// 克隆一份bd的Bean定义赋值给mdb
						mbd = ((RootBeanDefinition) bd).cloneBeanDefinition();
					}
					else {
						// 创建一个新的RootBeanDefinition作为bd的深层副本并赋值给mbd
						mbd = new RootBeanDefinition(bd);
					}
				}
				else {
					// Child bean definition: needs to be merged with parent.
					// 子bean定义：需要与父bean合并,定义一个父级BeanDefinition变量
					BeanDefinition pbd;
					try {
						// 获取bd的父级Bean对应的最终别名
						String parentBeanName = transformedBeanName(bd.getParentName());
						// 如果当前bean名不等于父级bean名
						if (!beanName.equals(parentBeanName)) {
							// 获取parentBeanName的"合并的"BeanDefinition赋值给pdb
							pbd = getMergedBeanDefinition(parentBeanName);
						}
						// 如果父定义的beanName与bd的beanName相同，则拿到父BeanFactory，
						// 只有在存在父BeanFactory的情况下，才允许父定义beanName与自己相同，否则就是将自己设置为父定义
						else {
							BeanFactory parent = getParentBeanFactory();
							// 如果父BeanFactory是ConfigurableBeanFactory，则通过父BeanFactory获取父定义的MergedBeanDefinition
							if (parent instanceof ConfigurableBeanFactory) {
								// 使用父工厂获取parentBeanName对应的合并BeanDefinition赋值给pdb
								pbd = ((ConfigurableBeanFactory) parent).getMergedBeanDefinition(parentBeanName);
							}
							else {
								// 如果父工厂不是ConfigurableBeanFactory,抛出没有此类bean定义异常，父级bean名为parentBeanName等于名为beanName的bean名；
								// 没有AbstractBeanFactory父级无法解决
								throw new NoSuchBeanDefinitionException(parentBeanName,
										"Parent name '" + parentBeanName + "' is equal to bean name '" + beanName +
										"': cannot be resolved without a ConfigurableBeanFactory parent");
							}
						}
					}
					catch (NoSuchBeanDefinitionException ex) {
						throw new BeanDefinitionStoreException(bd.getResourceDescription(), beanName,
								"Could not resolve parent bean definition '" + bd.getParentName() + "'", ex);
					}
					// Deep copy with overridden values.
					// 使用父定义pbd构建一个新的RootBeanDefinition对象
					mbd = new RootBeanDefinition(pbd);
					// 使用原始bd定义信息覆盖父级的定义信息:
					// 1. 如果在给定的bean定义中指定，则将覆盖beanClass
					// 2. 将始终从给定的bean定义中获取abstract,scope,lazyInit,autowireMode,
					// 			dependencyCheck和dependsOn
					// 3. 将给定bean定义中ConstructorArgumentValues,propertyValues,
					// 			methodOverrides 添加到现有的bean定义中
					// 4. 如果在给定的bean定义中指定，将覆盖factoryBeanName,factoryMethodName,
					// 		initMethodName,和destroyMethodName
					mbd.overrideFrom(bd);
				}

				// Set default singleton scope, if not configured before.
				// 设置默认的单例作用域（如果之前未配置）,如果mbd之前没有配置过作用域
				if (!StringUtils.hasLength(mbd.getScope())) {
					// 设置mbd的作用域为单例
					mbd.setScope(SCOPE_SINGLETON);
				}

				// A bean contained in a non-singleton bean cannot be a singleton itself.
				// Let's correct this on the fly here, since this might be the result of
				// parent-child merging for the outer bean, in which case the original inner bean
				// definition will not have inherited the merged outer bean's singleton status.
				// 非单例bean中包含的bean本身不能是单例。
				// 让我们在此即时进行更正，因为这可能是外部bean的父子合并的结果，在这种情况下，
				// 原始内部bean定义将不会继承合并的外部bean的单例状态。
				// 如果有传包含bean定义且包含bean定义不是单例但mbd又是单例
				if (containingBd != null && !containingBd.isSingleton() && mbd.isSingleton()) {
					// 让mbd的作用域设置为跟containingBd的作用域一样
					mbd.setScope(containingBd.getScope());
				}

				// Cache the merged bean definition for the time being
				// (it might still get re-merged later on in order to pick up metadata changes)
				// 暂时缓存合并的bean定义(稍后可能仍会重新合并以获取元数据更正),如果没有传入包含bean定义 且 当前工厂是同意缓存bean元数据
				if (containingBd == null && isCacheBeanMetadata()) {
					//将beanName和mbd的关系添加到 从bean名称映射到合并的RootBeanDefinition集合中
					this.mergedBeanDefinitions.put(beanName, mbd);
				}
			}
			// 如果存在上一个从bean名称映射到合并的RootBeanDefinition集合中取出的mbd
			// 且该mbd需要重新合并定义
			if (previous != null) {
				// 拿previous来对mdb进行重新合并定义：
				// 1. 设置mbd的目标类型为previous的目标类型
				// 2. 设置mbd的工厂bean标记为previous的工厂bean标记
				// 3. 设置mbd的用于缓存给定bean定义的确定的Class为previous的用于缓存给定bean定义的确定的Class
				// 4. 设置mbd的工厂方法返回类型为previous的工厂方法返回类型
				// 5. 设置mbd的用于缓存用于自省的唯一工厂方法候选为previous的用于缓存用于自省的唯一工厂方法候选
				copyRelevantMergedBeanDefinitionCaches(previous, mbd);
			}
			// 返回MergedBeanDefinition
			return mbd;
		}
	}

	/**
	 * 复制相关的合并bean定义缓存，或者说拿previous来对mdb进行重新合并定义
	 * @param previous
	 * @param mbd
	 */
	private void copyRelevantMergedBeanDefinitionCaches(RootBeanDefinition previous, RootBeanDefinition mbd) {
		// ObjectUtils.nullSafeEquals:确定给定的对象是否相等，如果两个都为null返回true,
		// 如果其中一个为null，返回false,mbd和previous的当前Bean类名称相同，工厂bean名称相同，工厂方法名相同
		if (ObjectUtils.nullSafeEquals(mbd.getBeanClassName(), previous.getBeanClassName()) &&
				ObjectUtils.nullSafeEquals(mbd.getFactoryBeanName(), previous.getFactoryBeanName()) &&
				ObjectUtils.nullSafeEquals(mbd.getFactoryMethodName(), previous.getFactoryMethodName())) {
			// 获取mbd的目标类型
			ResolvableType targetType = mbd.targetType;
			// 获取previous的目标类型
			ResolvableType previousTargetType = previous.targetType;
			// 如果mdb的目标类型为null或者mdb的目标类型与previous的目标类型相同
			if (targetType == null || targetType.equals(previousTargetType)) {
				// 设置mbd的目标类型为previous的目标类型
				mbd.targetType = previousTargetType;
				// 设置mbd的工厂bean标记为previous的工厂bean标记
				mbd.isFactoryBean = previous.isFactoryBean;
				// 设置mbd的用于缓存给定bean定义的确定的Class为previous的用于缓存给定bean定义的确定的Class
				mbd.resolvedTargetType = previous.resolvedTargetType;
				// 设置mbd的工厂方法返回类型为previous的工厂方法返回类型
				mbd.factoryMethodReturnType = previous.factoryMethodReturnType;
				// 设置mbd的用于缓存用于自省的唯一工厂方法候选为previous的用于缓存用于自省的唯一工厂方法候选
				mbd.factoryMethodToIntrospect = previous.factoryMethodToIntrospect;
			}
		}
	}

	/**
	 * 检测当前BeanDefinition是否是抽象的，如果是抽象的，那么就抛出异常
	 *
	 * Check the given merged bean definition,
	 * potentially throwing validation exceptions.
	 * @param mbd the merged bean definition to check
	 * @param beanName the name of the bean
	 * @param args the arguments for bean creation, if any
	 * @throws BeanDefinitionStoreException in case of validation failure
	 */
	protected void checkMergedBeanDefinition(RootBeanDefinition mbd, String beanName, @Nullable Object[] args)
			throws BeanDefinitionStoreException {

		// 如果mbd所配置的bean是抽象的
		if (mbd.isAbstract()) {
			// 抛出Bean为抽象异常
			throw new BeanIsAbstractException(beanName);
		}
	}

	/**
	 * 将beanName对应的合并后RootBeanDefinition对象标记为重新合并定义
	 *
	 * Remove the merged bean definition for the specified bean,
	 * recreating it on next access.
	 * @param beanName the bean name to clear the merged definition for
	 */
	protected void clearMergedBeanDefinition(String beanName) {
		// 从合并后BeanDefinition集合缓存中获取beanName对应的合并后RootBeanDefinition对象
		RootBeanDefinition bd = this.mergedBeanDefinitions.get(beanName);
		// 如果成功获取到了bd
		if (bd != null) {
			// 将bd标记为需要重新合并定义
			bd.stale = true;
		}
	}

	/**
	 * 默认实现 如果 mergedBeanDefinitions中的beanNamee没有资格缓存其BeanDefinition元数据时，将所对应的bd标记为需要重新合并定义
	 *
	 * Clear the merged bean definition cache, removing entries for beans
	 * which are not considered eligible for full metadata caching yet.
	 * <p>Typically triggered after changes to the original bean definitions,
	 * e.g. after applying a {@code BeanFactoryPostProcessor}. Note that metadata
	 * for beans which have already been created at this point will be kept around.
	 * @since 4.2
	 */
	public void clearMetadataCache() {
		// mergedBeanDefinitions:从bean名称映射到合并的RootBeanDefinition
		// 遍历mergedBeanDefinitions
		this.mergedBeanDefinitions.forEach((beanName, bd) -> {
			// 如果BeanName没有资格缓存其BeanDefinition元数据
			if (!isBeanEligibleForMetadataCaching(beanName)) {
				// 将bd标记为需要重新合并定义
				bd.stale = true;
			}
		});
	}

	/**
	 * 为指定的bean定义解析bean类，将bean类名解析为Class引用（如果需要）,并将解析后的Class存储在bean定义中以备将来使用
	 *
	 * Resolve the bean class for the specified bean definition,
	 * resolving a bean class name into a Class reference (if necessary)
	 * and storing the resolved Class in the bean definition for further use.
	 * @param mbd the merged bean definition to determine the class for
	 * @param beanName the name of the bean (for error handling purposes)
	 * @param typesToMatch the types to match in case of internal type matching purposes
	 * (also signals that the returned {@code Class} will never be exposed to application code)
	 * @return the resolved bean class (or {@code null} if none)
	 * @throws CannotLoadBeanClassException if we failed to load the class
	 */
	@Nullable
	protected Class<?> resolveBeanClass(RootBeanDefinition mbd, String beanName, Class<?>... typesToMatch)
			throws CannotLoadBeanClassException {

		try {
			// 判断mbd的定义信息中是否包含beanClass，并且是Class类型的，如果是直接返回，否则的话进行详细的解析
			if (mbd.hasBeanClass()) {
				// 如果mbd指定了bean类
				return mbd.getBeanClass();
			}
			// 判断是否有安全管理器
			if (System.getSecurityManager() != null) {
				return AccessController.doPrivileged((PrivilegedExceptionAction<Class<?>>)
						() -> doResolveBeanClass(mbd, typesToMatch), getAccessControlContext());
			}
			else {
				// 进行详细的处理解析过程
				return doResolveBeanClass(mbd, typesToMatch);
			}
		}
		catch (PrivilegedActionException pae) {
			ClassNotFoundException ex = (ClassNotFoundException) pae.getException();
			throw new CannotLoadBeanClassException(mbd.getResourceDescription(), beanName, mbd.getBeanClassName(), ex);
		}
		catch (ClassNotFoundException ex) {
			throw new CannotLoadBeanClassException(mbd.getResourceDescription(), beanName, mbd.getBeanClassName(), ex);
		}
		catch (LinkageError err) {
			throw new CannotLoadBeanClassException(mbd.getResourceDescription(), beanName, mbd.getBeanClassName(), err);
		}
	}

	/**
	 * 获取mbd配置的bean类名，将bean类名解析为Class对象,并将解析后的Class对象缓存在mdb中以备将来使用
	 * @param mbd
	 * @param typesToMatch
	 * @return
	 * @throws ClassNotFoundException
	 */
	@Nullable
	private Class<?> doResolveBeanClass(RootBeanDefinition mbd, Class<?>... typesToMatch)
			throws ClassNotFoundException {

		// 获取该工厂的加载bean用的类加载器
		ClassLoader beanClassLoader = getBeanClassLoader();
		// 初始化动态类加载器为该工厂的加载bean用的类加载器,如果该工厂有
		// 临时类加载器器时，该动态类加载器就是该工厂的临时类加载器
		ClassLoader dynamicLoader = beanClassLoader;
		// 表示mdb的配置的bean类名需要重新被dynameicLoader加载的标记，默认不需要
		boolean freshResolve = false;

		//如果有传入要匹配的类型
		if (!ObjectUtils.isEmpty(typesToMatch)) {
			// When just doing type checks (i.e. not creating an actual instance yet),
			// use the specified temporary class loader (e.g. in a weaving scenario).
			// 仅进行类型检查时（即尚未创建实际实例），请使用指定的临时类加载器
			// 获取该工厂的临时类加载器，该临时类加载器专门用于类型匹配
			ClassLoader tempClassLoader = getTempClassLoader();
			// 如果成功获取到临时类加载器
			if (tempClassLoader != null) {
				// 以该工厂的临时类加载器作为动态类加载器
				dynamicLoader = tempClassLoader;
				// 标记mdb的配置的bean类名需要重新被dynameicLoader加载
				freshResolve = true;
				// DecoratingClassLoader:装饰ClassLoader的基类,提供对排除的包和类的通用处理
				// 如果临时类加载器是DecoratingClassLoader的基类
				if (tempClassLoader instanceof DecoratingClassLoader) {
					// 将临时类加载器强转为DecoratingClassLoader实例
					DecoratingClassLoader dcl = (DecoratingClassLoader) tempClassLoader;
					// 对要匹配的类型进行在装饰类加载器中的排除，以交由父ClassLoader以常规方式处理
					for (Class<?> typeToMatch : typesToMatch) {
						dcl.excludeClass(typeToMatch.getName());
					}
				}
			}
		}

		// 从mbd中获取配置的bean类名
		String className = mbd.getBeanClassName();
		// 如果能成功获得配置的bean类名
		if (className != null) {
			//评估benaDefinition中包含的className,如果className是可解析表达式，会对其进行解析，否则直接返回className:
			Object evaluated = evaluateBeanDefinitionString(className, mbd);
			// 判断className是否等于计算出的表达式的结果，如果不等于，那么判断evaluated的类型
			if (!className.equals(evaluated)) {
				// A dynamically resolved expression, supported as of 4.2...
				// 如果evaluated属于Class实例
				if (evaluated instanceof Class) {
					// 强转evaluatedw为Class对象并返回出去
					return (Class<?>) evaluated;
				}
				// 如果evaluated属于String实例
				else if (evaluated instanceof String) {
					// 将evaluated作为className的值
					className = (String) evaluated;
					// 标记mdb的配置的bean类名需要重新被dynameicLoader加载
					freshResolve = true;
				}
				else {
					// 抛出非法状态异常：无效的类名表达式结果：evaluated
					throw new IllegalStateException("Invalid class name expression result: " + evaluated);
				}
			}
			// 如果mdb的配置的bean类名需要重新被dynameicLoader加载
			if (freshResolve) {
				// When resolving against a temporary class loader, exit early in order
				// to avoid storing the resolved Class in the bean definition.
				// 当使用临时类加载器进行解析时，请尽早退出以避免将已解析的类存储在BeanDefinition中
				// 如果动态类加载器不为null
				if (dynamicLoader != null) {
					try {
						// 使用dynamicLoader加载className对应的类型，并返回加载成功的Class对象
						return dynamicLoader.loadClass(className);
					}
					// 捕捉未找到类异常，
					catch (ClassNotFoundException ex) {
						if (logger.isTraceEnabled()) {
							logger.trace("Could not load class [" + className + "] from " + dynamicLoader + ": " + ex);
						}
					}
				}
				// 使用classLoader加载name对应的Class对象,该方式是Spring用于代替Class.forName()的方法，支持返回原始的类实例(如'int')
				// 和数组类名 (如'String[]')。此外，它还能够以Java source样式解析内部类名(如:'java.lang.Thread.State'
				// 而不是'java.lang.Thread$State')
				return ClassUtils.forName(className, dynamicLoader);
			}
		}

		// Resolve regularly, caching the result in the BeanDefinition...
		// 定期解析，将结果缓存在BeanDefinition中...
		// 使用classLoader加载当前BeanDefinitiond对象所配置的Bean类名的Class对象（每次调用都会重新加载,可通过
		// AbstractBeanDefinition#getBeanClass 获取缓存）
		return mbd.resolveBeanClass(beanClassLoader);
	}

	/**
	 * 通过表达式处理器解析beanDefinition中给定的字符串
	 *
	 * Evaluate the given String as contained in a bean definition,
	 * potentially resolving it as an expression.
	 * @param value the value to check
	 * @param beanDefinition the bean definition that the value comes from
	 * @return the resolved value
	 * @see #setBeanExpressionResolver
	 */
	@Nullable
	protected Object evaluateBeanDefinitionString(@Nullable String value, @Nullable BeanDefinition beanDefinition) {
		// 如果该工厂没有设置bean定义值中表达式的解析策略
		if (this.beanExpressionResolver == null) {
			// 直接返回要检查的值
			return value;
		}

		// 值所来自的bean定义的当前目标作用域
		Scope scope = null;
		// 如果有传入值所来自的bean定义
		if (beanDefinition != null) {
			// 获取值所来自的bean定义的当前目标作用域名
			String scopeName = beanDefinition.getScope();
			// 如果成功获得值所来自的bean定义的当前目标作用域名
			if (scopeName != null) {
				// 获取scopeName对应的Scope对象
				scope = getRegisteredScope(scopeName);
			}
		}
		// 评估value作为表达式（如果适用）；否则按原样返回值
		return this.beanExpressionResolver.evaluate(value, new BeanExpressionContext(this, scope));
	}


	/**
	 *  预测mdb所指的bean的最终bean类型(已处理bean实例的类型)
	 *
	 * Predict the eventual bean type (of the processed bean instance) for the
	 * specified bean. Called by {@link #getType} and {@link #isTypeMatch}.
	 * Does not need to handle FactoryBeans specifically, since it is only
	 * supposed to operate on the raw bean type.
	 * <p>This implementation is simplistic in that it is not able to
	 * handle factory methods and InstantiationAwareBeanPostProcessors.
	 * It only predicts the bean type correctly for a standard bean.
	 * To be overridden in subclasses, applying more sophisticated type detection.
	 * @param beanName the name of the bean
	 * @param mbd the merged bean definition to determine the type for
	 * @param typesToMatch the types to match in case of internal type matching purposes
	 * (also signals that the returned {@code Class} will never be exposed to application code)
	 * @return the type of the bean, or {@code null} if not predictable
	 */
	@Nullable
	protected Class<?> predictBeanType(String beanName, RootBeanDefinition mbd, Class<?>... typesToMatch) {
		// 获取mbd的目标类型
		Class<?> targetType = mbd.getTargetType();
		// 如果成功获得mbd的目标类型
		if (targetType != null) {
			// 返回 mbd的目标类型
			return targetType;
		}
		// 如果有设置mbd的工厂方法名
		if (mbd.getFactoryMethodName() != null) {
			// 返回null，表示不可预测
			return null;
		}
		// 为mbd解析bean类，将beanName解析为Class引用（如果需要）,并将解析后的Class存储在mbd中以备将来使用。
		return resolveBeanClass(mbd, beanName, typesToMatch);
	}

	/**
	 * 根据名字和bean定义信息判断是否是FactoryBean
	 * 如果定义本身定义了isFactoryBean,那么直接返回结果，否则需要进行类型预测，会通过反射来判断名字对应的类是否是FactoryBean类型，如果是
	 * 返回true，如果不是返回false
	 *
	 * Check whether the given bean is defined as a {@link FactoryBean}.
	 * @param beanName the name of the bean
	 * @param mbd the corresponding bean definition
	 */
	protected boolean isFactoryBean(String beanName, RootBeanDefinition mbd) {
		// 定义一个存储mbd是否是FactoryBean的标记
		Boolean result = mbd.isFactoryBean;
		// 如果没有配置mbd的工厂Bean
		if (result == null) {
			// 根据预测指定bean的最终bean类型
			Class<?> beanType = predictBeanType(beanName, mbd, FactoryBean.class);
			// 如果成功获取最终bean类型，且最终bean类型属于FactoryBean类型
			result = (beanType != null && FactoryBean.class.isAssignableFrom(beanType));
			// 将result缓存在mbd中
			mbd.isFactoryBean = result;
		}
		// 如果不为空，直接返回
		return result;
	}

	/**
	 * 获取beanName,mbd所指的FactoryBean要创建的bean类型
	 *
	 * Determine the bean type for the given FactoryBean definition, as far as possible.
	 * Only called if there is no singleton instance registered for the target bean
	 * already. The implementation is allowed to instantiate the target factory bean if
	 * {@code allowInit} is {@code true} and the type cannot be determined another way;
	 * otherwise it is restricted to introspecting signatures and related metadata.
	 * <p>If no {@link FactoryBean#OBJECT_TYPE_ATTRIBUTE} if set on the bean definition
	 * and {@code allowInit} is {@code true}, the default implementation will create
	 * the FactoryBean via {@code getBean} to call its {@code getObjectType} method.
	 * Subclasses are encouraged to optimize this, typically by inspecting the generic
	 * signature of the factory bean class or the factory method that creates it.
	 * If subclasses do instantiate the FactoryBean, they should consider trying the
	 * {@code getObjectType} method without fully populating the bean. If this fails,
	 * a full FactoryBean creation as performed by this implementation should be used
	 * as fallback.
	 * @param beanName the name of the bean
	 * @param mbd the merged bean definition for the bean
	 * @param allowInit if initialization of the FactoryBean is permitted if the type
	 * cannot be determined another way
	 * @return the type for the bean if determinable, otherwise {@code ResolvableType.NONE}
	 * @since 5.2
	 * @see org.springframework.beans.factory.FactoryBean#getObjectType()
	 * @see #getBean(String)
	 */
	protected ResolvableType getTypeForFactoryBean(String beanName, RootBeanDefinition mbd, boolean allowInit) {
		// 通过检查mbd中的属性名FactoryBean.OBJECT_TYPE_ATTRIBUTE的值来
		// 确定FactoryBean的bean类型
		ResolvableType result = getTypeForFactoryBeanFromAttributes(mbd);
		// 如果得到的bean类型不是ResolvableType.NONE
		// ResolvableType.NONE:表示没有可用的值，相当于 null
		if (result != ResolvableType.NONE) {
			// 返回该Bean类型
			return result;
		}

		// 如果允许初始化FactoryBean且mbd配置了单例
		if (allowInit && mbd.isSingleton()) {
			try {
				// 获取该beanName的BeanFactory对象
				FactoryBean<?> factoryBean = doGetBean(FACTORY_BEAN_PREFIX + beanName, FactoryBean.class, null, true);
				// 获取factoryBean的创建出来的对象的类型
				Class<?> objectType = getTypeForFactoryBean(factoryBean);
				// 如果成功得到对象类型就将其封装成ResolvableType对象，否则返回ResolvableType.NONE
				// ResolvableType.NONE表示没有可用的值，相当于 null
				return (objectType != null ? ResolvableType.forClass(objectType) : ResolvableType.NONE);
			}
			// 捕捉尝试从Bean定义创建Bean时BeanFactory遇到错误时引发的异常
			catch (BeanCreationException ex) {
				if (ex.contains(BeanCurrentlyInCreationException.class)) {
					logger.trace(LogMessage.format("Bean currently in creation on FactoryBean type check: %s", ex));
				}
				else if (mbd.isLazyInit()) {
					logger.trace(LogMessage.format("Bean creation exception on lazy FactoryBean type check: %s", ex));
				}
				else {
					logger.debug(LogMessage.format("Bean creation exception on eager FactoryBean type check: %s", ex));
				}
				onSuppressedException(ex);
			}
		}
		// 如果无法通过mbd中的属性名FactoryBean.OBJECT_TYPE_ATTRIBUTE的值来确定类型
		// 又不允许初始化FactoryBean或者mbd不是配置成单例，
		// 或者允许初始化FactoryBean且mbd配置了单例时，尝试该beanName的BeanFactory对象来
		// 获取factoryBean的创建出来的对象的类型但抛出了尝试从Bean定义创建Bean时BeanFactory
		// 遇到错误时引发的异常 ，都会返回ResolvableType.NONE
		return ResolvableType.NONE;
	}

	/**
	 * 通过检查FactoryBean.OBJECT_TYPE_ATTRIBUTE值的属性来确定FactoryBean的bean类型
	 *
	 * Determine the bean type for a FactoryBean by inspecting its attributes for a
	 * {@link FactoryBean#OBJECT_TYPE_ATTRIBUTE} value.
	 * @param attributes the attributes to inspect
	 * @return a {@link ResolvableType} extracted from the attributes or
	 * {@code ResolvableType.NONE}
	 * @since 5.2
	 */
	ResolvableType getTypeForFactoryBeanFromAttributes(AttributeAccessor attributes) {
		// 获取FactoryBean.OBJECT_TYPE_ATTRIUTE在BeanDefinition对象的属性值
		Object attribute = attributes.getAttribute(FactoryBean.OBJECT_TYPE_ATTRIBUTE);
		// 如果属性值是ResolvableType的实例
		if (attribute instanceof ResolvableType) {
			// 强转并返回该属性值
			return (ResolvableType) attribute;
		}
		// 如果属性值是Class实例
		if (attribute instanceof Class) {
			// 使用ResolvableType封装属性值后返回
			return ResolvableType.forClass((Class<?>) attribute);
		}
		// 如果没有成功获取到FactoryBean.OBJECT_TYPE_ATTRIUTE在BeanDefinition对象的属性值
		// 则返回 ResolvableType.NONE，
		// ResolvableType.NONE：表示没有可用的值，相当于null
		return ResolvableType.NONE;
	}

	/**
	 * Determine the bean type for the given FactoryBean definition, as far as possible.
	 * Only called if there is no singleton instance registered for the target bean already.
	 * <p>The default implementation creates the FactoryBean via {@code getBean}
	 * to call its {@code getObjectType} method. Subclasses are encouraged to optimize
	 * this, typically by just instantiating the FactoryBean but not populating it yet,
	 * trying whether its {@code getObjectType} method already returns a type.
	 * If no type found, a full FactoryBean creation as performed by this implementation
	 * should be used as fallback.
	 * @param beanName the name of the bean
	 * @param mbd the merged bean definition for the bean
	 * @return the type for the bean if determinable, or {@code null} otherwise
	 * @see org.springframework.beans.factory.FactoryBean#getObjectType()
	 * @see #getBean(String)
	 * @deprecated since 5.2 in favor of {@link #getTypeForFactoryBean(String, RootBeanDefinition, boolean)}
	 */
	@Nullable
	@Deprecated
	protected Class<?> getTypeForFactoryBean(String beanName, RootBeanDefinition mbd) {
		return getTypeForFactoryBean(beanName, mbd, true).resolve();
	}

	/**
	 * 为指定的Bean标记为已经创建（或将要创建）
	 *
	 * Mark the specified bean as already created (or about to be created).
	 * <p>This allows the bean factory to optimize its caching for repeated
	 * creation of the specified bean.
	 * @param beanName the name of the bean
	 */
	protected void markBeanAsCreated(String beanName) {
		// 如果beanName还没有创建
		if (!this.alreadyCreated.contains(beanName)) {
			// 同步，使用mergedBenDefinitions作为锁
			synchronized (this.mergedBeanDefinitions) {
				// 如果beanName还没有创建
				if (!this.alreadyCreated.contains(beanName)) {
					// Let the bean definition get re-merged now that we're actually creating
					// the bean... just in case some of its metadata changed in the meantime.
					// 在我们实际创建时，重新合并bean定义,以防万一期间的某些元数据发生了变化
					// 删除beanName合并bean定义，在下次访问时重新创建
					clearMergedBeanDefinition(beanName);
					this.alreadyCreated.add(beanName);
				}
			}
		}
	}

	/**
	 * 在Bean创建失败后，对缓存的元数据执行适当的清理
	 *
	 * Perform appropriate cleanup of cached metadata after bean creation failed.
	 * @param beanName the name of the bean
	 */
	protected void cleanupAfterBeanCreationFailure(String beanName) {
		// mergedBeanDefinitions:从bean名称映射到合并的RootBeanDefinition
		// 使用mergedBeanDefinitions加锁，保证线程安全
		synchronized (this.mergedBeanDefinitions) {
			// alreadyCreated:至少已经创建一次的bean名称
			// 将beanName从alreadyCreated中删除
			this.alreadyCreated.remove(beanName);
		}
	}

	/**
	 * 确定指定的Bean是否有资格缓存其BeanDefinition元数据
	 *
	 * Determine whether the specified bean is eligible for having
	 * its bean definition metadata cached.
	 * @param beanName the name of the bean
	 * @return {@code true} if the bean's metadata may be cached
	 * at this point already
	 */
	protected boolean isBeanEligibleForMetadataCaching(String beanName) {
		// alreadyCreated:至少已经创建一次的bean名称
		// 返回alreadyCreated是否包含beanName的结果
		return this.alreadyCreated.contains(beanName);
	}

	/**
	 * 删除给定Bean名称的单例实例(如果有的话)，但仅当它没有用于类型检查之外的其他目的时才删除
	 *
	 * Remove the singleton instance (if any) for the given bean name,
	 * but only if it hasn't been used for other purposes than type checking.
	 * @param beanName the name of the bean
	 * @return {@code true} if actually removed, {@code false} otherwise
	 */
	protected boolean removeSingletonIfCreatedForTypeCheckOnly(String beanName) {
		// 如果已创建的bean名称中没有该beanName对应对象
		if (!this.alreadyCreated.contains(beanName)) {
			// 1.从该工厂单例缓存中删除具有给定名称的Bean。如果创建失败，则能够清理饿汉式注册 的单例
			// 2.FactoryBeanRegistrySupport重写以清除FactoryBean对象缓存
			// 3.AbstractAutowireCapableBeanFactory重写 以清除FactoryBean对象缓存
			removeSingleton(beanName);
			// 有删除时返回true
			return true;
		}
		else {
			// 没有删除时返回false
			return false;
		}
	}

	/**
	 * 该工厂是否已经开始创建Bean：只要alreadyCreated【至少已经创建一次的bean名称集合】不为空，返回true，表示该工厂已经开始创建Bean；否则返回false
	 *
	 * Check whether this factory's bean creation phase already started,
	 * i.e. whether any bean has been marked as created in the meantime.
	 * @since 4.2.2
	 * @see #markBeanAsCreated
	 */
	protected boolean hasBeanCreationStarted() {
		// 只要alreadyCreated【至少已经创建一次的bean名称集合】不为空，返回true，表示该工厂已经开始创建Bean,否则返回false
		return !this.alreadyCreated.isEmpty();
	}

	/**
	 * 从 beanInstannce 中获取公开的Bean对象，主要处理beanInstance是FactoryBean对象的情况，如果不是FactoryBean会直接返回beanInstance实例
	 *
	 * Get the object for the given bean instance, either the bean
	 * instance itself or its created object in case of a FactoryBean.
	 * @param beanInstance the shared bean instance
	 * @param name the name that may include factory dereference prefix
	 * @param beanName the canonical bean name
	 * @param mbd the merged bean definition
	 * @return the object to expose for the bean
	 */
	protected Object getObjectForBeanInstance(
			Object beanInstance, String name, String beanName, @Nullable RootBeanDefinition mbd) {

		// Don't let calling code try to dereference the factory if the bean isn't a factory.
		// 如果Bean不是工厂，不要让调用代码尝试取消对工厂的引用
		// 如果name为FactoryBean的解引用.name是以'&'开头，就是FactoryBean的解引用
		if (BeanFactoryUtils.isFactoryDereference(name)) {
			// 如果beanInstance是NullBean实例
			if (beanInstance instanceof NullBean) {
				// 返回beanInstance
				return beanInstance;
			}
			// 如果beanInstance不是FactoryBean实例
			if (!(beanInstance instanceof FactoryBean)) {
				// 抛出Bean不是一个Factory异常
				throw new BeanIsNotAFactoryException(beanName, beanInstance.getClass());
			}
			// 如果mbd不为null
			if (mbd != null) {
				// 设置mbd是否是FactoryBean标记为true
				mbd.isFactoryBean = true;
			}
			// 返回beanInstance
			return beanInstance;
		}

		// Now we have the bean instance, which may be a normal bean or a FactoryBean.
		// If it's a FactoryBean, we use it to create a bean instance, unless the
		// caller actually wants a reference to the factory.
		// 现在我们有了Bean实例，他可能是一个普通的Bean或FactoryBean。
		// 如果它是FactoryBean,我们使用它来创建一个Bean实例，除非调用者确实需要对工厂的引用。
		// 如果beanInstance不是FactoryBean实例
		if (!(beanInstance instanceof FactoryBean)) {
			return beanInstance;
		}

		// 定义为bean公开的对象，初始化为null
		Object object = null;
		// 如果mbd不为null
		if (mbd != null) {
			// 更新mbd的是否是FactoryBean标记为true
			mbd.isFactoryBean = true;
		}
		else {
			// 从FactoryBean获得的对象缓存集中获取beanName对应的Bean对象
			object = getCachedObjectForFactoryBean(beanName);
		}
		// 如果object为null
		if (object == null) {
			// Return bean instance from factory.
			// 从工厂返回Bean实例
			// 将beanInstance强转为FactoryBean对象
			FactoryBean<?> factory = (FactoryBean<?>) beanInstance;
			// Caches object obtained from FactoryBean if it is a singleton.
			// 如果是单例对象，则缓存从FactoryBean获得的对象、
			// 如果mbd为null&&该BeanFactory包含beanName的BeanDefinition对象。
			if (mbd == null && containsBeanDefinition(beanName)) {
				//获取beanName合并后的本地RootBeanDefintiond对象
				mbd = getMergedLocalBeanDefinition(beanName);
			}
			// 是否是'synthetic'标记：mbd不为null && 返回此bean定义是否是"synthetic"【一般是指只有AOP相关的prointCut配置或者
			// Advice配置才会将 synthetic设置为true】
			boolean synthetic = (mbd != null && mbd.isSynthetic());
			// 从BeanFactory对象中获取管理的对象.如果不是synthetic会对其对象进行该工厂的后置处理
			object = getObjectFromFactoryBean(factory, beanName, !synthetic);
		}
		// 返回为bean公开的对象
		return object;
	}

	/**
	 * 判断beanName是否已在该工厂中使用,即beanName是否是别名或该工厂是否已包含beanName的bean对象或该工厂是否已经为beanName注册了依赖Bean关系
	 *
	 * Determine whether the given bean name is already in use within this factory,
	 * i.e. whether there is a local bean or alias registered under this name or
	 * an inner bean created with this name.
	 * @param beanName the name to check
	 */
	public boolean isBeanNameInUse(String beanName) {
		// 判断是否bean名是否是别名或者本地BeanFactory包含beanName的bean对象或者已经为beanName注册了依赖Bean关系
		return isAlias(beanName) || containsLocalBean(beanName) || hasDependentBean(beanName);
	}

	/**
	 * 确定给定Bean在关闭时是否需要销毁
	 *
	 * Determine whether the given bean requires destruction on shutdown.
	 * <p>The default implementation checks the DisposableBean interface as well as
	 * a specified destroy method and registered DestructionAwareBeanPostProcessors.
	 * @param bean the bean instance to check
	 * @param mbd the corresponding bean definition
	 * @see org.springframework.beans.factory.DisposableBean
	 * @see AbstractBeanDefinition#getDestroyMethodName()
	 * @see org.springframework.beans.factory.config.DestructionAwareBeanPostProcessor
	 */
	protected boolean requiresDestruction(Object bean, RootBeanDefinition mbd) {
		// DestructionAwareBeanPostProcessor;该处理器将在关闭时应用于单例Bean
		// 如果bean类不是NullBean&&(如果bean有destroy方法||(该工厂持有一个DestructionAwareBeanPostProcessor)&&
		// Bean有应用于它的可识别销毁的后处理器)) 就为true;否则返回false
		return (bean.getClass() != NullBean.class &&
				(DisposableBeanAdapter.hasDestroyMethod(bean, mbd) || (hasDestructionAwareBeanPostProcessors() &&
						DisposableBeanAdapter.hasApplicableProcessors(bean, getBeanPostProcessors()))));
	}

	/**
	 * 将给定Bean添加到该工厂中的可丢弃Bean列表中，注册器可丢弃Bean接口和/或在工厂关闭时调用给定销毁方法（如果适用）。只适用单例
	 *
	 * Add the given bean to the list of disposable beans in this factory,
	 * registering its DisposableBean interface and/or the given destroy method
	 * to be called on factory shutdown (if applicable). Only applies to singletons.
	 * @param beanName the name of the bean
	 * @param bean the bean instance
	 * @param mbd the bean definition for the bean
	 * @see RootBeanDefinition#isSingleton
	 * @see RootBeanDefinition#getDependsOn
	 * @see #registerDisposableBean
	 * @see #registerDependentBean
	 */
	protected void registerDisposableBeanIfNecessary(String beanName, Object bean, RootBeanDefinition mbd) {
		// 如果有安全管理器器，获取其访问控制上下文
		AccessControlContext acc = (System.getSecurityManager() != null ? getAccessControlContext() : null);
		// 如果mbd不是Prototype作用域 && bean在关闭时需要销毁
		if (!mbd.isPrototype() && requiresDestruction(bean, mbd)) {
			// 如果mbd是单例作用域
			if (mbd.isSingleton()) {
				// Register a DisposableBean implementation that performs all destruction
				// work for the given bean: DestructionAwareBeanPostProcessors,
				// DisposableBean interface, custom destroy method.
				// 注册一个一次性Bean实现来执行给定Bean的销毁工作：DestructionAwareBeanPostProcessors 一次性Bean接口，自定义销毁方法。
				// DisposableBeanAdapter：实际一次性Bean和可运行接口适配器，对给定Bean实例执行各种销毁步骤
				// 构建Bean对应的DisposableBeanAdapter对象，与beanName绑定到 注册中心的一次性Bean列表中
				registerDisposableBean(beanName,
						new DisposableBeanAdapter(bean, beanName, mbd, getBeanPostProcessors(), acc));
			}
			else {
				// A bean with a custom scope...
				// 具有自定已作用域的Bean
				// 获取mdb的作用域
				Scope scope = this.scopes.get(mbd.getScope());
				// 如果作用域为null
				if (scope == null) {
					// 非法状态异常：无作用登记为作用名称'mbd.getScope'
					throw new IllegalStateException("No Scope registered for scope name '" + mbd.getScope() + "'");
				}
				// 注册一个回调，在销毁作用域中将构建Bean对应的DisposableBeanAdapter对象指定(或者在销毁整个作用域时执行，
				// 如果作用域没有销毁单个对象，而是全部终止)
				scope.registerDestructionCallback(beanName,
						new DisposableBeanAdapter(bean, beanName, mbd, getBeanPostProcessors(), acc));
			}
		}
	}


	//---------------------------------------------------------------------
	// Abstract methods to be implemented by subclasses
	//---------------------------------------------------------------------

	/**
	 * 该BeanFactory是否包含beanName的BeanDefinition对象。不考虑工厂可能参与的任何层次结构。未找到缓存的单例实例时，由{@code containsBean}调用
	 *
	 * Check if this bean factory contains a bean definition with the given name.
	 * Does not consider any hierarchy this factory may participate in.
	 * Invoked by {@code containsBean} when no cached singleton instance is found.
	 * <p>Depending on the nature of the concrete bean factory implementation,
	 * this operation might be expensive (for example, because of directory lookups
	 * in external registries). However, for listable bean factories, this usually
	 * just amounts to a local hash lookup: The operation is therefore part of the
	 * public interface there. The same implementation can serve for both this
	 * template method and the public interface method in that case.
	 * @param beanName the name of the bean to look for
	 * @return if this bean factory contains a bean definition with the given name
	 * @see #containsBean
	 * @see org.springframework.beans.factory.ListableBeanFactory#containsBeanDefinition
	 */
	protected abstract boolean containsBeanDefinition(String beanName);

	/**
	 * 返回给定bean名称的bean定义
	 *
	 * Return the bean definition for the given bean name.
	 * Subclasses should normally implement caching, as this method is invoked
	 * by this class every time bean definition metadata is needed.
	 * <p>Depending on the nature of the concrete bean factory implementation,
	 * this operation might be expensive (for example, because of directory lookups
	 * in external registries). However, for listable bean factories, this usually
	 * just amounts to a local hash lookup: The operation is therefore part of the
	 * public interface there. The same implementation can serve for both this
	 * template method and the public interface method in that case.
	 * @param beanName the name of the bean to find a definition for
	 * @return the BeanDefinition for this prototype name (never {@code null})
	 * @throws org.springframework.beans.factory.NoSuchBeanDefinitionException
	 * if the bean definition cannot be resolved
	 * @throws BeansException in case of errors
	 * @see RootBeanDefinition
	 * @see ChildBeanDefinition
	 * @see org.springframework.beans.factory.config.ConfigurableListableBeanFactory#getBeanDefinition
	 */
	protected abstract BeanDefinition getBeanDefinition(String beanName) throws BeansException;

	/**
	 * 为给定的合并后BeanDefinition(和参数)创建一个bean实例
	 *
	 * Create a bean instance for the given merged bean definition (and arguments).
	 * The bean definition will already have been merged with the parent definition
	 * in case of a child definition.
	 * <p>All bean retrieval methods delegate to this method for actual bean creation.
	 * @param beanName the name of the bean
	 * @param mbd the merged bean definition for the bean
	 * @param args explicit arguments to use for constructor or factory method invocation
	 * @return a new instance of the bean
	 * @throws BeanCreationException if the bean could not be created
	 */
	protected abstract Object createBean(String beanName, RootBeanDefinition mbd, @Nullable Object[] args)
			throws BeanCreationException;

}
