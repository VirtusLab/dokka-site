package com.virtuslab.dokka.site

import org.jetbrains.dokka.DokkaConfiguration
import org.jetbrains.dokka.model.DModule
import org.jetbrains.dokka.plugability.DokkaContext
import org.jetbrains.dokka.plugability.Extension
import org.jetbrains.dokka.plugability.ExtensionBuilder
import org.jetbrains.dokka.plugability.OrderingKind
import org.jetbrains.dokka.transformers.sources.SourceToDocumentableTranslator


// TODO (#39): investigate if scala3doc can live without it
data class SourceSetWrapper(val sourceSet: DokkaConfiguration.DokkaSourceSet) {
    fun toSet(): Set<DokkaConfiguration.DokkaSourceSet> = setOf(sourceSet)
    fun <T> asMap(value: T): Map<DokkaConfiguration.DokkaSourceSet, T> = mapOf(sourceSet to value)
}

// TODO (#39): add fixes to in dokka
abstract class JavaSourceToDocumentableTranslator: SourceToDocumentableTranslator {
    override suspend fun invoke(sourceSet: DokkaConfiguration.DokkaSourceSet, context: DokkaContext): DModule =
        process(sourceSet, context)

    abstract fun process(sourceSet: DokkaConfiguration.DokkaSourceSet, context: DokkaContext): DModule
}

// TODO (#39): fix that in dokka
class ExtensionBuilderEx {
    fun <T: Any> newOrdering(old: ExtensionBuilder<T>, before: Array<Extension<*, *, *>>, after: Array<Extension<*, *, *>>) =
        old.copy(ordering = OrderingKind.ByDsl {
            before(*before)
            after(*after)
        })
}
