---
layout: vl_header
---

# Dokka site

**Generate a static documentation for in your dokka project**

**Yes, this page was generated using dokka-site**

You can learn more from out [documentation](dokka-site/index.html).

## Getting started

To install our plugin in your project add following to your `build.html` (to generate documentation from `documentation` directory)

```
dependencies {
    dokkaHtmlPlugin("com.virtuslab.dokka:dokka-site:0.1.0")
}

tasks.dokkaHtml {
    (pluginsConfiguration as MutableMap<String, String>) += "ExternalDocsTooKey" to "documentation"
}
```
