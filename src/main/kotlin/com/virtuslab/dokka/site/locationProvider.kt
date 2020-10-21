package com.virtuslab.dokka.site

import org.jetbrains.dokka.base.resolvers.local.DokkaLocationProvider
import org.jetbrains.dokka.base.resolvers.local.LocationProvider
import org.jetbrains.dokka.base.resolvers.local.LocationProviderFactory
import org.jetbrains.dokka.pages.ContentPage
import org.jetbrains.dokka.pages.PageNode
import org.jetbrains.dokka.pages.RootPageNode
import org.jetbrains.dokka.plugability.DokkaContext

class StaticSiteLocationProviderFactory(private val ctx: DokkaContext) : LocationProviderFactory {
    override fun getLocationProvider(pageNode: RootPageNode): LocationProvider {
        return StaticSiteLocationProvider(ctx, pageNode)
    }
}

class StaticSiteLocationProvider(ctx: DokkaContext, pageNode: RootPageNode) : DokkaLocationProvider(pageNode, ctx) {
    private fun updatePageEntry(path: List<String>, page: PageNode): List<String> =
       if (page is BaseStaticSiteProcessor.StaticPageNode){
           if (page.dri.contains(docsRootDRI)) listOf("index")
           else {
               val start = if (path[0] == "--root--") listOf("docs") else path.take(1)
               start + path.drop(1).dropLast(1) + listOf(page.loadedTemplate.file.nameWithoutExtension)
           }
       }
       else if (page is ContentPage && page.dri.contains(docsDRI)) listOf("docs")
       else if (page is ContentPage && page.dri.contains(apiPageDRI)) listOf("api", "index")
       else if (path.size > 1 && path[0] == "--root--" && path[1] == "-a-p-i") listOf("api") + path.drop(2)
       else path

    override val pathsIndex: Map<PageNode, List<String>> = super.pathsIndex.mapValues { updatePageEntry(it.value, it.key) }
}
