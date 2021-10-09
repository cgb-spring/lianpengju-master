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

package org.springframework.core.io.support;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Properties;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.core.io.Resource;
import org.springframework.lang.Nullable;
import org.springframework.util.CollectionUtils;
import org.springframework.util.DefaultPropertiesPersister;
import org.springframework.util.PropertiesPersister;

/**
 * Base class for JavaBean-style components that need to load properties
 * from one or more resources. Supports local properties as well, with
 * configurable overriding.
 *
 * @author Juergen Hoeller
 * @since 1.2.2
 */
public abstract class PropertiesLoaderSupport {

	/** Logger available to subclasses. */
	protected final Log logger = LogFactory.getLog(getClass());

	// 本地属性，通过设置Properties对象直接设置进来的属性
	@Nullable
	protected Properties[] localProperties;

	// 本地属性和其他设置属性的优先级设置，false表示外来属性优先级高于本地属性，true表示本地属性优先级高于外来属性
	protected boolean localOverride = false;

	// 外部属性对应资源，通过设置外部资源位置设置要加载的属性
	@Nullable
	private Resource[] locations;

	// 如果遇到不存在的资源路径，那么false，输入日志，继续执行其他逻辑，true，抛出异常
	private boolean ignoreResourceNotFound = false;

	// 设置文件的编码格式
	@Nullable
	private String fileEncoding;

	// 外部属性加载工具
	private PropertiesPersister propertiesPersister = new DefaultPropertiesPersister();


	/**
	 * Set local properties, e.g. via the "props" tag in XML bean definitions.
	 * These can be considered defaults, to be overridden by properties
	 * loaded from files.
	 */
	public void setProperties(Properties properties) {
		this.localProperties = new Properties[] {properties};
	}

	/**
	 * Set local properties, e.g. via the "props" tag in XML bean definitions,
	 * allowing for merging multiple properties sets into one.
	 */
	public void setPropertiesArray(Properties... propertiesArray) {
		this.localProperties = propertiesArray;
	}

	/**
	 * Set a location of a properties file to be loaded.
	 * <p>Can point to a classic properties file or to an XML file
	 * that follows JDK 1.5's properties XML format.
	 */
	public void setLocation(Resource location) {
		this.locations = new Resource[] {location};
	}

	/**
	 * Set locations of properties files to be loaded.
	 * <p>Can point to classic properties files or to XML files
	 * that follow JDK 1.5's properties XML format.
	 * <p>Note: Properties defined in later files will override
	 * properties defined earlier files, in case of overlapping keys.
	 * Hence, make sure that the most specific files are the last
	 * ones in the given list of locations.
	 */
	public void setLocations(Resource... locations) {
		this.locations = locations;
	}

	/**
	 * Set whether local properties override properties from files.
	 * <p>Default is "false": Properties from files override local defaults.
	 * Can be switched to "true" to let local properties override defaults
	 * from files.
	 */
	public void setLocalOverride(boolean localOverride) {
		this.localOverride = localOverride;
	}

	/**
	 * Set if failure to find the property resource should be ignored.
	 * <p>"true" is appropriate if the properties file is completely optional.
	 * Default is "false".
	 */
	public void setIgnoreResourceNotFound(boolean ignoreResourceNotFound) {
		this.ignoreResourceNotFound = ignoreResourceNotFound;
	}

	/**
	 * Set the encoding to use for parsing properties files.
	 * <p>Default is none, using the {@code java.util.Properties}
	 * default encoding.
	 * <p>Only applies to classic properties files, not to XML files.
	 * @see org.springframework.util.PropertiesPersister#load
	 */
	public void setFileEncoding(String encoding) {
		this.fileEncoding = encoding;
	}

	/**
	 * Set the PropertiesPersister to use for parsing properties files.
	 * The default is DefaultPropertiesPersister.
	 * @see org.springframework.util.DefaultPropertiesPersister
	 */
	public void setPropertiesPersister(@Nullable PropertiesPersister propertiesPersister) {
		this.propertiesPersister =
				(propertiesPersister != null ? propertiesPersister : new DefaultPropertiesPersister());
	}


	/**
	 * 返回一个合并的属性实例包含加载的属性和设置在factorybean中的属性
	 *
	 * Return a merged Properties instance containing both the
	 * loaded properties and properties set on this FactoryBean.
	 */
	protected Properties mergeProperties() throws IOException {
		Properties result = new Properties();

		if (this.localOverride) {
			// Load properties from file upfront, to let local properties override.
			// 加载外部属性到结果对象中
			loadProperties(result);
		}

		if (this.localProperties != null) {
			// 将本地属性合并到结果对象
			for (Properties localProp : this.localProperties) {
				CollectionUtils.mergePropertiesIntoMap(localProp, result);
			}
		}

		if (!this.localOverride) {
			// Load properties from file afterwards, to let those properties override.
			// 加载外部属性到结果对象中
			loadProperties(result);
		}

		return result;
	}

	/**
	 * Load properties into the given instance.
	 * @param props the Properties instance to load into
	 * @throws IOException in case of I/O errors
	 * @see #setLocations
	 */
	protected void loadProperties(Properties props) throws IOException {
		if (this.locations != null) {
			// 读取每一个属性文件资源
			for (Resource location : this.locations) {
				if (logger.isTraceEnabled()) {
					logger.trace("Loading properties file from " + location);
				}
				try {
					// 使用指定的字符集从外部资源路径location读取属性到props，使用的属性读取工具是propertiesPersister
					PropertiesLoaderUtils.fillProperties(
							props, new EncodedResource(location, this.fileEncoding), this.propertiesPersister);
				}
				catch (FileNotFoundException | UnknownHostException | SocketException ex) {
					// 出现异常时的处理
					if (this.ignoreResourceNotFound) {
						if (logger.isDebugEnabled()) {
							logger.debug("Properties resource not found: " + ex.getMessage());
						}
					}
					else {
						throw ex;
					}
				}
			}
		}
	}

}
