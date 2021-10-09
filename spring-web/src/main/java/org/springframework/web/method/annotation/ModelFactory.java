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

package org.springframework.web.method.annotation;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.BeanUtils;
import org.springframework.core.Conventions;
import org.springframework.core.GenericTypeResolver;
import org.springframework.core.MethodParameter;
import org.springframework.lang.Nullable;
import org.springframework.ui.Model;
import org.springframework.ui.ModelMap;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.HttpSessionRequiredException;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.method.support.InvocableHandlerMethod;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * ModelFactory是用来维护model的，具体包含两个功能，1、初始化Model，2、处理器执行后将Model中相应的参数更新到sessionAttribute中
 *
 * Assist with initialization of the {@link Model} before controller method
 * invocation and with updates to it after the invocation.
 *
 * <p>On initialization the model is populated with attributes temporarily stored
 * in the session and through the invocation of {@code @ModelAttribute} methods.
 *
 * <p>On update model attributes are synchronized with the session and also
 * {@link BindingResult} attributes are added if missing.
 *
 * @author Rossen Stoyanchev
 * @since 3.1
 */
public final class ModelFactory {

	private final List<ModelMethod> modelMethods = new ArrayList<>();

	private final WebDataBinderFactory dataBinderFactory;

	private final SessionAttributesHandler sessionAttributesHandler;


	/**
	 * Create a new instance with the given {@code @ModelAttribute} methods.
	 * @param handlerMethods the {@code @ModelAttribute} methods to invoke
	 * @param binderFactory for preparation of {@link BindingResult} attributes
	 * @param attributeHandler for access to session attributes
	 */
	public ModelFactory(@Nullable List<InvocableHandlerMethod> handlerMethods,
			WebDataBinderFactory binderFactory, SessionAttributesHandler attributeHandler) {

		if (handlerMethods != null) {
			for (InvocableHandlerMethod handlerMethod : handlerMethods) {
				this.modelMethods.add(new ModelMethod(handlerMethod));
			}
		}
		this.dataBinderFactory = binderFactory;
		this.sessionAttributesHandler = attributeHandler;
	}


	/**
	 * Populate the model in the following order:
	 * <ol>
	 * <li>Retrieve "known" session attributes listed as {@code @SessionAttributes}.
	 * <li>Invoke {@code @ModelAttribute} methods
	 * <li>Find {@code @ModelAttribute} method arguments also listed as
	 * {@code @SessionAttributes} and ensure they're present in the model raising
	 * an exception if necessary.
	 * </ol>
	 * @param request the current request
	 * @param container a container with the model to be initialized
	 * @param handlerMethod the method for which the model is initialized
	 * @throws Exception may arise from {@code @ModelAttribute} methods
	 */
	public void initModel(NativeWebRequest request, ModelAndViewContainer container, HandlerMethod handlerMethod)
			throws Exception {

		// 从SessionAttributes中取出保存的参数，并合并到MavContainer中
		Map<String, ?> sessionAttributes = this.sessionAttributesHandler.retrieveAttributes(request);
		container.mergeAttributes(sessionAttributes);
		// 执行注释了@ModelAttribute的方法并将结果设置到Model中
		invokeModelAttributeMethods(request, container);

		// 遍历既注释了@ModelAttribute又在@SessionAttributes注释中的参数
		for (String name : findSessionAttributeArguments(handlerMethod)) {
			if (!container.containsAttribute(name)) {
				Object value = this.sessionAttributesHandler.retrieveAttribute(request, name);
				if (value == null) {
					throw new HttpSessionRequiredException("Expected session attribute '" + name + "'", name);
				}
				container.addAttribute(name, value);
			}
		}
	}

	/**
	 * Invoke model attribute methods to populate the model.
	 * Attributes are added only if not already present in the model.
	 */
	private void invokeModelAttributeMethods(NativeWebRequest request, ModelAndViewContainer container)
			throws Exception {

		while (!this.modelMethods.isEmpty()) {
			// 获取注释了@ModelAttribute的方法
			InvocableHandlerMethod modelMethod = getNextModelMethod(container).getHandlerMethod();
			// 获取注释了@ModelAttribute中设置的value作为参数名
			ModelAttribute ann = modelMethod.getMethodAnnotation(ModelAttribute.class);
			Assert.state(ann != null, "No ModelAttribute annotation");
			// 如果参数名已经在mavContainer中则跳过
			if (container.containsAttribute(ann.name())) {
				if (!ann.binding()) {
					container.setBindingDisabled(ann.name());
				}
				continue;
			}

			// container不包含参数名，执行方法
			Object returnValue = modelMethod.invokeForRequest(request, container);
			// 判断返回值是否是void类型，如果是void类型，方法自己将参数设置到model中，不处理
			// 如果不是void，使用getNameForReturnValue获取参数名，
			if (!modelMethod.isVoid()){
				// 使用getNameForReturnValue获取参数名
				String returnValueName = getNameForReturnValue(returnValue, modelMethod.getReturnType());
				if (!ann.binding()) {
					container.setBindingDisabled(returnValueName);
				}
				// 如果不存在container，添加进去
				if (!container.containsAttribute(returnValueName)) {
					container.addAttribute(returnValueName, returnValue);
				}
			}
		}
	}

	private ModelMethod getNextModelMethod(ModelAndViewContainer container) {
		for (ModelMethod modelMethod : this.modelMethods) {
			if (modelMethod.checkDependencies(container)) {
				this.modelMethods.remove(modelMethod);
				return modelMethod;
			}
		}
		ModelMethod modelMethod = this.modelMethods.get(0);
		this.modelMethods.remove(modelMethod);
		return modelMethod;
	}

	/**
	 * 获取同时有@ModelAttribute注解又有@SessionAttributes注解中的参数
	 *
	 * Find {@code @ModelAttribute} arguments also listed as {@code @SessionAttributes}.
	 */
	private List<String> findSessionAttributeArguments(HandlerMethod handlerMethod) {
		List<String> result = new ArrayList<>();
		// 遍历方法中的参数
		for (MethodParameter parameter : handlerMethod.getMethodParameters()) {
			// 如果有@modelAttribute注解
			if (parameter.hasParameterAnnotation(ModelAttribute.class)) {
				// 获取参数名和参数类型
				String name = getNameForParameter(parameter);
				Class<?> paramType = parameter.getParameterType();
				// 根据获取到的参数名和参数类型检查参数是否在@sessionAttributes注解中
				if (this.sessionAttributesHandler.isHandlerSessionAttribute(name, paramType)) {
					// 如果在@SessionAttributes注解中,即为符合要求的参数,将参数名放入集合
					result.add(name);
				}
			}
		}
		return result;
	}

	/**
	 * Promote model attributes listed as {@code @SessionAttributes} to the session.
	 * Add {@link BindingResult} attributes where necessary.
	 * @param request the current request
	 * @param container contains the model to update
	 * @throws Exception if creating BindingResult attributes fails
	 */
	public void updateModel(NativeWebRequest request, ModelAndViewContainer container) throws Exception {
		// 获取defaultModel
		ModelMap defaultModel = container.getDefaultModel();
		// 对SessionAttributes进行设置，如果处理器里调用了setComplete则将SessionAttribute清空，否则将defaultModel中的参数设置到SessionAttributes中
		if (container.getSessionStatus().isComplete()){
			this.sessionAttributesHandler.cleanupAttributes(request);
		}
		// 将mavContainer的defaultModel中的参数设置到SessionAttributes
		else {
			this.sessionAttributesHandler.storeAttributes(request, defaultModel);
		}
		// 判断请求是否已经处理完或者是redirect类型的返回值，其实就是判断是否需要进行页面的渲染操作
		if (!container.isRequestHandled() && container.getModel() == defaultModel) {
			updateBindingResult(request, defaultModel);
		}
	}

	/**
	 * 如果处理器绑定参数时注释了@Valid和@Validated注解，那么会讲校验的结果设置到BindingResult类型的参数中，如果没有添加校验的注释，为了渲染方便，ModelFactory
	 * 会给Model设置一个跟参数相对应的BindingResult
	 *
	 * Add {@link BindingResult} attributes to the model for attributes that require it.
	 */
	private void updateBindingResult(NativeWebRequest request, ModelMap model) throws Exception {
		List<String> keyNames = new ArrayList<>(model.keySet());
		for (String name : keyNames) {
			Object value = model.get(name);
			// 遍历每一个Model中保存的参数，判断是否需要添加BindingResult，如果需要则使用WebDataBinder获取BindingResult并添加到Model，在添加前
			// 检查Model中是否已经存在，如果已经存在就不添加了
			if (value != null && isBindingCandidate(name, value)) {
				String bindingResultKey = BindingResult.MODEL_KEY_PREFIX + name;
				// 如果model中不存在bindingResult
				if (!model.containsAttribute(bindingResultKey)) {
					// 通过dataBinderFactory创建webDataBinder
					WebDataBinder dataBinder = this.dataBinderFactory.createBinder(request, value, name);
					// 添加到model
					model.put(bindingResultKey, dataBinder.getBindingResult());
				}
			}
		}
	}

	/**
	 * 判断是都需要添加BindingResult对象
	 *
	 * Whether the given attribute requires a {@link BindingResult} in the model.
	 */
	private boolean isBindingCandidate(String attributeName, Object value) {
		// 判断是不是其他参数绑定结果的BindingResult，如果是，则不需要添加
		if (attributeName.startsWith(BindingResult.MODEL_KEY_PREFIX)) {
			return false;
		}

		// 判断是不是SessionAttribute管理的属性，如果是返回true
		if (this.sessionAttributesHandler.isHandlerSessionAttribute(attributeName, value.getClass())) {
			return true;
		}

		// 判断如果不是空值、数组、Collection、Map和简单类型，则返回true添加到BindingResult
		return (!value.getClass().isArray() && !(value instanceof Collection) &&
				!(value instanceof Map) && !BeanUtils.isSimpleValueType(value.getClass()));
	}


	/**
	 * Derive the model attribute name for the given method parameter based on
	 * a {@code @ModelAttribute} parameter annotation (if present) or falling
	 * back on parameter type based conventions.
	 * @param parameter a descriptor for the method parameter
	 * @return the derived name
	 * @see Conventions#getVariableNameForParameter(MethodParameter)
	 */
	public static String getNameForParameter(MethodParameter parameter) {
		ModelAttribute ann = parameter.getParameterAnnotation(ModelAttribute.class);
		String name = (ann != null ? ann.value() : null);
		return (StringUtils.hasText(name) ? name : Conventions.getVariableNameForParameter(parameter));
	}

	/**
	 * Derive the model attribute name for the given return value. Results will be
	 * based on:
	 * <ol>
	 * <li>the method {@code ModelAttribute} annotation value
	 * <li>the declared return type if it is more specific than {@code Object}
	 * <li>the actual return value type
	 * </ol>
	 * @param returnValue the value returned from a method invocation
	 * @param returnType a descriptor for the return type of the method
	 * @return the derived name (never {@code null} or empty String)
	 */
	public static String getNameForReturnValue(@Nullable Object returnValue, MethodParameter returnType) {
		// 获取返回值的@ModelAttribute注解，也就是方法的@ModelAttribute注解，如果设置了value则直接将其作为参数名返回，
		// 否则使用Convertions的静态方法getVariableNameForReturnType根据方法、返回值类型和返回值获取参数名
		ModelAttribute ann = returnType.getMethodAnnotation(ModelAttribute.class);
		// 如果设置了value则直接将其作为参数名返回
		if (ann != null && StringUtils.hasText(ann.value())) {
			return ann.value();
		}
		else {
			// 否则使用Conventions的静态方法getVariableNameForReturnType根据方法、返回值类型和返回值获取参数名
			Method method = returnType.getMethod();
			Assert.state(method != null, "No handler method");
			Class<?> containingClass = returnType.getContainingClass();
			Class<?> resolvedType = GenericTypeResolver.resolveReturnType(method, containingClass);
			return Conventions.getVariableNameForReturnType(method, resolvedType, returnValue);
		}
	}


	private static class ModelMethod {

		private final InvocableHandlerMethod handlerMethod;

		private final Set<String> dependencies = new HashSet<>();

		public ModelMethod(InvocableHandlerMethod handlerMethod) {
			this.handlerMethod = handlerMethod;
			for (MethodParameter parameter : handlerMethod.getMethodParameters()) {
				if (parameter.hasParameterAnnotation(ModelAttribute.class)) {
					this.dependencies.add(getNameForParameter(parameter));
				}
			}
		}

		public InvocableHandlerMethod getHandlerMethod() {
			return this.handlerMethod;
		}

		public boolean checkDependencies(ModelAndViewContainer mavContainer) {
			for (String name : this.dependencies) {
				if (!mavContainer.containsAttribute(name)) {
					return false;
				}
			}
			return true;
		}

		@Override
		public String toString() {
			return this.handlerMethod.getMethod().toGenericString();
		}
	}

}
