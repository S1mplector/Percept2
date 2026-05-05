package com.jvn.scripting.jes.ast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JesAst {
  public static class Program {
    public final List<SceneDecl> scenes = new ArrayList<>();
  }
  public static class SceneDecl {
    public String name;
    public final Map<String,Object> props = new HashMap<>();
    public final List<EntityDecl> entities = new ArrayList<>();
    public final List<InputBinding> bindings = new ArrayList<>();
    public final List<TimelineAction> timeline = new ArrayList<>();
    public final List<TilesetDecl> tilesets = new ArrayList<>();
    public final List<MapDecl> maps = new ArrayList<>();
    public final List<ItemDecl> items = new ArrayList<>();
  }
  public static class EntityDecl {
    public String name;
    public final List<ComponentDecl> components = new ArrayList<>();
  }
  public static class ComponentDecl {
    public String type;
    public final Map<String,Object> props = new HashMap<>();
  }
  public static class InputBinding {
    public String key;
    public String action;
    public final Map<String,Object> props = new HashMap<>();
  }
  public static class TimelineAction {
    public String type; // wait, move, rotate, scale, call
    public String target; // optional entity name
    public final Map<String,Object> props = new HashMap<>();
    public final List<TimelineAction> children = new ArrayList<>();
  }
  public static class TilesetDecl {
    public String name;
    public final Map<String,Object> props = new HashMap<>();
  }
  public static class MapDecl {
    public String name;
    public final Map<String,Object> props = new HashMap<>();
    public final List<MapLayerDecl> layers = new ArrayList<>();
  }
  public static class MapLayerDecl {
    public String name;
    public final Map<String,Object> props = new HashMap<>();
  }
  public static class ItemDecl {
    public String id;
    public final Map<String,Object> props = new HashMap<>();
  }
}
