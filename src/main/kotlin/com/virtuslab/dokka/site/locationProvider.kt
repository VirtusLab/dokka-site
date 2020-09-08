package com.virtuslab.dokka.site

import org.jetbrains.dokka.base.resolvers.local.DokkaLocationProvider
import org.jetbrains.dokka.base.resolvers.local.LocationProvider
import org.jetbrains.dokka.base.resolvers.local.LocationProviderFactory
import org.jetbrains.dokka.pages.PageNode
import org.jetbrains.dokka.pages.RootPageNode
import org.jetbrains.dokka.plugability.DokkaContext

class StaticSiteLocationProviderFactory(private val ctx: DokkaContext) : LocationProviderFactory {
    override fun getLocationProvider(pageNode: RootPageNode): LocationProvider {
        return StaticSiteLocationProvider(ctx, pageNode)
    }
}

class StaticSiteLocationProvider(ctx: DokkaContext, pageNode: RootPageNode) : DokkaLocationProvider(pageNode, ctx) {
    override fun pathTo(node: PageNode, context: PageNode?): String =
        if (node is BaseStaticSiteProcessor.StaticPageNode)
            if (node.dri.contains(docsRootDRI)) "index"
            else {
                // replace title with original markdown file name
                val original = super.pathTo(node, context)
                val paths = original.split("/")
                val fileName = node.loadedTemplate.file.name
                (paths.dropLast(1) + listOf(fileName)).joinToString("/")
            }
        else
            super.pathTo(node, context)
}
