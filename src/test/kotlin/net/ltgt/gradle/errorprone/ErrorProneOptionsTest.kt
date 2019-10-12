package net.ltgt.gradle.errorprone

import com.google.common.truth.Truth.assertThat
import com.google.errorprone.ErrorProneOptions.Severity
import com.google.errorprone.InvalidCommandLineOptionException
import org.gradle.api.InvalidUserDataException
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ProviderFactory
import org.gradle.kotlin.dsl.property
import org.gradle.process.CommandLineArgumentProvider
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert.fail
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ErrorProneOptionsTest {
    companion object {
        @JvmField @ClassRule
        val projectDir = TemporaryFolder()

        lateinit var objects: ObjectFactory
        lateinit var providers: ProviderFactory

        @JvmStatic @BeforeClass
        fun setup() {
            ProjectBuilder.builder().withProjectDir(projectDir.root).build().let { project ->
                objects = project.objects
                providers = project.providers
            }
        }
    }

    @Test
    fun `generates correct error prone options`() {
        doTestOptions { disableAllChecks.set(true) }
        doTestOptions { allErrorsAsWarnings.set(true) }
        doTestOptions { allDisabledChecksAsWarnings.set(true) }
        doTestOptions { disableWarningsInGeneratedCode.set(true) }
        doTestOptions { ignoreUnknownCheckNames.set(true) }
        doTestOptions { ignoreSuppressionAnnotations.set(true) }
        doTestOptions { isCompilingTestOnlyCode.set(true) }
        doTestOptions { excludedPaths.set(".*/build/generated/.*") }
        doTestOptions { enable("ArrayEquals") }
        doTestOptions { disable("ArrayEquals") }
        doTestOptions { warn("ArrayEquals") }
        doTestOptions { error("ArrayEquals") }
        doTestOptions { check("ArrayEquals" to CheckSeverity.OFF) }
        doTestOptions { check("ArrayEquals", CheckSeverity.WARN) }
        doTestOptions { checks.put("ArrayEquals", CheckSeverity.ERROR) }
        doTestOptions { checks.set(mutableMapOf("ArrayEquals" to CheckSeverity.DEFAULT)) }
        doTestOptions { option("Foo") }
        doTestOptions { option("Foo", "Bar") }
        doTestOptions { checkOptions.put("Foo", "Bar") }
        doTestOptions { checkOptions.set(mutableMapOf("Foo" to "Bar")) }

        doTestOptions {
            disableAllChecks.set(true)
            allErrorsAsWarnings.set(true)
            allDisabledChecksAsWarnings.set(true)
            disableWarningsInGeneratedCode.set(true)
            ignoreUnknownCheckNames.set(true)
            ignoreSuppressionAnnotations.set(true)
            isCompilingTestOnlyCode.set(true)
            excludedPaths.set(".*/build/generated/.*")
            enable("BetaApi")
            check("NullAway", CheckSeverity.ERROR)
            option("Foo")
            option("NullAway:AnnotatedPackages", "net.ltgt.gradle.errorprone")
        }
    }

    private fun doTestOptions(configure: ErrorProneOptions.() -> Unit) {
        val options = ErrorProneOptions(objects).apply(configure)
        val parsedOptions = parseOptions(options)
        assertOptionsEqual(options, parsedOptions)
    }

    @Test
    fun `correctly passes free arguments`() {
        // We cannot test arguments that are not yet covered, and couldn't check patching options
        // (due to class visibility issue), so we're testing equivalence between free-form args
        // vs. args generated by flags (that we already test above on their own)
        doTestOptions({ errorproneArgs.add("-XepDisableAllChecks") }, { disableAllChecks.set(true) })

        doTestOptions(
            { errorproneArgs.set(mutableListOf("-XepDisableAllChecks", "-Xep:BetaApi")) },
            { disableAllChecks.set(true); enable("BetaApi") }
        )

        doTestOptions(
            {
                errorproneArgumentProviders.add(
                    CommandLineArgumentProvider {
                        listOf(
                            "-Xep:NullAway:ERROR",
                            "-XepOpt:NullAway:AnnotatedPackages=net.ltgt.gradle.errorprone"
                        )
                    }
                )
            },
            {
                check("NullAway", CheckSeverity.ERROR)
                option("NullAway:AnnotatedPackages", "net.ltgt.gradle.errorprone")
            }
        )
    }

    @Test
    fun `correctly allows lazy configuration`() {
        doTestOptions(
            {
                check("NullAway", isCompilingTestOnlyCode.map { if (it) CheckSeverity.WARN else CheckSeverity.ERROR })
            },
            {
                error("NullAway")
            }
        )

        doTestOptions(
            {
                check("NullAway", isCompilingTestOnlyCode.map { if (it) CheckSeverity.WARN else CheckSeverity.ERROR })
                isCompilingTestOnlyCode.set(providers.provider { true })
            },
            {
                isCompilingTestOnlyCode.set(true)
                warn("NullAway")
            }
        )

        doTestOptions(
            {
                val annotatedPackages = objects.property<String>()
                option("NullAway:AnnotatedPackages", annotatedPackages)
                annotatedPackages.set(providers.provider { "net.ltgt.gradle.errorprone" })
            },
            {
                option("NullAway:AnnotatedPackages", "net.ltgt.gradle.errorprone")
            }
        )
    }

    private fun doTestOptions(configure: ErrorProneOptions.() -> Unit, reference: ErrorProneOptions.() -> Unit) {
        val options = ErrorProneOptions(objects).apply(reference)
        val parsedOptions = parseOptions(ErrorProneOptions(objects).apply(configure))
        assertOptionsEqual(options, parsedOptions)
    }

    @Test
    fun `rejects spaces`() {
        doTestSpaces("-XepExcludedPaths:") {
            excludedPaths.set("/home/user/My Projects/project-name/build/generated sources/.*")
        }
        doTestSpaces("-Xep:") { enable("Foo Bar") }
        doTestSpaces("-XepOpt:") { option("Foo Bar") }
        doTestSpaces("-XepOpt:") { option("Foo", "Bar Baz") }
        doTestSpaces("-Xep:Foo -Xep:Bar") { errorproneArgs.add("-Xep:Foo -Xep:Bar") }
        doTestSpaces("-Xep:Foo -Xep:Bar") {
            errorproneArgumentProviders.add(CommandLineArgumentProvider { listOf("-Xep:Foo -Xep:Bar") })
        }
    }

    private fun doTestSpaces(argPrefix: String, configure: ErrorProneOptions.() -> Unit) {
        try {
            ErrorProneOptions(objects).apply(configure).toString()
            fail("Should have thrown")
        } catch (e: InvalidUserDataException) {
            assertThat(e).hasMessageThat().startsWith("""Error Prone options cannot contain white space: "$argPrefix""")
        }
    }

    @Test
    fun `rejects colon in check name`() {
        try {
            ErrorProneOptions(objects).apply({ enable("ArrayEquals:OFF") }).toString()
            fail("Should have thrown")
        } catch (e: InvalidUserDataException) {
            assertThat(e).hasMessageThat()
                .isEqualTo("""Error Prone check name cannot contain a colon (":"): "ArrayEquals:OFF".""")
        }

        // Won't analyze free-form arguments, but those should be caught (later) by argument parsing
        // This test asserts that we're not being too restrictive, and only try to fail early.
        try {
            parseOptions(
                ErrorProneOptions(objects).apply {
                    ignoreUnknownCheckNames.set(true)
                    errorproneArgs.add("-Xep:Foo:Bar")
                }
            )
            fail("Should have thrown")
        } catch (ignore: InvalidCommandLineOptionException) {}
    }

    private fun parseOptions(options: ErrorProneOptions) =
        com.google.errorprone.ErrorProneOptions.processArgs(splitArgs(options.toString()))

    // This is how JavaC "parses" the -Xplugin: values: https://git.io/vx8yI
    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    private fun splitArgs(args: String): Array<String> = (args as java.lang.String).split("""\s+""")

    private fun assertOptionsEqual(
        options: ErrorProneOptions,
        parsedOptions: com.google.errorprone.ErrorProneOptions
    ) {
        assertThat(parsedOptions.isDisableAllChecks).isEqualTo(options.disableAllChecks.get())
        assertThat(parsedOptions.isDropErrorsToWarnings).isEqualTo(options.allErrorsAsWarnings.get())
        assertThat(parsedOptions.isEnableAllChecksAsWarnings).isEqualTo(options.allDisabledChecksAsWarnings.get())
        assertThat(parsedOptions.disableWarningsInGeneratedCode()).isEqualTo(options.disableWarningsInGeneratedCode.get())
        assertThat(parsedOptions.ignoreUnknownChecks()).isEqualTo(options.ignoreUnknownCheckNames.get())
        assertThat(parsedOptions.isIgnoreSuppressionAnnotations).isEqualTo(options.ignoreSuppressionAnnotations.get())
        assertThat(parsedOptions.isTestOnlyTarget).isEqualTo(options.isCompilingTestOnlyCode.get())
        assertThat(parsedOptions.excludedPattern?.pattern()).isEqualTo(options.excludedPaths.orNull)
        assertThat(parsedOptions.severityMap).containsExactlyEntriesIn(options.checks.get().mapValues { it.value.toSeverity() })
        assertThat(parsedOptions.flags.flagsMap).containsExactlyEntriesIn(options.checkOptions.get())
        assertThat(parsedOptions.remainingArgs).isEmpty()
    }

    private fun CheckSeverity.toSeverity(): Severity =
        when (this) {
            CheckSeverity.DEFAULT -> Severity.DEFAULT
            CheckSeverity.OFF -> Severity.OFF
            CheckSeverity.WARN -> Severity.WARN
            CheckSeverity.ERROR -> Severity.ERROR
            else -> throw AssertionError()
        }
}
