package com.jvn.scala.dsl

import com.jvn.core.menu.config.{MenuStyleSpec, MenuLayoutSpec, MenuButtonLayoutSpec}
import scala.collection.mutable.ListBuffer
import scala.jdk.CollectionConverters.*

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
    private var _hintsFontWeight: String = null
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

    def hints(color: String, fontFamily: String = null, fontWeight: String = null, fontSize: Int = -1): Unit =
      _hintsColor = color
      _hintsFontFamily = fontFamily
      _hintsFontWeight = fontWeight
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
      _hintsColor, _hintsFontFamily, _hintsFontWeight, _hintsFontSize,
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
    private var _listXCenter: java.lang.Double = null
    private var _titleX: java.lang.Double = null
    private var _maxVisibleItems: java.lang.Integer = null

    def listYStart(v: Double): Unit = _listYStart = v
    def lineHeight(v: Double): Unit = _lineHeight = v
    def listWidthFactor(v: Double): Unit = _listWidthFactor = v
    def textAlign(v: String): Unit = _textAlign = v
    def hintsBottomMargin(v: Double): Unit = _hintsBottomMargin = v
    def titleY(v: Double): Unit = _titleY = v
    def listXCenter(v: Double): Unit = _listXCenter = v
    def titleX(v: Double): Unit = _titleX = v
    def maxVisibleItems(v: Int): Unit = _maxVisibleItems = v

    def build(): MenuLayoutSpec = new MenuLayoutSpec(
      id, _listYStart, _lineHeight, _listWidthFactor, _textAlign, _hintsBottomMargin, _titleY,
      _listXCenter, _titleX, _maxVisibleItems
    )

  def menuLayout(id: String)(configure: LayoutBuilder ?=> Unit): MenuLayoutSpec =
    val builder = LayoutBuilder(id)
    configure(using builder)
    builder.build()

  // --- Button layout builder ---

  /**
   * Builder for individual button bounds within a button layout.
   */
  class ButtonBoundsBuilder(val id: String):
    private var _label: String = null
    private var _tag: String = null
    private var _boundsX: java.lang.Double = null
    private var _boundsY: java.lang.Double = null
    private var _boundsW: java.lang.Double = null
    private var _boundsH: java.lang.Double = null
    private var _asset: String = null
    private var _hoverAsset: String = null
    private var _disabledAsset: String = null
    private val _extras = scala.collection.mutable.LinkedHashMap[String, String]()

    def label(v: String): Unit = _label = v
    def tag(v: String): Unit = _tag = v

    def bounds(x: Double, y: Double, w: Double, h: Double): Unit =
      _boundsX = x; _boundsY = y; _boundsW = w; _boundsH = h

    def boundsX(v: Double): Unit = _boundsX = v
    def boundsY(v: Double): Unit = _boundsY = v
    def boundsW(v: Double): Unit = _boundsW = v
    def boundsH(v: Double): Unit = _boundsH = v

    def asset(normal: String, hover: String = null, disabled: String = null): Unit =
      _asset = normal
      if hover != null then _hoverAsset = hover
      if disabled != null then _disabledAsset = disabled

    def extra(key: String, value: String): Unit = _extras(key) = value

    def build(): MenuButtonLayoutSpec.ButtonBounds =
      new MenuButtonLayoutSpec.ButtonBounds(
        id, _label, _tag,
        _boundsX, _boundsY, _boundsW, _boundsH,
        _asset, _hoverAsset, _disabledAsset,
        _extras.asJava
      )

  /**
   * Builder for a complete button layout spec.
   *
   * Usage:
   * {{{
   *   val layout = buttonLayout("main", resolution = "1920x1080", menuType = "main") {
   *     button("new_game") {
   *       label("New Game")
   *       tag("primary")
   *       bounds(0.25, 0.30, 0.50, 0.08)
   *       asset("assets/ui/btn.png", hover = "assets/ui/btn_hover.png")
   *     }
   *     button("load") {
   *       label("Load Game")
   *       bounds(0.25, 0.40, 0.50, 0.08)
   *     }
   *   }
   * }}}
   */
  class ButtonLayoutBuilder(val menuId: String, val resolution: String, val menuType: String):
    private val _buttons = ListBuffer[MenuButtonLayoutSpec.ButtonBounds]()
    private val _extras = scala.collection.mutable.LinkedHashMap[String, String]()

    def button(id: String)(configure: ButtonBoundsBuilder ?=> Unit): Unit =
      val builder = ButtonBoundsBuilder(id)
      configure(using builder)
      _buttons += builder.build()

    def extra(key: String, value: String): Unit = _extras(key) = value

    def build(): MenuButtonLayoutSpec =
      new MenuButtonLayoutSpec(
        menuId, resolution, menuType,
        _buttons.toList.asJava,
        _extras.asJava
      )

  def buttonLayout(
      menuId: String,
      resolution: String = "default",
      menuType: String = null
  )(configure: ButtonLayoutBuilder ?=> Unit): MenuButtonLayoutSpec =
    val builder = ButtonLayoutBuilder(menuId, resolution, menuType)
    configure(using builder)
    builder.build()
