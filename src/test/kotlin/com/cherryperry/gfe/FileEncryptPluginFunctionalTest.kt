package com.cherryperry.gfe

import org.eclipse.jgit.api.Git
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.BuildTask
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.gradle.testkit.runner.UnexpectedBuildFailure
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

@RunWith(Parameterized::class)
class FileEncryptPluginFunctionalTest(
    private val gradleVersion: String
) {

    companion object {
        const val EMPTY_BUILD_GRADLE = "plugins { id 'com.cherryperry.gradle-file-encrypt' }"
        const val CONTENT_1 = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua."
        const val CONTENT_2 = "Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat."
        const val PASSWORD = "password"
        const val PASSWORD_2 = "password2"
        const val FILE_BUILD_GRADLE = "build.gradle"
        const val FILE_GRADLE_PROPERTIES = "gradle.properties"

        // test all supported gradle versions
        @JvmStatic
        @Parameterized.Parameters(name = "Gradle version: {0}")
        fun gradleVersions(): Collection<Array<Any>> = arrayListOf(
            arrayOf<Any>("4.0.2"),
            arrayOf<Any>("4.1"),
            arrayOf<Any>("4.2.1"),
            arrayOf<Any>("4.3.1"),
            arrayOf<Any>("4.4.1"),
            arrayOf<Any>("4.5.1"),
            arrayOf<Any>("4.6"),
            arrayOf<Any>("4.7"),
            arrayOf<Any>("4.8.1"),
            arrayOf<Any>("4.9"),
            arrayOf<Any>("4.10.3"),
            arrayOf<Any>("5.0"),
            arrayOf<Any>("5.1.1"),
            arrayOf<Any>("5.2.1"),
            arrayOf<Any>("5.3.1"),
            arrayOf<Any>("5.4.1"),
            arrayOf<Any>("5.5.1"),
            arrayOf<Any>("6.0.1"),
            arrayOf<Any>("6.1.1"),
            arrayOf<Any>("6.2.2")
        )
    }

    @Rule
    @JvmField
    val temporaryFolder = TemporaryFolder()

    @Rule
    @JvmField
    val expectedException: ExpectedException = ExpectedException.none()

    private fun createRunner(
        buildGradleContent: String = EMPTY_BUILD_GRADLE,
        vararg args: String
    ): BuildResult {
        val buildGradle = File(temporaryFolder.root, FILE_BUILD_GRADLE)
        buildGradle.writeText(buildGradleContent)
        val gradleProperties = File(temporaryFolder.root, FILE_GRADLE_PROPERTIES)
        javaClass.classLoader.getResourceAsStream(FILE_GRADLE_PROPERTIES).use { inputStream ->
            gradleProperties.outputStream().use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }
        return GradleRunner.create()
            .withPluginClasspath()
            .withProjectDir(temporaryFolder.root)
            .withGradleVersion(gradleVersion)
            .withArguments(mutableListOf("--stacktrace") + args)
            .forwardStdOutput(System.out.writer())
            .forwardStdError(System.err.writer())
            .build()
    }

    private fun buildGradleConfigurationWithFiles(
        file: File,
        password: String = PASSWORD,
        mappedFile: File? = null
    ): String {
        val mapping = if (mappedFile != null)
            "mapping = ['${file.relativeTo(temporaryFolder.root).linuxPath}':'${mappedFile.relativeTo(temporaryFolder.root).linuxPath}']"
        else
            ""
        return """
            $EMPTY_BUILD_GRADLE
            gradleFileEncrypt
            {
                files.from('${file.relativeTo(temporaryFolder.root).linuxPath}')
                $mapping
                passwordProvider { return '$password'.toCharArray() }
            }
        """.trimIndent()
    }

    @Test
    fun testEncryptTaskNoSourceWhenEmpty() {
        // test task without input must be skipped
        createRunner(EMPTY_BUILD_GRADLE, FileEncryptPlugin.TASK_ENCRYPT_NAME).let {
            Assert.assertEquals(TaskOutcome.NO_SOURCE, it[FileEncryptPlugin.TASK_ENCRYPT_NAME].outcome)
        }
    }

    @Test
    fun testDecryptTaskNoSourceWhenEmpty() {
        // test task without input must be skipped
        createRunner(EMPTY_BUILD_GRADLE, FileEncryptPlugin.TASK_DECRYPT_NAME).let {
            Assert.assertEquals(TaskOutcome.NO_SOURCE, it[FileEncryptPlugin.TASK_DECRYPT_NAME].outcome)
        }
    }

    @Test
    fun testEncryptAndDecryptSingleFile() {
        // test tasks work as expected
        val testFile = temporaryFolder.newFile()
        testFile.writeText(CONTENT_1)
        createRunner(buildGradleConfigurationWithFiles(testFile), FileEncryptPlugin.TASK_ENCRYPT_NAME).let {
            Assert.assertEquals(TaskOutcome.SUCCESS, it[FileEncryptPlugin.TASK_ENCRYPT_NAME].outcome)
        }
        testFile.delete()
        createRunner(buildGradleConfigurationWithFiles(testFile), FileEncryptPlugin.TASK_DECRYPT_NAME).let {
            Assert.assertEquals(TaskOutcome.SUCCESS, it[FileEncryptPlugin.TASK_DECRYPT_NAME].outcome)
        }
        Assert.assertEquals(CONTENT_1, testFile.readText())
    }

    @Test
    fun testEncryptAndDecryptSingleFileCache() {
        // test gradle task cache support without file changes
        val testFile = temporaryFolder.newFile()
        testFile.writeText(CONTENT_1)
        createRunner(buildGradleConfigurationWithFiles(testFile), FileEncryptPlugin.TASK_ENCRYPT_NAME).let {
            Assert.assertEquals(TaskOutcome.SUCCESS, it[FileEncryptPlugin.TASK_ENCRYPT_NAME].outcome)
        }
        // second call should be skipped due cache
        createRunner(buildGradleConfigurationWithFiles(testFile), FileEncryptPlugin.TASK_ENCRYPT_NAME).let {
            Assert.assertEquals(TaskOutcome.UP_TO_DATE, it[FileEncryptPlugin.TASK_ENCRYPT_NAME].outcome)
        }
        createRunner(buildGradleConfigurationWithFiles(testFile), FileEncryptPlugin.TASK_DECRYPT_NAME).let {
            Assert.assertEquals(TaskOutcome.SUCCESS, it[FileEncryptPlugin.TASK_DECRYPT_NAME].outcome)
        }
        // second call should be skipped due cache
        createRunner(buildGradleConfigurationWithFiles(testFile), FileEncryptPlugin.TASK_DECRYPT_NAME).let {
            Assert.assertEquals(TaskOutcome.UP_TO_DATE, it[FileEncryptPlugin.TASK_DECRYPT_NAME].outcome)
        }
    }

    @Test
    fun testEncryptAndDecryptSingleFileCacheInvalidate() {
        // test gradle task cache support with file changes
        val testFile = temporaryFolder.newFile()
        testFile.writeText(CONTENT_1)
        createRunner(buildGradleConfigurationWithFiles(testFile), FileEncryptPlugin.TASK_ENCRYPT_NAME).let {
            Assert.assertEquals(TaskOutcome.SUCCESS, it[FileEncryptPlugin.TASK_ENCRYPT_NAME].outcome)
        }
        // invalidate encryption source
        testFile.writeText(CONTENT_2)
        createRunner(buildGradleConfigurationWithFiles(testFile), FileEncryptPlugin.TASK_ENCRYPT_NAME).let {
            Assert.assertEquals(TaskOutcome.SUCCESS, it[FileEncryptPlugin.TASK_ENCRYPT_NAME].outcome)
        }
        testFile.delete()
        createRunner(buildGradleConfigurationWithFiles(testFile), FileEncryptPlugin.TASK_DECRYPT_NAME).let {
            Assert.assertEquals(TaskOutcome.SUCCESS, it[FileEncryptPlugin.TASK_DECRYPT_NAME].outcome)
        }
        Assert.assertEquals(CONTENT_2, testFile.readText())
        // invalidate decryption result
        testFile.writeText(CONTENT_1)
        createRunner(buildGradleConfigurationWithFiles(testFile), FileEncryptPlugin.TASK_DECRYPT_NAME).let {
            Assert.assertEquals(TaskOutcome.SUCCESS, it[FileEncryptPlugin.TASK_DECRYPT_NAME].outcome)
        }
        Assert.assertEquals(CONTENT_2, testFile.readText())
    }

    @Test
    fun testEncryptSameResultTwiceNoCache() {
        // 2 passes of encryption task should produce same result
        val testFile = temporaryFolder.newFile()
        testFile.writeText(CONTENT_1)
        createRunner(buildGradleConfigurationWithFiles(testFile), FileEncryptPlugin.TASK_ENCRYPT_NAME).let {
            Assert.assertEquals(TaskOutcome.SUCCESS, it[FileEncryptPlugin.TASK_ENCRYPT_NAME].outcome)
        }
        val resultFile = FileNameTransformer.encryptedFileFromFile(testFile)
        val data = resultFile.readBytes()
        // change output file to invalidate cache
        resultFile.writeBytes(data.copyOf(data.size - 1))
        // generate again
        createRunner(buildGradleConfigurationWithFiles(testFile), FileEncryptPlugin.TASK_ENCRYPT_NAME).let {
            Assert.assertEquals(TaskOutcome.SUCCESS, it[FileEncryptPlugin.TASK_ENCRYPT_NAME].outcome)
        }
        Assert.assertArrayEquals(data, resultFile.readBytes())
    }

    @Test
    fun testEncryptSameIV() {
        // encrypt task must use same iv, if encryption target exists
        val testFile = temporaryFolder.newFile()
        testFile.writeText(CONTENT_1)
        createRunner(buildGradleConfigurationWithFiles(testFile), FileEncryptPlugin.TASK_ENCRYPT_NAME).let {
            Assert.assertEquals(TaskOutcome.SUCCESS, it[FileEncryptPlugin.TASK_ENCRYPT_NAME].outcome)
        }
        val resultFile = FileNameTransformer.encryptedFileFromFile(testFile)
        val fileStart1 = resultFile.inputStream().use {
            val array = ByteArray(17)
            it.read(array)
            array
        }
        testFile.writeText(CONTENT_2)
        createRunner(buildGradleConfigurationWithFiles(testFile), FileEncryptPlugin.TASK_ENCRYPT_NAME).let {
            Assert.assertEquals(TaskOutcome.SUCCESS, it[FileEncryptPlugin.TASK_ENCRYPT_NAME].outcome)
        }
        val fileStart2 = resultFile.inputStream().use {
            val array = ByteArray(17)
            it.read(array)
            array
        }
        Assert.assertArrayEquals(fileStart1, fileStart2)
    }

    @Test
    fun testPasswordChangeInvalidateTask() {
        val testFile = temporaryFolder.newFile()
        testFile.writeText(CONTENT_1)
        createRunner(buildGradleConfigurationWithFiles(testFile), FileEncryptPlugin.TASK_ENCRYPT_NAME).let {
            Assert.assertEquals(TaskOutcome.SUCCESS, it[FileEncryptPlugin.TASK_ENCRYPT_NAME].outcome)
        }
        // change password and run again
        createRunner(buildGradleConfigurationWithFiles(testFile, PASSWORD_2), FileEncryptPlugin.TASK_ENCRYPT_NAME).let {
            Assert.assertEquals(TaskOutcome.SUCCESS, it[FileEncryptPlugin.TASK_ENCRYPT_NAME].outcome)
        }
        // do not change password and check cache
        createRunner(buildGradleConfigurationWithFiles(testFile, PASSWORD_2), FileEncryptPlugin.TASK_ENCRYPT_NAME).let {
            Assert.assertEquals(TaskOutcome.UP_TO_DATE, it[FileEncryptPlugin.TASK_ENCRYPT_NAME].outcome)
        }
    }

    @Test
    fun testEncryptAndDecryptMappedFile() {
        // test file mapping
        val testFile = temporaryFolder.newFile()
        testFile.writeText(CONTENT_1)
        // encrypted content should be here
        val mappedFile = temporaryFolder.newFile()
        mappedFile.delete()
        val encryptedMappedFile = FileNameTransformer.encryptedFileFromFile(mappedFile)
        createRunner(buildGradleConfigurationWithFiles(testFile, mappedFile = mappedFile), FileEncryptPlugin.TASK_ENCRYPT_NAME).let {
            Assert.assertEquals(TaskOutcome.SUCCESS, it[FileEncryptPlugin.TASK_ENCRYPT_NAME].outcome)
        }
        testFile.delete()
        // was file at least written?
        Assert.assertTrue(encryptedMappedFile.exists())
        Assert.assertTrue(encryptedMappedFile.length() > 0)
        // decrypt mapped file
        createRunner(buildGradleConfigurationWithFiles(testFile, mappedFile = mappedFile), FileEncryptPlugin.TASK_DECRYPT_NAME).let {
            Assert.assertEquals(TaskOutcome.SUCCESS, it[FileEncryptPlugin.TASK_DECRYPT_NAME].outcome)
        }
        Assert.assertEquals(CONTENT_1, testFile.readText())
    }

    @Test
    fun testGitCheckIsIgnoredIfNoInputs() {
        // test task without input must be skipped
        createRunner(EMPTY_BUILD_GRADLE, FileEncryptPlugin.TASK_GIT_IGNORE_NAME).let {
            Assert.assertEquals(TaskOutcome.NO_SOURCE, it[FileEncryptPlugin.TASK_GIT_IGNORE_NAME].outcome)
        }
    }

    @Test
    fun testFileIsIgnoredByRootGitIgnore() {
        // test single file in ignore list is ignored
        val testFile = temporaryFolder.newFile()
        testFile.writeText(CONTENT_1)
        val gitIgnoreFile = temporaryFolder.newFile(CheckGitIgnoreTask.FILE_GIT_IGNORE)
        gitIgnoreFile.appendText(testFile.name)
        Git.init().setDirectory(temporaryFolder.root).call()
        createRunner(buildGradleConfigurationWithFiles(testFile), FileEncryptPlugin.TASK_GIT_IGNORE_NAME).let {
            Assert.assertEquals(TaskOutcome.SUCCESS, it[FileEncryptPlugin.TASK_GIT_IGNORE_NAME].outcome)
        }
    }

    @Test
    fun testFileIsIgnoredBySubDirectoryGitIgnore() {
        // test single file in ignore list in subdirectory is ignored
        val testDirectory = TemporaryFolder(temporaryFolder.newFolder())
        testDirectory.create()
        val testFile = testDirectory.newFile()
        val gitIgnoreFile = testDirectory.newFile(CheckGitIgnoreTask.FILE_GIT_IGNORE)
        gitIgnoreFile.appendText(testFile.name)
        Git.init().setDirectory(temporaryFolder.root).call()
        createRunner(buildGradleConfigurationWithFiles(testFile), FileEncryptPlugin.TASK_GIT_IGNORE_NAME).let {
            Assert.assertEquals(TaskOutcome.SUCCESS, it[FileEncryptPlugin.TASK_GIT_IGNORE_NAME].outcome)
        }
    }

    @Test
    fun testFileIsNotIgnoredByAnyGitIgnore() {
        // test task is failed when file is not ignored
        val testFile = temporaryFolder.newFile()
        temporaryFolder.newFile(CheckGitIgnoreTask.FILE_GIT_IGNORE)
        Git.init().setDirectory(temporaryFolder.root).call()
        // don't know why exception instead of TaskOutcome.FAILED
        expectedException.expect(UnexpectedBuildFailure::class.java)
        createRunner(buildGradleConfigurationWithFiles(testFile), FileEncryptPlugin.TASK_GIT_IGNORE_NAME)
    }

    @Test
    fun testFileIsNotIgnoredByTemplateGitIgnore() {
        // test task is failed when file is not ignored
        val testFile = temporaryFolder.newFile("file2.plain")
        val gitIgnoreFile = temporaryFolder.newFile(CheckGitIgnoreTask.FILE_GIT_IGNORE)
        gitIgnoreFile.appendText("!*.encrypted")
        Git.init().setDirectory(temporaryFolder.root).call()
        // don't know why exception instead of TaskOutcome.FAILED
        expectedException.expect(UnexpectedBuildFailure::class.java)
        createRunner(buildGradleConfigurationWithFiles(testFile), FileEncryptPlugin.TASK_GIT_IGNORE_NAME)
    }

    private operator fun BuildResult.get(taskName: String): BuildTask = task(":$taskName")!!

    /** Gradle consumes only linux paths in it's configuration files. Fix to run this test on Windows. */
    private val File.linuxPath
        get() = this.path.replace(File.separatorChar, '/')
}
