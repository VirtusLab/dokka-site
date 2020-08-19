---
layout: vl_header
title: Layouts
---

# Layouts
Layouts allow to create a template for ours pages.  
They can be used to provide some shared parts across the pages (e.g. headers or footers).  
Both `.md` and `.html` formats are supported.

## Layout definition
The layout must be defined under `<root>/_layouts` directory.  
It must contain the `{{ "{{ content " }}}}` label indicates the place where the content will be rendered here.  

### basic_layout.md
```md
# Hello World!
{{ "{{ content " }}}}
```

## Layout usage
To use a layout one must declare it within the config section of the file  
(the config section is the section between the pair of `---`).  

### page.md
```md
---
layout: basic_layout
---
Year 2020
```
  
## The result
Given the layout and the page from above the following page will be created:  

```md
# Hello World!
Year 2020
```
  
## Additional information

Note that a layout can declare a layout.

___
