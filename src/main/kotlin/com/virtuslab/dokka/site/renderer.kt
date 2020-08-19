package com.virtuslab.dokka.site

import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.parser.Parser
import kotlinx.html.*
import kotlinx.html.stream.createHTML
import org.jetbrains.dokka.pages.ContentPage
import org.jetbrains.dokka.pages.PageNode
import org.jetbrains.dokka.plugability.DokkaContext
import java.net.URI

class ExternalDocsToolRenderer(context: DokkaContext) : org.jetbrains.dokka.base.renderers.html.HtmlRenderer(context) {
    override fun buildPageContent(context: FlowContent, page: ContentPage) {
        context.buildNavigation(page)
        fun FlowContent.render(txt: String) = div { unsafe { +txt } }
        when (val content = page.content) {
            is PreRenderedContent -> context.render(content.html)
            else -> content.build(context, page)
        }
    }

    // TODO (#15): remove once html render has proper API
    private fun PageNode.root(path: String) = locationProvider.resolveRoot(this) + path
    private fun resolveLink(link: String, page: PageNode): String = if (URI(link).isAbsolute) link else page.root(link)

    // TODO (#15): change API of HTML renderer
    override fun buildHtml(page: PageNode, resources: List<String>, content: FlowContent.() -> Unit) =
        when (page) {
            is BaseStaticSiteProcessor.HtmlPageNode ->
                createHtml(page, page.resolved.code)
            is BaseStaticSiteProcessor.MdPageNode ->
                if (page.dri.contains(docsRootDRI)) {
                    val parser: Parser = Parser.builder().build()
                    val htmlContent =
                        HtmlRenderer.builder(defaultMarkdownOptions).build().render(parser.parse(page.resolved.code))
                    createHtml(page, htmlContent)
                } else super.buildHtml(page, resources, content)
            else ->
                super.buildHtml(page, resources, content)
        }

    private fun createHtml(page: BaseStaticSiteProcessor.BasePageNode, htmlContent: String): String {
        return createHTML().html {
            head {
                meta(name = "viewport", content = "width=device-width, initial-scale=1", charset = "UTF-8")
                title(page.name)
                page.resolved.resources.forEach {
                    when {
                        it.substringBefore('?').substringAfterLast('.') == "css" -> link(
                            rel = LinkRel.stylesheet,
                            href = resolveLink(it, page)
                        )
                        it.substringBefore('?').substringAfterLast('.') == "js" -> script(
                            type = ScriptType.textJavaScript,
                            src = resolveLink(it, page)
                        ) {
                            async = true
                        }
                        else -> unsafe { +it }
                    }
                }
                script { unsafe { +"""var pathToRoot = "${locationProvider.resolveRoot(page)}";""" } }
            }
            body {
                unsafe { +htmlContent }
            }
        }
    }

}
