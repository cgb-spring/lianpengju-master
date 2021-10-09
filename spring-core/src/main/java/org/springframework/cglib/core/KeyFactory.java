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

import java.lang.reflect.Method;
import java.security.ProtectionDomain;
import java.util.Collections;
import java.util.List;

import org.springframework.asm.ClassVisitor;
import org.springframework.asm.Label;
import org.springframework.asm.Type;
import org.springframework.cglib.core.internal.CustomizerRegistry;

/**
 * 生成class为了处理多个键的组合一起作为key，例如，1,2,3是一个组合，2,3也是一个组合，都可以作为key
 *
 * Generates classes to handle multi-valued keys, for use in things such as Maps and Sets.
 * Code for <code>equals</code> and <code>hashCode</code> methods follow the
 * the rules laid out in <i>Effective Java</i> by Joshua Bloch.
 * <p>
 * To generate a <code>KeyFactory</code>, you need to supply an interface which
 * describes the structure of the key. The interface should have a
 * single method named <code>newInstance</code>, which returns an
 * <code>Object</code>. The arguments array can be
 * <i>anything</i>--Objects, primitive values, or single or
 * multi-dimension arrays of either. For example:
 * <p><pre>
 *     private interface IntStringKey {
 *         public Object newInstance(int i, String s);
 *     }
 * </pre><p>
 * Once you have made a <code>KeyFactory</code>, you generate a new key by calling
 * the <code>newInstance</code> method defined by your interface.
 * <p><pre>
 *     IntStringKey factory = (IntStringKey)KeyFactory.create(IntStringKey.class);
 *     Object key1 = factory.newInstance(4, "Hello");
 *     Object key2 = factory.newInstance(4, "World");
 * </pre><p>
 * <b>Note:</b>
 * <code>hashCode</code> equality between two keys <code>key1</code> and <code>key2</code> is only guaranteed if
 * <code>key1.equals(key2)</code> <i>and</i> the keys were produced by the same factory.
 * @version $Id: KeyFactory.java,v 1.26 2006/03/05 02:43:19 herbyderby Exp $
 */
@SuppressWarnings({"rawtypes", "unchecked"})
abstract public class KeyFactory {

	private static final Signature GET_NAME =
			TypeUtils.parseSignature("String getName()");

	private static final Signature GET_CLASS =
			TypeUtils.parseSignature("Class getClass()");

	private static final Signature HASH_CODE =
			TypeUtils.parseSignature("int hashCode()");

	private static final Signature EQUALS =
			TypeUtils.parseSignature("boolean equals(Object)");

	private static final Signature TO_STRING =
			TypeUtils.parseSignature("String toString()");

	private static final Signature APPEND_STRING =
			TypeUtils.parseSignature("StringBuffer append(String)");

	private static final Type KEY_FACTORY =
			TypeUtils.parseType("org.springframework.cglib.core.KeyFactory");

	private static final Signature GET_SORT =
			TypeUtils.parseSignature("int getSort()");

	//generated numbers:
	private final static int PRIMES[] = {
			11, 73, 179, 331,
			521, 787, 1213, 1823,
			2609, 3691, 5189, 7247,
			10037, 13931, 19289, 26627,
			36683, 50441, 69403, 95401,
			131129, 180179, 247501, 340057,
			467063, 641371, 880603, 1209107,
			1660097, 2279161, 3129011, 4295723,
			5897291, 8095873, 11114263, 15257791,
			20946017, 28754629, 39474179, 54189869,
			74391461, 102123817, 140194277, 192456917,
			264202273, 362693231, 497900099, 683510293,
			938313161, 1288102441, 1768288259};


	public static final Customizer CLASS_BY_NAME = new Customizer() {
		public void customize(CodeEmitter e, Type type) {
			if (type.equals(Constants.TYPE_CLASS)) {
				e.invoke_virtual(Constants.TYPE_CLASS, GET_NAME);
			}
		}
	};

	public static final FieldTypeCustomizer STORE_CLASS_AS_STRING = new FieldTypeCustomizer() {
		public void customize(CodeEmitter e, int index, Type type) {
			if (type.equals(Constants.TYPE_CLASS)) {
				e.invoke_virtual(Constants.TYPE_CLASS, GET_NAME);
			}
		}
		public Type getOutType(int index, Type type) {
			if (type.equals(Constants.TYPE_CLASS)) {
				return Constants.TYPE_STRING;
			}
			return type;
		}
	};

	/**
	 * {@link Type#hashCode()} is very expensive as it traverses full descriptor to calculate hash code.
	 * This customizer uses {@link Type#getSort()} as a hash code.
	 */
	public static final HashCodeCustomizer HASH_ASM_TYPE = new HashCodeCustomizer() {
		public boolean customize(CodeEmitter e, Type type) {
			if (Constants.TYPE_TYPE.equals(type)) {
				e.invoke_virtual(type, GET_SORT);
				return true;
			}
			return false;
		}
	};

	/**
	 * @deprecated this customizer might result in unexpected class leak since key object still holds a strong reference to the Object and class.
	 * It is recommended to have pre-processing method that would strip Objects and represent Classes as Strings
	 */
	@Deprecated
	public static final Customizer OBJECT_BY_CLASS = new Customizer() {
		public void customize(CodeEmitter e, Type type) {
			e.invoke_virtual(Constants.TYPE_OBJECT, GET_CLASS);
		}
	};

	protected KeyFactory() {
	}

	public static KeyFactory create(Class keyInterface) {
		return create(keyInterface, null);
	}

	public static KeyFactory create(Class keyInterface, Customizer customizer) {
		return create(keyInterface.getClassLoader(), keyInterface, customizer);
	}

	public static KeyFactory create(Class keyInterface, KeyFactoryCustomizer first, List<KeyFactoryCustomizer> next) {
		return create(keyInterface.getClassLoader(), keyInterface, first, next);
	}

	public static KeyFactory create(ClassLoader loader, Class keyInterface, Customizer customizer) {
		return create(loader, keyInterface, customizer, Collections.<KeyFactoryCustomizer>emptyList());
	}

	public static KeyFactory create(ClassLoader loader, Class keyInterface, KeyFactoryCustomizer customizer,
			List<KeyFactoryCustomizer> next) {
		// 创建一个最简易的代理类生成器 即只会生成HashCode equals toString newInstance方法
		Generator gen = new Generator();
		// 设置接口为enhancerKey类型
		gen.setInterface(keyInterface);
		// SPRING PATCH BEGIN
		gen.setContextClass(keyInterface);
		// SPRING PATCH END

		if (customizer != null) {
			// 添加定制器
			gen.addCustomizer(customizer);
		}
		if (next != null && !next.isEmpty()) {
			for (KeyFactoryCustomizer keyFactoryCustomizer : next) {
				// 添加定制器
				gen.addCustomizer(keyFactoryCustomizer);
			}
		}
		// 设置生成器的类加载器
		gen.setClassLoader(loader);
		// 生成enhancerKey的代理类
		return gen.create();
	}


	public static class Generator extends AbstractClassGenerator {

		private static final Source SOURCE = new Source(KeyFactory.class.getName());

		private static final Class[] KNOWN_CUSTOMIZER_TYPES = new Class[]{Customizer.class, FieldTypeCustomizer.class};

		private Class keyInterface;

		// TODO: Make me final when deprecated methods are removed
		private CustomizerRegistry customizers = new CustomizerRegistry(KNOWN_CUSTOMIZER_TYPES);

		private int constant;

		private int multiplier;

		public Generator() {
			super(SOURCE);
		}

		protected ClassLoader getDefaultClassLoader() {
			return keyInterface.getClassLoader();
		}

		protected ProtectionDomain getProtectionDomain() {
			return ReflectUtils.getProtectionDomain(keyInterface);
		}

		/**
		 * @deprecated Use {@link #addCustomizer(KeyFactoryCustomizer)} instead.
		 */
		@Deprecated
		public void setCustomizer(Customizer customizer) {
			customizers = CustomizerRegistry.singleton(customizer);
		}

		public void addCustomizer(KeyFactoryCustomizer customizer) {
			customizers.add(customizer);
		}

		public <T> List<T> getCustomizers(Class<T> klass) {
			return customizers.get(klass);
		}

		public void setInterface(Class keyInterface) {
			this.keyInterface = keyInterface;
		}

		public KeyFactory create() {
			// 设置了该生成器生成代理类的名字前缀，即我们的接口名Enhancer.enhancerKey
			setNamePrefix(keyInterface.getName());
			return (KeyFactory) super.create(keyInterface.getName());
		}

		public void setHashConstant(int constant) {
			this.constant = constant;
		}

		public void setHashMultiplier(int multiplier) {
			this.multiplier = multiplier;
		}

		protected Object firstInstance(Class type) {
			return ReflectUtils.newInstance(type);
		}

		protected Object nextInstance(Object instance) {
			return instance;
		}

		// 该方法为字节码写入方法 为最后一步
		public void generateClass(ClassVisitor v) {
			// 创建类写入聚合对象
			ClassEmitter ce = new ClassEmitter(v);

			//找到被代理类的newInstance方法 如果没有会报异常,由此可知,如果想用Generator代理类生成器,必须要有newInstance方法
			Method newInstance = ReflectUtils.findNewInstance(keyInterface);
			//如果被代理类的newInstance不为Object则报异常,此处我们代理的Enchaer.EnhancerKey newInstance方法返回值为Object
			if (!newInstance.getReturnType().equals(Object.class)) {
				throw new IllegalArgumentException("newInstance method must return Object");
			}

			// 找到newInstance方法的所有参数类型并当做成员变量
			Type[] parameterTypes = TypeUtils.getTypes(newInstance.getParameterTypes());
			// 1.创建类开始写入类头,版本号,访问权限,类名等通用信息
			ce.begin_class(Constants.V1_8,
					Constants.ACC_PUBLIC,
					getClassName(),
					KEY_FACTORY,
					new Type[]{Type.getType(keyInterface)},
					Constants.SOURCE_FILE);
			// 2.写入无参构造方法
			EmitUtils.null_constructor(ce);
			// 3.写入newInstance方法
			EmitUtils.factory_method(ce, ReflectUtils.getSignature(newInstance));

			int seed = 0;
			// 4.开始构造有参构造方法
			CodeEmitter e = ce.begin_method(Constants.ACC_PUBLIC,
					TypeUtils.parseConstructor(parameterTypes),
					null);
			e.load_this();
			// 4.1有参构造中调用父类构造方法,即super.构造方法()
			e.super_invoke_constructor();
			e.load_this();
			// 4.2找到传入的定制器 例如一开始传入的hashCode方法定制器
			List<FieldTypeCustomizer> fieldTypeCustomizers = getCustomizers(FieldTypeCustomizer.class);
			// 4.3遍历成员变量即newInstance方法的所有参数
			for (int i = 0; i < parameterTypes.length; i++) {
				Type parameterType = parameterTypes[i];
				Type fieldType = parameterType;
				for (FieldTypeCustomizer customizer : fieldTypeCustomizers) {
					fieldType = customizer.getOutType(i, fieldType);
				}
				seed += fieldType.hashCode();
				// 4.3将这些参数全部声明到写入类中
				ce.declare_field(Constants.ACC_PRIVATE | Constants.ACC_FINAL,
						getFieldName(i),
						fieldType,
						null);
				e.dup();
				e.load_arg(i);
				for (FieldTypeCustomizer customizer : fieldTypeCustomizers) {
					customizer.customize(e, i, parameterType);
				}
				// 4.4设置每个成员变量的值  即我们常见的有参构造中的this.xx = xx
				e.putfield(getFieldName(i));
			}
			// 设置返回值
			e.return_value();
			// 有参构造及成员变量写入完成
			e.end_method();

			// hash code
			// 5.写入hashcode方法
			e = ce.begin_method(Constants.ACC_PUBLIC, HASH_CODE, null);
			int hc = (constant != 0) ? constant : PRIMES[(Math.abs(seed) % PRIMES.length)];
			int hm = (multiplier != 0) ? multiplier : PRIMES[(Math.abs(seed * 13) % PRIMES.length)];
			e.push(hc);
			for (int i = 0; i < parameterTypes.length; i++) {
				e.load_this();
				e.getfield(getFieldName(i));
				EmitUtils.hash_code(e, parameterTypes[i], hm, customizers);
			}
			e.return_value();
			// hashcode方法结束
			e.end_method();

			// equals
			// 6.写入equals方法
			e = ce.begin_method(Constants.ACC_PUBLIC, EQUALS, null);
			Label fail = e.make_label();
			e.load_arg(0);
			e.instance_of_this();
			e.if_jump(CodeEmitter.EQ, fail);
			for (int i = 0; i < parameterTypes.length; i++) {
				e.load_this();
				e.getfield(getFieldName(i));
				e.load_arg(0);
				e.checkcast_this();
				e.getfield(getFieldName(i));
				EmitUtils.not_equals(e, parameterTypes[i], fail, customizers);
			}
			e.push(1);
			e.return_value();
			e.mark(fail);
			e.push(0);
			e.return_value();
			// equals方法结束
			e.end_method();

			// toString
			// 7.写入toString方法
			e = ce.begin_method(Constants.ACC_PUBLIC, TO_STRING, null);
			e.new_instance(Constants.TYPE_STRING_BUFFER);
			e.dup();
			e.invoke_constructor(Constants.TYPE_STRING_BUFFER);
			for (int i = 0; i < parameterTypes.length; i++) {
				if (i > 0) {
					e.push(", ");
					e.invoke_virtual(Constants.TYPE_STRING_BUFFER, APPEND_STRING);
				}
				e.load_this();
				e.getfield(getFieldName(i));
				EmitUtils.append_string(e, parameterTypes[i], EmitUtils.DEFAULT_DELIMITERS, customizers);
			}
			e.invoke_virtual(Constants.TYPE_STRING_BUFFER, TO_STRING);
			e.return_value();
			// toString方法结束
			e.end_method();
			// 类写入结束,至此类信息收集完成并全部写入ClassVisitor
			ce.end_class();
		}

		private String getFieldName(int arg) {
			return "FIELD_" + arg;
		}
	}

}
