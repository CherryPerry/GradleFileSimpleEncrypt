package com.cherryperry.gfe

import java.io.File

object FileNameTransformer {

    const val EXTENSION = "encrypted"

    fun encryptedRelativePathFromRelativePath(relativePath: String) = "$relativePath.$EXTENSION"

    @Deprecated(
        message = "Do not use absolute paths like this in Gradle",
        replaceWith = ReplaceWith("encryptedRelativePathFromRelativePath")
    )
    fun encryptedFileFromFile(file: File) = File(file.parentFile, "${file.name}.$EXTENSION")

    @Deprecated(
        message = "Do not use absolute paths like this in Gradle",
        replaceWith = ReplaceWith("encryptedRelativePathFromRelativePath")
    )
    fun originalFileFromEncryptedFile(file: File) =
        if (file.extension == EXTENSION) File(file.parentFile, file.nameWithoutExtension) else file
}
