package com.cherryperry.gfe

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.Callable

/**
 * Test valid configuration of input and output properties of task.
 */
class FileEncryptTaskConfigurationTest {

    companion object {
        private const val PASSWORD = "password"
    }

    @Rule
    @JvmField
    var temporaryFolder = TemporaryFolder()

    private lateinit var project: Project
    private lateinit var extension: FileEncryptPluginExtension
    private lateinit var encryptTask: EncryptTask
    private lateinit var decryptTask: DecryptTask
    private lateinit var checkGitTask: CheckGitIgnoreTask

    @Before
    fun before() {
        project = ProjectBuilder.builder().withProjectDir(temporaryFolder.root).build()
        project.pluginManager.apply(PLUGIN_NAME)
        extension = project.extensions.getByType(FileEncryptPluginExtension::class.java)
        extension.passwordProvider = Callable { PASSWORD.toCharArray() }
        encryptTask = project.tasks.getByName(FileEncryptPlugin.TASK_ENCRYPT_NAME) as EncryptTask
        decryptTask = project.tasks.getByName(FileEncryptPlugin.TASK_DECRYPT_NAME) as DecryptTask
        checkGitTask = project.tasks.getByName(FileEncryptPlugin.TASK_GIT_IGNORE_NAME) as CheckGitIgnoreTask
    }

    @Test
    fun testEncryptTaskConfigurationEmpty() {
        extension.files.setFrom()
        val inputs = encryptTask.plainFiles.toList()
        Assert.assertEquals(0, inputs.size)
        val outputs = encryptTask.encryptedFiles.toList()
        Assert.assertEquals(0, outputs.size)
    }

    @Test
    fun testEncryptTaskConfigurationSingle() {
        extension.files.setFrom("1.txt")
        val inputs = encryptTask.plainFiles.toList()
        Assert.assertEquals(1, inputs.size)
        Assert.assertTrue(inputs[0].endsWith("1.txt"))
        val outputs = encryptTask.encryptedFiles.toList()
        Assert.assertEquals(1, outputs.size)
        Assert.assertTrue(outputs[0].endsWith("1.txt.${FileNameTransformer.EXTENSION}"))
    }

    @Test
    fun testEncryptTaskConfigurationMultiple() {
        extension.files.setFrom("1.txt", "2.txt")
        val inputs = encryptTask.plainFiles.toList()
        Assert.assertEquals(2, inputs.size)
        Assert.assertTrue(inputs[0].endsWith("1.txt"))
        Assert.assertTrue(inputs[1].endsWith("2.txt"))
        val outputs = encryptTask.encryptedFiles.toList()
        Assert.assertEquals(2, outputs.size)
        Assert.assertTrue(outputs[0].endsWith("1.txt.${FileNameTransformer.EXTENSION}"))
        Assert.assertTrue(outputs[1].endsWith("2.txt.${FileNameTransformer.EXTENSION}"))
    }

    @Test
    fun testEncryptTaskConfigurationMapping() {
        extension.files.setFrom("1.txt", "2.txt")
        extension.mapping = mapOf("2.txt" to "3.txt")
        val inputs = encryptTask.plainFiles.toList()
        Assert.assertEquals(2, inputs.size)
        Assert.assertTrue(inputs[0].endsWith("1.txt"))
        Assert.assertTrue(inputs[1].endsWith("2.txt"))
        val outputs = encryptTask.encryptedFiles.toList()
        Assert.assertEquals(2, outputs.size)
        Assert.assertTrue(outputs[0].endsWith("1.txt.${FileNameTransformer.EXTENSION}"))
        Assert.assertTrue(outputs[1].endsWith("3.txt.${FileNameTransformer.EXTENSION}"))
    }

    @Test
    fun testDecryptTaskConfigurationEmpty() {
        extension.files.setFrom()
        val inputs = decryptTask.encryptedFiles.toList()
        Assert.assertEquals(0, inputs.size)
        val outputs = encryptTask.plainFiles.toList()
        Assert.assertEquals(0, outputs.size)
    }

    @Test
    fun testDecryptTaskConfigurationSingle() {
        extension.files.setFrom("1.txt")
        val inputs = encryptTask.encryptedFiles.toList()
        Assert.assertEquals(1, inputs.size)
        Assert.assertTrue(inputs[0].endsWith("1.txt.${FileNameTransformer.EXTENSION}"))
        val outputs = encryptTask.plainFiles.toList()
        Assert.assertEquals(1, outputs.size)
        Assert.assertTrue(outputs[0].endsWith("1.txt"))
    }

    @Test
    fun testDecryptTaskConfigurationMultiple() {
        extension.files.setFrom("1.txt", "2.txt")
        val inputs = encryptTask.encryptedFiles.toList()
        Assert.assertEquals(2, inputs.size)
        Assert.assertTrue(inputs[0].endsWith("1.txt.${FileNameTransformer.EXTENSION}"))
        Assert.assertTrue(inputs[1].endsWith("2.txt.${FileNameTransformer.EXTENSION}"))
        val outputs = encryptTask.plainFiles.toList()
        Assert.assertEquals(2, outputs.size)
        Assert.assertTrue(outputs[0].endsWith("1.txt"))
        Assert.assertTrue(outputs[1].endsWith("2.txt"))
    }

    @Test
    fun testDecryptTaskConfigurationMapping() {
        extension.files.setFrom("1.txt", "2.txt")
        extension.mapping = mapOf("2.txt" to "3.txt")
        val inputs = encryptTask.encryptedFiles.toList()
        Assert.assertEquals(2, inputs.size)
        Assert.assertTrue(inputs[0].endsWith("1.txt.${FileNameTransformer.EXTENSION}"))
        Assert.assertTrue(inputs[1].endsWith("3.txt.${FileNameTransformer.EXTENSION}"))
        val outputs = encryptTask.plainFiles.toList()
        Assert.assertEquals(2, outputs.size)
        Assert.assertTrue(outputs[0].endsWith("1.txt"))
        Assert.assertTrue(outputs[1].endsWith("2.txt"))
    }
}
