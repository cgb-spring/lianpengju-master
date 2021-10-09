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

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeansException;
import org.springframework.beans.TypeConverter;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.config.DependencyDescriptor;
import org.springframework.beans.factory.config.NamedBeanHolder;
import org.springframework.beans.factory.config.RuntimeBeanNameReference;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.config.TypedStringValue;
import org.springframework.lang.Nullable;
import org.springframework.util.ClassUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

/**
 * 在bean工厂实现中使用Helper类，它将beanDefinition对象中包含的值解析为应用于目标bean实例的实际值
 *
 * Helper class for use in bean factory implementations,
 * resolving values contained in bean definition objects
 * into the actual values applied to the target bean instance.
 *
 * <p>Operates on an {@link AbstractBeanFactory} and a plain
 * {@link org.springframework.beans.factory.config.BeanDefinition} object.
 * Used by {@link AbstractAutowireCapableBeanFactory}.
 *
 * @author Juergen Hoeller
 * @since 1.2
 * @see AbstractAutowireCapableBeanFactory
 */
class BeanDefinitionValueResolver {

	// 当前bean工厂
	private final AbstractAutowireCapableBeanFactory beanFactory;

	// 要使用的bean名称
	private final String beanName;

	// beanName对应的BeanDefinition
	private final BeanDefinition beanDefinition;

	// 用于解析TypeStringValues的TypeConverter
	private final TypeConverter typeConverter;


	/**
	 * 为给定BeanFactory和BeanDefinition创建一个BeanDefinitionValueResolver实例
	 *
	 * Create a BeanDefinitionValueResolver for the given BeanFactory and BeanDefinition.
	 * @param beanFactory the BeanFactory to resolve against
	 * @param beanName the name of the bean that we work on
	 * @param beanDefinition the BeanDefinition of the bean that we work on
	 * @param typeConverter the TypeConverter to use for resolving TypedStringValues
	 */
	public BeanDefinitionValueResolver(AbstractAutowireCapableBeanFactory beanFactory, String beanName,
			BeanDefinition beanDefinition, TypeConverter typeConverter) {

		this.beanFactory = beanFactory;
		this.beanName = beanName;
		this.beanDefinition = beanDefinition;
		this.typeConverter = typeConverter;
	}


	/**
	 * Given a PropertyValue, return a value, resolving any references to other
	 * beans in the factory if necessary. The value could be:
	 * <li>A BeanDefinition, which leads to the creation of a corresponding
	 * new bean instance. Singleton flags and names of such "inner beans"
	 * are always ignored: Inner beans are anonymous prototypes.
	 * <li>A RuntimeBeanReference, which must be resolved.
	 * <li>A ManagedList. This is a special collection that may contain
	 * RuntimeBeanReferences or Collections that will need to be resolved.
	 * <li>A ManagedSet. May also contain RuntimeBeanReferences or
	 * Collections that will need to be resolved.
	 * <li>A ManagedMap. In this case the value may be a RuntimeBeanReference
	 * or Collection that will need to be resolved.
	 * <li>An ordinary object or {@code null}, in which case it's left alone.
	 * @param argName the name of the argument that the value is defined for
	 * @param value the value object to resolve
	 * @return the resolved object
	 */
	@Nullable
	public Object resolveValueIfNecessary(Object argName, @Nullable Object value) {
		// We must check each value to see whether it requires a runtime reference
		// to another bean to be resolved.
		// 我们必需检查每个值，以查看它是否需要对另一个bean的运行时引用才能解决
		// RuntimeBeanReference:当属性值对象是工厂中另一个bean的引用时，使用不可变的占位符类，在运行时进行解析

		// 如果values是RuntimeBeanReference实例
		if (value instanceof RuntimeBeanReference) {
			//将value强转成RuntimeBeanReference对象
			RuntimeBeanReference ref = (RuntimeBeanReference) value;
			//解析出对应ref所封装的Bean元信息(即Bean名,Bean类型)的Bean对象:
			return resolveReference(argName, ref);
		}
		//RuntimeBeanNameReference对应于<idref bean="bea" />.
		// idref注入的是目标bean的id而不是目标bean的实例，同时使用idref容器在部署的时候还会验证这个名称的bean
		// 是否真实存在。其实idref就跟value一样，只是将某个字符串注入到属性或者构造函数中，只不过注入的是某个
		// Bean定义的id属性值:
		// 即: <idref bean="bea" /> 等同于 <value>bea</value>
		// 如果values是RuntimeBeanReference实例
		else if (value instanceof RuntimeBeanNameReference) {
			//从value中获取引用的Bean名
			String refName = ((RuntimeBeanNameReference) value).getBeanName();
			//对refName进行解析，然后重新赋值给refName
			refName = String.valueOf(doEvaluate(refName));
			//如果该bean工厂不包含具有refName的beanDefinintion或外部注册的singleton实例
			if (!this.beanFactory.containsBean(refName)) {
				//抛出BeanDefintion存储异常：argName的Bean引用中的Bean名'refName'无效
				throw new BeanDefinitionStoreException(
						"Invalid bean name '" + refName + "' in bean reference for " + argName);
			}
			//返回经过解析且经过检查其是否存在于Bean工厂的引用Bean名【refName】
			return refName;
		}
		//BeanDefinitionHolder:具有名称和别名的bean定义的持有者，可以注册为内部bean的占位符
		//如果value是BeanDefinitionHolder实例
		else if (value instanceof BeanDefinitionHolder) {
			// Resolve BeanDefinitionHolder: contains BeanDefinition with name and aliases.
			// 解决BeanDefinitionHolder：包含具有名称和别名的BenDefinition
			// 将value强转为BeanDefinitionHolder对象
			BeanDefinitionHolder bdHolder = (BeanDefinitionHolder) value;
			//根据bdHolder所封装的Bean名和BeanDefinition对象解析出内部Bean对象
			return resolveInnerBean(argName, bdHolder.getBeanName(), bdHolder.getBeanDefinition());
		}
		//一般在内部匿名bean的配置才会出现BeanDefinition
		//如果value是BeanDefinition实例
		else if (value instanceof BeanDefinition) {
			// Resolve plain BeanDefinition, without contained name: use dummy name.
			// 解析纯BeanDefinition,不包含名称：使用虚拟名称
			// 将value强转为BeanDefinition对象
			BeanDefinition bd = (BeanDefinition) value;
			//拼装内部Bean名:"(inner bean)#"+bd的身份哈希码的十六进制字符串形式
			String innerBeanName = "(inner bean)" + BeanFactoryUtils.GENERATED_BEAN_NAME_SEPARATOR +
					ObjectUtils.getIdentityHexString(bd);
			//根据innerBeanName和bd解析出内部Bean对象
			return resolveInnerBean(argName, innerBeanName, bd);
		}
		//如果values是DependencyDesciprtor实例
		else if (value instanceof DependencyDescriptor) {
			//定义一个用于存放所找到的所有候选Bean名的集合，初始化长度为4
			Set<String> autowiredBeanNames = new LinkedHashSet<>(4);
			//根据descriptor的依赖类型解析出与descriptor所包装的对象匹配的候选Bean对象
			Object result = this.beanFactory.resolveDependency(
					(DependencyDescriptor) value, this.beanName, autowiredBeanNames, this.typeConverter);
			//遍历autowiredBeanNames
			for (String autowiredBeanName : autowiredBeanNames) {
				//如果该bean工厂包含具有autowiredBeanName的beanDefinition或外部注册的singleton实例：
				if (this.beanFactory.containsBean(autowiredBeanName)) {
					//注册autowiredBeanName与beanName的依赖关系
					this.beanFactory.registerDependentBean(autowiredBeanName, this.beanName);
				}
			}
			//返回与descriptor所包装的对象匹配的候选Bean对象【result】
			return result;
		}
		//如果value是ManagedArray实例
		else if (value instanceof ManagedArray) {
			// May need to resolve contained runtime references.
			// 可能需要解析包含的运行时引用，将value强转为ManagedArray对象
			ManagedArray array = (ManagedArray) value;
			//获取array的已解析元素类型
			Class<?> elementType = array.resolvedElementType;
			//如果elementType为null
			if (elementType == null) {
				//获取arry的元素类型名，指array标签的value-type属性
				String elementTypeName = array.getElementTypeName();
				//如果elementTypeName不是空字符串
				if (StringUtils.hasText(elementTypeName)) {
					try {
						//使用Bean工厂的Bean类型加载器加载elementTypeName对应的Class对象。
						elementType = ClassUtils.forName(elementTypeName, this.beanFactory.getBeanClassLoader());
						//让array#resolvedElementType属性引用elementType
						array.resolvedElementType = elementType;
					}
					//捕捉加载elementTypeName对应的Class对象的所有异常
					catch (Throwable ex) {
						// Improve the message by showing the context.
						throw new BeanCreationException(
								this.beanDefinition.getResourceDescription(), this.beanName,
								"Error resolving array type for " + argName, ex);
					}
				}
				else {
					//让elementType默认使用Object类对象
					elementType = Object.class;
				}
			}
			//解析ManagedArray对象，以得到解析后的数组对象
			return resolveManagedArray(argName, (List<?>) value, elementType);
		}
		// 对managedList进行解析
		else if (value instanceof ManagedList) {
			// May need to resolve contained runtime references.
			// 可能需要解析包含的运行时引用，解析ManagedList对象，以得到解析后的List对象并结果返回出去
			return resolveManagedList(argName, (List<?>) value);
		}
		// 对managedSet进行解析
		else if (value instanceof ManagedSet) {
			// May need to resolve contained runtime references.
			// 可能需要解析包含的运行时引用，解析ManagedSet对象，以得到解析后的Set对象并结果返回出去
			return resolveManagedSet(argName, (Set<?>) value);
		}
		// 对managedMap进行解析
		else if (value instanceof ManagedMap) {
			// May need to resolve contained runtime references.
			//可能需要解析包含的运行时引用，解析ManagedMap对象，以得到解析后的Map对象并结果返回出去
			return resolveManagedMap(argName, (Map<?, ?>) value);
		}
		// 对managedProperties进行解析
		else if (value instanceof ManagedProperties) {
			//将value强转为Properties对象
			Properties original = (Properties) value;
			//定义一个用于存储将original的所有Property的键/值解析后的键/值的Properties对象
			Properties copy = new Properties();
			//遍历original，键名为propKey,值为propValue
			original.forEach((propKey, propValue) -> {
				//如果proKey是TypeStringValue实例
				if (propKey instanceof TypedStringValue) {
					//在propKey封装的value可解析成表达式的情况下,将propKey封装的value评估为表达式并解析出表达式的值
					propKey = evaluate((TypedStringValue) propKey);
				}
				//如果proValue式TypeStringValue实例
				if (propValue instanceof TypedStringValue) {
					//在propValue封装的value可解析成表达式的情况下,将propValue封装的value评估为表达式并解析出表达式的值
					propValue = evaluate((TypedStringValue) propValue);
				}
				//如果proKey或者propValue为null
				if (propKey == null || propValue == null) {
					//抛出Bean创建异常:转换argName的属性键/值时出错：解析为null
					throw new BeanCreationException(
							this.beanDefinition.getResourceDescription(), this.beanName,
							"Error converting Properties key/value pair for " + argName + ": resolved to null");
				}
				//将propKey和propValue添加到copy中
				copy.put(propKey, propValue);
			});
			return copy;
		}
		// 对TypedStringValue进行解析
		else if (value instanceof TypedStringValue) {
			// Convert value to target type here.
			// 在此处将value转换为目标类型，将value强转为TypedStringValue对象
			TypedStringValue typedStringValue = (TypedStringValue) value;
			//在typedStringValue封装的value可解析成表达式的情况下,将typedStringValue封装的value评估为表达式并解析出表达式的值
			Object valueObject = evaluate(typedStringValue);
			try {
				//在typedStringValue中解析目标类型
				Class<?> resolvedTargetType = resolveTargetType(typedStringValue);
				//如果resolvedTargetType不为null
				if (resolvedTargetType != null) {
					//使用typeConverter将值转换为所需的类型
					return this.typeConverter.convertIfNecessary(valueObject, resolvedTargetType);
				}
				else {
					//返回并解析出来表达式的值
					return valueObject;
				}
			}
			//捕捉在解析目标类型或转换类型过程中抛出的异常
			catch (Throwable ex) {
				// Improve the message by showing the context.
				throw new BeanCreationException(
						this.beanDefinition.getResourceDescription(), this.beanName,
						"Error converting typed String value for " + argName, ex);
			}
		}
		//如果value时NullBean实例
		else if (value instanceof NullBean) {
			//直接返回null
			return null;
		}
		else {
			//对于value是String/String[]类型会尝试评估为表达式并解析出表达式的值，其他类型直接返回value.
			return evaluate(value);
		}
	}

	/**
	 * 在value封装的value可解析成表达式的情况下,将value封装的value评估为表达式并解析出表达式的值
	 *
	 * Evaluate the given value as an expression, if necessary.
	 * @param value the candidate value (may be an expression)
	 * @return the resolved value
	 */
	@Nullable
	protected Object evaluate(TypedStringValue value) {
		//如有必要(value可解析成表达式的情况下)，将value封装的value评估为表达式并解析出表达式的值
		Object result = doEvaluate(value.getValue());
		//如果result与value所封装的value不相等
		if (!ObjectUtils.nullSafeEquals(result, value.getValue())) {
			//将value标记为动态，即包含一个表达式，因此不进行缓存
			value.setDynamic();
		}
		//返回result
		return result;
	}

	/**
	 * 对于value是String/String[]类型会尝试评估为表达式并解析出表达式的值，其他类型直接返回value
	 *
	 * Evaluate the given value as an expression, if necessary.
	 * @param value the original value (may be an expression)
	 * @return the resolved value if necessary, or the original value
	 */
	@Nullable
	protected Object evaluate(@Nullable Object value) {
		//如果value是String对象
		if (value instanceof String) {
			//如有必要(value可解析成表达式的情况下)，将value评估为表达式并解析出表达式的值并返回出去
			return doEvaluate((String) value);
		}
		//如果value是String数组对象
		else if (value instanceof String[]) {
			//将value强转为String数组
			String[] values = (String[]) value;
			//是否经过解析的标记，默认为false
			boolean actuallyResolved = false;
			//定义用于存放解析的值的Object数组，长度为values的长度
			Object[] resolvedValues = new Object[values.length];
			//遍历values(以fori形式)
			for (int i = 0; i < values.length; i++) {
				//获取第i个values元素
				String originalValue = values[i];
				//如有必要(value可解析成表达式的情况下)，将originalValue评估为表达式并解析出表达式的值
				Object resolvedValue = doEvaluate(originalValue);
				//如果resolvedValue与orgininalValue不是同一个对象
				if (resolvedValue != originalValue) {
					//经过解析标记为true，表示已经过解析
					actuallyResolved = true;
				}
				//将resolvedValue赋值第i个resolvedValues元素中
				resolvedValues[i] = resolvedValue;
			}
			//如果已经过解析，返回解析后的数组【resovledValues】；否则返回values
			return (actuallyResolved ? resolvedValues : values);
		}
		else {
			//其他类型直接返回value
			return value;
		}
	}

	/**
	 * 如有必要(value可解析成表达式的情况下)，将给定的String值评估为表达式并解析出表达式的值
	 *
	 * Evaluate the given String value as an expression, if necessary.
	 * @param value the original value (may be an expression)
	 * @return the resolved value if necessary, or the original String value
	 */
	@Nullable
	private Object doEvaluate(@Nullable String value) {
		//评估value,如果value是可解析表达式，会对其进行解析，否则直接返回value
		return this.beanFactory.evaluateBeanDefinitionString(value, this.beanDefinition);
	}

	/**
	 * 在给定的TypedStringValue中解析目标类型
	 *
	 * Resolve the target type in the given TypedStringValue.
	 * @param value the TypedStringValue to resolve
	 * @return the resolved target type (or {@code null} if none specified)
	 * @throws ClassNotFoundException if the specified type cannot be resolved
	 * @see TypedStringValue#resolveTargetType
	 */
	@Nullable
	protected Class<?> resolveTargetType(TypedStringValue value) throws ClassNotFoundException {
		//如果value有携带目标类型
		if (value.hasTargetType()) {
			//返回value的目标类型
			return value.getTargetType();
		}
		//从value中解析出目标类型
		return value.resolveTargetType(this.beanFactory.getBeanClassLoader());
	}

	/**
	 * 解析出对应ref所封装的Bean元信息(即Bean名,Bean类型)的Bean对象，在工厂中解决对另一个bean的引用
	 *
	 * Resolve a reference to another bean in the factory.
	 */
	@Nullable
	private Object resolveReference(Object argName, RuntimeBeanReference ref) {
		try {
			//定义用于一个存储bean对象的变量
			Object bean;
			//获取另一个Bean引用的Bean类型
			Class<?> beanType = ref.getBeanType();
			//如果引用来自父工厂
			if (ref.isToParent()) {
				//获取父工厂
				BeanFactory parent = this.beanFactory.getParentBeanFactory();
				//如果没有父工厂
				if (parent == null) {
					//抛出Bean创建异常：无法解析对bean的引用 ref 在父工厂中:没有可以的父工厂
					throw new BeanCreationException(
							this.beanDefinition.getResourceDescription(), this.beanName,
							"Cannot resolve reference to bean " + ref +
									" in parent factory: no parent factory available");
				}
				//如果引用的Bean类型不为null
				if (beanType != null) {
					//从父工厂中获取引用的Bean类型对应的Bean对象
					bean = parent.getBean(beanType);
				}
				else {
					//否则,使用引用的Bean名,从父工厂中获取对应的Bean对像
					bean = parent.getBean(String.valueOf(doEvaluate(ref.getBeanName())));
				}
			}
			else {
				//定义一个用于存储解析出来的Bean名的变量
				String resolvedName;
				//如果beanType不为null
				if (beanType != null) {
					//解析与beanType唯一匹配的bean实例，包括其bean名
					NamedBeanHolder<?> namedBean = this.beanFactory.resolveNamedBean(beanType);
					//让bean引用nameBean所封装的Bean对象
					bean = namedBean.getBeanInstance();
					//让resolvedName引用nameBean所封装的Bean名
					resolvedName = namedBean.getBeanName();
				}
				else {
					//让resolvedName引用ref所包装的Bean名
					resolvedName = String.valueOf(doEvaluate(ref.getBeanName()));
					//获取resolvedName的Bean对象
					bean = this.beanFactory.getBean(resolvedName);
				}
				//注册beanName与dependentBeanNamed的依赖关系到Bean工厂
				this.beanFactory.registerDependentBean(resolvedName, this.beanName);
			}
			//如果Bean对象是NullBean实例
			if (bean instanceof NullBean) {
				//将bean置为null
				bean = null;
			}
			//返回解析出来对应ref所封装的Bean元信息(即Bean名,Bean类型)的Bean对象
			return bean;
		}
		//捕捉Bean包和子包中引发的所有异常
		catch (BeansException ex) {
			throw new BeanCreationException(
					this.beanDefinition.getResourceDescription(), this.beanName,
					"Cannot resolve reference to bean '" + ref.getBeanName() + "' while setting " + argName, ex);
		}
	}

	/**
	 * 解析出内部Bean对象
	 *
	 * Resolve an inner bean definition.
	 * @param argName the name of the argument that the inner bean is defined for
	 * @param innerBeanName the name of the inner bean
	 * @param innerBd the bean definition for the inner bean
	 * @return the resolved inner bean instance
	 */
	@Nullable
	private Object resolveInnerBean(Object argName, String innerBeanName, BeanDefinition innerBd) {
		//定义一个用于保存innerBd与beanDefinition合并后的BeanDefinition对象的变量
		RootBeanDefinition mbd = null;
		try {
			//获取innerBd与beanDefinition合并后的BeanDefinition对象
			mbd = this.beanFactory.getMergedBeanDefinition(innerBeanName, innerBd, this.beanDefinition);
			// Check given bean name whether it is unique. If not already unique,
			// add counter - increasing the counter until the name is unique.
			// 检查给定的Bean名是否唯一。如果还不是唯一的,添加计数器-增加计数器,直到名称唯一为止.
			// 解决内部Bean名需要唯一的问题
			// 定义实际的内部Bean名,初始为innerBeanName
			String actualInnerBeanName = innerBeanName;
			//如果mbd配置成了单例
			if (mbd.isSingleton()) {
				//调整innerBeanName,直到该Bean名在工厂中唯一。最后将结果赋值给actualInnerBeanName
				actualInnerBeanName = adaptInnerBeanName(innerBeanName);
			}
			//将actualInnerBeanName和beanName的包含关系注册到该工厂中
			this.beanFactory.registerContainedBean(actualInnerBeanName, this.beanName);
			// Guarantee initialization of beans that the inner bean depends on.
			// 确保内部Bean依赖的Bean的初始化，获取mdb的要依赖的Bean名
			String[] dependsOn = mbd.getDependsOn();
			//如果有需要依赖的Bean名
			if (dependsOn != null) {
				//遍历depensOn
				for (String dependsOnBean : dependsOn) {
					//注册dependsOnBean与actualInnerBeanName的依赖关系到该工厂中
					this.beanFactory.registerDependentBean(dependsOnBean, actualInnerBeanName);
					//获取dependsOnBean的Bean对像(不引用，只是为了让dependsOnBean所对应的Bean对象实例化)
					this.beanFactory.getBean(dependsOnBean);
				}
			}
			// Actually create the inner bean instance now...
			// 实际上现有创建内部bean实例，创建actualInnerBeanName的Bean对象
			Object innerBean = this.beanFactory.createBean(actualInnerBeanName, mbd, null);
			//如果innerBean时FactoryBean的实例
			if (innerBean instanceof FactoryBean) {
				//mbds是否是"synthetic"的标记。一般是指只有AOP相关的prointCut配置或者Advice配置才会将 synthetic设置为true
				boolean synthetic = mbd.isSynthetic();
				//从BeanFactory对象中获取管理的对象，只有mbd不是synthetic才对其对象进行该工厂的后置处理
				innerBean = this.beanFactory.getObjectFromFactoryBean(
						(FactoryBean<?>) innerBean, actualInnerBeanName, !synthetic);
			}
			//如果innerBean是NullBean实例
			if (innerBean instanceof NullBean) {
				//将innerBean设置为null
				innerBean = null;
			}
			//返回actualInnerBeanName的Bean对象【innerBean】
			return innerBean;
		}
		//捕捉解析内部Bean对象过程中抛出的Bean包和子包中引发的所有异常
		catch (BeansException ex) {
			throw new BeanCreationException(
					this.beanDefinition.getResourceDescription(), this.beanName,
					"Cannot create inner bean '" + innerBeanName + "' " +
					(mbd != null && mbd.getBeanClassName() != null ? "of type [" + mbd.getBeanClassName() + "] " : "") +
					"while setting " + argName, ex);
		}
	}

	/**
	 * 检查给定Bean名是否唯一.如果还不是唯一的,则添加该计数器,直到名称唯一位置
	 *
	 * Checks the given bean name whether it is unique. If not already unique,
	 * a counter is added, increasing the counter until the name is unique.
	 * @param innerBeanName the original name for the inner bean
	 * @return the adapted name for the inner bean
	 */
	private String adaptInnerBeanName(String innerBeanName) {
		//定义一个实际内部Bean名变量，初始为innerBean名
		String actualInnerBeanName = innerBeanName;
		//定义一个用于计数的计数器，初始为0
		int counter = 0;
		// 获取前缀
		String prefix = innerBeanName + BeanFactoryUtils.GENERATED_BEAN_NAME_SEPARATOR;
		//只要actualInnerBeanName是否已在该工厂中使用就继续循环,即actualInnerBeanName是否是别名
		// 或该工厂是否已包含actualInnerBeanName的bean对象 或 该工厂是否已经为actualInnerBeanName注册了依赖Bean关系
		while (this.beanFactory.isBeanNameInUse(actualInnerBeanName)) {
			//计数器+1
			counter++;
			//让actualInnerBeanName重新引用拼接后的字符串:innerBeanName+'#'+count
			actualInnerBeanName = prefix + counter;
		}
		//返回经过调整后的Bean名
		return actualInnerBeanName;
	}

	/**
	 * 解析ManagedArray对象，以得到解析后的数组对象
	 * For each element in the managed array, resolve reference if necessary.
	 */
	private Object resolveManagedArray(Object argName, List<?> ml, Class<?> elementType) {
		//创建一个用于存放解析后的实例对象的elementType类型长度为ml大小的数组
		Object resolved = Array.newInstance(elementType, ml.size());
		//遍历ml(以fori形式)
		for (int i = 0; i < ml.size(); i++) {
			//获取第i个ml元素对象，解析出该元素对象的实例对象然后设置到第i个resolved元素中
			Array.set(resolved, i, resolveValueIfNecessary(new KeyedArgName(argName, i), ml.get(i)));
		}
		//返回解析后的的数组对象【resolved】
		return resolved;
	}

	/**
	 * 解析ManagedList对象，以得到解析后的List对象
	 * For each element in the managed list, resolve reference if necessary.
	 */
	private List<?> resolveManagedList(Object argName, List<?> ml) {
		//定义一个用于存放解析后的实例对象的ArrayList，初始容量为ml大小
		List<Object> resolved = new ArrayList<>(ml.size());
		//遍历ml(以fori形式)
		for (int i = 0; i < ml.size(); i++) {
			//获取第i个ml元素对象，解析出该元素对象的实例对象然后添加到resolved中
			resolved.add(resolveValueIfNecessary(new KeyedArgName(argName, i), ml.get(i)));
		}
		//返回【resolved】
		return resolved;
	}

	/**
	 * 解析ManagedSet对象，以得到解析后的Set对象
	 * For each element in the managed set, resolve reference if necessary.
	 */
	private Set<?> resolveManagedSet(Object argName, Set<?> ms) {
		//定义一个用于存放解析后的实例对象的LinkedHashSet，初始容量为ml大小
		Set<Object> resolved = new LinkedHashSet<>(ms.size());
		//定义一个遍历时的偏移量i
		int i = 0;
		//遍历ms，元素为m
		for (Object m : ms) {
			//解析出该m的实例对象然后添加到resolved中
			resolved.add(resolveValueIfNecessary(new KeyedArgName(argName, i), m));
			//偏移量+1
			i++;
		}
		//返回resolved
		return resolved;
	}

	/**
	 * 解析ManagedMap对象，以得到解析后的Map对象
	 * For each element in the managed map, resolve reference if necessary.
	 */
	private Map<?, ?> resolveManagedMap(Object argName, Map<?, ?> mm) {
		//定义用于存储解析后的key实例对象和value实例对象的LinkedHashMap,长度为mm的大小
		Map<Object, Object> resolved = new LinkedHashMap<>(mm.size());
		//遍历mm
		mm.forEach((key, value) -> {
			//解析mm的key的实例对象
			Object resolvedKey = resolveValueIfNecessary(argName, key);
			//解析mm的value的实例对象
			Object resolvedValue = resolveValueIfNecessary(new KeyedArgName(argName, key), value);
			//将解析出来的key和value的实例对象添加到resolved中
			resolved.put(resolvedKey, resolvedValue);
		});
		//返回resolved
		return resolved;
	}


	/**
	 * 用于延迟toString构建的Holder类
	 *
	 * Holder class used for delayed toString building.
	 */
	private static class KeyedArgName {

		// 参数名
		private final Object argName;

		// 键值
		private final Object key;

		// 新建一个KeyedArgName对象
		public KeyedArgName(Object argName, Object key) {
			this.argName = argName;
			this.key = key;
		}

		@Override
		public String toString() {
			return this.argName + " with key " + BeanWrapper.PROPERTY_KEY_PREFIX +
					this.key + BeanWrapper.PROPERTY_KEY_SUFFIX;
		}
	}

}
