package com.virtuslab.dokka.site


import org.jetbrains.dokka.model.doc.*
import org.junit.Assert.assertEquals
import org.junit.Test


class ParserTest {
    private fun runtest(md: String, expected: DocTag) {
        val parser = ExtendableMarkdownParser(md) { null }
        val compiled = parser.parse()
        assertEquals(expected, compiled)
    }


    @Test
    fun simpleTest() = runtest("ala", P(listOf(Text("ala"))))

    @Test
    fun code() = runtest(
        """
            ```scala
            def ala() = 123
            ```
        """.trimIndent(),
        CodeBlock(listOf(Text("def ala() = 123")), mapOf("lang" to "scala"))
    )

    @Test
    fun relativeLink() = runtest(
        """
            [link](ala/maKota.md)
        """.trimIndent(),
        P(listOf(A(listOf(Text("link")), mapOf("href" to "ala/maKota.md"))))
    )

    @Test
    fun listTest() = runtest(
        """
        List:

          - element 1
          - element 2
        """.trimIndent(),
        P(
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
    )
}