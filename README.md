# Simplifier PDF-Plugin

## Introduction

The PDF-Plugin is an extension to [Simplifier](http://simplifier.io), adding the capability of creating PDF documents. 

Simplifier Community docs: [https://community.simplifier.io](https://community.simplifier.io/doc/current-release/extend/plugins/list-of-plugins/pdf-plugin/)


## Documentation

See here for a short [Plugin documentation](documentation/plugin.md).

## Development

For MC2510 / LTS10 or newer: When changing the UI, the `ui5lint` tool should be
run. As it requires a UI5 project, you should copy the files into the sources of
the monolith:
```
cp src/main/resources/assets/adminui/pdfGenerator.controller.js $SIMPLIFIER_DIR/src/main/assets/ui/webapp/controller/
cp src/main/resources/assets/adminui/pdfGenerator.view.xml $SIMPLIFIER_DIR/src/main/assets/ui/webapp/views/
cd $SIMPLIFIER_DIR/src/main/assets/ui/
ui5lint
```

Remember to remove the files afterwards.

## Deployment

You can run the plugin locally. Your Simplifier AppServer has to be running and has to be accessible locally.


## Local Deployment

The build runs the process with SBT locally, Simplifier's plugin registration port and the plugin communication port must be accessible locally.


### Prerequisites

- A plugin secret has to be available. It can be created in the Plugins section of Simplifier,
  please look [here for an instruction](https://community.simplifier.io/doc/current-release/extend/plugins/plugin-secrets/).
- replace the default secret: <b>XXXXXX</b> in the [PluginRegistrationSecret](./src/main/scala/byDeployment/PluginRegistrationSecret.scala)
  class with the actual secret.
- Simplifier must be running and the <b>plugin.registration</b> in the settings must be configured accordingly.


### Preparation

#### Manual Installation of the underlying conversion software

You need to install the program [wkhtmltopdf](http://wkhtmltopdf.org/).

#### Simplifier Configuration Modification

Copy the file [settings.conf.dist](./src/main/resources/settings.conf.dist) as <b>settings.conf</b> to your installation path and edit the values as needed.
When launching the jar, the config file must be given as a commandline argument.


- The Property `pdfPlugin.wkhtmltopdf` must point to the location of the `wkhtmltopdf` executable.
- The `pdfPlugin.storageDir` and `pdfPlugin.tempDir` must point to writable paths for storing PDF data.
- Provide the correct ```database``` data according to your local setup __note__: Only MySQL and Oracle are supported!
- Provide the correct ```filename``` path according to your local setup.
- The flag `security.allowJavascript` allows to toggle server side Javascript execution during PDF rendering. 
This setting can be overridden by the environment variable `PDFPLUGIN_SECURITY_ALLOW_JAVASCRIPT`. 
It is recommended to set it to _false_ for increased securtity (default for Simplifier >= 8.EHP3, using PdfPlugin >= 0.4.0).



### Build and Run

At the commandline, run
```bash
sbt compile
```

and then

```bash
sbt run
```