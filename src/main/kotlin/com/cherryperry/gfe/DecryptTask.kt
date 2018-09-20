package com.cherryperry.gfe

import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFiles
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.incremental.IncrementalTaskInputs
import org.gradle.workers.IsolationMode
import org.gradle.workers.WorkerExecutor
import java.io.File
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.SecretKey
import javax.inject.Inject

open class DecryptTask @Inject constructor(
    private val workerExecutor: WorkerExecutor
) : BaseTask() {

    public override val encryptedFiles: Iterable<File>
        @InputFiles @SkipWhenEmpty get() = super.encryptedFiles

    public override val plainFiles: Iterable<File>
        @OutputFiles get() = super.plainFiles

    init {
        description = "Decrypts all encrypted files from configuration if they exist"
    }

    @TaskAction
    fun decrypt(incrementalTaskInputs: IncrementalTaskInputs) {
        if (incrementalTaskInputs.isIncremental) {
            logger.info("Input is incremental")
            incrementalTaskInputs.outOfDate {
                logger.info("Out of date: ${it.file}")
                val encryptedFile = it.file
                val index = encryptedFiles.indexOf(encryptedFile)
                val plainFile = plainFiles.asSequence().filterIndexed { i, _ -> i == index }.first()
                enqueueDecryptionRunnable(key, encryptedFile, plainFile)
            }
            incrementalTaskInputs.removed {
                // Do nothing on file removal, user must delete decrypted files by himself
            }
        } else {
            logger.info("Input is not incremental")
            plainFiles.zip(encryptedFiles).forEach { (plainFile, encryptedFile) ->
                enqueueDecryptionRunnable(key, encryptedFile, plainFile)
            }
        }
    }

    private fun enqueueDecryptionRunnable(key: SecretKey, encryptedFile: File, plainFile: File) {
        workerExecutor.submit(DecryptTaskRunnable::class.java) { config ->
            config.isolationMode = IsolationMode.NONE
            config.params(key, encryptedFile, plainFile)
        }
    }

    class DecryptTaskRunnable @Inject constructor(
        private val key: SecretKey,
        private val encryptedFile: File,
        private val plainFile: File
    ) : Runnable {

        private val logger: Logger = Logging.getLogger(DecryptTaskRunnable::class.java)

        override fun run() {
            logger.info("Decrypting file: ${encryptedFile.absolutePath}")
            if (!encryptedFile.exists() || !encryptedFile.canRead()) {
                logger.error("${encryptedFile.name} does not exist or can't be read")
                return
            }
            val fileInputStream = encryptedFile.inputStream()
            val ivSize = fileInputStream.read()
            val iv = ByteArray(ivSize)
            fileInputStream.read(iv)
            val cipher = createCipher(Cipher.DECRYPT_MODE, key, iv)
            CipherInputStream(fileInputStream, cipher).use { cipherInputStream ->
                plainFile.parentFile.mkdirs()
                plainFile.outputStream().use { fileOutputStream ->
                    cipherInputStream.copyTo(fileOutputStream)
                }
            }
            logger.info("Decrypted file: ${plainFile.absolutePath}")
        }
    }
}
