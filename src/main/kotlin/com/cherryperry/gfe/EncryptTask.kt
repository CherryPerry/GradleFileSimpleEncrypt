package com.cherryperry.gfe

import com.cherryperry.gfe.base.BaseTask
import com.cherryperry.gfe.base.EncryptedFilesAware
import com.cherryperry.gfe.base.EncryptedFilesAwareDelegate
import com.cherryperry.gfe.base.PlainFilesAware
import com.cherryperry.gfe.base.PlainFilesAwareDelegate
import com.cherryperry.gfe.base.SecretKeyAware
import com.cherryperry.gfe.base.SecretKeyAwareDelegate
import org.gradle.api.file.FileCollection
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFiles
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.incremental.IncrementalTaskInputs
import org.gradle.workers.IsolationMode
import org.gradle.workers.WorkerExecutor
import java.io.File
import javax.crypto.Cipher
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKey
import javax.inject.Inject

open class EncryptTask @Inject constructor(
    private val workerExecutor: WorkerExecutor
) : BaseTask(), SecretKeyAware, PlainFilesAware, EncryptedFilesAware {

    @get:Input
    override val key: SecretKey by SecretKeyAwareDelegate()

    @get:InputFiles
    @get:SkipWhenEmpty
    override val plainFiles: FileCollection by PlainFilesAwareDelegate()

    @get:OutputFiles
    override val encryptedFiles: FileCollection by EncryptedFilesAwareDelegate()

    init {
        description = "Encrypts all unencrypted files from configuration if they exist"
    }

    @TaskAction
    open fun encrypt(incrementalTaskInputs: IncrementalTaskInputs) {
        if (incrementalTaskInputs.isIncremental) {
            logger.info("Input is incremental")
            incrementalTaskInputs.outOfDate {
                logger.info("Out of date: ${it.file}")
                val plainFile = it.file
                val index = plainFiles.indexOf(plainFile)
                val encryptedFile = encryptedFiles.asSequence().filterIndexed { i, _ -> i == index }.first()
                enqueueEncryptionRunnable(key, plainFile, encryptedFile)
            }
            incrementalTaskInputs.removed {
                // Do nothing on file removal, user must delete encrypted files by himself
            }
        } else {
            logger.info("Input is not incremental")
            plainFiles.zip(encryptedFiles).forEach { (plainFile, encryptedFile) ->
                enqueueEncryptionRunnable(key, plainFile, encryptedFile)
            }
        }
    }

    private fun enqueueEncryptionRunnable(key: SecretKey, plainFile: File, encryptedFile: File) {
        workerExecutor.submit(EncryptTaskRunnable::class.java) { config ->
            config.isolationMode = IsolationMode.NONE
            config.params(key, plainFile, encryptedFile)
        }
    }

    class EncryptTaskRunnable @Inject constructor(
        private val key: SecretKey,
        private val plainFile: File,
        private val encryptedFile: File
    ) : Runnable {

        private val logger: Logger = Logging.getLogger(EncryptTaskRunnable::class.java)

        override fun run() {
            logger.info("Encrypting file: ${plainFile.absolutePath}")
            if (!plainFile.exists() || !plainFile.canRead()) {
                logger.error("${plainFile.name} does not exist or can't be read")
                return
            }
            val iv = readOrGenerateIv()
            val cipher = createCipher(Cipher.ENCRYPT_MODE, key, iv)
            writeFile(cipher)
            logger.info("Encrypted file: ${encryptedFile.absolutePath}")
        }

        private fun readOrGenerateIv(): ByteArray =
            if (encryptedFile.exists() && encryptedFile.canRead()) {
                // if encrypted file already exists - reuse it's IV
                logger.info("Encrypted file already exists, reuse it's IV")
                encryptedFile.inputStream().use { fileInputStream ->
                    val ivSize = fileInputStream.read()
                    val iv = ByteArray(ivSize)
                    fileInputStream.read(iv)
                    iv
                }
            } else {
                generateIv()
            }

        private fun writeFile(cipher: Cipher) {
            plainFile.inputStream().use { fileInputStream ->
                encryptedFile.parentFile.mkdirs()
                encryptedFile.outputStream().use { fileOutputStream ->
                    fileOutputStream.write(cipher.iv.size)
                    fileOutputStream.write(cipher.iv)
                    CipherOutputStream(fileOutputStream, cipher).use { cipherOutputStream ->
                        fileInputStream.copyTo(cipherOutputStream)
                    }
                }
            }
        }
    }
}
