package com.virtuslab.dokka.site

import org.jetbrains.dokka.CoreExtensions
import org.jetbrains.dokka.base.DokkaBase
import org.jetbrains.dokka.plugability.DokkaContext
import org.jetbrains.dokka.plugability.DokkaPlugin
import java.io.File

class StaticSitePlugin : DokkaPlugin() {
    private val dokkaBase by lazy { plugin<DokkaBase>() }

    private fun loadStaticSiteContext(cxt: DokkaContext): StaticSiteContext? =
        cxt.configuration.pluginsConfiguration
            .filter { it.fqPluginName == ExternalDocsTooKey }
            .map { StaticSiteContext(File(it.values), cxt) }
            .firstOrNull()

    val customDocumentationProvider by extending {
        dokkaBase.htmlPreprocessors providing { ctx ->
            SitePagesCreator(loadStaticSiteContext(ctx))
        } order {
            after(dokkaBase.rootCreator)
            before(dokkaBase.navigationPageInstaller)
            before(dokkaBase.scriptsInstaller)
            before(dokkaBase.stylesInstaller)
            before(dokkaBase.packageListCreator)
        }
    }

    val customIndexRootProvider by extending {
        dokkaBase.htmlPreprocessors providing { ctx ->
            RootIndexPageCreator(loadStaticSiteContext(ctx))
        } order {
            after(dokkaBase.navigationPageInstaller)
            before(dokkaBase.scriptsInstaller)
            before(dokkaBase.stylesInstaller)
        }
    }

    val customDocumentationResources by extending {
        dokkaBase.htmlPreprocessors providing { ctx ->
            SiteResourceManager(loadStaticSiteContext(ctx))
        } order {
            // We want our css and scripts after default ones
            after(dokkaBase.scriptsInstaller)
            before(dokkaBase.stylesInstaller)
        }
    }

    val customRenderer by extending {
        (CoreExtensions.renderer
                providing { ctx -> SiteRenderer(ctx) }
                override dokkaBase.htmlRenderer)
    }

    val locationProvider by extending {
        dokkaBase.locationProviderFactory providing ::StaticSiteLocationProviderFactory override dokkaBase.locationProvider
    }
}
