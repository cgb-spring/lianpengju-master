package com.mashibing.selfAutowired;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.PropertyValues;
import org.springframework.beans.TypeConverter;
import org.springframework.beans.factory.*;
import org.springframework.beans.factory.annotation.AutowiredAnnotationBeanPostProcessor;
import org.springframework.beans.factory.annotation.InjectionMetadata;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.DependencyDescriptor;
import org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessorAdapter;
import org.springframework.beans.factory.support.MergedBeanDefinitionPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.core.BridgeMethodResolver;
import org.springframework.core.MethodParameter;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.annotation.MergedAnnotation;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

import java.beans.PropertyDescriptor;
import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class LianAnnotationBeanPostProcessor extends InstantiationAwareBeanPostProcessorAdapter
        implements MergedBeanDefinitionPostProcessor, PriorityOrdered, BeanFactoryAware {
    protected final Log logger = LogFactory.getLog(getClass());

    // 该处理器支持解析的注解们，默认支持的是3个
    private final Set<Class<? extends Annotation>> autowiredAnnotationTypes = new LinkedHashSet<>(4);

    // @Autowired(required = false)这个注解的属性值名称
    private String requiredParameterName = "required";

    // 这个值一般请不要改变（若改成false，效果required = false的作用是相反的了）
    private boolean requiredParameterValue = true;

    private int order = Ordered.LOWEST_PRECEDENCE - 2;

    @Nullable
    private ConfigurableListableBeanFactory beanFactory;

    // 对@Lookup方法的支持
    private final Set<String> lookupMethodsChecked = Collections.newSetFromMap(new ConcurrentHashMap<>(256));

    // 构造函数候选器缓存
    private final Map<Class<?>, Constructor<?>[]> candidateConstructorsCache = new ConcurrentHashMap<>(256);

    // 方法注入、字段filed注入
    // 此处InjectionMetadata这个类非常重要，到了此处@Autowired注解含义已经没有了，完全被准备成这个元数据了
    // InjectionMetadata持有targetClass、Collection<InjectedElement> injectedElements等两个重要属性
    // 其中InjectedElement这个抽象类最重要的两个实现为：AutowiredFieldElement和AutowiredMethodElement
    private final Map<String, InjectionMetadata> injectionMetadataCache = new ConcurrentHashMap<>(256);

    public LianAnnotationBeanPostProcessor(){
        this.autowiredAnnotationTypes.add(Lian.class);
    }

    public void setAutowiredAnnotationType(Class<? extends Annotation> autowiredAnnotationType) {
        Assert.notNull(autowiredAnnotationType, "'autowiredAnnotationType' must not be null");
        this.autowiredAnnotationTypes.clear();
        this.autowiredAnnotationTypes.add(autowiredAnnotationType);
    }

    public void setAutowiredAnnotationTypes(Set<Class<? extends Annotation>> autowiredAnnotationTypes) {
        Assert.notEmpty(autowiredAnnotationTypes, "'autowiredAnnotationTypes' must not be empty");
        this.autowiredAnnotationTypes.clear();
        this.autowiredAnnotationTypes.addAll(autowiredAnnotationTypes);
    }

    public void setRequiredParameterName(String requiredParameterName) {
        this.requiredParameterName = requiredParameterName;
    }

    public void setRequiredParameterValue(boolean requiredParameterValue) {
        this.requiredParameterValue = requiredParameterValue;
    }

    public void setOrder(int order) {
        this.order = order;
    }

    @Override
    public int getOrder() {
        return this.order;
    }


    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        if (!(beanFactory instanceof ConfigurableListableBeanFactory)) {
            throw new IllegalArgumentException(
                    "AutowiredAnnotationBeanPostProcessor requires a ConfigurableListableBeanFactory: " + beanFactory);
        }
        this.beanFactory = (ConfigurableListableBeanFactory) beanFactory;
    }

    @Override
    public void postProcessMergedBeanDefinition(RootBeanDefinition beanDefinition, Class<?> beanType, String beanName) {
        // 解析注解并缓存
        InjectionMetadata metadata = findAutowiringMetadata(beanName, beanType, null);
        metadata.checkConfigMembers(beanDefinition);
    }

    @Override
    public void resetBeanDefinition(String beanName) {
        this.lookupMethodsChecked.remove(beanName);
        this.injectionMetadataCache.remove(beanName);
    }

    @Override
    public PropertyValues postProcessProperties(PropertyValues pvs, Object bean, String beanName) {
        // 从缓存中取出这个bean对应的依赖注入的元信息~
        InjectionMetadata metadata = findAutowiringMetadata(beanName, bean.getClass(), pvs);
        try {
            // 进行属性注入
            metadata.inject(bean, beanName, pvs);
        }
        catch (BeanCreationException ex) {
            throw ex;
        }
        catch (Throwable ex) {
            throw new BeanCreationException(beanName, "Injection of autowired dependencies failed", ex);
        }
        return pvs;
    }

    public void processInjection(Object bean) throws BeanCreationException {
        Class<?> clazz = bean.getClass();
        InjectionMetadata metadata = findAutowiringMetadata(clazz.getName(), clazz, null);
        try {
            metadata.inject(bean, null, null);
        }
        catch (BeanCreationException ex) {
            throw ex;
        }
        catch (Throwable ex) {
            throw new BeanCreationException(
                    "Injection of autowired dependencies failed for class [" + clazz + "]", ex);
        }
    }

    private InjectionMetadata findAutowiringMetadata(String beanName, Class<?> clazz, @Nullable PropertyValues pvs) {
        // Fall back to class name as cache key, for backwards compatibility with custom callers.
        String cacheKey = (StringUtils.hasLength(beanName) ? beanName : clazz.getName());
        // Quick check on the concurrent map first, with minimal locking.
        // 从缓存中获取该类的信息
        InjectionMetadata metadata = this.injectionMetadataCache.get(cacheKey);
        // 判断是否需要刷新缓存
        if (InjectionMetadata.needsRefresh(metadata, clazz)) {
            synchronized (this.injectionMetadataCache) {
                metadata = this.injectionMetadataCache.get(cacheKey);
                if (InjectionMetadata.needsRefresh(metadata, clazz)) {
                    if (metadata != null) {
                        metadata.clear(pvs);
                    }
                    // 构建自动装配的属性和方法元数据
                    metadata = buildAutowiringMetadata(clazz);
                    this.injectionMetadataCache.put(cacheKey, metadata);
                }
            }
        }
        return metadata;
    }

    private InjectionMetadata buildAutowiringMetadata(final Class<?> clazz) {
        // 如果clazz是JDK中的类，直接忽略，因为不可能标注有这些标注
        if (!AnnotationUtils.isCandidateClass(clazz, this.autowiredAnnotationTypes)) {
            return InjectionMetadata.EMPTY;
        }

        List<InjectionMetadata.InjectedElement> elements = new ArrayList<>();
        Class<?> targetClass = clazz;

        do {
            final List<InjectionMetadata.InjectedElement> currElements = new ArrayList<>();

            // 遍历类中的每个属性，判断属性是否包含指定的属性(通过 findAutowiredAnnotation 方法)
            // 如果存在则保存，这里注意，属性保存的类型是 AutowiredFieldElement
            ReflectionUtils.doWithLocalFields(targetClass, field -> {
                MergedAnnotation<?> ann = findAutowiredAnnotation(field);
                if (ann != null) {
                    //Autowired注解不支持静态方法
                    if (Modifier.isStatic(field.getModifiers())) {
                        if (logger.isInfoEnabled()) {
                            logger.info("Autowired annotation is not supported on static fields: " + field);
                        }
                        return;
                    }
                    //查看是否是required的
                    boolean required = determineRequiredStatus(ann);
                    currElements.add(new LianFieldElement(field, required));
                }
            });


            // 遍历类中的每个方法，判断属性是否包含指定的属性(通过 findAutowiredAnnotation 方法)
            // 如果存在则保存，这里注意，方法保存的类型是 AutowiredMethodElement
            ReflectionUtils.doWithLocalMethods(targetClass, method -> {
                Method bridgedMethod = BridgeMethodResolver.findBridgedMethod(method);
                if (!BridgeMethodResolver.isVisibilityBridgeMethodPair(method, bridgedMethod)) {
                    return;
                }
                MergedAnnotation<?> ann = findAutowiredAnnotation(bridgedMethod);
                if (ann != null && method.equals(ClassUtils.getMostSpecificMethod(method, clazz))) {
                    if (Modifier.isStatic(method.getModifiers())) {
                        if (logger.isInfoEnabled()) {
                            logger.info("Autowired annotation is not supported on static methods: " + method);
                        }
                        return;
                    }
                    // 如果方法没有入参，输出日志，不做任何处理
                    if (method.getParameterCount() == 0) {
                        if (logger.isInfoEnabled()) {
                            logger.info("Autowired annotation should only be used on methods with parameters: " +
                                    method);
                        }
                    }
                    boolean required = determineRequiredStatus(ann);
                    PropertyDescriptor pd = BeanUtils.findPropertyForMethod(bridgedMethod, clazz);
                    // AutowiredMethodElement里封装了一个PropertyDescriptor（比字段多了一个参数）
                    currElements.add(new LianMethodElement(method, required, pd));
                }
            });

            // 父类的都放在第一位，所以父类是最先完成依赖注入的
            elements.addAll(0, currElements);
            targetClass = targetClass.getSuperclass();
        }
        while (targetClass != null && targetClass != Object.class);

        // InjectionMetadata就是对clazz和elements的一个包装而已
        return InjectionMetadata.forElements(elements, clazz);
    }

    @Nullable
    private MergedAnnotation<?> findAutowiredAnnotation(AccessibleObject ao) {
        MergedAnnotations annotations = MergedAnnotations.from(ao);
        for (Class<? extends Annotation> type : this.autowiredAnnotationTypes) {
            MergedAnnotation<?> annotation = annotations.get(type);
            if (annotation.isPresent()) {
                return annotation;
            }
        }
        return null;
    }

    @SuppressWarnings({"deprecation", "cast"})
    protected boolean determineRequiredStatus(MergedAnnotation<?> ann) {
        // The following (AnnotationAttributes) cast is required on JDK 9+.
        return determineRequiredStatus((AnnotationAttributes)
                ann.asMap(mergedAnnotation -> new AnnotationAttributes(mergedAnnotation.getType())));
    }

    @Deprecated
    protected boolean determineRequiredStatus(AnnotationAttributes ann) {
        return (!ann.containsKey(this.requiredParameterName) ||
                this.requiredParameterValue == ann.getBoolean(this.requiredParameterName));
    }

    protected <T> Map<String, T> findAutowireCandidates(Class<T> type) throws BeansException {
        if (this.beanFactory == null) {
            throw new IllegalStateException("No BeanFactory configured - " +
                    "override the getBeanOfType method or specify the 'beanFactory' property");
        }
        return BeanFactoryUtils.beansOfTypeIncludingAncestors(this.beanFactory, type);
    }

    private void registerDependentBeans(@Nullable String beanName, Set<String> autowiredBeanNames) {
        if (beanName != null) {
            for (String autowiredBeanName : autowiredBeanNames) {
                if (this.beanFactory != null && this.beanFactory.containsBean(autowiredBeanName)) {
                    this.beanFactory.registerDependentBean(autowiredBeanName, beanName);
                }
                if (logger.isTraceEnabled()) {
                    logger.trace("Autowiring by type from bean name '" + beanName +
                            "' to bean named '" + autowiredBeanName + "'");
                }
            }
        }
    }

    @Nullable
    private Object resolvedCachedArgument(@Nullable String beanName, @Nullable Object cachedArgument) {
        if (cachedArgument instanceof DependencyDescriptor) {
            DependencyDescriptor descriptor = (DependencyDescriptor) cachedArgument;
            Assert.state(this.beanFactory != null, "No BeanFactory available");
            return this.beanFactory.resolveDependency(descriptor, beanName, null, null);
        }
        else {
            return cachedArgument;
        }
    }

    private class LianFieldElement extends InjectionMetadata.InjectedElement {

        private final boolean required;

        private volatile boolean cached = false;

        @Nullable
        private volatile Object cachedFieldValue;

        public LianFieldElement(Field field, boolean required) {
            super(field, null);
            this.required = required;
        }

        /**
         * 完成属性的注入
         * @param bean
         * @param beanName
         * @param pvs
         * @throws Throwable
         */
        @Override
        protected void inject(Object bean, @Nullable String beanName, @Nullable PropertyValues pvs) throws Throwable {
            Field field = (Field) this.member;
            Object value;
            // 如果缓存，从缓存中获取
            if (this.cached) {
                // 如果  cachedFieldValue instanceof DependencyDescriptor。则调用 resolveDependency 方法重新加载。
                value = resolvedCachedArgument(beanName, this.cachedFieldValue);
            }
            else {
                // 否则调用了 resolveDependency 方法。这个在前篇讲过，在 populateBean 方法中按照类型注入的时候就是通过此方法，
                // 也就是说明了 @Autowired 和 @Inject默认是 按照类型注入的
                DependencyDescriptor desc = new DependencyDescriptor(field, this.required);
                desc.setContainingClass(bean.getClass());
                Set<String> autowiredBeanNames = new LinkedHashSet<>(1);
                Assert.state(beanFactory != null, "No BeanFactory available");
                // 转换器使用的bean工厂的转换器
                TypeConverter typeConverter = beanFactory.getTypeConverter();
                try {
                    // 获取依赖的value值的工作  最终还是委托给beanFactory.resolveDependency()去完成的
                    // 这个接口方法由AutowireCapableBeanFactory提供，它提供了从bean工厂里获取依赖值的能力
                    value = beanFactory.resolveDependency(desc, beanName, autowiredBeanNames, typeConverter);
                }
                catch (BeansException ex) {
                    throw new UnsatisfiedDependencyException(null, beanName, new InjectionPoint(field), ex);
                }
                // 把缓存值缓存起来
                synchronized (this) {
                    // 如果没有缓存，则开始缓存
                    if (!this.cached) {
                        // 可以看到value！=null并且required=true才会进行缓存的处理
                        if (value != null || this.required) {
                            // 这里先缓存一下 desc，如果下面 utowiredBeanNames.size() > 1。则在上面从缓存中获取的时候会重新获取。
                            this.cachedFieldValue = desc;
                            // 注册依赖bean
                            registerDependentBeans(beanName, autowiredBeanNames);
                            // autowiredBeanNames里可能会有别名的名称,所以size可能大于1
                            if (autowiredBeanNames.size() == 1) {
                                // beanFactory.isTypeMatch挺重要的,因为@Autowired是按照类型注入的
                                String autowiredBeanName = autowiredBeanNames.iterator().next();
                                if (beanFactory.containsBean(autowiredBeanName) &&
                                        beanFactory.isTypeMatch(autowiredBeanName, field.getType())) {
                                    this.cachedFieldValue = new ShortcutDependencyDescriptor(
                                            desc, autowiredBeanName, field.getType());
                                }
                            }
                        }
                        else {
                            this.cachedFieldValue = null;
                        }
                        this.cached = true;
                    }
                }
            }
            if (value != null) {
                // 通过反射，给属性赋值
                ReflectionUtils.makeAccessible(field);
                field.set(bean, value);
            }
        }
    }

    private class LianMethodElement extends InjectionMetadata.InjectedElement {

        private final boolean required;

        private volatile boolean cached = false;

        @Nullable
        private volatile Object[] cachedMethodArguments;

        public LianMethodElement(Method method, boolean required, @Nullable PropertyDescriptor pd) {
            super(method, pd);
            this.required = required;
        }

        /**
         * 完成方法的注入
         * @param bean
         * @param beanName
         * @param pvs
         * @throws Throwable
         */
        @Override
        protected void inject(Object bean, @Nullable String beanName, @Nullable PropertyValues pvs) throws Throwable {
            // 检测是否可以跳过
            if (checkPropertySkipping(pvs)) {
                return;
            }
            // 获取方法
            Method method = (Method) this.member;
            Object[] arguments;
            // 如果缓存中有，从缓存中获取
            if (this.cached) {
                // Shortcut for avoiding synchronization...
                arguments = resolveCachedArguments(beanName);
            }
            else {
                // 获取方法的参数，从Spring 容器中获取(缓存中没有则尝试创建)
                int argumentCount = method.getParameterCount();
                arguments = new Object[argumentCount];
                DependencyDescriptor[] descriptors = new DependencyDescriptor[argumentCount];
                Set<String> autowiredBeans = new LinkedHashSet<>(argumentCount);
                Assert.state(beanFactory != null, "No BeanFactory available");
                TypeConverter typeConverter = beanFactory.getTypeConverter();
                // 遍历参数从容器中获取
                for (int i = 0; i < arguments.length; i++) {
                    MethodParameter methodParam = new MethodParameter(method, i);
                    DependencyDescriptor currDesc = new DependencyDescriptor(methodParam, this.required);
                    currDesc.setContainingClass(bean.getClass());
                    descriptors[i] = currDesc;
                    try {
                        // 根据类型从容器中获取
                        Object arg = beanFactory.resolveDependency(currDesc, beanName, autowiredBeans, typeConverter);
                        if (arg == null && !this.required) {
                            arguments = null;
                            break;
                        }
                        arguments[i] = arg;
                    }
                    catch (BeansException ex) {
                        throw new UnsatisfiedDependencyException(null, beanName, new InjectionPoint(methodParam), ex);
                    }
                }
                synchronized (this) {
                    if (!this.cached) {
                        if (arguments != null) {
                            DependencyDescriptor[] cachedMethodArguments = Arrays.copyOf(descriptors, arguments.length);
                            registerDependentBeans(beanName, autowiredBeans);
                            if (autowiredBeans.size() == argumentCount) {
                                Iterator<String> it = autowiredBeans.iterator();
                                Class<?>[] paramTypes = method.getParameterTypes();
                                for (int i = 0; i < paramTypes.length; i++) {
                                    String autowiredBeanName = it.next();
                                    if (beanFactory.containsBean(autowiredBeanName) &&
                                            beanFactory.isTypeMatch(autowiredBeanName, paramTypes[i])) {
                                        cachedMethodArguments[i] = new ShortcutDependencyDescriptor(
                                                descriptors[i], autowiredBeanName, paramTypes[i]);
                                    }
                                }
                            }
                            this.cachedMethodArguments = cachedMethodArguments;
                        }
                        else {
                            this.cachedMethodArguments = null;
                        }
                        this.cached = true;
                    }
                }
            }
            if (arguments != null) {
                try {
                    // 通过反射，调用注解标注的方法
                    ReflectionUtils.makeAccessible(method);
                    method.invoke(bean, arguments);
                }
                catch (InvocationTargetException ex) {
                    throw ex.getTargetException();
                }
            }
        }

        @Nullable
        private Object[] resolveCachedArguments(@Nullable String beanName) {
            Object[] cachedMethodArguments = this.cachedMethodArguments;
            if (cachedMethodArguments == null) {
                return null;
            }
            Object[] arguments = new Object[cachedMethodArguments.length];
            for (int i = 0; i < arguments.length; i++) {
                arguments[i] = resolvedCachedArgument(beanName, cachedMethodArguments[i]);
            }
            return arguments;
        }
    }


    @SuppressWarnings("serial")
    private static class ShortcutDependencyDescriptor extends DependencyDescriptor {

        private final String shortcut;

        private final Class<?> requiredType;

        public ShortcutDependencyDescriptor(DependencyDescriptor original, String shortcut, Class<?> requiredType) {
            super(original);
            this.shortcut = shortcut;
            this.requiredType = requiredType;
        }

        @Override
        public Object resolveShortcut(BeanFactory beanFactory) {
            return beanFactory.getBean(this.shortcut, this.requiredType);
        }
    }

}
