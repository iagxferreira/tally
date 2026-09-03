package tally;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Proves the build harness itself runs: JUnit 5 discovers tests, AssertJ is on
 * the test classpath, and the toolchain is the Java version the project pins.
 *
 * <p>This is scaffolding, not a domain test. It should be deleted once real
 * domain tests exist.
 */
class BuildHarnessTest {

    @Test
    void compilesAndRunsOnThePinnedToolchain() {
        assertThat(Runtime.version().feature()).isEqualTo(25);
    }
}
