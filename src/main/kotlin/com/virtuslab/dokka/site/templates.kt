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
import java.util.*

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
    internal fun nest(code: String, file: File, resources: List<String>) =
        copy(
            resolving = resolving + file.absolutePath,
            properties = properties + ("content" to code),
            resources = this.resources + resources
        )
}

data class LayoutInfo(
    val htmlTemple: TemplateFile,
    val ctx: RenderingContext,
)

data class PreResolvedPage(
    val code: String,
    val nextLayoutInfo: LayoutInfo?,
    val hasMarkdown: Boolean,
    val resources: List<String> = emptyList()
) {
    fun render(renderedMarkdown: String): ResolvedPage =
        if (nextLayoutInfo == null) ResolvedPage(renderedMarkdown, hasMarkdown, resources) else {
            val newCtx =
                nextLayoutInfo.ctx.copy(properties = nextLayoutInfo.ctx.properties + ("content" to renderedMarkdown))
            val res = nextLayoutInfo.htmlTemple.resolveToHtml(newCtx, hasMarkdown)
            ResolvedPage(res.code, hasMarkdown, res.resources)
        }
}

data class ResolvedPage(
    val code: String,
    val hasMarkdown: Boolean,
    val resources: List<String> = emptyList()
)

/**
 * Data class for the template files.
 * Template file is a file `.md` or `.html` handling settings.
 *
 * @param file The Actual file defining the template.
 * @param rawCode The content, what is to be shown, everything but settings.
 * @param settings The config defined in the begging of the file, between the pair of `---` (e.g. layout: basic).
 */
data class TemplateFile(
    val file: File,
    val isHtml: Boolean,
    val rawCode: String,
    private val settings: Map<String, List<String>>
) {
    private fun stringSetting(name: String): String? {
        val list = settings[name]
        list?.also { assert(it.size == 1) { "Setting $name is a list in $settings" } }
        return list?.first()?.removePrefix("\"")?.removeSuffix("\"")
    }

    private fun listSetting(name: String): List<String> = settings[name] ?: emptyList()

    fun name(): String = stringSetting("name") ?: file.name.removeSuffix(if (isHtml) ".html" else ".md")
    fun title(): String = stringSetting("title") ?: name()
    fun layout(): String? = stringSetting("layout")
    fun hasFrame(): Boolean = stringSetting("hasFrame") != "false"


    fun resolveMarkdown(ctx: RenderingContext): PreResolvedPage =
        resolveInner(
            ctx = ctx.copy(properties = HashMap(ctx.properties) + ("page" to mapOf("title" to title()))),
            stopAtHtml = true,
            !isHtml // This is toplevel template
        )

    fun resolveToHtml(ctx: RenderingContext, hasMarkdown: Boolean): PreResolvedPage =
        resolveInner(ctx, stopAtHtml = false, hasMarkdown)

    private fun resolveInner(ctx: RenderingContext, stopAtHtml: Boolean, hasMarkdown: Boolean): PreResolvedPage {
        if (ctx.resolving.contains(file.absolutePath))
            throw java.lang.RuntimeException("Cycle in templates involving $file: ${ctx.resolving}")

        val layoutTemplate =
            layout()?.let { ctx.layouts[it] ?: throw RuntimeException("No layouts named $it in ${ctx.layouts}") }

        if (!stopAtHtml && !isHtml)
            throw java.lang.RuntimeException(
                "Markdown layout cannot be applied after .html. Rendering $file after: ${ctx.resolving}"
            )

        return if (stopAtHtml && isHtml) {
            PreResolvedPage(ctx.properties["content"].toString(), LayoutInfo(this, ctx), hasMarkdown)
        } else {
            val rendered =
                Template.parse(this.rawCode).render(HashMap(ctx.properties)) // Library requires mutable maps..
            val code = if (!isHtml) rendered else {
                val parser: Parser = Parser.builder().build()
                HtmlRenderer.builder(ctx.markdownOptions).build().render(parser.parse(rendered))
            }
            val resources = listSetting("extraCSS") + listSetting("extraJS")
            val resolved = layoutTemplate?.resolveInner(ctx.nest(code, file, resources), stopAtHtml, hasMarkdown)
            resolved ?: PreResolvedPage(code, null, hasMarkdown, resources + ctx.resources)
        }
    }
}

fun emptyTemplate(file: File) = TemplateFile(file, isHtml = true, "", emptyMap())

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
        file.name.endsWith(".html"),
        rawCode = content.joinToString(LineSeparator),
        settings = yamlCollector.data
    )
}
