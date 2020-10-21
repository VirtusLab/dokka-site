package com.virtuslab.dokka.site

import org.jetbrains.dokka.base.renderers.html.NavigationNode
import org.jetbrains.dokka.base.renderers.html.NavigationPage
import org.jetbrains.dokka.links.DRI
import org.jetbrains.dokka.model.Documentable
import org.jetbrains.dokka.model.doc.Text
import org.jetbrains.dokka.pages.*
import org.jetbrains.dokka.transformers.pages.PageTransformer
import java.io.File

const val ExternalDocsTooKey = "ExternalDocsTooKey"

val docsRootDRI = DRI.topLevel.copy(extra = "_top_level_index")
val docsDRI = DRI.topLevel.copy(extra = "_docs_level_index")
val apiPageDRI = DRI(classNames = "api", extra = "__api__")

abstract class BaseStaticSiteProcessor(private val staticSiteContext: StaticSiteContext?) : PageTransformer {

    final override fun invoke(input: RootPageNode): RootPageNode =
        staticSiteContext?.let { transform(input, it) } ?: input

    protected abstract fun transform(input: RootPageNode, ctx: StaticSiteContext): RootPageNode

    data class LoadedTemplate(val templateFile: TemplateFile, val children: List<LoadedTemplate>, val file: File) {
        fun isIndexPage() = file.isFile && (file.name == "index.md" || file.name == "index.html")
        fun relativePath(root: File): String =
            root.toPath().relativize(file.toPath()).toString().replace(File.separatorChar, '.')
    }

    data class StaticPageNode(
        override val name: String,
        override val children: List<PageNode>,
        val loadedTemplate: LoadedTemplate,
        override val dri: Set<DRI>,
        override val embeddedResources: List<String> = emptyList(),
        override val content: ContentNode,
    ) : ContentPage {
        override val documentable: Documentable? = null

        fun title(): String = loadedTemplate.templateFile.title()
        fun hasFrame(): Boolean = loadedTemplate.templateFile.hasFrame()

        override fun modified(
            name: String,
            content: ContentNode,
            dri: Set<DRI>,
            embeddedResources: List<String>,
            children: List<PageNode>
        ): ContentPage =
            copy(
                name = name,
                content = content,
                dri = dri,
                embeddedResources = embeddedResources,
                children = children
            )

        override fun modified(name: String, children: List<PageNode>): PageNode = copy(name = name, children = children)

        fun resources() = when (content) {
            is PartiallyRenderedContent ->
                content.allResources
            else ->
                emptyList()
        }

    }
}

class SiteResourceManager(ctx: StaticSiteContext?) : BaseStaticSiteProcessor(ctx) {
    private fun listResources(nodes: List<PageNode>): Set<String> =
        nodes.flatMap {
            when (it) {
                is StaticPageNode -> listResources(it.children) + it.resources()
                else -> emptySet()
            }
        }.toSet()

    override fun transform(input: RootPageNode, ctx: StaticSiteContext): RootPageNode {
        val images = File(ctx.root, "images").walkTopDown().filter { it.isFile }
            .map { ctx.root.toPath().relativize(it.toPath()).toString() }
        val resources = listResources(input.children) + images
        val resourcePages = resources.map { path ->
            RendererSpecificResourcePage(path, emptyList(), RenderingStrategy.Write(File(ctx.root, path).readText()))
        }
        val modified = input.transformContentPagesTree {
            when (it) {
                is StaticPageNode -> it.modified(embeddedResources = it.embeddedResources + it.resources())
                else -> it
            }
        }
        return modified.modified(children = resourcePages + modified.children)
    }
}

data class AContentPage(
    override val name: String,
    override val children: List<PageNode>,
    override val content: ContentNode,
    override val dri: Set<DRI>,
    override val embeddedResources: List<String> = emptyList(),
) : ContentPage {
    override val documentable: Documentable? = null

    override fun modified(
        name: String,
        content: ContentNode,
        dri: Set<DRI>,
        embeddedResources: List<String>,
        children: List<PageNode>
    ): ContentPage = copy(name, children, content, dri, embeddedResources)

    override fun modified(name: String, children: List<PageNode>): PageNode = copy(name, children = children)
}


class SitePagesCreator(ctx: StaticSiteContext?) : BaseStaticSiteProcessor(ctx) {

    private fun processRootPage(input: RootPageNode, children: List<PageNode> = emptyList()): AContentPage =
        when (input) {
            is ContentPage ->
                AContentPage(
                    input.name,
                    children,
                    input.content,
                    setOf(apiPageDRI),
                    input.embeddedResources
                )
            is RendererSpecificRootPage ->
                children.filterIsInstance<RootPageNode>().single().let { nestedRoot ->
                    processRootPage(
                        nestedRoot,
                        children.filter { it != nestedRoot } + nestedRoot.children)
                }
            else -> TODO("UNSUPPORTED! ${input.javaClass.name}")
        }

    override fun transform(input: RootPageNode, ctx: StaticSiteContext): RootPageNode {
        val (contentPage, others) = input.children.partition { it is ContentPage }
        val modifiedModuleRoot = processRootPage(input, contentPage)
        val allFiles = ctx.docsFile.listFiles()?.toList() ?: emptyList()
        val (indexes, children) = ctx.loadFiles(allFiles).partition { it.loadedTemplate.isIndexPage() }
        if (indexes.size > 1) println("ERROR: Multiple index pages found $indexes}") // TODO (#14): provide proper error handling

        val rootContent =
            indexes.map { it.content }.firstOrNull() ?: ctx.asContent(Text(), DRI(extra = "root_content"))[0]

        val root = AContentPage(
            input.name,
            listOf(modifiedModuleRoot.modified(name = "API")) + children,
            rootContent,
            setOf(docsDRI),
            emptyList()
        )

        return RendererSpecificRootPage(
            modifiedModuleRoot.name,
            listOf(root) + others,
            RenderingStrategy.DoNothing
        )
    }

}

class RootIndexPageCreator(ctx: StaticSiteContext?) : BaseStaticSiteProcessor(ctx) {
    override fun transform(input: RootPageNode, ctx: StaticSiteContext): RootPageNode =
         ctx.indexPage()?.let {
            val (contentNodes, nonContent) = input.children.partition { it is ContentNode }
            val (navigations, rest) = nonContent.partition { it is NavigationPage }
            val modifiedNavigation = navigations.map {
                val root = (it as NavigationPage).root
                val (api, rest) = root.children.partition { it.dri == apiPageDRI }
                NavigationPage(
                    NavigationNode(
                        input.name,
                        docsRootDRI,
                        root.sourceSets,
                        rest + api
                    )
                )
            }
            val newRoot = it.modified(dri = setOf(docsRootDRI), children = contentNodes)
            input.modified(children = listOf(newRoot) + rest + modifiedNavigation)
        } ?: input
}
