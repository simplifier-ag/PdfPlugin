# Simplifier PDF-Plugin

## Introduction

The PDF-Plugin is an extension to [Simplifier](http://simplifier.io), adding the capability of creating PDF documents. 

Simplifier Community docs: [https://community.simplifier.io](https://community.simplifier.io/doc/current-release/extend/plugins/list-of-plugins/pdf-plugin/)


[Plugin documentation](documentation/plugin.md)



## Deployment

### Docker Image

The build creates a base image with the necessary files to add to the Simplifier appserver container.

#### Prerequisites

docker

Scala 2.12, sbt

JDK of the target platform (GraalVM-CE 20.0.2)

#### Cross build options
TBD



#### build and test

At the commandline, run
```bash
sbt test
```
```bash
sbt dockerBuild
```

#### Configiration

The created image is named after the sbt projects name and version. So the result would be something like "pdfplugin:0.0.1-SNAPSHOT"

Settings and commandline arguments may be changed by editing [./deployment/assets](./deployment/assets)

The setup process itself is defined by [./deployment/setup.sh](./deployment/setup.sh). 


### Appserver build

The docker image from the previous step has to be available in the registry when an appserver image is built.

In the appserver Dockerfile, refer to it as follows:

```dockerfile
FROM pdfplugin:0.0.1-SNAPSHOT as pdfplugin
```

Below  ```FROM simplifierag/simplifierbase```, insert the following lines:
```dockerfile
# install PDF plugin
COPY --from=pdfplugin /opt/plugin /tmp/pdfPlugin
RUN /tmp/pdfPlugin/setup.sh /opt/simplifier
```


## Manual Installation

You need to install the program [wkhtmltopdf](http://wkhtmltopdf.org/).

Copy the file [settings.conf.dist](./src/main/resources/settings.conf.dist) to your installation path and edit the values as needed.
When launching the jar, the config file must be given as a commandline argument.

 Property `pdfPlugin.wkhtmltopdf` must point to the location of the `wkhtmltopdf` executable.

`pdfPlugin.storageDir` and `pdfPlugin.tempDir` must point to writable paths for storing PDF data.




