package com.jvn.scala.dsl

import com.jvn.core.animation.{TimelineData, Easing}
import scala.collection.mutable.ListBuffer

/**
 * Type-safe Scala DSL for building JVN animation timelines.
 *
 * Usage:
 * {{{
 *   import com.jvn.scala.dsl.TimelineDsl.*
 *
 *   val tl = timeline("entrance", 2000) {
 *     move("codel") {
 *       x(640)
 *       y(468)
 *       dur(340)
 *       easing(Easing.Type.EASE_IN_OUT)
 *     }
 *     waitMs(340)
 *     fade("codel") {
 *       alpha(0.0)
 *       dur(500)
 *     }
 *     scale("codel") {
 *       scaleX(2.0)
 *       scaleY(2.0)
 *       dur(200)
 *     }
 *     rotate("codel") {
 *       angle(45.0)
 *       dur(300)
 *     }
 *   }
 * }}}
 */
object TimelineDsl:

  sealed trait Action
  case class MoveAction(entity: String, x: Option[Double], y: Option[Double], dur: Double, easing: Easing.Type) extends Action
  case class FadeAction(entity: String, alpha: Double, dur: Double, easing: Easing.Type) extends Action
  case class ScaleAction(entity: String, sx: Option[Double], sy: Option[Double], dur: Double, easing: Easing.Type) extends Action
  case class RotateAction(entity: String, angle: Double, dur: Double, easing: Easing.Type) extends Action
  case class WaitAction(ms: Double) extends Action

  // --- Action builders ---

  class MoveBuilder(val entity: String):
    private var _x: Option[Double] = None
    private var _y: Option[Double] = None
    private var _dur: Double = 0
    private var _easing: Easing.Type = Easing.Type.LINEAR
    def x(v: Double): Unit = _x = Some(v)
    def y(v: Double): Unit = _y = Some(v)
    def dur(v: Double): Unit = _dur = v
    def easing(e: Easing.Type): Unit = _easing = e
    def build(): MoveAction = MoveAction(entity, _x, _y, _dur, _easing)

  class FadeBuilder(val entity: String):
    private var _alpha: Double = 1.0
    private var _dur: Double = 0
    private var _easing: Easing.Type = Easing.Type.LINEAR
    def alpha(v: Double): Unit = _alpha = v
    def dur(v: Double): Unit = _dur = v
    def easing(e: Easing.Type): Unit = _easing = e
    def build(): FadeAction = FadeAction(entity, _alpha, _dur, _easing)

  class ScaleBuilder(val entity: String):
    private var _sx: Option[Double] = None
    private var _sy: Option[Double] = None
    private var _dur: Double = 0
    private var _easing: Easing.Type = Easing.Type.LINEAR
    def scaleX(v: Double): Unit = _sx = Some(v)
    def scaleY(v: Double): Unit = _sy = Some(v)
    def dur(v: Double): Unit = _dur = v
    def easing(e: Easing.Type): Unit = _easing = e
    def build(): ScaleAction = ScaleAction(entity, _sx, _sy, _dur, _easing)

  class RotateBuilder(val entity: String):
    private var _angle: Double = 0
    private var _dur: Double = 0
    private var _easing: Easing.Type = Easing.Type.LINEAR
    def angle(v: Double): Unit = _angle = v
    def dur(v: Double): Unit = _dur = v
    def easing(e: Easing.Type): Unit = _easing = e
    def build(): RotateAction = RotateAction(entity, _angle, _dur, _easing)

  // --- Timeline builder ---

  class TimelineBuilder(val name: String, val durationMs: Double):
    private val actions = ListBuffer[Action]()
    private var cursor: Double = 0

    def move(entity: String)(configure: MoveBuilder ?=> Unit): Unit =
      val b = MoveBuilder(entity)
      configure(using b)
      actions += b.build()

    def fade(entity: String)(configure: FadeBuilder ?=> Unit): Unit =
      val b = FadeBuilder(entity)
      configure(using b)
      actions += b.build()

    def scale(entity: String)(configure: ScaleBuilder ?=> Unit): Unit =
      val b = ScaleBuilder(entity)
      configure(using b)
      actions += b.build()

    def rotate(entity: String)(configure: RotateBuilder ?=> Unit): Unit =
      val b = RotateBuilder(entity)
      configure(using b)
      actions += b.build()

    def waitMs(ms: Double): Unit =
      actions += WaitAction(ms)

    def build(): TimelineData =
      val data = new TimelineData(name, durationMs)
      var time = 0.0
      for action <- actions do
        action match
          case WaitAction(ms) =>
            time += ms
          case MoveAction(entity, x, y, dur, easing) =>
            val track = getOrCreateTrack(data, entity)
            val endTime = time + dur
            x.foreach(v => track.addKeyframe(TimelineData.Property.X, new TimelineData.Keyframe(endTime, v, easing)))
            y.foreach(v => track.addKeyframe(TimelineData.Property.Y, new TimelineData.Keyframe(endTime, v, easing)))
          case FadeAction(entity, alpha, dur, easing) =>
            val track = getOrCreateTrack(data, entity)
            track.addKeyframe(TimelineData.Property.ALPHA, new TimelineData.Keyframe(time + dur, alpha, easing))
          case ScaleAction(entity, sx, sy, dur, easing) =>
            val track = getOrCreateTrack(data, entity)
            val endTime = time + dur
            sx.foreach(v => track.addKeyframe(TimelineData.Property.SCALE_X, new TimelineData.Keyframe(endTime, v, easing)))
            sy.foreach(v => track.addKeyframe(TimelineData.Property.SCALE_Y, new TimelineData.Keyframe(endTime, v, easing)))
          case RotateAction(entity, angle, dur, easing) =>
            val track = getOrCreateTrack(data, entity)
            track.addKeyframe(TimelineData.Property.ROTATION, new TimelineData.Keyframe(time + dur, angle, easing))
      data

    private def getOrCreateTrack(data: TimelineData, entity: String): TimelineData.Track =
      val existing = data.getTrack(entity)
      if existing != null then existing
      else
        val track = new TimelineData.Track(entity)
        data.addTrack(track)
        track

  def timeline(name: String, durationMs: Double = 0)(configure: TimelineBuilder ?=> Unit): TimelineData =
    val builder = TimelineBuilder(name, durationMs)
    configure(using builder)
    builder.build()
