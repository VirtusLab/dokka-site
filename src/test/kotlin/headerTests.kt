package com.virtuslab.dokka.site

import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.parser.Parser
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Files

class TemplateFileTests {
    private fun testTemplate(code: String, ext: String = "html", op: TemplateFile.() -> Unit) {
        val tmpFile = Files.createTempFile("headerTests", ".${ext}").toFile()
        try {
            tmpFile.writeText(code)
            val f = loadTemplateFile(tmpFile)
            f.op()
        } finally {
            tmpFile.delete()
        }
    }

    private fun testTemplates(
        props: Map<String, String>,
        template: List<Pair<String, String>>,
        op: (RenderingContext) -> Unit
    ) {
        fun rec(cxt: RenderingContext, remaining: List<Pair<String, String>>) {
            if (remaining.isEmpty()) op(cxt)
            else {
                val (code, ext) = remaining.first()
                testTemplate(code, ext) {
                    rec(cxt.copy(layouts = cxt.layouts + (name() to this)), remaining.drop(1))
                }
            }
        }
        rec(RenderingContext(props), template)
    }


    private fun TemplateFile.fullRender(ctx: RenderingContext): String {
        val code = resolveMarkdown(ctx)
        val parser: Parser = Parser.builder().build()
        val built = HtmlRenderer.builder(ctx.markdownOptions).build().render(parser.parse(code.code))
        return code.render(built).code.trim()
    }

    @Test
    fun testParsingHeaders() {
        testTemplate(
            """
            ---
            title: myTitle
            ---
            code
            """.trimIndent()
        ) {
            assertEquals(rawCode, "code")
            assertEquals(title(), "myTitle")
        }
    }

    @Test
    fun testLinks() {
        val base =
            """
            ---
            title: myTitle
            name: base
            ---
            Ala {{ content }}. {{p2}} with [link](link/target.md)!
            """.trimIndent()

        val content =
            """
                ---
                layout: base
                name: content
                ---
                ma kota w **{{ p1 }}** from [here](link/here.md)
                """.trimIndent()


        val expected = """
             <p>Ala <p>ma kota w <strong>paski</strong> from <a href="link/here.md">here</a></p>
             . Hej with <a href="link/target.md">link</a>!</p>
            """.trimIndent()

        testTemplates(
            mapOf("p1" to "paski", "p2" to "Hej"),
            listOf(base to "html", content to "md")
        ) {
            assertEquals(
                expected,
                it.layouts.getValue("content").fullRender(it)
            )
        }
    }


    @Test
    fun layout() {
        val base =
            """
            ---
            title: myTitle
            name: base
            ---
            Ala {{ content }}. {{p2}}!
            """.trimIndent()

        val content =
            """
                ---
                layout: base
                name: content
                ---
                ma kota w **{{ p1 }}**
                """.trimIndent()


        val expected = """
                <p>Ala <p>ma kota w <strong>paski</strong></p>
                . Hej!</p>
            """.trimIndent()

        testTemplates(
            mapOf("p1" to "paski", "p2" to "Hej"),
            listOf(base to "html", content to "md")
        ) {
            assertEquals(
                expected,
                it.layouts.getValue("content").fullRender(it)
            )
        }
    }

    @Test
    fun nestedLayout_htmlMdHtml() {
        val toplevel =
            """
            ---
            name: toplevel
            ---
            <div id="root">{{ content }}</div>
            """.trimIndent()

        val basePage =
            """
            ---
            layout: toplevel
            name: basePage
            ---
            # {{ pageName }}

            {{content}}

            ## {{ pageName }} end
            """.trimIndent()

        val content =
            """
            ---
            layout: basePage
            name: content
            ---
            Hello {{ name }}!
            """.trimIndent()


        val expected =
            """
                <div id="root"><h1>Test page</h1>
                <p>Hello world!!</p>
                <h2>Test page end</h2>
                </div>
            """.trimIndent()

        testTemplates(
            mapOf("pageName" to "Test page", "name" to "world!"),
            listOf(
                toplevel to "html",
                basePage to "md",
                content to "md"
            )
        ) {
            assertEquals(
                expected,
                it.layouts.getValue("content").fullRender(it)
            )
        }
    }

    @Test
    fun nestedLayout_mdHtmlMd() {
        val toplevel =
            """
            ---
            name: toplevel
            ---
            <h1>The Page</h1>
            {{ content }}
            """.trimIndent()

        val basePage =
            """
            ---
            layout: toplevel
            name: basePage
            ---
            <h2>{{ pageName }}</h2>

            {{content}}

            <h3>{{ pageName }} end</h3>
            """.trimIndent()

        val content =
            """
            ---
            layout: basePage
            name: content
            ---
            Hello {{ name }}!
            """.trimIndent()


        val expected =
            """ 
                <h1>The Page</h1>
                <h2>Test page</h2>
                <p>Hello world!!</p>
                <h3>Test page end</h3>
            """.trimIndent()

        testTemplates(
            mapOf("pageName" to "Test page", "name" to "world!"),
            listOf(
                toplevel to "html",
                basePage to "html",
                content to "md"
            )
        ) {
            assertEquals(
                expected,
                it.layouts.getValue("content").fullRender(it)
            )
        }
    }

    @Test
    fun markdown() {
        testTemplate(
            """
            # Hello {{ msg }}!
            """.trimIndent(),
            ext = "md"
        ) {
            assertEquals("# Hello there!", resolveMarkdown(RenderingContext(mapOf("msg" to "there"))).code.trim())
        }
    }

    @Test
    fun mixedTemplates() {
        testTemplate(
            """
            # Hello {{ msg }}!
            """.trimIndent(),
            ext = "md"
        ) {
            assertEquals("# Hello there!", resolveMarkdown(RenderingContext(mapOf("msg" to "there"))).code.trim())
        }
    }
}