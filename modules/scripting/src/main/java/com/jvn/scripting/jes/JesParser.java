package com.jvn.scripting.jes;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.jvn.scripting.jes.JesTokenType.COLON;
import static com.jvn.scripting.jes.JesTokenType.COMMA;
import static com.jvn.scripting.jes.JesTokenType.EOF;
import static com.jvn.scripting.jes.JesTokenType.IDENT;
import static com.jvn.scripting.jes.JesTokenType.LBRACE;
import static com.jvn.scripting.jes.JesTokenType.LPAREN;
import static com.jvn.scripting.jes.JesTokenType.NUMBER;
import static com.jvn.scripting.jes.JesTokenType.RBRACE;
import static com.jvn.scripting.jes.JesTokenType.RPAREN;
import static com.jvn.scripting.jes.JesTokenType.STRING;
import com.jvn.scripting.jes.ast.JesAst;

public class JesParser {
  private final List<JesToken> toks;
  private int i = 0;

  private static final Map<String, Set<String>> COMPONENT_PROPS = Map.ofEntries(
    Map.entry("Panel2D", Set.of("x", "y", "w", "h", "fill")),
    Map.entry("Sprite2D", Set.of("image", "x", "y", "w", "h", "alpha", "originX", "originY", "sx", "sy", "sw", "sh", "dw", "dh")),
    Map.entry("Label2D", Set.of("text", "x", "y", "size", "bold", "color", "align")),
    Map.entry("ParticleEmitter2D", Set.of("x", "y", "emissionRate", "minLife", "maxLife", "minSize", "maxSize", "endSizeScale", "minSpeed", "maxSpeed", "minAngle", "maxAngle", "gravityY", "texture", "additive", "startColor", "endColor")),
    Map.entry("PhysicsBody2D", Set.of("shape", "x", "y", "w", "h", "r", "mass", "restitution", "static", "sensor", "vx", "vy", "color", "onTrigger")),
    Map.entry("Character2D", Set.of("spriteSheet", "frameW", "frameH", "cols", "drawW", "drawH", "x", "y", "startTileX", "startTileY", "speed", "originX", "originY", "animations", "startAnim", "dialogueId", "z", "controllable")),
    Map.entry("Stats", Set.of("maxHp", "hp", "maxMp", "mp", "atk", "def", "speed", "onDeathCall", "removeOnDeath")),
    Map.entry("Inventory", Set.of("slots", "items")),
    Map.entry("Ai2D", Set.of("type", "target", "aggroRange", "attackRange", "attackIntervalMs", "attackAmount", "moveSpeed", "attackCooldownMs", "patrolRadius", "patrolIntervalMs", "requiresLineOfSight", "guardRadius", "fleeDistance")),
    Map.entry("Button2D", Set.of("x", "y", "w", "h", "text", "call", "normal", "hover", "pressed", "textColor", "fontSize")),
    Map.entry("Slider2D", Set.of("x", "y", "w", "h", "min", "max", "value", "call", "trackColor", "fillColor", "knobColor"))
  );
  private static final Set<String> COMPONENT_FREE_PROPS = Set.of("Equipment");

  private static final Map<String, Set<String>> TIMELINE_PROPS = Map.ofEntries(
    Map.entry("move", Set.of("x", "y", "dur", "easing")),
    Map.entry("pivot", Set.of("ox", "oy", "dur", "easing")),
    Map.entry("walkToTile", Set.of("tx", "ty", "x", "y", "dur", "easing")),
    Map.entry("rotate", Set.of("deg", "dur", "easing")),
    Map.entry("scale", Set.of("sx", "sy", "dur", "easing")),
    Map.entry("fade", Set.of("alpha", "dur", "easing")),
    Map.entry("visible", Set.of("value")),
    Map.entry("cameraMove", Set.of("x", "y", "dur", "easing")),
    Map.entry("cameraZoom", Set.of("zoom", "dur", "easing")),
    Map.entry("cameraShake", Set.of("ampX", "ampY", "dur")),
    Map.entry("damage", Set.of("amount", "source")),
    Map.entry("heal", Set.of("amount", "source")),
    Map.entry("waitForCall", Set.of("name")),
    Map.entry("playAudio", Set.of("id", "volume", "loop", "bgm")),
    Map.entry("stopAudio", Set.of("id")),
    Map.entry("emitParticles", Set.of("count")),
    Map.entry("cameraFollow", Set.of("target", "lerp", "offsetX", "offsetY", "deadZoneW", "deadZoneH")),
    Map.entry("setParallax", Set.of("px", "py")),
    Map.entry("loop", Set.of("count", "until")),
    Map.entry("parallel", Set.of()),
    Map.entry("label", Set.of("name")),
    Map.entry("jump", Set.of("target"))
  );
  private static final Set<String> TIMELINE_FREE_PROPS = Set.of("call");

  public JesParser(List<JesToken> toks) { this.toks = toks == null ? new ArrayList<>() : toks; }

  private JesToken peek() { return i < toks.size() ? toks.get(i) : new JesToken(EOF, "", -1, -1); }
  private JesToken prev() { return toks.get(Math.max(0, i-1)); }
  private boolean match(JesTokenType t) { if (peek().type == t) { i++; return true; } return false; }
  private JesToken expect(JesTokenType t, String msg) {
    if (match(t)) return prev();
    JesToken p = peek();
    throw new JesParseException("Expected " + msg + " but found '" + p.lexeme + "'", p.line, p.col);
  }
  private JesParseException error(String m) {
    JesToken p = peek();
    return new JesParseException(m, p.line, p.col);
  }
  private JesParseException error(String m, JesToken at) {
    if (at == null) return error(m);
    return new JesParseException(m, at.line, at.col);
  }

  public JesAst.Program parseProgram() {
    JesAst.Program p = new JesAst.Program();
    while (peek().type != EOF) {
      JesToken kw = expect(IDENT, "'scene'");
      if (!"scene".equals(kw.lexeme)) {
        throw error("Expected 'scene' declaration", kw);
      }
      p.scenes.add(parseScene());
    }
    return p;
  }

  private JesAst.SceneDecl parseScene() {
    JesAst.SceneDecl s = new JesAst.SceneDecl();
    JesToken nameTok = expect(STRING, "scene name");
    s.name = nameTok.lexeme;
    expect(LBRACE, "'{'" );
    while (!match(RBRACE)) {
      if (peek().type == EOF) throw error("Unterminated scene block");
      JesToken wordTok = expect(IDENT, "identifier");
      String word = wordTok.lexeme;
      if ("tileset".equals(word)) {
        s.tilesets.add(parseTileset());
      } else if ("item".equals(word)) {
        s.items.add(parseItem());
      } else if ("map".equals(word)) {
        s.maps.add(parseMap());
      } else if ("entity".equals(word)) {
        s.entities.add(parseEntity());
      } else if ("on".equals(word)) {
        s.bindings.add(parseBinding());
      } else if ("timeline".equals(word)) {
        expect(LBRACE, "'{'" );
        while (!match(RBRACE)) {
          if (peek().type == EOF) throw error("Unterminated timeline block");
          JesToken actionTok = expect(IDENT, "timeline action");
          s.timeline.add(parseTimelineAction(actionTok));
        }
      } else {
        // scene-level prop: key : value
        expect(COLON, ":");
        Object val = parseValue();
        s.props.put(word, val);
      }
    }
    return s;
  }

  private JesAst.TilesetDecl parseTileset() {
    JesAst.TilesetDecl t = new JesAst.TilesetDecl();
    t.name = expect(STRING, "tileset name").lexeme;
    expect(LBRACE, "'{'");
    while (!match(RBRACE)) {
      if (peek().type == EOF) throw error("Unterminated tileset block");
      String key = expect(IDENT, "identifier").lexeme;
      expect(COLON, ":");
      Object val = parseValue();
      t.props.put(key, val);
    }
    return t;
  }

  private JesAst.ItemDecl parseItem() {
    JesAst.ItemDecl it = new JesAst.ItemDecl();
    it.id = expect(STRING, "item id").lexeme;
    expect(LBRACE, "'{'" );
    while (!match(RBRACE)) {
      if (peek().type == EOF) throw error("Unterminated item block");
      String key = expect(IDENT, "identifier").lexeme;
      expect(COLON, ":");
      Object val = parseValue();
      it.props.put(key, val);
    }
    return it;
  }

  private JesAst.MapDecl parseMap() {
    JesAst.MapDecl m = new JesAst.MapDecl();
    m.name = expect(STRING, "map name").lexeme;
    expect(LBRACE, "'{'");
    while (!match(RBRACE)) {
      if (peek().type == EOF) throw error("Unterminated map block");
      JesToken wordTok = expect(IDENT, "identifier");
      String word = wordTok.lexeme;
      if ("layer".equals(word)) {
        m.layers.add(parseMapLayer());
      } else {
        expect(COLON, ":");
        Object val = parseValue();
        m.props.put(word, val);
      }
    }
    return m;
  }

  private JesAst.MapLayerDecl parseMapLayer() {
    JesAst.MapLayerDecl l = new JesAst.MapLayerDecl();
    l.name = expect(STRING, "layer name").lexeme;
    expect(LBRACE, "'{'");
    while (!match(RBRACE)) {
      if (peek().type == EOF) throw error("Unterminated map layer block");
      String key = expect(IDENT, "identifier").lexeme;
      expect(COLON, ":");
      Object val = parseValue();
      l.props.put(key, val);
    }
    return l;
  }

  private JesAst.TimelineAction parseTimelineAction(JesToken kindTok) {
    String kind = kindTok == null ? null : kindTok.lexeme;
    JesAst.TimelineAction a = new JesAst.TimelineAction();
    a.type = kind;
    switch (kind) {
      case "wait" -> {
        double ms = parseNum();
        a.props.put("ms", ms);
      }
      case "waitForCall" -> {
        String name = expect(STRING, "call name").lexeme;
        a.props.put("name", name);
      }
      case "call" -> {
        String name = expect(STRING, "function name").lexeme;
        a.target = name;
        if (match(LBRACE)) {
          while (!match(RBRACE)) {
            if (match(IDENT)) {
              JesToken keyTok = prev();
              String k = keyTok.lexeme;
              validateTimelineProp(kind, k, keyTok);
              expect(COLON, ":");
              Object v = parseValue();
              a.props.put(k, v);
            } else { throw error("Expected property name in call action"); }
          }
        }
      }
      case "move", "pivot", "rotate", "scale", "fade", "visible", "walkToTile", "damage", "heal" -> {
        String target = expect(STRING, "entity name").lexeme;
        a.target = target;
        expect(LBRACE, "'{'" );
        while (!match(RBRACE)) {
          if (match(IDENT)) {
            JesToken keyTok = prev();
            String k = keyTok.lexeme;
            validateTimelineProp(kind, k, keyTok);
            expect(COLON, ":");
            Object v = parseValue();
            a.props.put(k, v);
          } else { throw error("Expected property name in timeline action"); }
        }
      }
      case "cameraMove", "cameraZoom", "cameraShake" -> {
        expect(LBRACE, "'{'" );
        while (!match(RBRACE)) {
          if (match(IDENT)) {
            JesToken keyTok = prev();
            String k = keyTok.lexeme;
            validateTimelineProp(kind, k, keyTok);
            expect(COLON, ":");
            Object v = parseValue();
            a.props.put(k, v);
          } else { throw error("Expected property name in camera action"); }
        }
      }
      case "playAudio", "stopAudio" -> {
        String id = expect(STRING, "audio id").lexeme;
        a.target = id;
        if (match(LBRACE)) {
          while (!match(RBRACE)) {
            if (match(IDENT)) {
              JesToken keyTok = prev();
              String k = keyTok.lexeme;
              validateTimelineProp(kind, k, keyTok);
              expect(COLON, ":");
              Object v = parseValue();
              a.props.put(k, v);
            } else { throw error("Expected property name in audio action"); }
          }
        }
      }
      case "emitParticles" -> {
        String target = expect(STRING, "emitter name").lexeme;
        a.target = target;
        if (match(LBRACE)) {
          while (!match(RBRACE)) {
            if (match(IDENT)) {
              JesToken keyTok = prev();
              String k = keyTok.lexeme;
              validateTimelineProp(kind, k, keyTok);
              expect(COLON, ":");
              Object v = parseValue();
              a.props.put(k, v);
            } else { throw error("Expected property name in emitParticles"); }
          }
        }
      }
      case "cameraFollow" -> {
        // optional immediate target
        if (peek().type == STRING) {
          a.target = expect(STRING, "entity name").lexeme;
        }
        if (match(LBRACE)) {
          while (!match(RBRACE)) {
            if (match(IDENT)) {
              JesToken keyTok = prev();
              String k = keyTok.lexeme;
              validateTimelineProp(kind, k, keyTok);
              expect(COLON, ":");
              Object v = parseValue();
              a.props.put(k, v);
            } else { throw error("Expected property name in cameraFollow"); }
          }
        }
      }
      case "setParallax" -> {
        String target = expect(STRING, "entity name").lexeme;
        a.target = target;
        expect(LBRACE, "'{'" );
        while (!match(RBRACE)) {
          if (match(IDENT)) {
            JesToken keyTok = prev();
            String k = keyTok.lexeme;
            validateTimelineProp(kind, k, keyTok);
            expect(COLON, ":");
            Object v = parseValue();
            a.props.put(k, v);
          } else { throw error("Expected property name in setParallax"); }
        }
      }
      case "label" -> {
        String name = expect(STRING, "label name").lexeme;
        a.target = name;
        a.props.put("name", name);
      }
      case "jump" -> {
        String name = expect(STRING, "label name").lexeme;
        a.target = name;
      }
      case "parallel" -> {
        expect(LBRACE, "'{'");
        while (!match(RBRACE)) {
          if (peek().type == EOF) throw error("Unterminated parallel block");
          JesToken childTok = expect(IDENT, "timeline action");
          a.children.add(parseTimelineAction(childTok));
        }
      }
      case "loop" -> {
        // loop 3 { ... } or loop until "event" { ... }
        if (match(NUMBER)) {
          JesToken cntTok = prev();
          try {
            a.props.put("count", Double.parseDouble(cntTok.lexeme));
          } catch (NumberFormatException ex) {
            throw new JesParseException("Malformed loop count '" + cntTok.lexeme + "'", cntTok.line, cntTok.col);
          }
        } else if (peek().type == IDENT && "until".equalsIgnoreCase(peek().lexeme)) {
          match(IDENT);
          String ev = expect(STRING, "event name").lexeme;
          a.props.put("until", ev);
        }
        expect(LBRACE, "'{'");
        while (!match(RBRACE)) {
          if (peek().type == EOF) throw error("Unterminated loop block");
          JesToken childTok = expect(IDENT, "timeline action");
          a.children.add(parseTimelineAction(childTok));
        }
      }
      default -> throw error("Unknown timeline action '" + kind + "'", kindTok);
    }
    return a;
  }

  private JesAst.InputBinding parseBinding() {
    expect(IDENT, "'key'"); // expect 'key'
    String keyName = expect(STRING, "key name").lexeme;
    expect(IDENT, "'do'"); // expect 'do'
    String action = expect(IDENT, "action").lexeme;
    JesAst.InputBinding b = new JesAst.InputBinding();
    b.key = keyName; b.action = action;
    if (match(LBRACE)) {
      while (!match(RBRACE)) {
        if (peek().type == EOF) throw error("Unterminated input binding block");
        String k = expect(IDENT, "identifier").lexeme;
        expect(COLON, ":");
        Object v = parseValue();
        b.props.put(k, v);
      }
    }
    return b;
  }

  private JesAst.EntityDecl parseEntity() {
    JesAst.EntityDecl e = new JesAst.EntityDecl();
    e.name = expect(STRING, "entity name").lexeme;
    expect(LBRACE, "'{'");
    while (!match(RBRACE)) {
        if (peek().type == EOF) throw error("Unterminated entity block");
        JesToken kw = expect(IDENT, "'component'");
        if (!"component".equals(kw.lexeme)) {
          throw error("Expected 'component' declaration", kw);
        }
        e.components.add(parseComponent());
    }
    return e;
  }

  private JesAst.ComponentDecl parseComponent() {
    JesAst.ComponentDecl c = new JesAst.ComponentDecl();
    c.type = expect(IDENT, "component type").lexeme;
    expect(LBRACE, "'{'");
    while (!match(RBRACE)) {
      if (peek().type == EOF) throw error("Unterminated component block");
      String key = expect(IDENT, "identifier").lexeme;
      validateComponentProp(c.type, key, prev());
      expect(COLON, ":");
      Object val = parseValue();
      c.props.put(key, val);
    }
    return c;
  }

  private void validateComponentProp(String type, String key, JesToken tok) {
    if (type == null || key == null) return;
    if (COMPONENT_FREE_PROPS.contains(type)) return;
    Set<String> allowed = COMPONENT_PROPS.get(type);
    if (allowed == null) return; // unknown component type, allow freely
    if (!allowed.contains(key)) {
      throw error("Unknown property '" + key + "' for component '" + type + "'", tok);
    }
  }

  private void validateTimelineProp(String action, String key, JesToken tok) {
    if (action == null || key == null) return;
    if (TIMELINE_FREE_PROPS.contains(action)) return;
    Set<String> allowed = TIMELINE_PROPS.get(action);
    if (allowed == null) return;
    if (!allowed.contains(key)) {
      throw error("Unknown property '" + key + "' for timeline action '" + action + "'", tok);
    }
  }

  private Object parseValue() {
    if (match(NUMBER)) {
      JesToken numTok = prev();
      try {
        return Double.parseDouble(numTok.lexeme);
      } catch (NumberFormatException ex) {
        throw new JesParseException("Malformed number '" + numTok.lexeme + "'", numTok.line, numTok.col);
      }
    } else if (match(STRING)) {
      return prev().lexeme;
    } else if (match(IDENT)) {
      String ident = prev().lexeme;
      if ("rgb".equals(ident) || "rgba".equals(ident)) {
        expect(LPAREN, "(");
        double r = parseNum(); expect(COMMA, ",");
        double g = parseNum(); expect(COMMA, ",");
        double b = parseNum();
        double a = 1.0;
        if (match(COMMA)) a = parseNum();
        expect(RPAREN, ")");
        return new double[]{r,g,b,a};
      } else if ("true".equalsIgnoreCase(ident) || "false".equalsIgnoreCase(ident)) {
        return Boolean.parseBoolean(ident);
      } else {
        // Treat bare identifiers as string literals (e.g., left, circle, box)
        return ident;
      }
    }
    JesToken p = peek();
    throw new JesParseException("Value expected", p.line, p.col);
  }

  private double parseNum() {
    JesToken t = expect(NUMBER, "number");
    try {
      return Double.parseDouble(t.lexeme);
    } catch (NumberFormatException ex) {
      throw new JesParseException("Malformed number '" + t.lexeme + "'", t.line, t.col);
    }
  }
}
