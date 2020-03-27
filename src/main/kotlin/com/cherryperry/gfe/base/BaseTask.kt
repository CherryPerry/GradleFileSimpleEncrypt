package com.cherryperry.gfe.base

import com.cherryperry.gfe.FileEncryptPluginExtension
import com.cherryperry.gfe.GROUP_NAME
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Internal

/**
 * Base class for encryption tasks.
 * Has encryption key provider and project extension.
 */
abstract class BaseTask : DefaultTask() {

    @get:Internal
    internal val extension: FileEncryptPluginExtension =
        project.extensions.getByType(FileEncryptPluginExtension::class.java)

    init {
        group = GROUP_NAME
    }
}
