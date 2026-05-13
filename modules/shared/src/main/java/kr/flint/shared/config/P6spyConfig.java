package kr.flint.shared.config;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import kr.flint.shared.p6spy.CustomP6spySqlFormat;

@Configuration(proxyBeanMethods = false)
public class P6spyConfig {

	private static final String DEFAULT_EXCLUDED_CATEGORIES = "info,debug,result,resultset,commit,rollback";
	private static final String DEFAULT_STACK_ALLOW_PREFIXES = "kr.flint";
	private static final String DEFAULT_STACK_DENY_PREFIXES = "kr.flint.shared.p6spy,kr.flint.shared.config.P6spyConfig";
	private static final int DEFAULT_STACK_MAX_DEPTH = 5;

	private static final String P6SPY_LOG_MESSAGE_FORMAT_PROPERTY = "p6spy.config.logMessageFormat";
	private static final String P6SPY_EXECUTION_THRESHOLD_PROPERTY = "p6spy.config.executionThreshold";
	private static final String P6SPY_EXCLUDE_CATEGORIES_PROPERTY = "p6spy.config.excludecategories";

	private static final String SLOW_QUERY_THRESHOLD_PROPERTY = "flint.logging.sql.slow-query-threshold-ms";
	private static final String STACK_ALLOW_PREFIXES_PROPERTY = "flint.logging.sql.stack.allow-prefixes";
	private static final String STACK_DENY_PREFIXES_PROPERTY = "flint.logging.sql.stack.deny-prefixes";
	private static final String STACK_MAX_DEPTH_PROPERTY = "flint.logging.sql.stack.max-depth";

	@Bean
	static BeanFactoryPostProcessor p6spySystemPropertyConfigurer() {
		return new P6spySystemPropertyConfigurer();
	}

	private static final class P6spySystemPropertyConfigurer implements BeanFactoryPostProcessor, EnvironmentAware {

		private Environment environment;

		@Override
		public void setEnvironment(Environment environment) {
			this.environment = environment;
		}

		@Override
		public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
			long threshold = Math.max(0, environment.getProperty(SLOW_QUERY_THRESHOLD_PROPERTY, Long.class, 0L));
			int stackMaxDepth = Math.max(0, environment.getProperty(
				STACK_MAX_DEPTH_PROPERTY,
				Integer.class,
				DEFAULT_STACK_MAX_DEPTH
			));

			System.setProperty(P6SPY_LOG_MESSAGE_FORMAT_PROPERTY, CustomP6spySqlFormat.class.getName());
			System.setProperty(P6SPY_EXECUTION_THRESHOLD_PROPERTY, String.valueOf(threshold));
			System.setProperty(P6SPY_EXCLUDE_CATEGORIES_PROPERTY, DEFAULT_EXCLUDED_CATEGORIES);
			System.setProperty(CustomP6spySqlFormat.SLOW_QUERY_THRESHOLD_PROPERTY, String.valueOf(threshold));
			System.setProperty(
				CustomP6spySqlFormat.STACK_ALLOW_PREFIXES_PROPERTY,
				environment.getProperty(STACK_ALLOW_PREFIXES_PROPERTY, DEFAULT_STACK_ALLOW_PREFIXES)
			);
			System.setProperty(
				CustomP6spySqlFormat.STACK_DENY_PREFIXES_PROPERTY,
				environment.getProperty(STACK_DENY_PREFIXES_PROPERTY, DEFAULT_STACK_DENY_PREFIXES)
			);
			System.setProperty(CustomP6spySqlFormat.STACK_MAX_DEPTH_PROPERTY, String.valueOf(stackMaxDepth));
		}
	}
}
