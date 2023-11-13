ThisBuild / organization := "com.itizzimo"
ThisBuild / version := sys.env.get("VERSION").getOrElse("NA")
ThisBuild / scalaVersion := "2.12.15"

ThisBuild / useCoursier := true


lazy val contentRepoPlugin = (project in file("."))
  .settings(
    name := "PdfPlugin",
    assembly / assemblyJarName := "pdfPlugin.jar",
    assembly / test := {},
    assembly / assemblyOutputPath := file(s"${target.value.toString}/${(assembly/assemblyJarName).value}"),
    assembly / assemblyMergeStrategy := {
      case x if x.endsWith("module-info.class") => MergeStrategy.discard
      case x =>
        val oldStrategy = (assembly / assemblyMergeStrategy).value
        oldStrategy(x)
    },
    libraryDependencies ++= Seq(
      "io.github.simplifier-ag" %% "simplifier-plugin-base" % "1.0.0" withSources(),
      "io.github.simplifier-ag" %% "spdf" % "1.5.0" withSources() withJavadoc(),
      "com.typesafe" %% "ssl-config-core" % "0.6.1" withSources() withJavadoc(),
      "org.apache.pdfbox" % "pdfbox" % "1.8.10" withSources() withJavadoc(),
      "org.scalatra.scalate" %% "scalate-core" % "1.9.8" withSources() withJavadoc() ,
      "org.lesscss" % "lesscss" % "1.7.0.1.1" withSources() withJavadoc() exclude("org.slf4j", "slf4j-simple"),
      "org.jsoup" % "jsoup" % "1.8.3" withSources() withJavadoc(),
      "org.scalatest" %% "scalatest" % "3.1.4" % "test" withSources() withJavadoc(),
    )
  )

