package io.searchable.core.infrastructure.lucene;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.ja.JapaneseAnalyzer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises every branch of {@link SudachiAnalyzerFactory#create(String)}:
 * the happy path, the {@link InvocationTargetException} catch, and the
 * fallback to {@link JapaneseAnalyzer} when the Sudachi class is not on
 * the classpath.
 *
 * <p>A test-only stub class at the FQN
 * {@code com.worksap.nlp.lucene.sudachi.ja.SudachiAnalyzer} stands in for
 * the real Sudachi analyzer so that we do not need the actual library +
 * 70&nbsp;MB system dictionary on every test run. The stub's constructor
 * behavior is controlled by the {@code stub.sudachi.mode} system property.
 */
class SudachiAnalyzerFactoryTest {

    @BeforeEach
    @AfterEach
    void clearMode() {
        System.clearProperty("stub.sudachi.mode");
    }

    @Test
    void happyPathInstantiatesSudachiAnalyzer() {
        final Analyzer analyzer = new SudachiAnalyzerFactory().create("ns");
        assertThat(analyzer)
            .isNotNull()
            .isInstanceOf(com.worksap.nlp.lucene.sudachi.ja.SudachiAnalyzer.class);
    }

    @Test
    void constructorFailureWrappedAsIllegalStateException() {
        System.setProperty("stub.sudachi.mode", "throw");
        assertThatThrownBy(() -> new SudachiAnalyzerFactory().create("ns"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Failed to instantiate");
    }

    @Test
    void classNotFoundFallsBackToKuromoji() throws Exception {
        // Build a child classloader that delegates everything to the
        // system loader EXCEPT the Sudachi stub class — for which it
        // throws ClassNotFoundException. The SudachiAnalyzerFactory class
        // is then re-defined in this filtered loader (via raw bytes) so
        // its Class.forName lookup hits the missing-class branch.
        final ClassLoader system = SudachiAnalyzerFactory.class.getClassLoader();
        final ClassLoader filtered = new ClassLoader(system) {
            @Override
            protected Class<?> loadClass(final String name, final boolean resolve)
                    throws ClassNotFoundException {
                if (name.equals("com.worksap.nlp.lucene.sudachi.ja.SudachiAnalyzer")) {
                    throw new ClassNotFoundException(name);
                }
                if (name.equals(SudachiAnalyzerFactory.class.getName())) {
                    // Define the factory class fresh in this loader so its
                    // Class.forName(...) inherits this loader's restriction.
                    final String resource = name.replace('.', '/') + ".class";
                    try (var in = system.getResourceAsStream(resource)) {
                        if (in == null) {
                            throw new ClassNotFoundException(name);
                        }
                        final byte[] bytes = in.readAllBytes();
                        final Class<?> c = defineClass(name, bytes, 0, bytes.length);
                        if (resolve) {
                            resolveClass(c);
                        }
                        return c;
                    } catch (java.io.IOException e) {
                        throw new ClassNotFoundException(name, e);
                    }
                }
                return super.loadClass(name, resolve);
            }
        };
        final Class<?> reloaded = Class.forName(
            SudachiAnalyzerFactory.class.getName(), true, filtered);
        final Object factory = reloaded.getDeclaredConstructor().newInstance();
        final Object analyzer = reloaded.getMethod("create", String.class)
            .invoke(factory, "ns");
        assertThat(analyzer).isInstanceOf(JapaneseAnalyzer.class);
    }
}
