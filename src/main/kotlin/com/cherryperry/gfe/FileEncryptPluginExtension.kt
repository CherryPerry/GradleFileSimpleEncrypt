package com.cherryperry.gfe

import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import java.util.concurrent.Callable

open class FileEncryptPluginExtension constructor(
    project: Project
) {

    /**
     * Files to encrypt.
     */
    val files: ConfigurableFileCollection = project.files()

    /**
     * File mapping between plain and encrypted versions of file.
     * Key is plain file and value is encrypted.
     * Encrypted part SHOULD NOT contain `.encrypted` extension.
     *
     * Sample: `[ 'secrets.txt' : 'encrypted/1' ]`
     * will create 'encrypted/1.encrypted' file from `secrets.txt`.
     */
    var mapping: Map<Any, Any> = emptyMap()

    /**
     * Custom password provider to encrypt and decrypt files.
     * Use it, if you don't like provided methods of setting password.
     */
    var passwordProvider: Callable<CharArray>? = null
}
