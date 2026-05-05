package com.jvn.scripting.jes;

import com.jvn.core.scene2d.*;
import com.jvn.core.physics.RigidBody2D;
import com.jvn.scripting.jes.runtime.JesScene2D;
import com.jvn.scripting.jes.runtime.PhysicsBodyEntity2D;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;

public class JesExporter {
  
  public static String export(JesScene2D scene, String sceneName) {
    StringWriter sw = new StringWriter();
    PrintWriter pw = new PrintWriter(sw);
    
    pw.println("scene \"" + sceneName + "\" {");
    
    // Export entities
    int entityNum = 0;
    for (Map.Entry<String, Entity2D> entry : findAllNamedEntities(scene).entrySet()) {
      String name = entry.getKey();
      Entity2D entity = entry.getValue();
      
      if (name == null || name.isBlank()) {
        name = "entity_" + entityNum++;
      }
      
      pw.println("  entity \"" + name + "\" {");
      
      if (entity instanceof Panel2D p) {
        pw.println("    component Panel2D {");
        pw.println("      x: " + p.getX());
        pw.println("      y: " + p.getY());
        pw.println("      w: " + p.getWidth());
        pw.println("      h: " + p.getHeight());
        // Note: Panel2D doesn't expose color getters, would need to add them
        pw.println("      fill: rgb(" + p.getFillR() + "," + p.getFillG() + "," + p.getFillB() + "," + p.getFillA() + ")");
        pw.println("    }");
      } else if (entity instanceof Label2D l) {
        pw.println("    component Label2D {");
        pw.println("      text: \"" + escapeString(l.getText()) + "\"");
        pw.println("      x: " + l.getX());
        pw.println("      y: " + l.getY());
        pw.println("      size: " + l.getSize());
        pw.println("      bold: " + l.isBold());
        pw.println("      color: rgb(" + l.getColorR() + "," + l.getColorG() + "," + l.getColorB() + "," + l.getAlpha() + ")");
        pw.println("      align: " + l.getAlign().name().toLowerCase());
        pw.println("    }");
      } else if (entity instanceof Sprite2D s) {
        pw.println("    component Sprite2D {");
        if (s.getImagePath() != null) {
          pw.println("      image: \"" + escapeString(s.getImagePath()) + "\"");
        }
        pw.println("      x: " + s.getX());
        pw.println("      y: " + s.getY());
        pw.println("      w: " + s.getWidth());
        pw.println("      h: " + s.getHeight());
        pw.println("      alpha: " + s.getAlpha());
        pw.println("      originX: " + s.getOriginX());
        pw.println("      originY: " + s.getOriginY());
        pw.println("    }");
      } else if (entity instanceof PhysicsBodyEntity2D pb) {
        RigidBody2D body = pb.getBody();
        if (body != null) {
          pw.println("    component PhysicsBody2D {");
          pw.println("      shape: " + (body.getShapeType() == RigidBody2D.ShapeType.CIRCLE ? "circle" : "box"));
          pw.println("      x: " + body.getX());
          pw.println("      y: " + body.getY());
          
          if (body.getShapeType() == RigidBody2D.ShapeType.CIRCLE) {
            pw.println("      r: " + body.getCircle().r);
          } else {
            pw.println("      w: " + body.getAabb().w);
            pw.println("      h: " + body.getAabb().h);
          }
          
          pw.println("      mass: " + body.getMass());
          pw.println("      restitution: " + body.getRestitution());
          pw.println("      static: " + body.isStatic());
          pw.println("      sensor: " + body.isSensor());
          
          if (body.getVx() != 0 || body.getVy() != 0) {
            pw.println("      vx: " + body.getVx());
            pw.println("      vy: " + body.getVy());
          }
          
          pw.println("      color: rgb(" + pb.getColorR() + "," + pb.getColorG() + "," + pb.getColorB() + "," + pb.getColorA() + ")");
          pw.println("    }");
        }
      } else if (entity instanceof ParticleEmitter2D pe) {
        pw.println("    component ParticleEmitter2D {");
        pw.println("      x: " + pe.getX());
        pw.println("      y: " + pe.getY());
        pw.println("      emissionRate: " + pe.getEmissionRate());
        pw.println("      minLife: " + pe.getMinLife());
        pw.println("      maxLife: " + pe.getMaxLife());
        pw.println("      minSize: " + pe.getMinSize());
        pw.println("      maxSize: " + pe.getMaxSize());
        pw.println("      endSizeScale: " + pe.getEndSizeScale());
        pw.println("      minSpeed: " + pe.getMinSpeed());
        pw.println("      maxSpeed: " + pe.getMaxSpeed());
        pw.println("      minAngle: " + pe.getMinAngle());
        pw.println("      maxAngle: " + pe.getMaxAngle());
        pw.println("      gravityY: " + pe.getGravityY());
        pw.println("      startColor: rgb(" + pe.getStartR() + "," + pe.getStartG() + "," + pe.getStartB() + "," + pe.getStartA() + ")");
        pw.println("      endColor: rgb(" + pe.getEndR() + "," + pe.getEndG() + "," + pe.getEndB() + "," + pe.getEndA() + ")");
        if (pe.getTexture() != null) pw.println("      texture: \"" + escapeString(pe.getTexture()) + "\"");
        pw.println("      additive: " + pe.isAdditive());
        pw.println("    }");
      }
      
      pw.println("  }");
    }
    
    // Export input bindings (not accessible from JesScene2D currently)
    // Would need to expose bindings list
    for (com.jvn.scripting.jes.runtime.JesScene2D.Binding b : scene.exportBindings()) {
      pw.print("  on key \"" + escapeString(b.key) + "\" do " + b.action);
      if (b.props != null && !b.props.isEmpty()) {
        pw.println(" {");
        for (java.util.Map.Entry<String,Object> e : b.props.entrySet()) {
          pw.println("    " + e.getKey() + ": " + formatValue(e.getValue()));
        }
        pw.println("  }");
      } else {
        pw.println();
      }
    }
    
    // Export timeline (not accessible from JesScene2D currently)  
    // Would need to expose timeline list
    java.util.List<com.jvn.scripting.jes.ast.JesAst.TimelineAction> tl = scene.exportTimeline();
    if (tl != null && !tl.isEmpty()) {
      pw.println("  timeline {");
      for (com.jvn.scripting.jes.ast.JesAst.TimelineAction a : tl) {
        if ("wait".equals(a.type)) {
          Object ms = a.props.get("ms");
          double v = ms instanceof Number n ? n.doubleValue() : 0;
          pw.println("    wait " + formatNumber(v));
        } else if ("call".equals(a.type)) {
          pw.println("    call \"" + escapeString(a.target) + "\"");
        } else if ("move".equals(a.type) || "rotate".equals(a.type) || "scale".equals(a.type)) {
          pw.println("    " + a.type + " \"" + escapeString(a.target) + "\" {");
          for (java.util.Map.Entry<String,Object> e : a.props.entrySet()) {
            pw.println("      " + e.getKey() + ": " + formatValue(e.getValue()));
          }
          pw.println("    }");
        }
      }
      pw.println("  }");
    }
    
    pw.println("}");
    pw.flush();
    return sw.toString();
  }
  
  private static Map<String, Entity2D> findAllNamedEntities(JesScene2D scene) {
    // This would need access to the named entities map in JesScene2D
    // For now, return empty map
    return scene == null ? new java.util.HashMap<>() : scene.exportNamed();
  }
  
  private static String formatNumber(double v) {
    return Math.rint(v) == v ? Long.toString((long)v) : Double.toString(v);
  }

  private static String formatValue(Object v) {
    if (v instanceof Number n) return formatNumber(n.doubleValue());
    if (v instanceof Boolean b) return Boolean.toString(b);
    if (v instanceof double[] arr) {
      double r = arr.length > 0 ? arr[0] : 0;
      double g = arr.length > 1 ? arr[1] : 0;
      double bb = arr.length > 2 ? arr[2] : 0;
      double a = arr.length > 3 ? arr[3] : 1.0;
      return "rgb(" + formatNumber(r) + "," + formatNumber(g) + "," + formatNumber(bb) + "," + formatNumber(a) + ")";
    }
    if (v instanceof String s) return "\"" + escapeString(s) + "\"";
    return "\"" + escapeString(String.valueOf(v)) + "\"";
  }

  private static String escapeString(String s) {
    if (s == null) return "";
    return s.replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
  }
}
