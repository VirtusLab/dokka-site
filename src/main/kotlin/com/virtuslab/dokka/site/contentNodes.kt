package com.virtuslab.dokka.site

import org.jetbrains.dokka.model.DisplaySourceSet
import org.jetbrains.dokka.model.properties.PropertyContainer
import org.jetbrains.dokka.pages.*

data class PartiallyRenderedContent(
    val page: PreResolvedPage,
    override val children: List<ContentNode>,
    override val dci: DCI,
    override val sourceSets: Set<DisplaySourceSet>,
    override val style: Set<Style> = emptySet(),
    override val extra: PropertyContainer<ContentNode> = PropertyContainer.empty()
) : ContentNode {
    override fun hasAnyContent(): Boolean = children.find { hasAnyContent() } != null

    override fun withNewExtras(newExtras: PropertyContainer<ContentNode>): ContentNode =
        copy(extra = extra)

    override fun withSourceSets(sourceSets: Set<DisplaySourceSet>): ContentNode =
        copy(sourceSets = sourceSets)

    val allResources: List<String> by lazy {
        page.render("").resources
    }
}