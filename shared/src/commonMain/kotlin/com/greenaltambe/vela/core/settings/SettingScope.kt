package com.greenaltambe.vela.core.settings

/** Declared in order of specificity; a setting's scope is the deepest layer that may override it. */
enum class SettingScope { GLOBAL, CHANNEL, VIDEO }
