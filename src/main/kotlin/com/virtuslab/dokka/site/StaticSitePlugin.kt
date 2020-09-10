package com.virtuslab.dokka.site

import org.jetbrains.dokka.CoreExtensions
import org.jetbrains.dokka.base.DokkaBase
import org.jetbrains.dokka.plugability.DokkaPlugin

class StaticSitePlugin : DokkaPlugin() {
    private val dokkaBase by lazy { plugin<DokkaBase>() }

    val customDocumentationProvider by extending {
        dokkaBase.htmlPreprocessors providing { ctx ->
            SitePagesCreator(ctx)
        } order {
            after(dokkaBase.navigationPageInstaller)
            before(dokkaBase.styleAndScriptsAppender)
        }
    }

    val customDocumentationResources by extending {
        dokkaBase.htmlPreprocessors providing { ctx ->
            SiteResourceManager(ctx)
        } order {
            // We want our css and scripts after default ones
            after(dokkaBase.styleAndScriptsAppender)
        }
    }

    val customRenderer by extending {
        (CoreExtensions.renderer
                providing { ctx -> RendererDispatcher(ctx) }
                override dokkaBase.htmlRenderer)
    }

    val locationProvider by extending {
        dokkaBase.locationProviderFactory providing ::StaticSiteLocationProviderFactory override dokkaBase.locationProvider
    }
}
