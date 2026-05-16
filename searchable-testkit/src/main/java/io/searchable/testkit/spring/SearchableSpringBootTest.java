package io.searchable.testkit.spring;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Meta-annotation bundling the boilerplate shared by Spring Boot integration
 * tests: {@link SpringBootTest}, {@link AutoConfigureMockMvc}, and
 * {@link DirtiesContext}.
 *
 * <p>{@code @TestPropertySource} is intentionally NOT included because each
 * test needs unique paths (data-directory, index-directory, H2 DB name) to
 * avoid cross-test collisions. Tests should declare their own
 * {@code @TestPropertySource} alongside this annotation.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext
public @interface SearchableSpringBootTest {
}
