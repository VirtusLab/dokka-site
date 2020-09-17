package com.virtuslab.dokka.site

import org.jetbrains.dokka.base.renderers.html.NavigationNode
import org.jetbrains.dokka.base.renderers.html.NavigationPage
import org.jetbrains.dokka.base.transformers.pages.comments.DocTagToContentConverter
import org.jetbrains.dokka.links.DRI
import org.jetbrains.dokka.model.Documentable
import org.jetbrains.dokka.model.doc.Text
import org.jetbrains.dokka.model.properties.PropertyContainer
import org.jetbrains.dokka.model.toDisplaySourceSet
import org.jetbrains.dokka.pages.*
import org.jetbrains.dokka.plugability.DokkaContext
import org.jetbrains.dokka.transformers.pages.PageTransformer
import java.io.File

const val ExternalDocsTooKey = "ExternalDocsTooKey"

val docsRootDRI = DRI.topLevel.copy(extra = "_top_level_index")

abstract class BaseStaticSiteProcessor(cxt: DokkaContext) : PageTransformer {

    final override fun invoke(input: RootPageNode): RootPageNode =
        rawRoot?.let { transform(input) } ?: input

    protected abstract fun transform(input: RootPageNode): RootPageNode

    private val rawRoot: File? = cxt.configuration.pluginsConfiguration.get(ExternalDocsTooKey)?.let { File(it) }
    val root = rawRoot ?: File("unknown")

    protected val mySourceSet = cxt.configuration.sourceSets.toSet()
    protected val myDisplaySourceSet = mySourceSet.map { it.toDisplaySourceSet() }.toSet()
    protected val docsFile = File(root, "docs")

    data class LoadedTemplate(val templateFile: TemplateFile, val children: List<LoadedTemplate>, val file: File) {
        fun isIndexPage() = file.isFile && (file.name == "index.md" || file.name == "index.html")
        fun relativePath(root: File): String =
            root.toPath().relativize(file.toPath()).toString().replace(File.separatorChar, '.')
    }

    data class StaticPageNode(
        override val name: String,
        override val children: List<StaticPageNode>,
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
                children = children.map { it as StaticPageNode })

        override fun modified(name: String, children: List<PageNode>): PageNode =
            copy(name = name, children = children.map { it as StaticPageNode })

        fun resources() = when (content) {
            is PartiallyRenderedContent ->
                content.allResources
            else ->
                emptyList()
        }

    }
}

class SiteResourceManager(cxt: DokkaContext) : BaseStaticSiteProcessor(cxt) {
    private fun listResources(nodes: List<PageNode>): Set<String> =
        nodes.flatMap {
            when (it) {
                is StaticPageNode -> listResources(it.children) + it.resources()
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
                is StaticPageNode -> it.modified(embeddedResources = it.embeddedResources + it.resources())
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

        val allFiles = docsFile.listFiles()?.toList() ?: emptyList()
        val (indexes, children) = loadFiles(allFiles).partition { it.loadedTemplate.isIndexPage() }
        if (indexes.size > 1) println("ERROR: Multiple index pages found $indexes}") // TODO (#14): provide proper error handling

        fun toNavigationNode(c: StaticPageNode): NavigationNode =
            NavigationNode(
                c.loadedTemplate.templateFile.title(),
                c.dri.first(),
                myDisplaySourceSet,
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
                            it.modified(content = indexPage.content, children = it.children + listOf(packageList))
                        } else it
                    else ->
                        it
                }
            }
        } ?: rest

        val indexFiles = listOf(File(root, "index.html"), File(root, "index.md")).filter { it.exists() }
        if (indexFiles.size > 1) println("ERROR: Multiple root index pages found: ${indexFiles.map { it.absolutePath }}") // TODO (#14): provide proper error handling

        val topLevelIndexPage = loadFiles(indexFiles.take(1)).map { it.modified(dri = setOf(docsRootDRI)) }

        return input.modified(children = original + topLevelIndexPage + listOf(NavigationPage(mergedRoots)) + children)
    }

    private val layouts: Map<String, TemplateFile> by lazy {
        val layoutRoot = File(root, "_layouts")
        val dirs: Array<File> = layoutRoot.listFiles() ?: emptyArray()
        dirs.map { loadTemplateFile(it) }.map { it.name() to it }.toMap()
    }


    private fun isValidTemplate(file: File): Boolean =
        (file.isDirectory && !file.name.startsWith("_")) ||
                file.name.endsWith(".md") ||
                file.name.endsWith(".html")


    private fun loadTemplate(from: File): LoadedTemplate? =
        if (!isValidTemplate(from)) null else try {
            val (indexes, children) = (from.listFiles()?.mapNotNull { loadTemplate(it) }
                ?: emptyList()).partition { it.isIndexPage() }
            if (indexes.size > 1)
                println("ERROR: Multiple index pages for $from found in ${indexes.map { it.file }}") // TODO (#14): provide proper error handling

            fun loadIndexPage(): TemplateFile {
                val indexFiles = from.listFiles { file -> file.name == "index.md" || file.name == "index.html" }
                return when (indexFiles.size) {
                    0 -> emptyTemplate(from)
                    1 -> loadTemplateFile(indexFiles.first()).copy(file = from)
                    else -> throw java.lang.RuntimeException("ERROR: Multiple index pages found under ${from.path}")
                }
            }

            val templateFile = if (from.isDirectory) loadIndexPage() else loadTemplateFile(from)

            LoadedTemplate(templateFile, children, from)

        } catch (e: RuntimeException) {
            e.printStackTrace() // TODO (#14): provide proper error handling
            null
        }

    private fun parseMarkdown(page: PreResolvedPage, dri: Set<DRI>, allDRIs: Map<String, DRI>): ContentNode {
        val nodes = if (page.hasMarkdown) {
            val parser = ExtendableMarkdownParser(page.code) { link ->
                val driKey = if (link.startsWith("/")) {
                    // handle root related links
                    link.replace('/', '.').removePrefix(".")
                } else {
                    // todo handle relative links
                    link
                }
                allDRIs[driKey]
            }

            val docTag = try {
                parser.parse()
            } catch (e: Throwable) {
                val msg = "Error rendering (dri = $dri): ${e.message}"
                println("ERROR: $msg") // TODO (#14): provide proper error handling
                Text(msg, emptyList())
            }

            DocTagToContentConverter.buildContent(
                docTag,
                DCI(dri, ContentKind.Empty),
                mySourceSet,
                emptySet(),
                PropertyContainer.empty()
            )
        } else emptyList()
        return PartiallyRenderedContent(
            page,
            nodes,
            DCI(dri, ContentKind.Empty),
            myDisplaySourceSet,
            emptySet(),
            PropertyContainer.empty()
        )
    }

    private fun loadFiles(files: List<File>): List<StaticPageNode> {
        val all = files.mapNotNull { loadTemplate(it) }
        fun flatten(it: LoadedTemplate): List<String> =
            listOf(it.relativePath(root)) + it.children.flatMap { flatten(it) }

        fun pathToDri(path: String) = DRI("_.$path")

        val driMap = all.flatMap { flatten(it) }.map { it to pathToDri(it) }.toMap()

        fun templateToPage(myTemplate: LoadedTemplate): StaticPageNode {
            val dri = setOf(pathToDri(myTemplate.relativePath(root)))
            val page = try {
                val properties = myTemplate.templateFile.layout()
                    ?.let { mapOf("content" to myTemplate.templateFile.rawCode) } ?: emptyMap()

                myTemplate.templateFile.resolveMarkdown(RenderingContext(properties, layouts))
            } catch (e: Throwable) {
                val msg = "Error rendering $myTemplate: ${e.message}"
                println("ERROR: $msg") // TODO (#14): provide proper error handling
                PreResolvedPage("", null, true)
            }
            val content = parseMarkdown(page, dri, driMap)
            val children = myTemplate.children.map { templateToPage(it) }
            return StaticPageNode(myTemplate.templateFile.title(), children, myTemplate, dri, emptyList(), content)
        }
        return all.map { templateToPage(it) }
    }
}
