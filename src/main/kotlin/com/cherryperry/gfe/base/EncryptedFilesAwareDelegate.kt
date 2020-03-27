package com.cherryperry.gfe.base

import com.cherryperry.gfe.FileNameTransformer
import org.gradle.api.file.ConfigurableFileCollection
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

class EncryptedFilesAwareDelegate<T> : ReadOnlyProperty<T, ConfigurableFileCollection>
    where T : BaseTask, T : PlainFilesAware {

    override fun getValue(thisRef: T, property: KProperty<*>): ConfigurableFileCollection =
        thisRef.project.files(
            thisRef.extension.files
                .map { original -> thisRef.extension.mapping[original] ?: original }
                .map { thisRef.project.relativePath(it) }
                .map { FileNameTransformer.encryptedRelativePathFromRelativePath(it) }
        )
}
