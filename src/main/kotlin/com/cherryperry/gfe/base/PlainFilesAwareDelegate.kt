package com.cherryperry.gfe.base

import org.gradle.api.file.FileCollection
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

class PlainFilesAwareDelegate : ReadOnlyProperty<BaseTask, FileCollection> {

    override fun getValue(thisRef: BaseTask, property: KProperty<*>): FileCollection =
        thisRef.extension.files
}
