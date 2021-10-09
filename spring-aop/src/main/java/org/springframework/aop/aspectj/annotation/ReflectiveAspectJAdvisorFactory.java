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

package org.springframework.aop.aspectj.annotation;

import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.aopalliance.aop.Advice;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.DeclareParents;
import org.aspectj.lang.annotation.Pointcut;

import org.springframework.aop.Advisor;
import org.springframework.aop.MethodBeforeAdvice;
import org.springframework.aop.aspectj.AbstractAspectJAdvice;
import org.springframework.aop.aspectj.AspectJAfterAdvice;
import org.springframework.aop.aspectj.AspectJAfterReturningAdvice;
import org.springframework.aop.aspectj.AspectJAfterThrowingAdvice;
import org.springframework.aop.aspectj.AspectJAroundAdvice;
import org.springframework.aop.aspectj.AspectJExpressionPointcut;
import org.springframework.aop.aspectj.AspectJMethodBeforeAdvice;
import org.springframework.aop.aspectj.DeclareParentsAdvisor;
import org.springframework.aop.framework.AopConfigException;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.converter.ConvertingComparator;
import org.springframework.lang.Nullable;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.util.comparator.InstanceComparator;

/**
 * AbstractAspectJAdvisorFactory的子类。使用AspectJ注解 生成Advisor的具体实现类
 *
 * Factory that can create Spring AOP Advisors given AspectJ classes from
 * classes honoring AspectJ's annotation syntax, using reflection to invoke the
 * corresponding advice methods.
 *
 * @author Rod Johnson
 * @author Adrian Colyer
 * @author Juergen Hoeller
 * @author Ramnivas Laddad
 * @author Phillip Webb
 * @author Sam Brannen
 * @since 2.0
 */
@SuppressWarnings("serial")
public class ReflectiveAspectJAdvisorFactory extends AbstractAspectJAdvisorFactory implements Serializable {

	private static final Comparator<Method> METHOD_COMPARATOR;

	static {
		// Note: although @After is ordered before @AfterReturning and @AfterThrowing,
		// an @After advice method will actually be invoked after @AfterReturning and
		// @AfterThrowing methods due to the fact that AspectJAfterAdvice.invoke(MethodInvocation)
		// invokes proceed() in a `try` block and only invokes the @After advice method
		// in a corresponding `finally` block.
		Comparator<Method> adviceKindComparator = new ConvertingComparator<>(
				new InstanceComparator<>(
						Around.class, Before.class, After.class, AfterReturning.class, AfterThrowing.class),
				(Converter<Method, Annotation>) method -> {
					AspectJAnnotation<?> ann = AbstractAspectJAdvisorFactory.findAspectJAnnotationOnMethod(method);
					return (ann != null ? ann.getAnnotation() : null);
				});
		Comparator<Method> methodNameComparator = new ConvertingComparator<>(Method::getName);
		METHOD_COMPARATOR = adviceKindComparator.thenComparing(methodNameComparator);
	}


	@Nullable
	private final BeanFactory beanFactory;


	/**
	 * Create a new {@code ReflectiveAspectJAdvisorFactory}.
	 */
	public ReflectiveAspectJAdvisorFactory() {
		this(null);
	}

	/**
	 * Create a new {@code ReflectiveAspectJAdvisorFactory}, propagating the given
	 * {@link BeanFactory} to the created {@link AspectJExpressionPointcut} instances,
	 * for bean pointcut handling as well as consistent {@link ClassLoader} resolution.
	 * @param beanFactory the BeanFactory to propagate (may be {@code null}}
	 * @since 4.3.6
	 * @see AspectJExpressionPointcut#setBeanFactory
	 * @see org.springframework.beans.factory.config.ConfigurableBeanFactory#getBeanClassLoader()
	 */
	public ReflectiveAspectJAdvisorFactory(@Nullable BeanFactory beanFactory) {
		this.beanFactory = beanFactory;
	}


	/**
	 * 首先获取切面类型，然后获取除Pointcut注解的所有方法，根据方法注解来创建Advisor通知器
	 * @param aspectInstanceFactory the aspect instance factory
	 * (not the aspect instance itself in order to avoid eager instantiation)
	 * @return
	 */
	@Override
	public List<Advisor> getAdvisors(MetadataAwareAspectInstanceFactory aspectInstanceFactory) {
		// 获取标记为AspectJ的类
		Class<?> aspectClass = aspectInstanceFactory.getAspectMetadata().getAspectClass();
		// 获取标记为AspectJ的name
		String aspectName = aspectInstanceFactory.getAspectMetadata().getAspectName();
		// 对当前切面bean进行校验，主要是判断其切点是否为perflow或者是percflowbelow，Spring暂时不支持
		// 这两种类型的切点
		validate(aspectClass);

		// We need to wrap the MetadataAwareAspectInstanceFactory with a decorator
		// so that it will only instantiate once.
		// 将当前aspectInstanceFactory进行封装，这里LazySingletonAspectInstanceFactoryDecorator
		// 使用装饰器模式，主要是对获取到的切面实例进行了缓存，保证每次获取到的都是同一个切面实例
		MetadataAwareAspectInstanceFactory lazySingletonAspectInstanceFactory =
				new LazySingletonAspectInstanceFactoryDecorator(aspectInstanceFactory);

		List<Advisor> advisors = new ArrayList<>();
		// 这里getAdvisorMethods()会获取所有的没有使用@Pointcut注解标注的方法，然后对其进行遍历
		for (Method method : getAdvisorMethods(aspectClass)) {
			// Prior to Spring Framework 5.2.7, advisors.size() was supplied as the declarationOrderInAspect
			// to getAdvisor(...) to represent the "current position" in the declared methods list.
			// However, since Java 7 the "current position" is not valid since the JDK no longer
			// returns declared methods in the order in which they are declared in the source code.
			// Thus, we now hard code the declarationOrderInAspect to 0 for all advice methods
			// discovered via reflection in order to support reliable advice ordering across JVM launches.
			// Specifically, a value of 0 aligns with the default value used in
			// AspectJPrecedenceComparator.getAspectDeclarationOrder(Advisor).
			// 判断当前方法是否标注有@Before，@After或@Around等注解，如果标注了，则将其封装为一个Advisor
			Advisor advisor = getAdvisor(method, lazySingletonAspectInstanceFactory, 0, aspectName);
			if (advisor != null) {
				advisors.add(advisor);
			}
		}

		// If it's a per target aspect, emit the dummy instantiating aspect.
		// 这里的isLazilyInstantiated()方法判断的是当前bean是否应该被延迟初始化，其主要是判断当前
		// 切面类是否为perthis，pertarget或pertypewithiin等声明的切面。因为这些类型所环绕的目标bean
		// 都是多例的，因而需要在运行时动态判断目标bean是否需要环绕当前的切面逻辑
		if (!advisors.isEmpty() && lazySingletonAspectInstanceFactory.getAspectMetadata().isLazilyInstantiated()) {
			// 如果Advisor不为空，并且是需要延迟初始化的bean，则在第0位位置添加一个同步增强器，
			// 该同步增强器实际上就是一个BeforeAspect的Advisor
			Advisor instantiationAdvisor = new SyntheticInstantiationAdvisor(lazySingletonAspectInstanceFactory);
			advisors.add(0, instantiationAdvisor);
		}

		// Find introduction fields.
		// 判断属性上是否包含有@DeclareParents注解标注的需要新添加的属性，如果有，则将其封装为一个Advisor
		for (Field field : aspectClass.getDeclaredFields()) {
			Advisor advisor = getDeclareParentsAdvisor(field);
			if (advisor != null) {
				advisors.add(advisor);
			}
		}

		return advisors;
	}

	/**
	 * 获取所有不包含Pointcut注解的方法，然后进行排序，根据对应的顺序Around.class, Before.class, After.class, AfterReturning.class, AfterThrowing.class
	 * @param aspectClass
	 * @return
	 */
	private List<Method> getAdvisorMethods(Class<?> aspectClass) {
		final List<Method> methods = new ArrayList<>();
		// ReflectionUtils.MethodCallback的匿名实现
		ReflectionUtils.doWithMethods(aspectClass, method -> {
			// Exclude pointcuts
			// 声明为Pointcut的方法不处理
			if (AnnotationUtils.getAnnotation(method, Pointcut.class) == null) {
				methods.add(method);
			}
		}, ReflectionUtils.USER_DECLARED_METHODS);
		if (methods.size() > 1) {
			methods.sort(METHOD_COMPARATOR);
		}
		return methods;
	}

	/**
	 * Build a {@link org.springframework.aop.aspectj.DeclareParentsAdvisor}
	 * for the given introduction field.
	 * <p>Resulting Advisors will need to be evaluated for targets.
	 * @param introductionField the field to introspect
	 * @return the Advisor instance, or {@code null} if not an Advisor
	 */
	@Nullable
	private Advisor getDeclareParentsAdvisor(Field introductionField) {
		DeclareParents declareParents = introductionField.getAnnotation(DeclareParents.class);
		if (declareParents == null) {
			// Not an introduction field
			return null;
		}

		if (DeclareParents.class == declareParents.defaultImpl()) {
			throw new IllegalStateException("'defaultImpl' attribute must be set on DeclareParents");
		}

		return new DeclareParentsAdvisor(
				introductionField.getType(), declareParents.value(), declareParents.defaultImpl());
	}


	@Override
	@Nullable
	public Advisor getAdvisor(Method candidateAdviceMethod, MetadataAwareAspectInstanceFactory aspectInstanceFactory,
			int declarationOrderInAspect, String aspectName) {
		// 校验当前切面类是否使用了perflow或者percflowbelow标识的切点，Spring暂不支持这两种切点
		validate(aspectInstanceFactory.getAspectMetadata().getAspectClass());

		// 获取当前方法中@Before，@After或者@Around等标注的注解，并且获取该注解的值，将其
		// 封装为一个AspectJExpressionPointcut对象
		AspectJExpressionPointcut expressionPointcut = getPointcut(
				candidateAdviceMethod, aspectInstanceFactory.getAspectMetadata().getAspectClass());
		if (expressionPointcut == null) {
			return null;
		}

		// 将获取到的切点，切点方法等信息封装为一个Advisor对象，也就是说当前Advisor包含有所有
		// 当前切面进行环绕所需要的信息
		return new InstantiationModelAwarePointcutAdvisorImpl(expressionPointcut, candidateAdviceMethod,
				this, aspectInstanceFactory, declarationOrderInAspect, aspectName);
	}

	@Nullable
	private AspectJExpressionPointcut getPointcut(Method candidateAdviceMethod, Class<?> candidateAspectClass) {
		// 获取方法上的注解
		AspectJAnnotation<?> aspectJAnnotation =
				AbstractAspectJAdvisorFactory.findAspectJAnnotationOnMethod(candidateAdviceMethod);
		if (aspectJAnnotation == null) {
			return null;
		}

		// 使用AspectJExpressionPointcut实例封装获取的信息
		AspectJExpressionPointcut ajexp =
				new AspectJExpressionPointcut(candidateAspectClass, new String[0], new Class<?>[0]);
		// 提取得到的注解中的表达式
		ajexp.setExpression(aspectJAnnotation.getPointcutExpression());
		if (this.beanFactory != null) {
			ajexp.setBeanFactory(this.beanFactory);
		}
		return ajexp;
	}


	/**
	 * 最终的获取通知的方法，首先进行了一些验证，然后获得AspectJ的注解，根据注解类型进行创建，然后设置一些参数
	 * @param candidateAdviceMethod the candidate advice method
	 * @param expressionPointcut the AspectJ expression pointcut
	 * @param aspectInstanceFactory the aspect instance factory
	 * @param declarationOrder the declaration order within the aspect
	 * @param aspectName the name of the aspect
	 * @return
	 */
	@Override
	@Nullable
	public Advice getAdvice(Method candidateAdviceMethod, AspectJExpressionPointcut expressionPointcut,
			MetadataAwareAspectInstanceFactory aspectInstanceFactory, int declarationOrder, String aspectName) {

		// 获取我们的切面类的class对象
		Class<?> candidateAspectClass = aspectInstanceFactory.getAspectMetadata().getAspectClass();
		validate(candidateAspectClass);

		// 获取切面方法上的注解
		AspectJAnnotation<?> aspectJAnnotation =
				AbstractAspectJAdvisorFactory.findAspectJAnnotationOnMethod(candidateAdviceMethod);
		// 解析出来的注解信息是否为null
		if (aspectJAnnotation == null) {
			return null;
		}

		// If we get here, we know we have an AspectJ method.
		// Check that it's an AspectJ-annotated class
		// 再次判断是否是切面对象
		if (!isAspect(candidateAspectClass)) {
			throw new AopConfigException("Advice must be declared inside an aspect type: " +
					"Offending method '" + candidateAdviceMethod + "' in class [" +
					candidateAspectClass.getName() + "]");
		}

		if (logger.isDebugEnabled()) {
			logger.debug("Found AspectJ method: " + candidateAdviceMethod);
		}

		AbstractAspectJAdvice springAdvice;

		// 判断标注在方法上的注解类型
		switch (aspectJAnnotation.getAnnotationType()) {
			// 是PointCut注解,那么就抛出异常,因为在外面传递进来的方法已经排除了Pointcut的方法
			case AtPointcut:
				if (logger.isDebugEnabled()) {
					logger.debug("Processing pointcut '" + candidateAdviceMethod.getName() + "'");
				}
				return null;
			// 环绕通知,构建AspectJAroundAdvice
			case AtAround:
				springAdvice = new AspectJAroundAdvice(
						candidateAdviceMethod, expressionPointcut, aspectInstanceFactory);
				break;
			// 前置通知,构建AspectJMethodBeforeAdvice
			case AtBefore:
				springAdvice = new AspectJMethodBeforeAdvice(
						candidateAdviceMethod, expressionPointcut, aspectInstanceFactory);
				break;
			// 后置通知,AspectJAfterAdvice
			case AtAfter:
				springAdvice = new AspectJAfterAdvice(
						candidateAdviceMethod, expressionPointcut, aspectInstanceFactory);
				break;
			// 返回通知,AspectJAfterReturningAdvice
			case AtAfterReturning:
				springAdvice = new AspectJAfterReturningAdvice(
						candidateAdviceMethod, expressionPointcut, aspectInstanceFactory);
				AfterReturning afterReturningAnnotation = (AfterReturning) aspectJAnnotation.getAnnotation();
				if (StringUtils.hasText(afterReturningAnnotation.returning())) {
					springAdvice.setReturningName(afterReturningAnnotation.returning());
				}
				break;
			// 异常通知,AspectJAfterThrowingAdvice
			case AtAfterThrowing:
				springAdvice = new AspectJAfterThrowingAdvice(
						candidateAdviceMethod, expressionPointcut, aspectInstanceFactory);
				AfterThrowing afterThrowingAnnotation = (AfterThrowing) aspectJAnnotation.getAnnotation();
				if (StringUtils.hasText(afterThrowingAnnotation.throwing())) {
					springAdvice.setThrowingName(afterThrowingAnnotation.throwing());
				}
				break;
			default:
				throw new UnsupportedOperationException(
						"Unsupported advice type on method: " + candidateAdviceMethod);
		}

		// Now to configure the advice...
		// 设置我们构建出来的通知对象的相关属性比如DeclarationOrder，在代理调用的时候，责任链顺序上会用到
		springAdvice.setAspectName(aspectName);
		springAdvice.setDeclarationOrder(declarationOrder);
		String[] argNames = this.parameterNameDiscoverer.getParameterNames(candidateAdviceMethod);
		if (argNames != null) {
			springAdvice.setArgumentNamesFromStringArray(argNames);
		}
		springAdvice.calculateArgumentBindings();

		return springAdvice;
	}


	/**
	 * Synthetic advisor that instantiates the aspect.
	 * Triggered by per-clause pointcut on non-singleton aspect.
	 * The advice has no effect.
	 */
	@SuppressWarnings("serial")
	protected static class SyntheticInstantiationAdvisor extends DefaultPointcutAdvisor {

		public SyntheticInstantiationAdvisor(final MetadataAwareAspectInstanceFactory aif) {
			// 目标方法前调用，并且简单初始化aspect
			super(aif.getAspectMetadata().getPerClausePointcut(), (MethodBeforeAdvice)
					(method, args, target) -> aif.getAspectInstance());
		}
	}

}
