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

package org.springframework.context.annotation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ConfigurationCondition.ConfigurationPhase;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.env.Environment;
import org.springframework.core.env.EnvironmentCapable;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.MultiValueMap;

/**
 * Internal class used to evaluate {@link Conditional} annotations.
 *
 * @author Phillip Webb
 * @author Juergen Hoeller
 * @since 4.0
 */
class ConditionEvaluator {

    private final ConditionContextImpl context;


    /**
     * Create a new {@link ConditionEvaluator} instance.
     */
    public ConditionEvaluator(@Nullable BeanDefinitionRegistry registry,
                              @Nullable Environment environment, @Nullable ResourceLoader resourceLoader) {

        this.context = new ConditionContextImpl(registry, environment, resourceLoader);
    }


    /**
     * Determine if an item should be skipped based on {@code @Conditional} annotations.
     * The {@link ConfigurationPhase} will be deduced from the type of item (i.e. a
     * {@code @Configuration} class will be {@link ConfigurationPhase#PARSE_CONFIGURATION})
     *
     * @param metadata the meta data
     * @return if the item should be skipped
     */
    public boolean shouldSkip(AnnotatedTypeMetadata metadata) {
        return shouldSkip(metadata, null);
    }

    /**
     * 基于@Conditional标签判断该对象是否要跳过
     * <p>
     * Determine if an item should be skipped based on {@code @Conditional} annotations.
     *
     * @param metadata the meta data
     * @param phase    the phase of the call
     * @return if the item should be skipped
     */
    public boolean shouldSkip(@Nullable AnnotatedTypeMetadata metadata, @Nullable ConfigurationPhase phase) {
        // metadata为空或者配置类中不存在@Conditional标签
        if (metadata == null || !metadata.isAnnotated(Conditional.class.getName())) {
            return false;
        }

        // 采用递归的方式进行判断，第一次执行的时候phase为空，向下执行
        if (phase == null) {
            // 下面的逻辑判断中，需要进入ConfigurationClassUtils.isConfigurationCandidate方法，主要的逻辑如下：
            // 1、metadata是AnnotationMetadata类的一个实例
            // 2、检查bean中是否使用@Configuration注解
            // 3、检查bean不是一个接口
            // 4、检查bean中是否包含@Component @ComponentScan @Import @ImportResource中任意一个
            // 5、检查bean中是否有@Bean注解
            // 只要满足其中1,2或者1,3或者1,4或者1,5就会继续递归
            if (metadata instanceof AnnotationMetadata &&
                    ConfigurationClassUtils.isConfigurationCandidate((AnnotationMetadata) metadata)) {
                return shouldSkip(metadata, ConfigurationPhase.PARSE_CONFIGURATION);
            }
            return shouldSkip(metadata, ConfigurationPhase.REGISTER_BEAN);
        }

        List<Condition> conditions = new ArrayList<>();
        for (String[] conditionClasses : getConditionClasses(metadata)) {
            for (String conditionClass : conditionClasses) {
                // 获取到@Conditional注解后面的value数组
                Condition condition = getCondition(conditionClass, this.context.getClassLoader());
                conditions.add(condition);
            }
        }
        // 对相关的条件进行排序操作
        AnnotationAwareOrderComparator.sort(conditions);

        for (Condition condition : conditions) {
            ConfigurationPhase requiredPhase = null;
            if (condition instanceof ConfigurationCondition) {
                requiredPhase = ((ConfigurationCondition) condition).getConfigurationPhase();
            }
            // requiredPhase只可能是空或者是ConfigurationCondition的一个实例对象
            if ((requiredPhase == null || requiredPhase == phase) && !condition.matches(this.context, metadata)) {

                //此逻辑为：1.requiredPhase不是ConfigurationCondition的实例
                //2.phase==requiredPhase,从上述的递归可知：phase可为ConfigurationPhase.PARSE_CONFIGURATION或者ConfigurationPhase.REGISTER_BEAN
                //3.condition.matches(this.context, metadata)返回false
                //如果1、2或者1、3成立，则在此函数的上层将阻断bean注入Spring容器
                return true;
            }
        }

        return false;
    }

    @SuppressWarnings("unchecked")
    private List<String[]> getConditionClasses(AnnotatedTypeMetadata metadata) {
        MultiValueMap<String, Object> attributes = metadata.getAllAnnotationAttributes(Conditional.class.getName(), true);
        Object values = (attributes != null ? attributes.get("value") : null);
        return (List<String[]>) (values != null ? values : Collections.emptyList());
    }

    private Condition getCondition(String conditionClassName, @Nullable ClassLoader classloader) {
        Class<?> conditionClass = ClassUtils.resolveClassName(conditionClassName, classloader);
        return (Condition) BeanUtils.instantiateClass(conditionClass);
    }


    /**
     * Implementation of a {@link ConditionContext}.
     */
    private static class ConditionContextImpl implements ConditionContext {

        @Nullable
        private final BeanDefinitionRegistry registry;

        @Nullable
        private final ConfigurableListableBeanFactory beanFactory;

        private final Environment environment;

        private final ResourceLoader resourceLoader;

        @Nullable
        private final ClassLoader classLoader;

        public ConditionContextImpl(@Nullable BeanDefinitionRegistry registry,
                                    @Nullable Environment environment, @Nullable ResourceLoader resourceLoader) {

            this.registry = registry;
            this.beanFactory = deduceBeanFactory(registry);
            this.environment = (environment != null ? environment : deduceEnvironment(registry));
            this.resourceLoader = (resourceLoader != null ? resourceLoader : deduceResourceLoader(registry));
            this.classLoader = deduceClassLoader(resourceLoader, this.beanFactory);
        }

        @Nullable
        private ConfigurableListableBeanFactory deduceBeanFactory(@Nullable BeanDefinitionRegistry source) {
            if (source instanceof ConfigurableListableBeanFactory) {
                return (ConfigurableListableBeanFactory) source;
            }
            if (source instanceof ConfigurableApplicationContext) {
                return (((ConfigurableApplicationContext) source).getBeanFactory());
            }
            return null;
        }

        private Environment deduceEnvironment(@Nullable BeanDefinitionRegistry source) {
            if (source instanceof EnvironmentCapable) {
                return ((EnvironmentCapable) source).getEnvironment();
            }
            return new StandardEnvironment();
        }

        private ResourceLoader deduceResourceLoader(@Nullable BeanDefinitionRegistry source) {
            if (source instanceof ResourceLoader) {
                return (ResourceLoader) source;
            }
            return new DefaultResourceLoader();
        }

        @Nullable
        private ClassLoader deduceClassLoader(@Nullable ResourceLoader resourceLoader,
                                              @Nullable ConfigurableListableBeanFactory beanFactory) {

            if (resourceLoader != null) {
                ClassLoader classLoader = resourceLoader.getClassLoader();
                if (classLoader != null) {
                    return classLoader;
                }
            }
            if (beanFactory != null) {
                return beanFactory.getBeanClassLoader();
            }
            return ClassUtils.getDefaultClassLoader();
        }

        @Override
        public BeanDefinitionRegistry getRegistry() {
            Assert.state(this.registry != null, "No BeanDefinitionRegistry available");
            return this.registry;
        }

        @Override
        @Nullable
        public ConfigurableListableBeanFactory getBeanFactory() {
            return this.beanFactory;
        }

        @Override
        public Environment getEnvironment() {
            return this.environment;
        }

        @Override
        public ResourceLoader getResourceLoader() {
            return this.resourceLoader;
        }

        @Override
        @Nullable
        public ClassLoader getClassLoader() {
            return this.classLoader;
        }
    }

}
