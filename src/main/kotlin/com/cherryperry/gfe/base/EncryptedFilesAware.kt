package com.cherryperry.gfe.base

import org.gradle.api.file.FileCollection

interface EncryptedFilesAware {

    val encryptedFiles: FileCollection
}
