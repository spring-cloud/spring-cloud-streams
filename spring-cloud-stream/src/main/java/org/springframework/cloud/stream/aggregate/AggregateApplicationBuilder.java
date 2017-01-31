/*
 * Copyright 2015-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.stream.aggregate;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.autoconfigure.EndpointCorsProperties;
import org.springframework.boot.actuate.autoconfigure.ManagementServerProperties;
import org.springframework.boot.actuate.autoconfigure.ManagementServerPropertiesAutoConfiguration;
import org.springframework.boot.actuate.endpoint.AbstractEndpoint;
import org.springframework.boot.actuate.endpoint.MetricReaderPublicMetrics;
import org.springframework.boot.actuate.endpoint.MetricsEndpoint;
import org.springframework.boot.actuate.endpoint.mvc.AbstractEndpointMvcAdapter;
import org.springframework.boot.actuate.endpoint.mvc.AbstractMvcEndpoint;
import org.springframework.boot.actuate.endpoint.mvc.EndpointHandlerMapping;
import org.springframework.boot.actuate.endpoint.mvc.EndpointHandlerMappingCustomizer;
import org.springframework.boot.actuate.endpoint.mvc.MvcEndpoint;
import org.springframework.boot.actuate.endpoint.mvc.MvcEndpointSecurityInterceptor;
import org.springframework.boot.actuate.endpoint.mvc.MvcEndpoints;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.web.BasicErrorController;
import org.springframework.boot.autoconfigure.web.DispatcherServletAutoConfiguration;
import org.springframework.boot.autoconfigure.web.EmbeddedServletContainerAutoConfiguration;
import org.springframework.boot.autoconfigure.web.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.autoconfigure.web.ServerPropertiesAutoConfiguration;
import org.springframework.boot.autoconfigure.web.WebMvcAutoConfiguration;
import org.springframework.boot.bind.PropertySourcesPropertyValues;
import org.springframework.boot.bind.RelaxedDataBinder;
import org.springframework.boot.bind.RelaxedNames;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.stream.annotation.EnableBinding;
import org.springframework.cloud.stream.binding.BindableProxyFactory;
import org.springframework.cloud.stream.config.ChannelBindingAutoConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.PropertySources;
import org.springframework.integration.monitor.IntegrationMBeanExporter;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.handler.AbstractHandlerMethodMapping;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;

/**
 * Application builder for {@link AggregateApplication}.
 *
 * @author Dave Syer
 * @author Ilayaperumal Gopinathan
 * @author Marius Bogoevici
 * @author Venil Noronha
 */
@EnableBinding
public class AggregateApplicationBuilder implements AggregateApplication, ApplicationContextAware,
		SmartInitializingSingleton {

	private static final String CHILD_CONTEXT_SUFFIX = ".spring.cloud.stream.context";

	private SourceConfigurer sourceConfigurer;

	private SinkConfigurer sinkConfigurer;

	private List<ProcessorConfigurer> processorConfigurers = new ArrayList<>();

	private AggregateApplicationBuilder applicationBuilder = this;

	private ConfigurableApplicationContext parentContext;

	private List<Object> parentSources = new ArrayList<>();

	private List<String> parentArgs = new ArrayList<>();

	private boolean headless = true;

	private boolean webEnvironment = true;

	public AggregateApplicationBuilder(String... args) {
		this(new Object[] { ParentConfiguration.class }, args);
	}

	public AggregateApplicationBuilder(Object source, String... args) {
		this(new Object[] { source }, args);
	}

	public AggregateApplicationBuilder(Object[] sources, String[] args) {
		addParentSources(sources);
		this.parentArgs.addAll(Arrays.asList(args));
	}

	/**
	 * Adding auto configuration classes to parent sources excluding the configuration
	 * classes related to binder/binding.
	 */
	private void addParentSources(Object[] sources) {
		if (!this.parentSources.contains(ParentConfiguration.class)) {
			this.parentSources.add(ParentConfiguration.class);
		}
		this.parentSources.addAll(Arrays.asList(sources));
	}

	public AggregateApplicationBuilder parent(Object source, String... args) {
		return parent(new Object[] { source }, args);
	}

	public AggregateApplicationBuilder parent(Object[] sources, String[] args) {
		addParentSources(sources);
		this.parentArgs.addAll(Arrays.asList(args));
		return this;
	}

	/**
	 * Flag to explicitly request a web or non-web environment.
	 *
	 * @param webEnvironment true if the application has a web environment
	 * @return the AggregateApplicationBuilder being constructed
	 * @see SpringApplicationBuilder#web(boolean)
	 */
	public AggregateApplicationBuilder web(boolean webEnvironment) {
		this.webEnvironment = webEnvironment;
		return this;
	}

	/**
	 * Configures the headless attribute of the build application.
	 *
	 * @param headless true if the application is headless
	 * @return the AggregateApplicationBuilder being constructed
	 * @see SpringApplicationBuilder#headless(boolean)
	 */
	public AggregateApplicationBuilder headless(boolean headless) {
		this.headless = headless;
		return this;
	}

	@Override
	public void afterSingletonsInstantiated() {
		this.run();
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.parentContext = (ConfigurableApplicationContext) applicationContext;
	}

	@Override
	public <T> T getBinding(Class<T> bindableType, String namespace) {
		if (parentContext == null) {
			throw new IllegalStateException("The aggregate application has not been started yet");
		}
		try {
			ChildContextHolder contextHolder = parentContext.getBean(namespace + CHILD_CONTEXT_SUFFIX,
					ChildContextHolder.class);
			return contextHolder.getChildContext().getBean(bindableType);
		}
		catch (BeansException e) {
			throw new IllegalStateException("Binding not found for '" + bindableType.getName() + "' into namespace " +
					namespace);
		}
	}

	public SourceConfigurer from(Class<?> app) {
		SourceConfigurer sourceConfigurer = new SourceConfigurer(app);
		this.sourceConfigurer = sourceConfigurer;
		return sourceConfigurer;
	}

	public ConfigurableApplicationContext run(String... parentArgs) {
		this.parentArgs.addAll(Arrays.asList(parentArgs));
		List<AppConfigurer<?>> apps = new ArrayList<>();
		if (this.sourceConfigurer != null) {
			apps.add(sourceConfigurer);
		}
		if (!processorConfigurers.isEmpty()) {
			for (ProcessorConfigurer processorConfigurer : processorConfigurers) {
				apps.add(processorConfigurer);
			}
		}
		if (this.sinkConfigurer != null) {
			apps.add(sinkConfigurer);
		}
		LinkedHashMap<Class<?>, String> appsToEmbed = new LinkedHashMap<>();
		LinkedHashMap<AppConfigurer, String> appConfigurers = new LinkedHashMap<>();
		for (int i = 0; i < apps.size(); i++) {
			AppConfigurer<?> appConfigurer = apps.get(i);
			Class<?> appToEmbed = appConfigurer.getApp();
			// Always update namespace before preparing SharedChannelRegistry
			if (appConfigurer.namespace == null) {
				appConfigurer.namespace = AggregateApplicationUtils
						.getDefaultNamespace(appConfigurer.getApp().getName(), i);
			}
			appsToEmbed.put(appToEmbed, appConfigurer.namespace);
			appConfigurers.put(appConfigurer, appConfigurer.namespace);
		}
		if (this.parentContext == null) {
			if (Boolean.TRUE.equals(this.webEnvironment)) {
				this.addParentSources(new Object[]{EmbeddedServletContainerAutoConfiguration.class, WebMvcAutoConfiguration.class,
						DispatcherServletAutoConfiguration.class, HttpMessageConvertersAutoConfiguration.class,
						ManagementServerPropertiesAutoConfiguration.class, ServerPropertiesAutoConfiguration.class});
			}
			this.parentContext = AggregateApplicationUtils.createParentContext(
					this.parentSources.toArray(new Object[0]),
					this.parentArgs.toArray(new String[0]), selfContained(), this.webEnvironment, this.headless);
		}
		else {
			if (BeanFactoryUtils.beansOfTypeIncludingAncestors(this.parentContext, SharedBindingTargetRegistry.class)
					.size() == 0) {
				SharedBindingTargetRegistry sharedBindingTargetRegistry = new SharedBindingTargetRegistry();
				this.parentContext.getBeanFactory().registerSingleton("sharedBindingTargetRegistry",
						sharedBindingTargetRegistry);
				this.parentContext.getBeanFactory().registerSingleton("sharedChannelRegistry",
						new SharedChannelRegistry(sharedBindingTargetRegistry));
			}
		}
		SharedBindingTargetRegistry sharedBindingTargetRegistry = this.parentContext
				.getBean(SharedBindingTargetRegistry.class);
		AggregateApplicationUtils.prepareSharedBindingTargetRegistry(sharedBindingTargetRegistry, appsToEmbed);
		PropertySources propertySources = this.parentContext.getEnvironment()
				.getPropertySources();
		for (Map.Entry<AppConfigurer, String> appConfigurerEntry : appConfigurers.entrySet()) {
			AppConfigurer appConfigurer = appConfigurerEntry.getKey();
			String namespace = appConfigurerEntry.getValue().toLowerCase();
			Set<String> argsToUpdate = new LinkedHashSet<>();
			Set<String> argKeys = new LinkedHashSet<>();
			final HashMap<String, String> target = new HashMap<>();
			RelaxedDataBinder relaxedDataBinder = new RelaxedDataBinder(target, namespace);
			relaxedDataBinder.bind(new PropertySourcesPropertyValues(propertySources));
			if (!target.isEmpty()) {
				for (Map.Entry<String, String> entry : target.entrySet()) {
					// only update the values with the highest precedence level.
					if (!relaxedNameKeyExists(entry.getKey(), argKeys)) {
						String key = entry.getKey();
						// in case of environment variables pass the lower-case property
						// key
						// as we pass the properties as command line properties
						if (key.contains("_")) {
							key = key.replace("_", "-").toLowerCase();
						}
						argKeys.add(key);
						argsToUpdate.add("--" + key + "=" + entry.getValue());
					}
				}
			}
			// Add the args that are set at the application level if they weren't
			// overridden above from other property sources.
			if (appConfigurer.getArgs() != null) {
				for (String arg : appConfigurer.getArgs()) {
					// use the key part left to the assignment and trimming the prefix
					// `--`
					String key = arg.substring(0, arg.indexOf("=")).substring(2);
					if (!relaxedNameKeyExists(key, argKeys)) {
						argsToUpdate.add(arg);
					}
				}
			}
			if (!argsToUpdate.isEmpty()) {
				appConfigurer.args(argsToUpdate.toArray(new String[0]));
			}
		}
		exposeWebEndpoints(apps);
		if (BeanFactoryUtils.beansOfTypeIncludingAncestors(this.parentContext, AggregateApplication.class)
				.size() == 0) {
			this.parentContext.getBeanFactory().registerSingleton("aggregateApplicationAccessor", this);
		}
		return this.parentContext;
	}

	private void exposeWebEndpoints(List<AppConfigurer<?>> apps) {
		Set<MvcEndpoint> mvcEndpointsToAdd = new HashSet<>();
		Map<RequestMappingInfo, Map<Object, Method>> requestMappingInfo = new HashMap<>();
		for (int i = apps.size() - 1; i >= 0; i--) {
			AppConfigurer<?> appConfigurer = apps.get(i);
			ConfigurableApplicationContext childContext = appConfigurer.embed();
			// Map the Spring MVC RequestMapping into parent context
			AbstractHandlerMethodMapping handlerMethodMapping = childContext.getBean(AbstractHandlerMethodMapping.class);
			Map<RequestMappingInfo, HandlerMethod> mappings = handlerMethodMapping.getHandlerMethods();
			for (Map.Entry<RequestMappingInfo, HandlerMethod> mapping : mappings.entrySet()) {
				Map<Object, Method> handlerMethodMap = new HashMap<>();
				HandlerMethod handlerMethod = mapping.getValue();
				if (!BasicErrorController.class.isAssignableFrom(handlerMethod.getBeanType())) {
					Object handlerBean = handlerMethod.getBean();
					try {
						this.parentContext.getBeanFactory().getBean(handlerBean.toString());
					}
					catch (NoSuchBeanDefinitionException e) {
						// ChildContext is expected to have the bean as the request mapping is retrieved from the child context.
						this.parentContext.getBeanFactory().registerSingleton(handlerBean.toString(), childContext.getBean(handlerBean.toString()));
					}
					handlerMethodMap.put(handlerBean, handlerMethod.getMethod());
				}
				requestMappingInfo.put(mapping.getKey(), handlerMethodMap);
			}
			// Map the actuator web endpoints
			MvcEndpoints mvcEndpoints = new MvcEndpoints();
			mvcEndpoints.setApplicationContext(childContext);
			try {
				mvcEndpoints.afterPropertiesSet();
			}
			catch (Exception e) {
				throw new RuntimeException(e);
			}
			for (MvcEndpoint mvcEndpoint : mvcEndpoints.getEndpoints()) {
				String namespaceValue = Pattern.compile("\\w+").matcher(appConfigurer.getNamespace()).matches() ? appConfigurer.getNamespace() : "child_" + i;
				if (mvcEndpoint instanceof AbstractMvcEndpoint) {
					((AbstractMvcEndpoint) mvcEndpoint).setPath("/" + namespaceValue + "/" + mvcEndpoint.getPath());
				}
				else if (mvcEndpoint instanceof AbstractEndpointMvcAdapter) {
					((AbstractEndpointMvcAdapter) mvcEndpoint).setPath("/" + namespaceValue + "/" + mvcEndpoint.getPath());
				}
				else if (mvcEndpoint instanceof AbstractEndpoint) {
					((AbstractEndpoint) mvcEndpoint).setId(namespaceValue + "_" + ((AbstractEndpoint) mvcEndpoint).getId());
				}
			}
			mvcEndpointsToAdd.addAll(mvcEndpoints.getEndpoints());
		}
		MvcEndpointsHolder mvcEndpointsHolder = new MvcEndpointsHolder(mvcEndpointsToAdd);
		this.parentContext.getBeanFactory().registerSingleton("mvcEndpointsHolder", mvcEndpointsHolder);
		// invoke lazy bean to get instantiated after setting mvcEndpoints
		EndpointHandlerMapping endpointHandlerMapping = this.parentContext.getBean(EndpointHandlerMapping.class);
		for (Map.Entry<RequestMappingInfo, Map<Object, Method>> mappingInfoMapEntry : requestMappingInfo.entrySet()) {
			for (Map.Entry<Object, Method> handlerMethodEntry : mappingInfoMapEntry.getValue().entrySet()) {
				endpointHandlerMapping.registerMapping(mappingInfoMapEntry.getKey(), handlerMethodEntry.getKey(), handlerMethodEntry.getValue());
			}
		}
	}

	private boolean selfContained() {
		return (this.sourceConfigurer != null) && (this.sinkConfigurer != null);
	}

	private boolean relaxedNameKeyExists(String key, Collection<String> collection) {
		RelaxedNames relaxedNames = new RelaxedNames(key);
		for (String name : relaxedNames) {
			if (collection.contains(name)) {
				return true;
			}
		}
		return false;
	}

	private ChildContextBuilder childContext(Class<?> app, ConfigurableApplicationContext parentContext,
			String namespace) {
		return new ChildContextBuilder(AggregateApplicationUtils.embedApp(parentContext, namespace, app));
	}

	public class SourceConfigurer extends AppConfigurer<SourceConfigurer> {

		public SourceConfigurer(Class<?> app) {
			this.app = app;
			sourceConfigurer = this;
		}

		public SinkConfigurer to(Class<?> sink) {
			return new SinkConfigurer(sink);
		}

		public ProcessorConfigurer via(Class<?> processor) {
			return new ProcessorConfigurer(processor);
		}

	}

	public class SinkConfigurer extends AppConfigurer<SinkConfigurer> {

		public SinkConfigurer(Class<?> app) {
			this.app = app;
			sinkConfigurer = this;
		}

	}

	public class ProcessorConfigurer extends AppConfigurer<ProcessorConfigurer> {

		public ProcessorConfigurer(Class<?> app) {
			this.app = app;
			processorConfigurers.add(this);
		}

		public SinkConfigurer to(Class<?> sink) {
			return new SinkConfigurer(sink);
		}

		public ProcessorConfigurer via(Class<?> processor) {
			return new ProcessorConfigurer(processor);
		}

	}

	public abstract class AppConfigurer<T extends AppConfigurer<T>> {

		Class<?> app;

		String[] args;

		String[] names;

		String[] profiles;

		String namespace;

		Class<?> getApp() {
			return this.app;
		}

		public T as(String... names) {
			this.names = names;
			return getConfigurer();
		}

		public T args(String... args) {
			this.args = args;
			return getConfigurer();
		}

		public T profiles(String... profiles) {
			this.profiles = profiles;
			return getConfigurer();
		}

		@SuppressWarnings("unchecked")
		private T getConfigurer() {
			return (T) this;
		}

		public T namespace(String namespace) {
			this.namespace = namespace;
			return getConfigurer();
		}

		public ConfigurableApplicationContext run(String... args) {
			return applicationBuilder.run(args);
		}

		ConfigurableApplicationContext embed() {
			final ConfigurableApplicationContext childContext = childContext(this.app,
					AggregateApplicationBuilder.this.parentContext, this.namespace).args(this.args).config(this.names)
							.profiles(this.profiles).run();
			// Register bindable proxies as beans so they can be queried for later
			Map<String, BindableProxyFactory> bindableProxies = BeanFactoryUtils
					.beansOfTypeIncludingAncestors(childContext.getBeanFactory(), BindableProxyFactory.class);
			for (String bindableProxyName : bindableProxies.keySet()) {
				try {
					AggregateApplicationBuilder.this.parentContext.getBeanFactory().registerSingleton(
							this.getNamespace() + CHILD_CONTEXT_SUFFIX, new ChildContextHolder(childContext));
				}
				catch (Exception e) {
					throw new IllegalStateException(
							"Error while trying to register the aggregate bound interface '"
									+ bindableProxyName + "' into namespace '" + this.getNamespace() + "'",
							e);
				}
			}
			// Register metrics if JMX enabled and exporter avalable
			if (BeanFactoryUtils.beansOfTypeIncludingAncestors(AggregateApplicationBuilder.this.parentContext,
					IntegrationMBeanExporter.class).size() > 0) {
				BeanFactoryUtils
						.beanOfTypeIncludingAncestors(AggregateApplicationBuilder.this.parentContext,
								MetricsEndpoint.class)
						.registerPublicMetrics(
								new MetricReaderPublicMetrics(new NamespaceAwareSpringIntegrationMetricReader(
										this.namespace, childContext.getBean(IntegrationMBeanExporter.class))));
			}
			return childContext;
		}

		public AggregateApplication build() {
			return applicationBuilder;
		}

		public String[] getArgs() {
			return this.args;
		}

		public String getNamespace() {
			return this.namespace;
		}
	}

	private final class ChildContextBuilder {

		private SpringApplicationBuilder builder;

		private String configName;

		private String[] args;

		private ChildContextBuilder(SpringApplicationBuilder builder) {
			this.builder = builder;
		}

		public ChildContextBuilder profiles(String... profiles) {
			if (profiles != null) {
				this.builder.profiles(profiles);
			}
			return this;
		}

		public ChildContextBuilder config(String... configs) {
			if (configs != null) {
				this.configName = StringUtils.arrayToCommaDelimitedString(configs);
			}
			return this;
		}

		public ChildContextBuilder args(String... args) {
			this.args = args;
			return this;
		}

		public ConfigurableApplicationContext run() {
			List<String> args = new ArrayList<String>();
			if (this.args != null) {
				args.addAll(Arrays.asList(this.args));
			}
			if (this.configName != null) {
				args.add("--spring.config.name=" + this.configName);
			}
			return this.builder.run(args.toArray(new String[0]));
		}

	}

	private static class ChildContextHolder {

		private final ConfigurableApplicationContext childContext;

		ChildContextHolder(ConfigurableApplicationContext childContext) {
			Assert.notNull(childContext, "cannot be null");
			this.childContext = childContext;
		}

		public ConfigurableApplicationContext getChildContext() {
			return childContext;
		}
	}

	@EnableBinding
	@ImportAutoConfiguration({ChannelBindingAutoConfiguration.class})
	public static class ParentConfiguration {

		@Autowired(required = false)
		private ManagementServerProperties managementServerProperties;

		@Autowired(required = false)
		private List<EndpointHandlerMappingCustomizer> mappingCustomizers;

		@Autowired(required = false)
		private EndpointCorsProperties corsProperties;

		@Bean
		@ConditionalOnMissingBean(SharedBindingTargetRegistry.class)
		public SharedBindingTargetRegistry sharedBindingTargetRegistry() {
			return new SharedBindingTargetRegistry();
		}

		@Bean
		@ConditionalOnMissingBean(SharedChannelRegistry.class)
		public SharedChannelRegistry sharedChannelRegistry(SharedBindingTargetRegistry sharedBindingTargetRegistry) {
			return new SharedChannelRegistry(sharedBindingTargetRegistry);
		}

		@Bean
		@Lazy
		@ConditionalOnClass(name = "org.springframework.boot.actuate.endpoint.mvc.MvcEndpointSecurityInterceptor")
		public EndpointHandlerMapping endpointHandlerMapping(MvcEndpointsHolder mvcEndpointsHolder) {
			EndpointHandlerMapping mapping = null;
			if (this.corsProperties != null) {
				CorsConfiguration corsConfiguration = getCorsConfiguration(this.corsProperties);
				mapping = new EndpointHandlerMapping(mvcEndpointsHolder.getMvcEndpoints(), corsConfiguration);
			}
			else {
				mapping = new EndpointHandlerMapping(mvcEndpointsHolder.getMvcEndpoints(), null);
			}
			if (managementServerProperties != null) {
				mapping.setPrefix(this.managementServerProperties.getContextPath());
				MvcEndpointSecurityInterceptor securityInterceptor = new MvcEndpointSecurityInterceptor(
						this.managementServerProperties.getSecurity().isEnabled(),
						this.managementServerProperties.getSecurity().getRoles());
				mapping.setSecurityInterceptor(securityInterceptor);
			}
			if (this.mappingCustomizers != null) {
				for (EndpointHandlerMappingCustomizer customizer : this.mappingCustomizers) {
					customizer.customize(mapping);
				}
			}
			return mapping;
		}

		@Bean
		@Lazy
		@ConditionalOnMissingClass("org.springframework.boot.actuate.endpoint.mvc.MvcEndpointSecurityInterceptor")
		public EndpointHandlerMapping endpointHandlerMappingWithoutSecurityInterceptor(MvcEndpointsHolder mvcEndpointsHolder) {
			EndpointHandlerMapping mapping = null;
			if (this.corsProperties != null) {
				CorsConfiguration corsConfiguration = getCorsConfiguration(this.corsProperties);
				mapping = new EndpointHandlerMapping(mvcEndpointsHolder.getMvcEndpoints(), corsConfiguration);
			}
			else {
				mapping = new EndpointHandlerMapping(mvcEndpointsHolder.getMvcEndpoints(), null);
			}
			if (managementServerProperties != null) {
				mapping.setPrefix(this.managementServerProperties.getContextPath());
			}
			if (this.mappingCustomizers != null) {
				for (EndpointHandlerMappingCustomizer customizer : this.mappingCustomizers) {
					customizer.customize(mapping);
				}
			}
			return mapping;
		}

		private CorsConfiguration getCorsConfiguration(EndpointCorsProperties properties) {
			if (CollectionUtils.isEmpty(properties.getAllowedOrigins())) {
				return null;
			}
			CorsConfiguration configuration = new CorsConfiguration();
			configuration.setAllowedOrigins(properties.getAllowedOrigins());
			if (!CollectionUtils.isEmpty(properties.getAllowedHeaders())) {
				configuration.setAllowedHeaders(properties.getAllowedHeaders());
			}
			if (!CollectionUtils.isEmpty(properties.getAllowedMethods())) {
				configuration.setAllowedMethods(properties.getAllowedMethods());
			}
			if (!CollectionUtils.isEmpty(properties.getExposedHeaders())) {
				configuration.setExposedHeaders(properties.getExposedHeaders());
			}
			if (properties.getMaxAge() != null) {
				configuration.setMaxAge(properties.getMaxAge());
			}
			if (properties.getAllowCredentials() != null) {
				configuration.setAllowCredentials(properties.getAllowCredentials());
			}
			return configuration;
		}
	}

	public static class MvcEndpointsHolder {

		private final Set<MvcEndpoint> mvcEndpoints;

		public MvcEndpointsHolder(Set<MvcEndpoint> mvcEndpoints) {
			this.mvcEndpoints = mvcEndpoints;
		}

		public Set<MvcEndpoint> getMvcEndpoints() {
			return mvcEndpoints;
		}
	}
}
