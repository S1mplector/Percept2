package com.jvn.scala.dsl

import com.jvn.core.vn.*
import scala.collection.mutable.ListBuffer

/**
 * Type-safe Scala DSL for building VN scenes programmatically.
 *
 * Usage:
 * {{{
 *   import com.jvn.scala.dsl.SceneDsl.*
 *
 *   val scenario = scene("prologue") {
 *     character("codel", "Codel")
 *     background("classroom", "assets/backgrounds/classroom.png")
 *
 *     bg("classroom")
 *     show("codel", CharacterPosition.CENTER, "neutral")
 *     say("codel", "Welcome to the tutorial!")
 *     say("codel", "Let me show you around.")
 *
 *     choice(
 *       "Sure!" -> "accept_path",
 *       "Maybe later" -> "decline_path"
 *     )
 *
 *     label("accept_path")
 *     say("codel", "Great! Let's go!")
 *     transition(VnTransition.TransitionType.FADE, 500)
 *
 *     label("decline_path")
 *     say("codel", "No worries, take your time.")
 *     endScene()
 *   }
 * }}}
 */
object SceneDsl:

  class SceneBuilder(val id: String):
    private val builder = new VnScenarioBuilder(id)

    def character(id: String, displayName: String): Unit =
      builder.addCharacter(id, displayName)

    def background(id: String, path: String): Unit =
      builder.addBackground(id, path)

    def bg(id: String): Unit =
      builder.background(id)

    def show(charId: String, position: CharacterPosition, expression: String = "neutral"): Unit =
      builder.show(charId, expression, position)

    def hide(charId: String): Unit =
      builder.hide(charId)

    def say(speakerId: String, text: String): Unit =
      builder.dialogue(speakerId, text)

    def narrate(text: String): Unit =
      builder.dialogue("", text)

    def choice(options: (String, String)*): Unit =
      val choices = new java.util.ArrayList[Choice]()
      for (label, target) <- options do
        choices.add(Choice.builder().text(label).targetLabel(target).build())
      builder.choices(choices)

    def label(name: String): Unit =
      builder.label(name)

    def jump(target: String): Unit =
      builder.jump(target)

    def waitMs(ms: Long): Unit =
      builder.waitMs(ms)

    def transition(transType: VnTransition.TransitionType, durationMs: Long, targetBg: String = null): Unit =
      builder.transition(transType, durationMs, targetBg)

    def playBgm(track: String, loop: Boolean = true): Unit =
      builder.playBgm(track, loop)

    def stopBgm(): Unit =
      builder.stopBgm()

    def playSfx(track: String): Unit =
      builder.playSfx(track)

    def external(provider: String, payload: String): Unit =
      builder.external(provider, payload)

    def endScene(): Unit =
      builder.end()

    def build(): VnScenario = builder.build()

  def scene(id: String)(configure: SceneBuilder ?=> Unit): VnScenario =
    val builder = SceneBuilder(id)
    configure(using builder)
    builder.build()
