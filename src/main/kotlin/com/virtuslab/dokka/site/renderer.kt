package com.virtuslab.dokka.site

import kotlinx.html.*
import kotlinx.html.stream.createHTML
import org.jetbrains.dokka.pages.*
import org.jetbrains.dokka.plugability.DokkaContext
import java.net.URI


// this will be rewritten after PR is merged to dokka
class RendererDispatcher(context: DokkaContext) : org.jetbrains.dokka.base.renderers.html.HtmlRenderer(context) {

    override fun buildPageContent(context: FlowContent, page: ContentPage) {
        when (page) {
            is BaseStaticSiteProcessor.StaticPageNode ->
                if (page.hasFrame()) context.buildNavigation(page)
            else -> context.buildNavigation(page)
        }

        fun FlowContent.render(txt: String) {
            div { unsafe { +txt } }
        }
        when (val content = page.content) {
            is PartiallyRenderedContent ->
                context.render(render(content, page))
            else -> content.build(context, page)
        }
    }

    private fun render(c: PartiallyRenderedContent, p: ContentPage): String {
        val parsed =
            if (!c.page.hasMarkdown)
                c.page.code
            else {
                val ctx = createHTML()
                ctx.div {
                    c.children.forEach { it.build(this, p) }
                }
                ctx.finalize()
            }

        return c.page.render(parsed).code
    }

    override fun FlowContent.buildCodeBlock(code: ContentCodeBlock, pageContext: ContentPage) {
        pre {
            code(code.style.joinToString(" ") { it.toString().toLowerCase() } + " language-scala") {
                attributes["theme"] = "idea"
                code.children.forEach { buildContentNode(it, pageContext) }
            }
        }
    }

    private fun PageNode.root(path: String) = locationProvider.pathToRoot(this) + path
    private fun resolveLink(link: String, page: PageNode): String = if (URI(link).isAbsolute) link else page.root(link)

    override fun buildHtml(page: PageNode, resources: List<String>, content: FlowContent.() -> Unit): String =
        if (page !is BaseStaticSiteProcessor.StaticPageNode) super.buildHtml(page, resources, content) else
            createHTML().html {
                head {
                    meta(name = "viewport", content = "width=device-width, initial-scale=1", charset = "UTF-8")
                    title(page.loadedTemplate.templateFile.title())
                    (if (page.hasFrame()) resources else page.resources()).forEach {
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
                    script { unsafe { +"""var pathToRoot = "${locationProvider.pathToRoot(page)}";""" } }
                }
                body {
                    if (page.hasFrame()) defaultFrame(page, content) else buildPageContent(this, page)
                }
            }

    private fun FlowContent.defaultFrame(page: PageNode, content: FlowContent.() -> Unit): Unit = div {
        id = "container"
        div {
            id = "leftColumn"
            div {
                id = "logo"
            }
            if (page !is MultimoduleRootPage) {
                div {
                    id = "paneSearch"
                }
            }
            div {
                id = "sideMenu"
            }
        }
        div {
            id = "main"
            div {
                id = "searchBar"
            }
            script(type = ScriptType.textJavaScript, src = page.root("scripts/pages.js")) {}
            script(type = ScriptType.textJavaScript, src = page.root("scripts/main.js")) {}
            content()
        }
    }
}