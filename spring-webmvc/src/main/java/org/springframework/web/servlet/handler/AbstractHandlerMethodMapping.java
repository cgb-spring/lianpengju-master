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

package org.springframework.web.servlet.handler;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;

import kotlin.reflect.KFunction;
import kotlin.reflect.jvm.ReflectJvmMapping;

import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.KotlinDetector;
import org.springframework.core.MethodIntrospector;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;

/**
 * 抽象的基础类通过map的映射关系来存储request和handlerMethod之间的关系
 * Abstract base class for {@link HandlerMapping} implementations that define
 * a mapping between a request and a {@link HandlerMethod}.
 *
 * <p>For each registered handler method, a unique mapping is maintained with
 * subclasses defining the details of the mapping type {@code <T>}.
 *
 * @author Arjen Poutsma
 * @author Rossen Stoyanchev
 * @author Juergen Hoeller
 * @author Sam Brannen
 * @since 3.1
 * @param <T> the mapping for a {@link HandlerMethod} containing the conditions
 * needed to match the handler method to an incoming request.
 * 此泛型类型用来代表匹配handler的条件专门使用的一种类，这里的条件不只是url，还可以是其他条件，如请求方式，请求参数等都可以作为条件，默认使用的RequestMappingInfo
 *
 */
public abstract class AbstractHandlerMethodMapping<T> extends AbstractHandlerMapping implements InitializingBean {

	/**
	 * Bean name prefix for target beans behind scoped proxies. Used to exclude those
	 * targets from handler method detection, in favor of the corresponding proxies.
	 * <p>We're not checking the autowire-candidate status here, which is how the
	 * proxy target filtering problem is being handled at the autowiring level,
	 * since autowire-candidate may have been turned to {@code false} for other
	 * reasons, while still expecting the bean to be eligible for handler methods.
	 * <p>Originally defined in {@link org.springframework.aop.scope.ScopedProxyUtils}
	 * but duplicated here to avoid a hard dependency on the spring-aop module.
	 */
	private static final String SCOPED_TARGET_NAME_PREFIX = "scopedTarget.";

	private static final HandlerMethod PREFLIGHT_AMBIGUOUS_MATCH =
			new HandlerMethod(new EmptyHandler(), ClassUtils.getMethod(EmptyHandler.class, "handle"));

	private static final CorsConfiguration ALLOW_CORS_CONFIG = new CorsConfiguration();

	static {
		ALLOW_CORS_CONFIG.addAllowedOrigin("*");
		ALLOW_CORS_CONFIG.addAllowedMethod("*");
		ALLOW_CORS_CONFIG.addAllowedHeader("*");
		ALLOW_CORS_CONFIG.setAllowCredentials(true);
	}

	/**
	 * 是否只扫描可访问的 HandlerMethod 们
	 */
	private boolean detectHandlerMethodsInAncestorContexts = false;

	/**
	 * Mapping 命名策略
	 */
	@Nullable
	private HandlerMethodMappingNamingStrategy<T> namingStrategy;

	/**
	 * Mapping 注册表
	 */
	private final MappingRegistry mappingRegistry = new MappingRegistry();


	/**
	 * Whether to detect handler methods in beans in ancestor ApplicationContexts.
	 * <p>Default is "false": Only beans in the current ApplicationContext are
	 * considered, i.e. only in the context that this HandlerMapping itself
	 * is defined in (typically the current DispatcherServlet's context).
	 * <p>Switch this flag on to detect handler beans in ancestor contexts
	 * (typically the Spring root WebApplicationContext) as well.
	 * @see #getCandidateBeanNames()
	 */
	public void setDetectHandlerMethodsInAncestorContexts(boolean detectHandlerMethodsInAncestorContexts) {
		this.detectHandlerMethodsInAncestorContexts = detectHandlerMethodsInAncestorContexts;
	}

	/**
	 * Configure the naming strategy to use for assigning a default name to every
	 * mapped handler method.
	 * <p>The default naming strategy is based on the capital letters of the
	 * class name followed by "#" and then the method name, e.g. "TC#getFoo"
	 * for a class named TestController with method getFoo.
	 */
	public void setHandlerMethodMappingNamingStrategy(HandlerMethodMappingNamingStrategy<T> namingStrategy) {
		this.namingStrategy = namingStrategy;
	}

	/**
	 * Return the configured naming strategy or {@code null}.
	 */
	@Nullable
	public HandlerMethodMappingNamingStrategy<T> getNamingStrategy() {
		return this.namingStrategy;
	}

	/**
	 * Return a (read-only) map with all mappings and HandlerMethod's.
	 */
	public Map<T, HandlerMethod> getHandlerMethods() {
		this.mappingRegistry.acquireReadLock();
		try {
			return Collections.unmodifiableMap(this.mappingRegistry.getMappings());
		}
		finally {
			this.mappingRegistry.releaseReadLock();
		}
	}

	/**
	 * Return the handler methods for the given mapping name.
	 * @param mappingName the mapping name
	 * @return a list of matching HandlerMethod's or {@code null}; the returned
	 * list will never be modified and is safe to iterate.
	 * @see #setHandlerMethodMappingNamingStrategy
	 */
	@Nullable
	public List<HandlerMethod> getHandlerMethodsForMappingName(String mappingName) {
		return this.mappingRegistry.getHandlerMethodsByMappingName(mappingName);
	}

	/**
	 * Return the internal mapping registry. Provided for testing purposes.
	 */
	MappingRegistry getMappingRegistry() {
		return this.mappingRegistry;
	}

	/**
	 * Register the given mapping.
	 * <p>This method may be invoked at runtime after initialization has completed.
	 * @param mapping the mapping for the handler method
	 * @param handler the handler
	 * @param method the method
	 */
	public void registerMapping(T mapping, Object handler, Method method) {
		if (logger.isTraceEnabled()) {
			logger.trace("Register \"" + mapping + "\" to " + method.toGenericString());
		}
		this.mappingRegistry.register(mapping, handler, method);
	}

	/**
	 * Un-register the given mapping.
	 * <p>This method may be invoked at runtime after initialization has completed.
	 * @param mapping the mapping to unregister
	 */
	public void unregisterMapping(T mapping) {
		if (logger.isTraceEnabled()) {
			logger.trace("Unregister mapping \"" + mapping + "\"");
		}
		this.mappingRegistry.unregister(mapping);
	}


	// Handler method detection

	/**
	 * Detects handler methods at initialization.
	 * @see #initHandlerMethods
	 */
	@Override
	public void afterPropertiesSet() {
		// 初始化处理器的方法们
		initHandlerMethods();
	}

	/**
	 * Scan beans in the ApplicationContext, detect and register handler methods.
	 * @see #getCandidateBeanNames()
	 * @see #processCandidateBean
	 * @see #handlerMethodsInitialized
	 */
	protected void initHandlerMethods() {
		// 遍历 Bean ，逐个处理
		for (String beanName : getCandidateBeanNames()) {
			// 排除目标代理类，AOP 相关，可查看注释
			if (!beanName.startsWith(SCOPED_TARGET_NAME_PREFIX)) {
				// 处理 Bean
				processCandidateBean(beanName);
			}
		}
		// 初始化处理器的方法们，目前是空方法，暂无具体的实现
		handlerMethodsInitialized(getHandlerMethods());
	}

	/**
	 * Determine the names of candidate beans in the application context.
	 * @since 5.1
	 * @see #setDetectHandlerMethodsInAncestorContexts
	 * @see BeanFactoryUtils#beanNamesForTypeIncludingAncestors
	 */
	protected String[] getCandidateBeanNames() {
		// 获取上下文中所有的 Bean 的名称
		return (this.detectHandlerMethodsInAncestorContexts ?
				BeanFactoryUtils.beanNamesForTypeIncludingAncestors(obtainApplicationContext(), Object.class) :
				obtainApplicationContext().getBeanNamesForType(Object.class));
	}

	/**
	 * Determine the type of the specified candidate bean and call
	 * {@link #detectHandlerMethods} if identified as a handler type.
	 * <p>This implementation avoids bean creation through checking
	 * {@link org.springframework.beans.factory.BeanFactory#getType}
	 * and calling {@link #detectHandlerMethods} with the bean name.
	 * @param beanName the name of the candidate bean
	 * @since 5.1
	 * @see #isHandler
	 * @see #detectHandlerMethods
	 */
	protected void processCandidateBean(String beanName) {
		// 获得 Bean 对应的 Class 对象
		Class<?> beanType = null;
		try {
			beanType = obtainApplicationContext().getType(beanName);
		}
		catch (Throwable ex) {
			// An unresolvable bean type, probably from a lazy bean - let's ignore it.
			if (logger.isTraceEnabled()) {
				logger.trace("Could not resolve type for bean '" + beanName + "'", ex);
			}
		}
		// 判断 Bean 是否为处理器（例如有 @Controller 或者 @RequestMapping 注解）
		if (beanType != null && isHandler(beanType)) {
			// 扫描处理器方法
			detectHandlerMethods(beanName);
		}
	}

	/**
	 * Look for handler methods in the specified handler bean.
	 * @param handler either a bean name or an actual handler instance
	 * @see #getMappingForMethod
	 */
	protected void detectHandlerMethods(Object handler) {
		// 获取handler的类型
		Class<?> handlerType = (handler instanceof String ?
				obtainApplicationContext().getType((String) handler) : handler.getClass());

		if (handlerType != null) {
			// 如果是cglib代理的子对象类型，则返回父类型，否则直接返回传入的类型
			Class<?> userType = ClassUtils.getUserClass(handlerType);
			// 保存handler与匹配条件的对应关系，用于给registerHandlerMethod传入匹配条件
			Map<Method, T> methods = MethodIntrospector.selectMethods(userType,
					(MethodIntrospector.MetadataLookup<T>) method -> {
						try {
							// 创建该方法对应的 Mapping 对象，例如根据 @RequestMapping 注解创建 RequestMappingInfo 对象
							return getMappingForMethod(method, userType);
						}
						catch (Throwable ex) {
							throw new IllegalStateException("Invalid mapping on handler class [" +
									userType.getName() + "]: " + method, ex);
						}
					});
			if (logger.isTraceEnabled()) {
				logger.trace(formatMappings(userType, methods));
			}
			// 将符合要求的method注册起来，也就是保存到三个map中
			methods.forEach((method, mapping) -> {
				Method invocableMethod = AopUtils.selectInvocableMethod(method, userType);
				registerHandlerMethod(handler, invocableMethod, mapping);
			});
		}
	}

	private String formatMappings(Class<?> userType, Map<Method, T> methods) {
		String formattedType = Arrays.stream(ClassUtils.getPackageName(userType).split("\\."))
				.map(p -> p.substring(0, 1))
				.collect(Collectors.joining(".", "", "." + userType.getSimpleName()));
		Function<Method, String> methodFormatter = method -> Arrays.stream(method.getParameterTypes())
				.map(Class::getSimpleName)
				.collect(Collectors.joining(",", "(", ")"));
		return methods.entrySet().stream()
				.map(e -> {
					Method method = e.getKey();
					return e.getValue() + ": " + method.getName() + methodFormatter.apply(method);
				})
				.collect(Collectors.joining("\n\t", "\n\t" + formattedType + ":" + "\n\t", ""));
	}

	/**
	 * Register a handler method and its unique mapping. Invoked at startup for
	 * each detected handler method.
	 * @param handler the bean name of the handler or the handler instance
	 * @param method the method to register
	 * @param mapping the mapping conditions associated with the handler method
	 * @throws IllegalStateException if another method was already registered
	 * under the same mapping
	 */
	protected void registerHandlerMethod(Object handler, Method method, T mapping) {
		this.mappingRegistry.register(mapping, handler, method);
	}

	/**
	 * Create the HandlerMethod instance.
	 * @param handler either a bean name or an actual handler instance
	 * @param method the target method
	 * @return the created HandlerMethod
	 */
	protected HandlerMethod createHandlerMethod(Object handler, Method method) {
		// 如果 handler 类型为 String， 说明对应一个 Bean 对象的名称
		// 例如 UserController 使用 @Controller 注解后，默认入参 handler 就是它的 beanName ，即 `userController`
		if (handler instanceof String) {
			return new HandlerMethod((String) handler,
					obtainApplicationContext().getAutowireCapableBeanFactory(), method);
		}
		// 如果 handler 类型非 String ，说明是一个已经是一个 handler 对象，就无需处理，直接创建 HandlerMethod 对象
		return new HandlerMethod(handler, method);
	}

	/**
	 * Extract and return the CORS configuration for the mapping.
	 */
	@Nullable
	protected CorsConfiguration initCorsConfiguration(Object handler, Method method, T mapping) {
		return null;
	}

	/**
	 * Invoked after all handler methods have been detected.
	 * @param handlerMethods a read-only map with handler methods and mappings.
	 */
	protected void handlerMethodsInitialized(Map<T, HandlerMethod> handlerMethods) {
		// Total includes detected mappings + explicit registrations via registerMapping
		int total = handlerMethods.size();
		if ((logger.isTraceEnabled() && total == 0) || (logger.isDebugEnabled() && total > 0) ) {
			logger.debug(total + " mappings in " + formatMappingName());
		}
	}


	// Handler method lookup

	/**
	 * Look up a handler method for the given request.
	 */
	@Override
	protected HandlerMethod getHandlerInternal(HttpServletRequest request) throws Exception {
		// 获取访问的路径，一般类似于request.getServletPath()，返回不含contextPath的访问路径
		String lookupPath = getUrlPathHelper().getLookupPathForRequest(request);
		request.setAttribute(LOOKUP_PATH, lookupPath);
		// 获得读锁
		this.mappingRegistry.acquireReadLock();
		try {
			// 获取HandlerMethod作为handler对象，这里涉及到路径匹配的优先级
			// 优先级: 精确匹配>最长路径匹配>扩展名匹配
			HandlerMethod handlerMethod = lookupHandlerMethod(lookupPath, request);
			// handlerMethod内部包含有bean对象，其实指的是对应的controller
			return (handlerMethod != null ? handlerMethod.createWithResolvedBean() : null);
		}
		finally {
			//  释放读锁
			this.mappingRegistry.releaseReadLock();
		}
	}

	/**
	 * Look up the best-matching handler method for the current request.
	 * If multiple matches are found, the best match is selected.
	 * @param lookupPath mapping lookup path within the current servlet mapping
	 * @param request the current request
	 * @return the best-matching handler method, or {@code null} if no match
	 * @see #handleMatch(Object, String, HttpServletRequest)
	 * @see #handleNoMatch(Set, String, HttpServletRequest)
	 */
	@Nullable
	protected HandlerMethod lookupHandlerMethod(String lookupPath, HttpServletRequest request) throws Exception {
		//  Match数组，存储匹配上当前请求的结果（Mapping + HandlerMethod）
		List<Match> matches = new ArrayList<>();
		// 首先根据lookupPath获取到匹配条件
  		List<T> directPathMatches = this.mappingRegistry.getMappingsByUrl(lookupPath);
		if (directPathMatches != null) {
			// 将找到的匹配条件添加到matches
			addMatchingMappings(directPathMatches, matches, request);
		}
		// 如果不能直接使用lookupPath得到匹配条件，则将所有匹配条件加入matches
		if (matches.isEmpty()) {
			// No choice but to go through all mappings...
			addMatchingMappings(this.mappingRegistry.getMappings().keySet(), matches, request);
		}

		// 将包含匹配条件和handler的matches排序，并取第一个作为bestMatch，如果前面两个排序相同则抛出异常
		if (!matches.isEmpty()) {
			Match bestMatch = matches.get(0);
			if (matches.size() > 1) {
				// 创建MatchComparator对象，排序matches结果，排序器
				Comparator<Match> comparator = new MatchComparator(getMappingComparator(request));
				matches.sort(comparator);
				// 获得首个Match对象，也就是最匹配的
				bestMatch = matches.get(0);
				if (logger.isTraceEnabled()) {
					logger.trace(matches.size() + " matching mappings: " + matches);
				}
				if (CorsUtils.isPreFlightRequest(request)) {
					return PREFLIGHT_AMBIGUOUS_MATCH;
				}
				// 比较bestMatch和secondBestMatch，如果相等，说明有问题，抛出IllegalStateException异常
				// 因为，两个优先级一样高，说明无法判断谁更优先
				Match secondBestMatch = matches.get(1);
				if (comparator.compare(bestMatch, secondBestMatch) == 0) {
					Method m1 = bestMatch.handlerMethod.getMethod();
					Method m2 = secondBestMatch.handlerMethod.getMethod();
					String uri = request.getRequestURI();
					throw new IllegalStateException(
							"Ambiguous handler methods mapped for '" + uri + "': {" + m1 + ", " + m2 + "}");
				}
			}
			request.setAttribute(BEST_MATCHING_HANDLER_ATTRIBUTE, bestMatch.handlerMethod);
			// 处理首个Match对象
			handleMatch(bestMatch.mapping, lookupPath, request);
			// 返回首个Match对象的handlerMethod属性
			return bestMatch.handlerMethod;
		}
		// 如果匹配不到，则处理不匹配的情况
		else {
			return handleNoMatch(this.mappingRegistry.getMappings().keySet(), lookupPath, request);
		}
	}

	private void addMatchingMappings(Collection<T> mappings, List<Match> matches, HttpServletRequest request) {
		// 遍历 Mapping 数组
		for (T mapping : mappings) {
			// 执行匹配
			T match = getMatchingMapping(mapping, request);
			if (match != null) {
				// 如果匹配，则创建 Match 对象，添加到 matches 中
				matches.add(new Match(match, this.mappingRegistry.getMappings().get(mapping)));
			}
		}
	}

	/**
	 * Invoked when a matching mapping is found.
	 * @param mapping the matching mapping
	 * @param lookupPath mapping lookup path within the current servlet mapping
	 * @param request the current request
	 */
	protected void handleMatch(T mapping, String lookupPath, HttpServletRequest request) {
		request.setAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE, lookupPath);
	}

	/**
	 * Invoked when no matching mapping is not found.
	 * @param mappings all registered mappings
	 * @param lookupPath mapping lookup path within the current servlet mapping
	 * @param request the current request
	 * @throws ServletException in case of errors
	 */
	@Nullable
	protected HandlerMethod handleNoMatch(Set<T> mappings, String lookupPath, HttpServletRequest request)
			throws Exception {

		return null;
	}

	@Override
	protected boolean hasCorsConfigurationSource(Object handler) {
		return super.hasCorsConfigurationSource(handler) ||
				(handler instanceof HandlerMethod && this.mappingRegistry.getCorsConfiguration((HandlerMethod) handler) != null);
	}

	@Override
	protected CorsConfiguration getCorsConfiguration(Object handler, HttpServletRequest request) {
		CorsConfiguration corsConfig = super.getCorsConfiguration(handler, request);
		if (handler instanceof HandlerMethod) {
			HandlerMethod handlerMethod = (HandlerMethod) handler;
			if (handlerMethod.equals(PREFLIGHT_AMBIGUOUS_MATCH)) {
				return AbstractHandlerMethodMapping.ALLOW_CORS_CONFIG;
			}
			else {
				CorsConfiguration corsConfigFromMethod = this.mappingRegistry.getCorsConfiguration(handlerMethod);
				corsConfig = (corsConfig != null ? corsConfig.combine(corsConfigFromMethod) : corsConfigFromMethod);
			}
		}
		return corsConfig;
	}


	// Abstract template methods

	/**
	 * Whether the given type is a handler with handler methods.
	 * @param beanType the type of the bean being checked
	 * @return "true" if this a handler type, "false" otherwise.
	 */
	protected abstract boolean isHandler(Class<?> beanType);

	/**
	 * Provide the mapping for a handler method. A method for which no
	 * mapping can be provided is not a handler method.
	 * @param method the method to provide a mapping for
	 * @param handlerType the handler type, possibly a sub-type of the method's
	 * declaring class
	 * @return the mapping, or {@code null} if the method is not mapped
	 */
	@Nullable
	protected abstract T getMappingForMethod(Method method, Class<?> handlerType);

	/**
	 * Extract and return the URL paths contained in the supplied mapping.
	 */
	protected abstract Set<String> getMappingPathPatterns(T mapping);

	/**
	 * Check if a mapping matches the current request and return a (potentially
	 * new) mapping with conditions relevant to the current request.
	 * @param mapping the mapping to get a match for
	 * @param request the current HTTP servlet request
	 * @return the match, or {@code null} if the mapping doesn't match
	 */
	@Nullable
	protected abstract T getMatchingMapping(T mapping, HttpServletRequest request);

	/**
	 * Return a comparator for sorting matching mappings.
	 * The returned comparator should sort 'better' matches higher.
	 * @param request the current request
	 * @return the comparator (never {@code null})
	 */
	protected abstract Comparator<T> getMappingComparator(HttpServletRequest request);


	/**
	 * A registry that maintains all mappings to handler methods, exposing methods
	 * to perform lookups and providing concurrent access.
	 * <p>Package-private for testing purposes.
	 */
	class MappingRegistry {

		/**
		 * 注册表
		 *
		 * Key: Mapping
		 * Value：{@link MappingRegistration}（Mapping + HandlerMethod）
		 */
		private final Map<T, MappingRegistration<T>> registry = new HashMap<>();

		/**
		 * 保存RequestCondition和handlerMethod的对应关系
		 *
		 * Key：Mapping
		 * Value：{@link HandlerMethod}
		 */
		private final Map<T, HandlerMethod> mappingLookup = new LinkedHashMap<>();

		/**
		 * 保存url与RequestCondition的对应关系
		 *
		 * Key：直接 URL（就是固定死的路径，而非多个）
		 * Value：Mapping 数组
		 */
		private final MultiValueMap<String, T> urlLookup = new LinkedMultiValueMap<>();

		/**
		 * Mapping 的名字与 HandlerMethod 的映射
		 *
		 * Key：Mapping 的名字
		 * Value：HandlerMethod 数组
		 */
		private final Map<String, List<HandlerMethod>> nameLookup = new ConcurrentHashMap<>();

		private final Map<HandlerMethod, CorsConfiguration> corsLookup = new ConcurrentHashMap<>();

		/**
		 * 读写锁
		 */
		private final ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock();

		/**
		 * Return all mappings and handler methods. Not thread-safe.
		 * @see #acquireReadLock()
		 */
		public Map<T, HandlerMethod> getMappings() {
			return this.mappingLookup;
		}

		/**
		 * Return matches for the given URL path. Not thread-safe.
		 * @see #acquireReadLock()
		 */
		@Nullable
		public List<T> getMappingsByUrl(String urlPath) {
			return this.urlLookup.get(urlPath);
		}

		/**
		 * Return handler methods by mapping name. Thread-safe for concurrent use.
		 */
		public List<HandlerMethod> getHandlerMethodsByMappingName(String mappingName) {
			return this.nameLookup.get(mappingName);
		}

		/**
		 * Return CORS configuration. Thread-safe for concurrent use.
		 */
		@Nullable
		public CorsConfiguration getCorsConfiguration(HandlerMethod handlerMethod) {
			HandlerMethod original = handlerMethod.getResolvedFromHandlerMethod();
			return this.corsLookup.get(original != null ? original : handlerMethod);
		}

		/**
		 * 获得读锁
		 *
		 * Acquire the read lock when using getMappings and getMappingsByUrl.
		 */
		public void acquireReadLock() {
			this.readWriteLock.readLock().lock();
		}

		/**
		 * 释放读锁
		 *
		 * Release the read lock after using getMappings and getMappingsByUrl.
		 */
		public void releaseReadLock() {
			this.readWriteLock.readLock().unlock();
		}

		public void register(T mapping, Object handler, Method method) {
			// Assert that the handler method is not a suspending one.
			if (KotlinDetector.isKotlinType(method.getDeclaringClass())) {
				Class<?>[] parameterTypes = method.getParameterTypes();
				if ((parameterTypes.length > 0) && "kotlin.coroutines.Continuation".equals(parameterTypes[parameterTypes.length - 1].getName())) {
					throw new IllegalStateException("Unsupported suspending handler method detected: " + method);
				}
			}
			// 获得写锁
			this.readWriteLock.writeLock().lock();
			try {
				// 创建HandlerMethod对象
				HandlerMethod handlerMethod = createHandlerMethod(handler, method);
				// 校验当前mapping是否存在对应的HandlerMethod对象，如果已存在但不是当前的handlerMethod对象则抛出异常
				validateMethodMapping(handlerMethod, mapping);
				// 将mapping与handlerMethod的映射关系保存至this.mappingLookup
				this.mappingLookup.put(mapping, handlerMethod);

				// 获得mapping对应的普通URL数组
				List<String> directUrls = getDirectUrls(mapping);
				// 将url和mapping的映射关系保存至this.urlLookup
				for (String url : directUrls) {
					this.urlLookup.add(url, mapping);
				}

				// 初始化nameLookup
				String name = null;
				if (getNamingStrategy() != null) {
					// 获得Mapping的名字
					name = getNamingStrategy().getName(handlerMethod, mapping);
					// 将mapping的名字与HandlerMethod的映射关系保存至this.nameLookup
					addMappingName(name, handlerMethod);
				}

				// 初始化CorsConfiguration配置对象
				CorsConfiguration corsConfig = initCorsConfiguration(handler, method, mapping);
				if (corsConfig != null) {
					this.corsLookup.put(handlerMethod, corsConfig);
				}

				// 创建MappingRegistration对象
				// 并与mapping映射添加到registry注册表中
				this.registry.put(mapping, new MappingRegistration<>(mapping, handlerMethod, directUrls, name));
			}
			finally {
				// 释放写锁
				this.readWriteLock.writeLock().unlock();
			}
		}

		private void validateMethodMapping(HandlerMethod handlerMethod, T mapping) {
			// Assert that the supplied mapping is unique.
			HandlerMethod existingHandlerMethod = this.mappingLookup.get(mapping);
			if (existingHandlerMethod != null && !existingHandlerMethod.equals(handlerMethod)) {
				throw new IllegalStateException(
						"Ambiguous mapping. Cannot map '" + handlerMethod.getBean() + "' method \n" +
						handlerMethod + "\nto " + mapping + ": There is already '" +
						existingHandlerMethod.getBean() + "' bean method\n" + existingHandlerMethod + " mapped.");
			}
		}

		private List<String> getDirectUrls(T mapping) {
			List<String> urls = new ArrayList<>(1);
			// 遍历 Mapping 对应的路径
			for (String path : getMappingPathPatterns(mapping)) {
				// 非**模式**路径
				if (!getPathMatcher().isPattern(path)) {
					urls.add(path);
				}
			}
			return urls;
		}

		private void addMappingName(String name, HandlerMethod handlerMethod) {
			// 获得 Mapping 的名字，对应的 HandlerMethod 数组
			List<HandlerMethod> oldList = this.nameLookup.get(name);
			if (oldList == null) {
				oldList = Collections.emptyList();
			}

			// 如果已经存在，则不用添加
			for (HandlerMethod current : oldList) {
				if (handlerMethod.equals(current)) {
					return;
				}
			}

			// 添加到 nameLookup 中
			List<HandlerMethod> newList = new ArrayList<>(oldList.size() + 1);
			newList.addAll(oldList);
			newList.add(handlerMethod);
			this.nameLookup.put(name, newList);
		}

		public void unregister(T mapping) {
			// 获得写锁
			this.readWriteLock.writeLock().lock();
			try {
				// 从 registry 中移除
				MappingRegistration<T> definition = this.registry.remove(mapping);
				if (definition == null) {
					return;
				}

				// 从 mappingLookup 中移除
				this.mappingLookup.remove(definition.getMapping());

				// 从 urlLookup 移除
				for (String url : definition.getDirectUrls()) {
					List<T> list = this.urlLookup.get(url);
					if (list != null) {
						list.remove(definition.getMapping());
						if (list.isEmpty()) {
							this.urlLookup.remove(url);
						}
					}
				}

				// 从 nameLookup 移除
				removeMappingName(definition);

				// 从 corsLookup 中移除
				this.corsLookup.remove(definition.getHandlerMethod());
			}
			finally {
				this.readWriteLock.writeLock().unlock();
			}
		}

		private void removeMappingName(MappingRegistration<T> definition) {
			String name = definition.getMappingName();
			if (name == null) {
				return;
			}
			HandlerMethod handlerMethod = definition.getHandlerMethod();
			List<HandlerMethod> oldList = this.nameLookup.get(name);
			if (oldList == null) {
				return;
			}
			if (oldList.size() <= 1) {
				this.nameLookup.remove(name);
				return;
			}
			List<HandlerMethod> newList = new ArrayList<>(oldList.size() - 1);
			for (HandlerMethod current : oldList) {
				if (!current.equals(handlerMethod)) {
					newList.add(current);
				}
			}
			this.nameLookup.put(name, newList);
		}
	}


	private static class MappingRegistration<T> {

		/**
		 * Mapping 对象
		 */
		private final T mapping;

		/**
		 * HandlerMethod 对象
		 */
		private final HandlerMethod handlerMethod;

		/**
		 * 直接 URL 数组（就是固定死的路径，而非多个）
		 */
		private final List<String> directUrls;

		/**
		 * #mapping 的名字
		 */
		@Nullable
		private final String mappingName;

		public MappingRegistration(T mapping, HandlerMethod handlerMethod,
				@Nullable List<String> directUrls, @Nullable String mappingName) {

			Assert.notNull(mapping, "Mapping must not be null");
			Assert.notNull(handlerMethod, "HandlerMethod must not be null");
			this.mapping = mapping;
			this.handlerMethod = handlerMethod;
			this.directUrls = (directUrls != null ? directUrls : Collections.emptyList());
			this.mappingName = mappingName;
		}

		public T getMapping() {
			return this.mapping;
		}

		public HandlerMethod getHandlerMethod() {
			return this.handlerMethod;
		}

		public List<String> getDirectUrls() {
			return this.directUrls;
		}

		@Nullable
		public String getMappingName() {
			return this.mappingName;
		}
	}


	/**
	 * A thin wrapper around a matched HandlerMethod and its mapping, for the purpose of
	 * comparing the best match with a comparator in the context of the current request.
	 */
	private class Match {

		/**
		 * Mapping 对象
		 */
		private final T mapping;

		/**
		 * HandlerMethod 对象
		 */
		private final HandlerMethod handlerMethod;

		public Match(T mapping, HandlerMethod handlerMethod) {
			this.mapping = mapping;
			this.handlerMethod = handlerMethod;
		}

		@Override
		public String toString() {
			return this.mapping.toString();
		}
	}


	private class MatchComparator implements Comparator<Match> {

		private final Comparator<T> comparator;

		public MatchComparator(Comparator<T> comparator) {
			this.comparator = comparator;
		}

		@Override
		public int compare(Match match1, Match match2) {
			return this.comparator.compare(match1.mapping, match2.mapping);
		}
	}


	private static class EmptyHandler {

		@SuppressWarnings("unused")
		public void handle() {
			throw new UnsupportedOperationException("Not implemented");
		}
	}

	/**
	 * Inner class to avoid a hard dependency on Kotlin at runtime.
	 */
	private static class KotlinDelegate {

		static private boolean isSuspend(Method method) {
			KFunction<?> function = ReflectJvmMapping.getKotlinFunction(method);
			return function != null && function.isSuspend();
		}
	}

}
