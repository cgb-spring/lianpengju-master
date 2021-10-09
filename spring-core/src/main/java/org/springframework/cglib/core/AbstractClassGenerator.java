/*
 * Copyright 2003,2004 The Apache Software Foundation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cglib.core;

import java.lang.ref.WeakReference;
import java.security.ProtectionDomain;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import org.springframework.asm.ClassReader;
import org.springframework.cglib.core.internal.Function;
import org.springframework.cglib.core.internal.LoadingCache;

/**
 * 生成代码的cglib的工具类
 *
 * Abstract class for all code-generating CGLIB utilities.
 * In addition to caching generated classes for performance, it provides hooks for
 * customizing the <code>ClassLoader</code>, name of the generated class, and transformations
 * applied before generation.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
abstract public class AbstractClassGenerator<T> implements ClassGenerator {

	private static final ThreadLocal CURRENT = new ThreadLocal();

	private static volatile Map<ClassLoader, ClassLoaderData> CACHE = new WeakHashMap<ClassLoader, ClassLoaderData>();

	private static final boolean DEFAULT_USE_CACHE =
			Boolean.parseBoolean(System.getProperty("cglib.useCache", "true"));


	private GeneratorStrategy strategy = DefaultGeneratorStrategy.INSTANCE;

	private NamingPolicy namingPolicy = DefaultNamingPolicy.INSTANCE;

	private Source source;

	private ClassLoader classLoader;

	private Class contextClass;

	private String namePrefix;

	private Object key;

	private boolean useCache = DEFAULT_USE_CACHE;

	private String className;

	private boolean attemptLoad;


	protected static class ClassLoaderData {

		private final Set<String> reservedClassNames = new HashSet<String>();

		/**
		 * {@link AbstractClassGenerator} here holds "cache key" (e.g. {@link org.springframework.cglib.proxy.Enhancer}
		 * configuration), and the value is the generated class plus some additional values
		 * (see {@link #unwrapCachedValue(Object)}.
		 * <p>The generated classes can be reused as long as their classloader is reachable.</p>
		 * <p>Note: the only way to access a class is to find it through generatedClasses cache, thus
		 * the key should not expire as long as the class itself is alive (its classloader is alive).</p>
		 */
		private final LoadingCache<AbstractClassGenerator, Object, Object> generatedClasses;

		/**
		 * Note: ClassLoaderData object is stored as a value of {@code WeakHashMap<ClassLoader, ...>} thus
		 * this classLoader reference should be weak otherwise it would make classLoader strongly reachable
		 * and alive forever.
		 * Reference queue is not required since the cleanup is handled by {@link WeakHashMap}.
		 */
		private final WeakReference<ClassLoader> classLoader;

		private final Predicate uniqueNamePredicate = new Predicate() {
			public boolean evaluate(Object name) {
				return reservedClassNames.contains(name);
			}
		};

		private static final Function<AbstractClassGenerator, Object> GET_KEY = new Function<AbstractClassGenerator, Object>() {
			public Object apply(AbstractClassGenerator gen) {
				return gen.key;
			}
		};

		public ClassLoaderData(ClassLoader classLoader) {
			// 判断类加载器不能为空
			if (classLoader == null) {
				throw new IllegalArgumentException("classLoader == null is not yet supported");
			}
			// 设置类加载器，弱引用 即在下次垃圾回收时就会进行回收
			this.classLoader = new WeakReference<ClassLoader>(classLoader);
			// 新建一个回调函数，这个回调函数的作用在于缓存中没获取到值时，调用传入的生成的生成代理类并返回
			Function<AbstractClassGenerator, Object> load =
					new Function<AbstractClassGenerator, Object>() {
						public Object apply(AbstractClassGenerator gen) {
							Class klass = gen.generate(ClassLoaderData.this);
							return gen.wrapCachedClass(klass);
						}
					};
			// 为这个ClassLoadData新建一个缓存类
			generatedClasses = new LoadingCache<AbstractClassGenerator, Object, Object>(GET_KEY, load);
		}

		public ClassLoader getClassLoader() {
			return classLoader.get();
		}

		public void reserveName(String name) {
			reservedClassNames.add(name);
		}

		public Predicate getUniqueNamePredicate() {
			return uniqueNamePredicate;
		}

		public Object get(AbstractClassGenerator gen, boolean useCache) {
			// 如果不用缓存(默认使用)
			if (!useCache) {
				// 则直接调用生成器的命令
				return gen.generate(ClassLoaderData.this);
			}
			else {
				// 传入代理类生成器 并根据代理类生成器获取值返回
				Object cachedValue = generatedClasses.get(gen);
				// 解包装并返回
				return gen.unwrapCachedValue(cachedValue);
			}
		}
	}


	protected T wrapCachedClass(Class klass) {
		return (T) new WeakReference(klass);
	}

	protected Object unwrapCachedValue(T cached) {
		return ((WeakReference) cached).get();
	}


	protected static class Source {

		String name;

		public Source(String name) {
			this.name = name;
		}
	}


	protected AbstractClassGenerator(Source source) {
		this.source = source;
	}

	protected void setNamePrefix(String namePrefix) {
		this.namePrefix = namePrefix;
	}

	final protected String getClassName() {
		return className;
	}

	private void setClassName(String className) {
		this.className = className;
	}

	private String generateClassName(Predicate nameTestPredicate) {
		return namingPolicy.getClassName(namePrefix, source.name, key, nameTestPredicate);
	}

	/**
	 * Set the <code>ClassLoader</code> in which the class will be generated.
	 * Concrete subclasses of <code>AbstractClassGenerator</code> (such as <code>Enhancer</code>)
	 * will try to choose an appropriate default if this is unset.
	 * <p>
	 * Classes are cached per-<code>ClassLoader</code> using a <code>WeakHashMap</code>, to allow
	 * the generated classes to be removed when the associated loader is garbage collected.
	 * @param classLoader the loader to generate the new class with, or null to use the default
	 */
	public void setClassLoader(ClassLoader classLoader) {
		this.classLoader = classLoader;
	}

	// SPRING PATCH BEGIN
	public void setContextClass(Class contextClass) {
		this.contextClass = contextClass;
	}
	// SPRING PATCH END

	/**
	 * Override the default naming policy.
	 * @param namingPolicy the custom policy, or null to use the default
	 * @see DefaultNamingPolicy
	 */
	public void setNamingPolicy(NamingPolicy namingPolicy) {
		if (namingPolicy == null)
			namingPolicy = DefaultNamingPolicy.INSTANCE;
		this.namingPolicy = namingPolicy;
	}

	/**
	 * @see #setNamingPolicy
	 */
	public NamingPolicy getNamingPolicy() {
		return namingPolicy;
	}

	/**
	 * Whether use and update the static cache of generated classes
	 * for a class with the same properties. Default is <code>true</code>.
	 */
	public void setUseCache(boolean useCache) {
		this.useCache = useCache;
	}

	/**
	 * @see #setUseCache
	 */
	public boolean getUseCache() {
		return useCache;
	}

	/**
	 * If set, CGLIB will attempt to load classes from the specified
	 * <code>ClassLoader</code> before generating them. Because generated
	 * class names are not guaranteed to be unique, the default is <code>false</code>.
	 */
	public void setAttemptLoad(boolean attemptLoad) {
		this.attemptLoad = attemptLoad;
	}

	public boolean getAttemptLoad() {
		return attemptLoad;
	}

	/**
	 * Set the strategy to use to create the bytecode from this generator.
	 * By default an instance of {@link DefaultGeneratorStrategy} is used.
	 */
	public void setStrategy(GeneratorStrategy strategy) {
		if (strategy == null)
			strategy = DefaultGeneratorStrategy.INSTANCE;
		this.strategy = strategy;
	}

	/**
	 * @see #setStrategy
	 */
	public GeneratorStrategy getStrategy() {
		return strategy;
	}

	/**
	 * Used internally by CGLIB. Returns the <code>AbstractClassGenerator</code>
	 * that is being used to generate a class in the current thread.
	 */
	public static AbstractClassGenerator getCurrent() {
		return (AbstractClassGenerator) CURRENT.get();
	}

	public ClassLoader getClassLoader() {
		ClassLoader t = classLoader;
		if (t == null) {
			t = getDefaultClassLoader();
		}
		if (t == null) {
			t = getClass().getClassLoader();
		}
		if (t == null) {
			t = Thread.currentThread().getContextClassLoader();
		}
		if (t == null) {
			throw new IllegalStateException("Cannot determine classloader");
		}
		return t;
	}

	abstract protected ClassLoader getDefaultClassLoader();

	/**
	 * Returns the protection domain to use when defining the class.
	 * <p>
	 * Default implementation returns <code>null</code> for using a default protection domain. Sub-classes may
	 * override to use a more specific protection domain.
	 * </p>
	 * @return the protection domain (<code>null</code> for using a default)
	 */
	protected ProtectionDomain getProtectionDomain() {
		return null;
	}

	protected Object create(Object key) {
		try {
			// 获取到当前生成器的类加载器
			ClassLoader loader = getClassLoader();
			// 当前类加载器对应的缓存  缓存key为类加载器，缓存的value为ClassLoaderData，可以理解为一个缓存对象，只不过此缓存对象中包含的是具体的业务逻辑处理过程，有两个function的函数式接口，一个是返回gen.key,对应的名称叫GET_KEY,还有一个是为了创建具体的class，名字叫做load
			Map<ClassLoader, ClassLoaderData> cache = CACHE;
			// 先从缓存中获取下当前类加载器所有加载过的类
			ClassLoaderData data = cache.get(loader);
			// 如果为空
			if (data == null) {
				synchronized (AbstractClassGenerator.class) {
					cache = CACHE;
					data = cache.get(loader);
					if (data == null) {
						// 新建一个缓存Cache，并将之前的缓存Cache的数据添加进来，并将已经被gc回收的数据给清除掉
						Map<ClassLoader, ClassLoaderData> newCache = new WeakHashMap<ClassLoader, ClassLoaderData>(cache);
						// 新建一个当前加载器对应的ClassLoaderData并加到缓存中，但ClassLoaderData中此时还没有数据
						data = new ClassLoaderData(loader);
						newCache.put(loader, data);
						// 刷新全局缓存
						CACHE = newCache;
					}
				}
			}
			// 设置一个全局key
			this.key = key;
			// 在刚创建的data(ClassLoaderData)中调用get方法 并将当前生成器，
			// 以及是否使用缓存的标识穿进去 系统参数 System.getProperty("cglib.useCache", "true")
			// 返回的是生成好的代理类的class信息
			Object obj = data.get(this, getUseCache());
			// 如果为class则实例化class并返回我们需要的代理类
			if (obj instanceof Class) {
				return firstInstance((Class) obj);
			}
			// 如果不是则说明是实体，则直接执行另一个方法返回实体
			return nextInstance(obj);
		}
		catch (RuntimeException | Error ex) {
			throw ex;
		}
		catch (Exception ex) {
			throw new CodeGenerationException(ex);
		}
	}

	protected Class generate(ClassLoaderData data) {
		Class gen;
		Object save = CURRENT.get();
		// 当前的代理类生成器存入ThreadLocal中
		CURRENT.set(this);
		try {
			// 获取到ClassLoader
			ClassLoader classLoader = data.getClassLoader();
			// 判断不能为空
			if (classLoader == null) {
				throw new IllegalStateException("ClassLoader is null while trying to define class " +
						getClassName() + ". It seems that the loader has been expired from a weak reference somehow. " +
						"Please file an issue at cglib's issue tracker.");
			}
			synchronized (classLoader) {
				// 生成代理类名字
				String name = generateClassName(data.getUniqueNamePredicate());
				// 缓存中存入这个名字
				data.reserveName(name);
				// 当前代理类生成器设置类名
				this.setClassName(name);
			}
			//尝试从缓存中获取类
			if (attemptLoad) {
				try {
					//要是能获取到就直接返回了
					gen = classLoader.loadClass(getClassName());
					return gen;
				}
				catch (ClassNotFoundException e) {
					// ignore
				}
			}
			// 生成字节码
			byte[] b = strategy.generate(this);
			// 获取到字节码代表的class的名字
			String className = ClassNameReader.getClassName(new ClassReader(b));
			ProtectionDomain protectionDomain = getProtectionDomain();
			synchronized (classLoader) { // just in case
				// SPRING PATCH BEGIN
				gen = ReflectUtils.defineClass(className, b, classLoader, protectionDomain, contextClass);
				// SPRING PATCH END
			}
			return gen;
		}
		catch (RuntimeException | Error ex) {
			throw ex;
		}
		catch (Exception ex) {
			throw new CodeGenerationException(ex);
		}
		finally {
			CURRENT.set(save);
		}
	}

	abstract protected Object firstInstance(Class type) throws Exception;

	abstract protected Object nextInstance(Object instance) throws Exception;

}
