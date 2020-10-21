package com.virtuslab.dokka.site

import org.jetbrains.dokka.base.transformers.pages.comments.DocTagToContentConverter
import org.jetbrains.dokka.links.DRI
import org.jetbrains.dokka.model.doc.DocTag
import org.jetbrains.dokka.model.doc.Text
import org.jetbrains.dokka.model.properties.PropertyContainer
import org.jetbrains.dokka.model.toDisplaySourceSet
import org.jetbrains.dokka.pages.ContentKind
import org.jetbrains.dokka.pages.ContentNode
import org.jetbrains.dokka.pages.DCI
import org.jetbrains.dokka.pages.PageNode
import org.jetbrains.dokka.plugability.DokkaContext
import java.io.File

class StaticSiteContext(val root: File, cxt: DokkaContext){
    val docsFile = File(root, "docs")

    fun indexPage(): BaseStaticSiteProcessor.StaticPageNode? {
        val files = listOf(File(root, "index.html"), File(root, "index.md")).filter { it.exists() }
        if (files.size > 1) println("ERROR: Multiple root index pages found: ${files.map { it.absolutePath }}") // TODO (#14): provide proper error handling
        return loadFiles(files).firstOrNull()
    }

    private val mySourceSet = cxt.configuration.sourceSets.toSet()
    private val myDisplaySourceSet = mySourceSet.map { it.toDisplaySourceSet() }.toSet()

    private val layouts: Map<String, TemplateFile> by lazy {
        val layoutRoot = File(root, "_layouts")
        val dirs: Array<File> = layoutRoot.listFiles() ?: emptyArray()
        dirs.map { loadTemplateFile(it) }.map { it.name() to it }.toMap()
    }


    private fun isValidTemplate(file: File): Boolean =
        (file.isDirectory && !file.name.startsWith("_")) ||
                file.name.endsWith(".md") ||
                file.name.endsWith(".html")


    private fun loadTemplate(from: File): BaseStaticSiteProcessor.LoadedTemplate? =
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

            BaseStaticSiteProcessor.LoadedTemplate(templateFile, children, from)

        } catch (e: RuntimeException) {
            e.printStackTrace() // TODO (#14): provide proper error handling
            null
        }

    private fun parseMarkdown(page: PreResolvedPage, dri: DRI, allDRIs: Map<String, DRI>): ContentNode {
        val nodes = if (page.hasMarkdown) {
            val parser = ExtendableMarkdownParser(page.code) { link ->
                val driKey = if (link.startsWith("/")) {
                    // handle root related links
                    link.replace('/', '.').removePrefix(".")
                } else {
                    val unSuffixedDri = dri.packageName!!.removeSuffix(".html").removeSuffix(".md")
                    val parentDri = unSuffixedDri.take(unSuffixedDri.indexOfLast('.'::equals)).removePrefix("_.")
                    "${parentDri}.${link.replace('/', '.')}"
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

            asContent(docTag, dri)
        } else emptyList()
        return PartiallyRenderedContent(
            page,
            nodes,
            DCI(setOf(dri), ContentKind.Empty),
            myDisplaySourceSet,
            emptySet(),
            PropertyContainer.empty()
        )
    }

    fun asContent(d: DocTag, dri: DRI) = DocTagToContentConverter().buildContent(
        d,
        DCI(setOf(dri), ContentKind.Empty),
        mySourceSet,
        emptySet(),
        PropertyContainer.empty()
    )

    fun loadFiles(files: List<File>, customChildren: List<PageNode> = emptyList()): List<BaseStaticSiteProcessor.StaticPageNode> {
        val all = files.mapNotNull { loadTemplate(it) }
        fun flatten(it: BaseStaticSiteProcessor.LoadedTemplate): List<String> =
            listOf(it.relativePath(root)) + it.children.flatMap { flatten(it) }

        fun pathToDri(path: String) = DRI("_.$path")

        val driMap = all.flatMap { flatten(it) }.map { it to pathToDri(it) }.toMap()

        fun templateToPage(myTemplate: BaseStaticSiteProcessor.LoadedTemplate): BaseStaticSiteProcessor.StaticPageNode {
            val dri = pathToDri(myTemplate.relativePath(root))
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
            return BaseStaticSiteProcessor.StaticPageNode(
                myTemplate.templateFile.title(),
                children + customChildren,
                myTemplate,
                setOf(dri),
                emptyList(),
                content
            )
        }
        return all.map { templateToPage(it) }
    }
}

