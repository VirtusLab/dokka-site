package com.virtuslab.dokka.site

import org.jetbrains.dokka.base.parsers.MarkdownParser
import org.jetbrains.dokka.base.transformers.pages.comments.DocTagToContentConverter
import org.jetbrains.dokka.links.Callable
import org.jetbrains.dokka.links.DRI
import org.jetbrains.dokka.links.JavaClassReference
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
import java.net.MalformedURLException
import java.net.URL
import java.nio.file.Path

class StaticSiteContext(val root: File, cxt: DokkaContext) {
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

    private fun parseMarkdown(
        page: PreResolvedPage,
        dri: DRI,
        allDRIs: Map<Path, DRI>,
        proceededFilePath: String
    ): ContentNode {
        val nodes = if (page.hasMarkdown) {
            val externalDri = getExternalDriResolver(allDRIs, proceededFilePath)
            val parser = MarkdownParser(externalDri)

            val docTag = try {
                parser.parseStringToDocNode(page.code)
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

    fun loadFiles(
        files: List<File>,
        customChildren: List<PageNode> = emptyList()
    ): List<BaseStaticSiteProcessor.StaticPageNode> {
        val all = files.mapNotNull { loadTemplate(it) }
        fun flatten(it: BaseStaticSiteProcessor.LoadedTemplate): List<String> =
            listOf(it.relativePath(root)) + it.children.flatMap { flatten(it) }

        fun pathToDri(path: String) = DRI("_.$path")

        val driMap = all.flatMap(::flatten)
            .flatMap(::createBothMdAndHtmlKeys)
            .map { Path.of(it).normalize().run { this to pathToDri(this.toString()) } }
            .toMap()

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

            val proceededFilePath = myTemplate.file.path
                .removePrefix("documentation")
                .dropLastWhile { x -> x != File.separatorChar }
                .removeSuffix(File.separator)
            val content = parseMarkdown(page, dri, driMap, proceededFilePath)
            val children = myTemplate.children.map(::templateToPage)
            return BaseStaticSiteProcessor.StaticPageNode(
                myTemplate.templateFile.title(),
                children + customChildren,
                myTemplate,
                setOf(dri),
                emptyList(),
                content
            )
        }
        return all.map(::templateToPage)
    }

    private fun createBothMdAndHtmlKeys(x: String) =
        listOf(
            if (x.endsWith(".md")) x.removeSuffix(".md").plus(".html") else x,
            if (x.endsWith(".html")) x.removeSuffix(".html").plus(".md") else x
        )

    private fun getExternalDriResolver(allDRIs: Map<Path, DRI>, proceededFilePath: String): (String) -> DRI? = { it ->
        try {
            URL(it)
            null
        } catch (e: MalformedURLException) {
            val resolvePath = { x: String ->
                if (it.startsWith('/')) docsFile.resolve(x)
                else File(proceededFilePath).resolve(x)
            }
            val path = Path.of(it).normalize().toString()
                .let { s -> resolvePath(s) }
                .toString()
                .removePrefix(File.separator)
                .replace(File.separatorChar, '.')
                .let(Path::of)

            allDRIs[path] ?: it.replace("\\s".toRegex(), "").resolveLinkToApi()
        }
    }

    private fun String.resolveLinkToApi() = when {
        '#' in this -> {
            val (packageName, classNameAndRest) = split('#')
            when {
                "::" in classNameAndRest -> {
                    val (className, callableAndParams) = classNameAndRest.split("::")
                    makeApiDRI(callableAndParams, packageName, className)
                }
                else -> DRI(packageName = packageName, classNames = classNameAndRest)
            }
        }
        "::" in this -> {
            val (packageName, callableAndParams) = split("::")
            makeApiDRI(callableAndParams, packageName)
        }
        else -> DRI(packageName = this)
    }

    private fun makeApiDRI(
        callableAndParams: String,
        packageName: String,
        className: String? = null
    ): DRI {
        val callableName = callableAndParams.takeWhile { it != '(' }
        val params = callableAndParams.dropWhile { it != '(' }
            .removePrefix("(")
            .removeSuffix(")")
            .split(',')
            .filter(String::isNotBlank)
            .map(::JavaClassReference)
        val callable = Callable(name = callableName, params = params)
        return DRI(packageName = packageName, classNames = className, callable = callable)
    }
}
