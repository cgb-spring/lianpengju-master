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

package org.springframework.beans.factory.xml;

import java.io.IOException;

import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * EntityResolver接口表示的意思是如果应用程序实现自定义处理外部实体，那么久必须实现此接口，并且使用setEntityResolver方法向SAX驱动器注册一个实例
 * 本质表示的意思是，对于解析一个xml，sax首先会读取该xml文档上的生命，根据声明去寻找相应的dtd定义，以方便对文档进行验证，默认的寻找规则就是通过网络
 * 来根据URI地址来下载DTD声明，并进行验证，下载过程中如果有网络的问题，那么就有可能报错
 *
 * entityResolver的作用是项目本身就可以提供一个如何寻找DTD的声明方法，即由程序来实现寻找DTD声明的过程。比如我们将DTD放在项目的某处在实现时直接将
 * 此文档读取并返回SAX即可，这样就避免了通过网络来寻找DTD的声明
 *
 *
 *
 * {@link EntityResolver} implementation that delegates to a {@link BeansDtdResolver}
 * and a {@link PluggableSchemaResolver} for DTDs and XML schemas, respectively.
 *
 * @author Rob Harrop
 * @author Juergen Hoeller
 * @author Rick Evans
 * @since 2.0
 * @see BeansDtdResolver
 * @see PluggableSchemaResolver
 */
public class DelegatingEntityResolver implements EntityResolver {

	/** Suffix for DTD files. */
	public static final String DTD_SUFFIX = ".dtd";

	/** Suffix for schema definition files. */
	public static final String XSD_SUFFIX = ".xsd";


	private final EntityResolver dtdResolver;

	private final EntityResolver schemaResolver;


	/**
	 * Create a new DelegatingEntityResolver that delegates to
	 * a default {@link BeansDtdResolver} and a default {@link PluggableSchemaResolver}.
	 * <p>Configures the {@link PluggableSchemaResolver} with the supplied
	 * {@link ClassLoader}.
	 * @param classLoader the ClassLoader to use for loading
	 * (can be {@code null}) to use the default ClassLoader)
	 */
	public DelegatingEntityResolver(@Nullable ClassLoader classLoader) {
		this.dtdResolver = new BeansDtdResolver();
		// 当完成这行代码的调用之后，大家神奇的发现一件事情，schemaResolver对象的schemaMappings属性被完成了赋值操作，但是你遍历完成所有代码后依然没有看到显式调用
		// 其实此时的原理是非常简单的，我们在进行debug的时候，因为在程序运行期间需要显示当前类的所有信息，所以idea会帮助我们调用toString方法，只不过此过程我们识别不到而已
		this.schemaResolver = new PluggableSchemaResolver(classLoader);
	}

	/**
	 * Create a new DelegatingEntityResolver that delegates to
	 * the given {@link EntityResolver EntityResolvers}.
	 * @param dtdResolver the EntityResolver to resolve DTDs with
	 * @param schemaResolver the EntityResolver to resolve XML schemas with
	 */
	public DelegatingEntityResolver(EntityResolver dtdResolver, EntityResolver schemaResolver) {
		Assert.notNull(dtdResolver, "'dtdResolver' is required");
		Assert.notNull(schemaResolver, "'schemaResolver' is required");
		this.dtdResolver = dtdResolver;
		this.schemaResolver = schemaResolver;
	}


	@Override
	@Nullable
	public InputSource resolveEntity(@Nullable String publicId, @Nullable String systemId)
			throws SAXException, IOException {

		if (systemId != null) {
			if (systemId.endsWith(DTD_SUFFIX)) {
				return this.dtdResolver.resolveEntity(publicId, systemId);
			}
			else if (systemId.endsWith(XSD_SUFFIX)) {
				return this.schemaResolver.resolveEntity(publicId, systemId);
			}
		}

		// Fall back to the parser's default behavior.
		return null;
	}


	@Override
	public String toString() {
		return "EntityResolver delegating " + XSD_SUFFIX + " to " + this.schemaResolver +
				" and " + DTD_SUFFIX + " to " + this.dtdResolver;
	}

}
