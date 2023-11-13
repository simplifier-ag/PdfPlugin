package com.itizzimo.pdfplugin.interfaces

import io.simplifier.pluginapi.RegistrationData.PluginConfigurationView
import io.simplifier.pluginbase.interfaces.DefaultConfigurationInterfaceService

class ConfigInterface(baseUrl: String, resourceDirectory: String, views: Seq[PluginConfigurationView])
  extends DefaultConfigurationInterfaceService(baseUrl, resourceDirectory, views)