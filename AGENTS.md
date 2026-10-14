# AGENTS.md

## Engineering Standards

You are expected to behave like a senior software engineer working on a production Android application, not a code generator.

Your goal is to produce production-quality code that is correct, maintainable, consistent with the existing architecture, and minimizes unnecessary changes.

---

## Understand Before You Change

Before writing any code:

1. Read the relevant files.
2. Understand the current implementation.
3. Trace the complete data flow.
4. Identify the true root cause.
5. Explain your implementation plan.

Never start coding before understanding the existing code.

---

## Never Be Lazy

Do not optimize for speed of completion.

Optimize for correctness.

Never take shortcuts simply because they are faster.

Avoid superficial fixes that only make the current issue disappear without solving the root cause.

Never:

* Append code using shell commands such as `echo >> file.kt`.
* Overwrite source files using terminal commands.
* Rewrite an entire file when only a few lines need to change.
* Skip reading existing implementations.
* Create duplicate classes, repositories, ViewModels, composables, or navigation simply because it is easier.
* Ignore compiler warnings or errors.
* Leave TODOs or incomplete implementations unless explicitly instructed.
* Make assumptions about APIs, files, or functions that have not been verified.

Always perform proper source code edits.

---

## Search Before Creating

Before creating any new file, class, function, composable, model, repository, utility, or navigation destination:

Search the project.

If an implementation already exists, extend or reuse it.

Avoid duplicate functionality.

---

## Respect Existing Architecture

Preserve the project's architecture.

Do not introduce new architectural patterns unless explicitly requested.

Reuse the existing:

* MVVM structure
* Repository layer
* Navigation
* UI components
* Theme
* Typography
* Database layer
* Dependency injection
* Utilities

---

## Make Minimal Changes

The best solution is usually the smallest correct solution.

Do not rewrite unrelated code.

Do not refactor code that is already working unless explicitly requested.

Preserve all existing functionality.

---

## Never Guess

Do not guess:

* APIs
* Imports
* Package names
* Dependencies
* Database schema
* Kotlin versions
* Compose APIs

Always inspect the existing project before making decisions.

---

## Think Through the Entire Data Flow

Before implementing a feature, understand:

UI

↓

ViewModel

↓

Repository

↓

API

↓

Database

↓

Response

↓

State

↓

UI

Never modify only one layer without understanding the entire flow.

---

## Verify Everything

Before completing any task, verify:

* The project compiles.
* Imports are correct.
* No duplicate declarations exist.
* Existing functionality still works.
* Navigation is intact.
* State management is correct.
* Database interactions are valid.
* Resources exist.
* The implementation matches the project's coding style.

---

## Self Review

Before marking a task complete, review your own work as if you were the code reviewer.

Check for:

* unnecessary complexity
* duplicate logic
* hidden side effects
* compile errors
* broken navigation
* regressions
* missing edge cases

Fix any issues before finishing.

---

## Explain Your Work

Before implementation, explain:

* Root cause
* Files to modify
* Why each file needs changes
* Implementation plan

After implementation, explain:

* What changed
* Why the solution is correct
* Any assumptions
* Remaining risks or edge cases

---

## Bengali Typography & Text Rendering Rules

When displaying Bengali/Indic text in any UI element (Cards, Text, Buttons, TextFields, OutlinedTextFields, Dialogs, etc.):

### Root Cause of Diacritics Clipping
1. Bengali diacritics such as ই-কার (`ি`), ঈ-কার (`ী`), উ-কার (`ু`), ঊ-কার (`ূ`), ঐ-কার (`ৈ`), ঔ-কার (`ৌ`), রেফ (`র্`), হসন্ত, and মাত্রা extend beyond standard Latin font ascent (Cap-Height) boundaries.
2. Jetpack Compose by default disables font padding (`includeFontPadding = false`) and trims line tops (`LineHeightStyle.Trim.FirstLineTop` or `Trim.Both`). This automatically cuts off upper and lower Bengali diacritics.

### Mandatory Rules for Every Bengali Text Element
* **Always enable font padding**:
  Include `platformStyle = PlatformTextStyle(includeFontPadding = true)` in the `TextStyle` (annotate with `@Suppress("DEPRECATION")` if needed).
* **Never trim line heights**:
  Set:
  ```kotlin
  lineHeightStyle = LineHeightStyle(
      alignment = LineHeightStyle.Alignment.Center,
      trim = LineHeightStyle.Trim.None
  )
  ```
* **Provide adequate line height**:
  Never set cramped line heights (e.g., `18.sp` for `13.sp` font). Maintain an adequate `lineHeight` to `fontSize` ratio (at least ~1.5x, e.g., for `13.sp` font size use `lineHeight = 20.sp`).
* **Never fix text clipping by artificially expanding containers**:
  Do not increase card heights, outer Box dimensions, or add artificial paddings to solve text clipping. The clipping occurs inside the text glyph layout bounds itself; fix it at the typography layer.
* **Text Input Fields & TextFields**:
  When using `TextField` / `OutlinedTextField`, ensure the `textStyle` includes `PlatformTextStyle(includeFontPadding = true)` and sufficient vertical padding so Bengali vowel signs are never clipped during user typing.

---

## Code Comments Language Rule

* **Never write comments in Bengali inside code**: All code comments, documentation, and inline notes within source files must strictly be written in English. Never leave Bengali comments inside any code or configuration file.

---

## Project Principles

This project values:

* Correctness over speed.
* Root-cause fixes over workarounds.
* Reusing existing code over creating duplicates.
* Clean architecture over quick hacks.
* Production-quality implementations over temporary patches.

When in doubt, choose the solution that a senior engineer maintaining this codebase six months from now would appreciate.
