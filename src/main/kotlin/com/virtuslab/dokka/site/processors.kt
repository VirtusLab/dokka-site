package com.virtuslab.dokka.site

import org.jetbrains.dokka.DokkaConfiguration
import org.jetbrains.dokka.base.parsers.HtmlParser
import org.jetbrains.dokka.base.parsers.MarkdownParser
import org.jetbrains.dokka.base.renderers.html.NavigationNode
import org.jetbrains.dokka.base.renderers.html.NavigationPage
import org.jetbrains.dokka.base.transformers.pages.comments.DocTagToContentConverter
import org.jetbrains.dokka.links.DRI
import org.jetbrains.dokka.model.Documentable
import org.jetbrains.dokka.model.doc.Text
import org.jetbrains.dokka.model.properties.PropertyContainer
import org.jetbrains.dokka.pages.*
import org.jetbrains.dokka.plugability.DokkaContext
import org.jetbrains.dokka.transformers.pages.PageTransformer
import org.jetbrains.dokka.utilities.DokkaConsoleLogger
import java.io.File

const val ExternalDocsTooKey = "ExternalDocsTooKey"

val docsRootDRI = DRI.topLevel.copy(extra = "_top_level_index")

data class PreRenderedContent(
    val html: String,
    override val dci: DCI,
    override val sourceSets: Set<DokkaConfiguration.DokkaSourceSet>,
    override val style: Set<Style> = emptySet(),
    override val extra: PropertyContainer<ContentNode> = PropertyContainer.empty()
) : ContentNode {
    override fun hasAnyContent(): Boolean = !html.isBlank()

    override fun withNewExtras(newExtras: PropertyContainer<ContentNode>): ContentNode = copy(extra = newExtras)
}

abstract class BaseStaticSiteProcessor(cxt: DokkaContext) : PageTransformer {

    final override fun invoke(input: RootPageNode): RootPageNode =
        rawRoot?.let { transform(input) } ?: input

    protected abstract fun transform(input: RootPageNode): RootPageNode

    private val rawRoot: File? = cxt.configuration.pluginsConfiguration.get(ExternalDocsTooKey)?.let { File(it) }
    val root = rawRoot ?: File("unknown")

    protected val mySourceSet = cxt.configuration.sourceSets.toSet()
    protected val docsFile = File(root, "docs")

    protected fun File.asDri(): DRI {
        val relativePath = root.toPath().relativize(toPath()).toString().replace(File.separatorChar, '.')
        return DRI("_.$relativePath")
    }

    abstract inner class BasePageNode(
        file: File,
        private val template: TemplateFile,
        override val children: List<BasePageNode>,
        open val resolved: ResolvedPage,
        override val dri: Set<DRI>,
        override val embeddedResources: List<String> = emptyList()
    ) : ContentPage {
        override val name: String = template.name()
        override val documentable: Documentable? = null

        val isIndexPage = file.name == "index.md" || file.name == "index.html"

        fun title() = template.title()
    }

    inner class HtmlPageNode(
        private val file: File,
        private val template: TemplateFile,
        override val children: List<BasePageNode>,
        override val resolved: ResolvedPage,
        override val dri: Set<DRI>,
        override val embeddedResources: List<String> = emptyList()
    ) : BasePageNode(file, template, children, resolved, dri, embeddedResources) {

        override val content: ContentNode = PreRenderedContent(resolved.code, DCI(dri, ContentKind.Empty), mySourceSet)

        override fun modified(
            name: String,
            content: ContentNode,
            dri: Set<DRI>,
            embeddedResources: List<String>,
            children: List<PageNode>
        ): ContentPage =
            HtmlPageNode(file, template, children.filterIsInstance<BasePageNode>(), resolved, dri, embeddedResources)

        override fun modified(name: String, children: List<PageNode>): PageNode =
            HtmlPageNode(file, template, children.filterIsInstance<BasePageNode>(), resolved, dri, embeddedResources)
    }

    inner class MdPageNode(
        private val file: File,
        private val template: TemplateFile,
        override val children: List<BasePageNode>,
        override val resolved: ResolvedPage,
        override val dri: Set<DRI>,
        override val embeddedResources: List<String> = emptyList()
    ) : BasePageNode(file, template, children, resolved, dri, embeddedResources) {

        override val content: ContentNode = resolvedPageToContent(resolved, dri)

        override fun modified(
            name: String,
            content: ContentNode,
            dri: Set<DRI>,
            embeddedResources: List<String>,
            children: List<PageNode>
        ): ContentPage =
            MdPageNode(file, template, children.filterIsInstance<BasePageNode>(), resolved, dri, embeddedResources)

        override fun modified(name: String, children: List<PageNode>): PageNode =
            MdPageNode(file, template, children.filterIsInstance<BasePageNode>(), resolved, dri, embeddedResources)

        private fun resolvedPageToContent(
            resolvedPage: ResolvedPage,
            dri: Set<DRI>
        ): ContentGroup {
            val parser =
                if (resolvedPage.isHtml) HtmlParser()
                else MarkdownParser(logger = DokkaConsoleLogger)

            val docTag = try {
                parser.parseStringToDocNode(resolvedPage.code)
            } catch (e: Throwable) {
                val msg = "Error rendering (dri = $dri): ${e.message}"
                println("ERROR: $msg") // TODO (#14): provide proper error handling
                Text(msg, emptyList())
            }

            val contentNodes = DocTagToContentConverter.buildContent(
                docTag,
                DCI(dri, ContentKind.Empty),
                mySourceSet,
                emptySet(),
                PropertyContainer.empty()
            )

            return ContentGroup(
                contentNodes,
                DCI(dri, ContentKind.Empty),
                mySourceSet,
                emptySet(),
                PropertyContainer.empty()
            )
        }
    }
}

class SiteResourceManager(cxt: DokkaContext) : BaseStaticSiteProcessor(cxt) {
    private fun listResources(nodes: List<PageNode>): Set<String> =
        nodes.flatMap {
            when (it) {
                is BasePageNode -> listResources(it.children) + it.resolved.resources
                else -> emptySet()
            }
        }.toSet()

    override fun transform(input: RootPageNode): RootPageNode {
        val images = File(root, "images").walkTopDown().filter { it.isFile }
            .map { root.toPath().relativize(it.toPath()).toString() }
        val resources = listResources(input.children) + images
        val resourcePages = resources.map { path ->
            RendererSpecificResourcePage(path, emptyList(), RenderingStrategy.Write(File(root, path).readText()))
        }
        val modified = input.transformContentPagesTree {
            when (it) {
                is BasePageNode -> it.modified(embeddedResources = it.embeddedResources + it.resolved.resources)
                else -> it
            }
        }
        return modified.modified(children = resourcePages + modified.children)
    }
}

class SitePagesCreator(cxt: DokkaContext) : BaseStaticSiteProcessor(cxt) {

    override fun transform(input: RootPageNode): RootPageNode {
        val (navigationPage, rest) = input.children.partition { it is NavigationPage }
        val defaultNavigation = (navigationPage.single() as NavigationPage).root

        val (indexes, children) = loadFiles().partition { it.isIndexPage }
        if (indexes.size > 1) println("ERROR: Multiple index pages found ${children.filter { it.isIndexPage }}") // TODO (#14): provide proper error handling

        fun toNavigationNode(c: BasePageNode): NavigationNode =
            NavigationNode(
                c.title(),
                c.dri.first(),
                mySourceSet,
                c.children.map { toNavigationNode(it) }
            )

        val apiPageDri = DRI.topLevel.copy(extra = "_api_")

        val mergedRoots = NavigationNode(
            defaultNavigation.name,
            defaultNavigation.dri,
            defaultNavigation.sourceSets,
            children.map { toNavigationNode(it) } + listOf(
                NavigationNode(
                    "API",
                    apiPageDri,
                    defaultNavigation.sourceSets,
                    defaultNavigation.children
                )
            )
        )
        val original = indexes.firstOrNull()?.let { indexPage ->
            rest.map {
                when (it) {
                    is ModulePageNode ->
                        if (it.dri.contains(DRI.topLevel)) {
                            val packageList =
                                PackagePageNode(
                                    "_root_",
                                    it.content,
                                    setOf(apiPageDri),
                                    null,
                                    emptyList(),
                                    it.embeddedResources
                                )
                            it.modified(content = indexPage.content, children = it.children + packageList)
                        } else it
                    else ->
                        it
                }
            }
        } ?: rest

        val indexFiles = listOf(File(root, "index.html"), File(root, "index.md")).filter { it.exists() }
        if (indexFiles.size > 1) println("ERROR: Multiple root index pages found: ${indexFiles.map { it.absolutePath }}") // TODO (#14): provide proper error handling

        val topLevelIndexPage = indexFiles.take(1)
            .mapNotNull { renderDocs(it, noChildren = true)?.modified(dri = setOf(docsRootDRI)) }

        return input.modified(children = original + topLevelIndexPage + listOf(NavigationPage(mergedRoots)) + children)
    }

    private val layouts: Map<String, TemplateFile> by lazy {
        val layoutRoot = File(root, "_layouts")
        val dirs: Array<File> = layoutRoot.listFiles() ?: emptyArray()
        dirs.map { loadTemplateFile(it) }.map { it.name() to it }.toMap()
    }

    private fun renderDocs(from: File, noChildren: Boolean = false): BasePageNode? =
        if (from.name.startsWith("_")) null else try {
            val dri = setOf(from.asDri())

            val children =
                if (noChildren) emptyList() else from.listFiles()?.mapNotNull { renderDocs(it) } ?: emptyList()
            if (children.count { it.isIndexPage } > 1)
                println("ERROR: Multiple index pages found ${children.filter { it.isIndexPage }}") // TODO (#14): provide proper error handling

            val templateFile = loadTemplateFile(
                if (from.isDirectory) {
                    val indexFiles = from.listFiles { file -> file.name == "index.md" || file.name == "index.html" }
                    check(indexFiles!!.size == 1) { "ERROR: Multiple index pages found under ${from.path}" } // ensured above too
                    indexFiles.first()
                } else from
            )

            val resolvedPage = try {
                if (from.isDirectory) {
                    children.find { it.isIndexPage }?.resolved ?: EmptyResolvedPage
                } else {
                    val properties = templateFile.layout()
                        ?.let { mapOf("content" to templateFile.rawCode) } ?: emptyMap()
                    val context = RenderingContext(properties, layouts)
                    templateFile.resolve(context)
                }
            } catch (e: Throwable) {
                val msg = "Error rendering $from: ${e.message}"
                println("ERROR: $msg") // TODO (#14): provide proper error handling
                ResolvedPage(msg)
            }

            if (resolvedPage.isHtml) {
                HtmlPageNode(from, templateFile, children.filter { !it.isIndexPage }, resolvedPage, dri)
            } else { // isMd
                MdPageNode(from, templateFile, children.filter { !it.isIndexPage }, resolvedPage, dri)
            }

        } catch (e: RuntimeException) {
            e.printStackTrace() // TODO (#14): provide proper error handling
            null
        }

    private fun loadFiles(): List<BasePageNode> =
        docsFile.listFiles()?.mapNotNull { renderDocs(it) } ?: emptyList()

}
