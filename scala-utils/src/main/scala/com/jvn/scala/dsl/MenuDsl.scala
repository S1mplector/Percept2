package com.jvn.scala.dsl

import com.jvn.core.menu.config.{MenuStyleSpec, MenuLayoutSpec}

/**
 * Type-safe Scala DSL for building JVN menu styles and layouts.
 *
 * Usage:
 * {{{
 *   import com.jvn.scala.dsl.MenuDsl.*
 *
 *   val style = menuStyle("neon") {
 *     itemColor("#00ff88")
 *     selectedColor("#ffff00")
 *     hoverColor("#66ffaa")
 *     disabledColor("#555555")
 *     font("Monospace", "BOLD", 24)
 *     shadow("#00000088", 2.0, 2.0)
 *     opacity(0.95)
 *     prefix("  ", "> ", "- ")
 *     buttonAssets("btn.png", "btn_sel.png", "btn_hover.png", "btn_dis.png")
 *     buttonPadding(18.0, 0.0)
 *     title("#ffffff", "Georgia", "BOLD", 36, "#000000")
 *     hints("#aaaaaa", "Arial", 14)
 *     background("bg.png", "#1a1a2e", 0.9)
 *   }
 *
 *   val layout = menuLayout("compact") {
 *     listYStart(0.4)
 *     lineHeight(36.0)
 *     listWidthFactor(0.8)
 *     textAlign("center")
 *     hintsBottomMargin(16.0)
 *     titleY(50.0)
 *   }
 * }}}
 */
object MenuDsl:

  // --- Style builder ---

  class StyleBuilder(val id: String):
    private var _itemColor: String = null
    private var _selectedColor: String = null
    private var _hoverColor: String = null
    private var _disabledColor: String = null
    private var _prefix: String = null
    private var _selectedPrefix: String = null
    private var _disabledPrefix: String = null
    private var _fontFamily: String = null
    private var _fontWeight: String = null
    private var _fontSize: Integer = null
    private var _shadowColor: String = null
    private var _shadowOffsetX: java.lang.Double = null
    private var _shadowOffsetY: java.lang.Double = null
    private var _opacity: java.lang.Double = null
    private var _buttonAsset: String = null
    private var _buttonSelectedAsset: String = null
    private var _buttonHoverAsset: String = null
    private var _buttonDisabledAsset: String = null
    private var _buttonPaddingX: java.lang.Double = null
    private var _buttonPaddingY: java.lang.Double = null
    private var _titleColor: String = null
    private var _titleFontFamily: String = null
    private var _titleFontWeight: String = null
    private var _titleFontSize: Integer = null
    private var _titleShadowColor: String = null
    private var _hintsColor: String = null
    private var _hintsFontFamily: String = null
    private var _hintsFontSize: Integer = null
    private var _bgAsset: String = null
    private var _bgColor: String = null
    private var _bgOpacity: java.lang.Double = null

    def itemColor(c: String): Unit = _itemColor = c
    def selectedColor(c: String): Unit = _selectedColor = c
    def hoverColor(c: String): Unit = _hoverColor = c
    def disabledColor(c: String): Unit = _disabledColor = c

    def prefix(normal: String, selected: String = null, disabled: String = null): Unit =
      _prefix = normal
      if selected != null then _selectedPrefix = selected
      if disabled != null then _disabledPrefix = disabled

    def font(family: String, weight: String = "NORMAL", size: Int = 20): Unit =
      _fontFamily = family
      _fontWeight = weight
      _fontSize = size

    def shadow(color: String, offsetX: Double = 0, offsetY: Double = 0): Unit =
      _shadowColor = color
      _shadowOffsetX = offsetX
      _shadowOffsetY = offsetY

    def opacity(v: Double): Unit = _opacity = v

    def buttonAssets(normal: String, selected: String = null, hover: String = null, disabled: String = null): Unit =
      _buttonAsset = normal
      _buttonSelectedAsset = selected
      _buttonHoverAsset = hover
      _buttonDisabledAsset = disabled

    def buttonPadding(x: Double, y: Double = 0): Unit =
      _buttonPaddingX = x
      _buttonPaddingY = y

    def title(color: String, fontFamily: String = null, fontWeight: String = null, fontSize: Int = -1, shadowColor: String = null): Unit =
      _titleColor = color
      _titleFontFamily = fontFamily
      _titleFontWeight = fontWeight
      if fontSize > 0 then _titleFontSize = fontSize
      _titleShadowColor = shadowColor

    def hints(color: String, fontFamily: String = null, fontSize: Int = -1): Unit =
      _hintsColor = color
      _hintsFontFamily = fontFamily
      if fontSize > 0 then _hintsFontSize = fontSize

    def background(asset: String = null, color: String = null, opacity: Double = 1.0): Unit =
      _bgAsset = asset
      _bgColor = color
      if opacity < 1.0 then _bgOpacity = opacity

    def build(): MenuStyleSpec = new MenuStyleSpec(
      id,
      _itemColor, _selectedColor, _hoverColor, _disabledColor,
      _prefix, _selectedPrefix, _disabledPrefix,
      _fontFamily, _fontWeight, _fontSize,
      _shadowColor, _shadowOffsetX, _shadowOffsetY, _opacity,
      _buttonAsset, _buttonSelectedAsset, _buttonHoverAsset, _buttonDisabledAsset,
      _buttonPaddingX, _buttonPaddingY,
      _titleColor, _titleFontFamily, _titleFontWeight, _titleFontSize, _titleShadowColor,
      _hintsColor, _hintsFontFamily, _hintsFontSize,
      _bgAsset, _bgColor, _bgOpacity
    )

  def menuStyle(id: String)(configure: StyleBuilder ?=> Unit): MenuStyleSpec =
    val builder = StyleBuilder(id)
    configure(using builder)
    builder.build()

  // --- Layout builder ---

  class LayoutBuilder(val id: String):
    private var _listYStart: Double = 0.35
    private var _lineHeight: Double = 40.0
    private var _listWidthFactor: Double = 1.0
    private var _textAlign: String = "center"
    private var _hintsBottomMargin: Double = 20.0
    private var _titleY: java.lang.Double = null

    def listYStart(v: Double): Unit = _listYStart = v
    def lineHeight(v: Double): Unit = _lineHeight = v
    def listWidthFactor(v: Double): Unit = _listWidthFactor = v
    def textAlign(v: String): Unit = _textAlign = v
    def hintsBottomMargin(v: Double): Unit = _hintsBottomMargin = v
    def titleY(v: Double): Unit = _titleY = v

    def build(): MenuLayoutSpec = new MenuLayoutSpec(
      id, _listYStart, _lineHeight, _listWidthFactor, _textAlign, _hintsBottomMargin, _titleY
    )

  def menuLayout(id: String)(configure: LayoutBuilder ?=> Unit): MenuLayoutSpec =
    val builder = LayoutBuilder(id)
    configure(using builder)
    builder.build()
