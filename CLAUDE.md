# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

PdfPlugin is a Scala plugin for the [Simplifier](http://simplifier.io) platform that adds PDF generation capabilities via REST endpoints. It integrates with a running Simplifier AppServer instance.

## Build & Development Commands

```bash
sbt compile          # Compile
sbt test             # Run all tests
sbt assembly         # Build deployable JAR → target/pdfPlugin.jar
sbt run              # Run locally (requires Simplifier AppServer on port 8085)
```

Run a single test class:
```bash
sbt "testOnly DocumentConfigTest"
```

## Architecture

### Plugin Framework
The plugin extends `SimplifierPlugin` (from `simplifier-plugin-base`) with `PdfPluginLogic`. It registers with the Simplifier AppServer and exposes REST endpoints at `/client/2.0/pluginSlot/pdfPlugin/`.

### Slot-Based REST API
REST endpoints are implemented as *slots*, split across three services:
- **`AdministrationSlotService`** — template CRUD (add/delete/edit/fetch/list)
- **`GenerationSlotService`** — PDF generation from templates, HTML, or content repo
- **`CreationSlotService`** — direct PDF creation with inline content

Each slot has a controller in `controller/` containing the business logic, with the slot in `slots/` acting as the HTTP adapter.

### Async PDF Generation Pipeline (Actors)
PDF generation is asynchronous — endpoints return a `jobId` immediately; the result is published to the Simplifier Key-Value-Store when ready. The pipeline is implemented as Akka actor chains orchestrated by `ProcessCreation` and `ProcessGeneration`:

1. Evaluate data / fetch templates
2. Prepare temp files, fetch assets
3. Convert HTML → PDF via wkhtmltopdf (or spdf/PDFBox)
4. Merge PDFs if needed
5. Publish result to KVS

### Template System
Templates are HTML files with Mustache variables, paired with optional LESS stylesheets (compiled to CSS) and preview JSON for the admin UI. Templates are stored on the filesystem via `DirectoryTemplateStore` at the configured `pdfPlugin.storageDir` path.

### Security Model
- `DocumentConfig` parses JSON config into wkhtmltopdf CLI arguments with sanitization: drops blocklisted keys (`user-style-sheet`), rejects URL-like and absolute path values in parameters.
- JavaScript execution is disabled by default (`security.allowJavascript = false`).
- `PermissionHandler` enforces role-based access to generatePdf / administrate operations.
- VIRTUAL_HOST bypass supported for internal network resources.

## Configuration

Copy `src/main/resources/settings.conf.dist` to `settings.conf` and configure:
- `plugin.registration.host/port` — AppServer connection (default port 8085)
- `plugin.http.interface/port` — this plugin's HTTP server
- `pdfPlugin.wkhtmltopdf` — path to wkhtmltopdf binary (must be installed separately)
- `pdfPlugin.storageDir` / `pdfPlugin.tempDir` — template and working directories
- `security.allowJavascript` — enable/disable JS in PDF rendering
- `database` — only MySQL and Oracle are supported

## Key Files

| File | Purpose |
|---|---|
| `src/main/scala/.../PdfPlugin.scala` | Plugin entry point |
| `src/main/scala/.../DocumentConfig.scala` | JSON → wkhtmltopdf arg mapping + sanitization |
| `src/main/scala/.../TemplateStore.scala` | Abstract template storage interface |
| `src/main/scala/.../actor/` | Async PDF generation pipeline (Akka) |
| `src/main/scala/.../slots/` | HTTP slot adapters |
| `src/main/scala/.../controller/` | Business logic for each slot |
| `src/main/resources/plugin.json` | Plugin metadata (name, version, slots) |
| `src/test/scala/DocumentConfigTest.scala` | Tests for config parsing and security sanitization |
