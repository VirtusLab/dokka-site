package com.virtuslab.dokka.site

import org.junit.Test
import org.junit.Assert.*
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

        testTemplates(
            mapOf("p1" to "paski", "p2" to "Hej"),
            listOf(base to "html", content to "md")
        ) {
            assertEquals(
                "<p>Ala ma kota w <strong>paski</strong>. Hej!</p>",
                it.layouts.getValue("content").resolve(it).code.trim()
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
                content to "html"
            )
        ) {
            assertEquals(
                expected,
                it.layouts.getValue("content").resolve(it).code.trim()
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
            # The Page
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
                toplevel to "md",
                basePage to "html",
                content to "md"
            )
        ) {
            assertEquals(
                expected,
                it.layouts.getValue("content").resolve(it).code.trim()
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
            assertEquals("# Hello there!", resolve(RenderingContext(mapOf("msg" to "there"))).code.trim())
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
            assertEquals("# Hello there!", resolve(RenderingContext(mapOf("msg" to "there"))).code.trim())
        }
    }
}