package net.ltgt.gradle.errorprone

import java.io.File
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.util.GradleVersion
import org.junit.Before
import org.junit.Rule
import org.junit.rules.TemporaryFolder

abstract class AbstractPluginIntegrationTest {

    companion object {
        internal val testGradleVersion = System.getProperty("test.gradle-version", GradleVersion.current().version)

        internal val errorproneVersion = System.getProperty("errorprone.version")!!
        internal val errorproneJavacVersion = System.getProperty("errorprone-javac.version")!!

        internal const val FAILURE_SOURCE_COMPILATION_ERROR = "Failure.java:6: error: [ArrayEquals]"
    }

    @JvmField
    @Rule
    val testProjectDir = TemporaryFolder()

    lateinit var settingsFile: File
    lateinit var buildFile: File

    // TODO: refactor to avoid overriding this in GroovyDslIntegrationTest
    @Before
    open fun setupProject() {
        settingsFile = testProjectDir.newFile("settings.gradle.kts")
        buildFile = testProjectDir.newFile("build.gradle.kts").apply {
            writeText(
                """
                import net.ltgt.gradle.errorprone.*

                """.trimIndent()
            )
        }
    }

    protected fun writeSuccessSource() {
        File(testProjectDir.newFolder("src", "main", "java", "test"), "Success.java").apply {
            createNewFile()
            writeText(
                """
                package test;

                public class Success {
                    // See http://errorprone.info/bugpattern/ArrayEquals
                    @SuppressWarnings("ArrayEquals")
                    public boolean arrayEquals(int[] a, int[] b) {
                        return a.equals(b);
                    }
                }
                """.trimIndent()
            )
        }
    }

    protected fun writeFailureSource() {
        File(testProjectDir.newFolder("src", "main", "java", "test"), "Failure.java").apply {
            createNewFile()
            writeText(
                """
                package test;

                public class Failure {
                    // See http://errorprone.info/bugpattern/ArrayEquals
                    public boolean arrayEquals(int[] a, int[] b) {
                        return a.equals(b);
                    }
                }
                """.trimIndent()
            )
        }
    }

    protected fun writeTimeZoneJava16Source() {
        File(testProjectDir.newFolder("src", "main", "java", "test"), "TimeZoneJava16.java").apply {
            createNewFile()
            writeText(
                """
                package test;

                import java.util.TimeZone;

                public class TimeZoneJava16 {
                    public TimeZone foo() {
                        return TimeZone.getTimeZone("PST");
                    }
                }
                """.trimIndent()
            )
        }
    }

    protected fun buildWithArgs(vararg tasks: String): BuildResult {
        return prepareBuild(*tasks)
            .build()
    }

    protected fun buildWithArgsAndFail(vararg tasks: String): BuildResult {
        return prepareBuild(*tasks)
            .buildAndFail()
    }

    private fun prepareBuild(vararg tasks: String): GradleRunner {
        return GradleRunner.create()
            .withGradleVersion(testGradleVersion)
            .withProjectDir(testProjectDir.root)
            .withPluginClasspath()
            .withArguments(*tasks)
            .forwardOutput()
    }
}
