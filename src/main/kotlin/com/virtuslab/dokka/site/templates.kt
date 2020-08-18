package com.virtuslab.dokka.site

import com.vladsch.flexmark.ext.anchorlink.AnchorLinkExtension
import com.vladsch.flexmark.ext.autolink.AutolinkExtension
import com.vladsch.flexmark.ext.emoji.EmojiExtension
import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension
import com.vladsch.flexmark.ext.gfm.tasklist.TaskListExtension
import com.vladsch.flexmark.ext.tables.TablesExtension
import com.vladsch.flexmark.ext.yaml.front.matter.AbstractYamlFrontMatterVisitor
import com.vladsch.flexmark.ext.yaml.front.matter.YamlFrontMatterExtension
import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.parser.ParserEmulationProfile
import com.vladsch.flexmark.util.options.DataHolder
import com.vladsch.flexmark.util.options.MutableDataSet
import liqp.Template
import java.io.File
import java.util.HashMap

val defaultMarkdownOptions: DataHolder =
    MutableDataSet()
        .setFrom(ParserEmulationProfile.KRAMDOWN.options)
        .set(
            Parser.EXTENSIONS, listOf(
                TablesExtension.create(),
                TaskListExtension.create(),
                AutolinkExtension.create(),
                AnchorLinkExtension.create(),
                EmojiExtension.create(),
                YamlFrontMatterExtension.create(),
                StrikethroughExtension.create()
            )
        )
        .set(
            EmojiExtension.ROOT_IMAGE_PATH,
            "https://github.global.ssl.fastly.net/images/icons/emoji/"
        )

data class RenderingContext(
    val properties: Map<String, Any>,
    val layouts: Map<String, TemplateFile> = emptyMap(),
    val resolving: Set<String> = emptySet(),
    val markdownOptions: DataHolder = defaultMarkdownOptions,
    val resources: List<String> = emptyList()
) {
    internal fun nest(code: String, path: String, resources: List<String>) =
        copy(
            resolving = resolving + path,
            properties = properties + ("content" to code),
            resources = this.resources + resources
        )
}

data class ResolvedPage(
    val code: String,
    val isHtml: Boolean = true,
    val resources: List<String> = emptyList()
)

val EmptyResolvedPage = ResolvedPage(code = "")

data class TemplateFile(val file: File, val rawCode: String, private val settings: Map<String, List<String>>) {
    private fun stringSetting(name: String): String? {
        val list = settings.get(name)
        list?.also { assert(it.size == 1) { "Setting $name is a list in $settings" } }
        return list?.first()?.removePrefix("\"")?.removeSuffix("\"")
    }

    private fun listSetting(name: String): List<String> = settings.get(name) ?: emptyList()

    private val isHtml = file.name.endsWith(".html")
    fun name(): String = stringSetting("name") ?: file.name.removeSuffix(if (isHtml) ".html" else ".md")
    fun title(): String = stringSetting("title") ?: name()
    private fun layout(): String? = stringSetting("layout")


    fun resolve(ctx: RenderingContext): ResolvedPage =
        resolveInner(ctx.copy(properties = HashMap(ctx.properties) + ("page" to mapOf("title" to title()))), hasHtmlContent = false)

    private fun resolveInner(ctx: RenderingContext, hasHtmlContent: Boolean): ResolvedPage {
        if (ctx.resolving.contains(file.absolutePath))
            throw java.lang.RuntimeException("Cycle in templates involving $file: ${ctx.resolving}")

        val rendered = Template.parse(this.rawCode).render(HashMap(ctx.properties)) // Library requires mutable maps..
        val code = if (!(isHtml || hasHtmlContent)) rendered else {
            val parser: Parser = Parser.builder().build()
            HtmlRenderer.builder(ctx.markdownOptions).build().render(parser.parse(rendered))
        }
        val resources = listSetting("extraCSS") + listSetting("extraJS")
        return layout()?.let {
            val layoutTemplate = ctx.layouts[it] ?: throw RuntimeException("No layouts named $it in ${ctx.layouts}")
            layoutTemplate.resolveInner(ctx.nest(code, file.absolutePath, resources), hasHtmlContent = isHtml)
        } ?: ResolvedPage(code, isHtml, resources + ctx.resources)
    }
}

const val ConfigSeparator = "---"
const val LineSeparator = "\n"

val yamlParser: Parser = Parser.builder(defaultMarkdownOptions).build()

fun loadTemplateFile(file: File): TemplateFile {
    val lines = file.readLines()

    val (config, content) = if (lines.firstOrNull() == ConfigSeparator) {
        // Taking the second occurrence of ConfigSeparator.
        // The rest may appear within the content.
        val index = lines.drop(1).indexOf(ConfigSeparator) + 2
        Pair(lines.take(index), lines.drop(index))
    } else Pair(emptyList(), lines)

    val configParsed = yamlParser.parse(config.joinToString(LineSeparator))
    val yamlCollector = AbstractYamlFrontMatterVisitor()
    yamlCollector.visit(configParsed)

    return TemplateFile(
        file = file,
        rawCode = content.joinToString(LineSeparator),
        settings = yamlCollector.data)
}
