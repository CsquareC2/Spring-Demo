/*
 * Copyright 2012-2018 the original author or authors.
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

package org.springframework.boot.autoconfigure.security.reactive;

import org.junit.Test;
import reactor.core.publisher.Mono;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link ReactiveUserDetailsServiceAutoConfiguration}.
 *
 * @author Madhura Bhave
 */
public class ReactiveUserDetailsServiceAutoConfigurationTests {

	private final ReactiveWebApplicationContextRunner contextRunner = new ReactiveWebApplicationContextRunner()
			.withConfiguration(AutoConfigurations
					.of(ReactiveUserDetailsServiceAutoConfiguration.class));

	@Test
	public void configuresADefaultUser() {
		this.contextRunner.withUserConfiguration(TestSecurityConfiguration.class)
				.run((context) -> {
					ReactiveUserDetailsService userDetailsService = context
							.getBean(ReactiveUserDetailsService.class);
					assertThat(userDetailsService.findByUsername("user").block())
							.isNotNull();
				});
	}

	@Test
	public void doesNotConfigureDefaultUserIfUserDetailsServiceAvailable() {
		this.contextRunner
				.withUserConfiguration(UserConfig.class, TestSecurityConfiguration.class)
				.run((context) -> {
					ReactiveUserDetailsService userDetailsService = context
							.getBean(ReactiveUserDetailsService.class);
					assertThat(userDetailsService.findByUsername("user").block())
							.isNull();
					assertThat(userDetailsService.findByUsername("foo").block())
							.isNotNull();
					assertThat(userDetailsService.findByUsername("admin").block())
							.isNotNull();
				});
	}

	@Test
	public void doesNotConfigureDefaultUserIfAuthenticationManagerAvailable() {
		this.contextRunner
				.withUserConfiguration(AuthenticationManagerConfig.class,
						TestSecurityConfiguration.class)
				.withConfiguration(
						AutoConfigurations.of(ReactiveSecurityAutoConfiguration.class))
				.run((context) -> assertThat(context)
						.getBean(ReactiveUserDetailsService.class).isNull());
	}

	@Test
	public void userDetailsServiceWhenPasswordEncoderAbsentAndDefaultPassword() {
		this.contextRunner.withUserConfiguration(TestSecurityConfiguration.class)
				.run(((context) -> {
					MapReactiveUserDetailsService userDetailsService = context
							.getBean(MapReactiveUserDetailsService.class);
					String password = userDetailsService.findByUsername("user").block()
							.getPassword();
					assertThat(password).startsWith("{noop}");
				}));
	}

	@Test
	public void userDetailsServiceWhenPasswordEncoderAbsentAndRawPassword() {
		testPasswordEncoding(TestSecurityConfiguration.class, "secret", "{noop}secret");
	}

	@Test
	public void userDetailsServiceWhenPasswordEncoderAbsentAndEncodedPassword() {
		String password = "{bcrypt}$2a$10$sCBi9fy9814vUPf2ZRbtp.fR5/VgRk2iBFZ.ypu5IyZ28bZgxrVDa";
		testPasswordEncoding(TestSecurityConfiguration.class, password, password);
	}

	@Test
	public void userDetailsServiceWhenPasswordEncoderBeanPresent() {
		testPasswordEncoding(TestConfigWithPasswordEncoder.class, "secret", "secret");
	}

	private void testPasswordEncoding(Class<?> configClass, String providedPassword,
			String expectedPassword) {
		this.contextRunner.withUserConfiguration(configClass)
				.withPropertyValues("spring.security.user.password=" + providedPassword)
				.run(((context) -> {
					MapReactiveUserDetailsService userDetailsService = context
							.getBean(MapReactiveUserDetailsService.class);
					String password = userDetailsService.findByUsername("user").block()
							.getPassword();
					assertThat(password).isEqualTo(expectedPassword);
				}));
	}

	@Configuration
	@EnableWebFluxSecurity
	@EnableConfigurationProperties(SecurityProperties.class)
	protected static class TestSecurityConfiguration {

	}

	@Configuration
	static class UserConfig {

		@Bean
		public MapReactiveUserDetailsService userDetailsService() {
			UserDetails foo = User.withUsername("foo").password("foo").roles("USER")
					.build();
			UserDetails admin = User.withUsername("admin").password("admin")
					.roles("USER", "ADMIN").build();
			return new MapReactiveUserDetailsService(foo, admin);
		}

	}

	@Configuration
	static class AuthenticationManagerConfig {

		@Bean
		public ReactiveAuthenticationManager reactiveAuthenticationManager() {
			return new ReactiveAuthenticationManager() {
				@Override
				public Mono<Authentication> authenticate(Authentication authentication) {
					return null;
				}
			};
		}

	}

	@Configuration
	@Import(TestSecurityConfiguration.class)
	protected static class TestConfigWithPasswordEncoder {

		@Bean
		public PasswordEncoder passwordEncoder() {
			return mock(PasswordEncoder.class);
		}

	}

}
