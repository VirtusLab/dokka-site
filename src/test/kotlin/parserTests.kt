package com.virtuslab.dokka.site


import org.intellij.markdown.MarkdownElementTypes.MARKDOWN_FILE
import org.jetbrains.dokka.base.parsers.MarkdownParser
import org.jetbrains.dokka.base.parsers.factories.DocTagsFromIElementFactory
import org.jetbrains.dokka.model.doc.*
import org.junit.Assert.assertEquals
import org.junit.Test


class ParserTest {
    private fun runTest(md: String, expected: List<DocTag>) {
        val parser = MarkdownParser { null }
        val compiled = parser.parseStringToDocNode(md)
        val expectedWrapped = DocTagsFromIElementFactory.getInstance(MARKDOWN_FILE, expected)
        assertEquals(expectedWrapped, compiled)
    }


    @Test
    fun simpleTest() = runTest("ala", listOf(P(listOf(Text("ala")))))

    @Test
    fun code() = runTest(
        """
            ```scala
            def ala() = 123
            ```
        """.trimIndent(),
        listOf(CodeBlock(listOf(Text("def ala() = 123")), mapOf("lang" to "scala")))
    )

    @Test
    fun relativeLink() = runTest(
        """
            [link](ala/maKota.md)
        """.trimIndent(),
        listOf(P(listOf(Text(body = "ala/maKota.md", listOf(Text("link")), mapOf("href" to "ala/maKota.md")))))
    )

    @Test
    fun listTest() = runTest(
        """
        List:

          - element 1
          - element 2
        """.trimIndent(),
        listOf(
            P(listOf(Text("List:"))),
            Ul(
                listOf(
                    Li(listOf(P(listOf(Text("element 1"))))),
                    Li(listOf(P(listOf(Text("element 2")))))
                )
            )
        )
    )
}
