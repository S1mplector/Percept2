package com.jvn.editor.ui;

import com.jvn.core.physics.RigidBody2D;
import com.jvn.core.scene2d.Entity2D;
import com.jvn.core.scene2d.Label2D;
import com.jvn.core.scene2d.Panel2D;
import com.jvn.core.scene2d.ParticleEmitter2D;
import com.jvn.core.scene2d.Sprite2D;
import com.jvn.scripting.jes.runtime.JesScene2D;
import com.jvn.scripting.jes.runtime.PhysicsBodyEntity2D;
import com.jvn.editor.commands.CommandStack;
import com.jvn.editor.commands.FunctionalCommand;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

public class InspectorView extends VBox {
  private final Consumer<String> setStatus;
  private JesScene2D scene;
  private Entity2D selected;
  private CommandStack commands;

  public InspectorView(Consumer<String> setStatus) {
    this.setStatus = setStatus == null ? s -> {} : setStatus;
    getStyleClass().add("sidebar-tool-root");
    setSpacing(8);
  }

  public void setScene(JesScene2D scene) {
    this.scene = scene;
  }

  public void setSelection(Entity2D e) {
    this.selected = e;
    rebuild();
  }

  public void setCommandStack(CommandStack stack) { this.commands = stack; }

  private void rebuild() {
    getChildren().clear();
    if (selected == null) { getChildren().add(new Label("No selection")); return; }
    getChildren().add(new Label("Selected: " + selected.getClass().getSimpleName()));

    // Common position controls (undoable)
    var posX = makeNumberFieldCmd("x", () -> selected.getX(), v -> { selected.setPosition(v, selected.getY()); });
    var posY = makeNumberFieldCmd("y", () -> selected.getY(), v -> { selected.setPosition(selected.getX(), v); });
    getChildren().addAll(posX, posY);

    if (selected instanceof Panel2D p) {
      var w = makeNumberFieldCmd("width", p::getWidth, v -> { p.setSize(v, p.getHeight()); });
      var h = makeNumberFieldCmd("height", p::getHeight, v -> { p.setSize(p.getWidth(), v); });
      getChildren().addAll(w, h);
    } else if (selected instanceof PhysicsBodyEntity2D pb) {
      RigidBody2D body = pb.getBody(); if (body != null) {
        var mass = makeNumberFieldCmd("mass", body::getMass, v -> body.setMass(v));
        var rest = makeNumberFieldCmd("restitution", body::getRestitution, v -> body.setRestitution(v));
        CheckBox cbStatic = new CheckBox("static");
        cbStatic.setSelected(body.isStatic());
        cbStatic.setOnAction(e -> {
          boolean oldV = body.isStatic(); boolean newV = cbStatic.isSelected();
          if (commands != null) commands.pushAndExecute(new FunctionalCommand(() -> body.setStatic(newV), () -> body.setStatic(oldV)));
          else body.setStatic(newV);
        });
        CheckBox cbSensor = new CheckBox("sensor");
        cbSensor.setSelected(body.isSensor());
        cbSensor.setOnAction(e -> {
          boolean oldV = body.isSensor(); boolean newV = cbSensor.isSelected();
          if (commands != null) commands.pushAndExecute(new FunctionalCommand(() -> body.setSensor(newV), () -> body.setSensor(oldV)));
          else body.setSensor(newV);
        });
        var vx = makeNumberFieldCmd("vx", body::getVx, v -> body.setVelocity(v, body.getVy()));
        var vy = makeNumberFieldCmd("vy", body::getVy, v -> body.setVelocity(body.getVx(), v));
        getChildren().addAll(mass, rest, cbStatic, cbSensor, vx, vy);
      }
    } else if (selected instanceof Label2D lbl) {
      HBox rowText = new HBox(6);
      Label ltext = new Label("text");
      TextField tf = new TextField(lbl.getText() == null ? "" : lbl.getText());
      tf.setOnAction(e -> {
        String oldV = lbl.getText() == null ? "" : lbl.getText(); String newV = tf.getText();
        if (commands != null) commands.pushAndExecute(new FunctionalCommand(() -> lbl.setText(newV), () -> lbl.setText(oldV)));
        else lbl.setText(newV);
        setStatus.accept("Updated text");
      });
      tf.setOnKeyReleased(e -> {
        if ("ENTER".equals(e.getCode().toString())) {
          String oldV = lbl.getText() == null ? "" : lbl.getText(); String newV = tf.getText();
          if (commands != null) commands.pushAndExecute(new FunctionalCommand(() -> lbl.setText(newV), () -> lbl.setText(oldV)));
          else lbl.setText(newV);
          setStatus.accept("Updated text");
        }
      });
      rowText.getChildren().addAll(ltext, tf);

      var size = makeNumberFieldCmd("size", lbl::getSize, v -> { lbl.setFont(lbl.getFontFamily(), v, lbl.isBold()); });

      CheckBox cbBold = new CheckBox("bold");
      cbBold.setSelected(lbl.isBold());
      cbBold.setOnAction(e -> {
        boolean oldV = lbl.isBold(); boolean newV = cbBold.isSelected();
        if (commands != null) commands.pushAndExecute(new FunctionalCommand(() -> lbl.setFont(lbl.getFontFamily(), lbl.getSize(), newV), () -> lbl.setFont(lbl.getFontFamily(), lbl.getSize(), oldV)));
        else lbl.setFont(lbl.getFontFamily(), lbl.getSize(), newV);
      });

      HBox rowAlign = new HBox(6);
      Label lAlign = new Label("align");
      ComboBox<Label2D.Align> cbAlign = new ComboBox<>();
      cbAlign.getItems().addAll(Label2D.Align.values());
      cbAlign.getSelectionModel().select(lbl.getAlign());
      cbAlign.setOnAction(e -> {
        Label2D.Align oldV = lbl.getAlign(); Label2D.Align newV = cbAlign.getValue();
        if (commands != null) commands.pushAndExecute(new FunctionalCommand(() -> lbl.setAlign(newV), () -> lbl.setAlign(oldV)));
        else lbl.setAlign(newV);
      });
      rowAlign.getChildren().addAll(lAlign, cbAlign);

      HBox rowColor = new HBox(6);
      Label lColor = new Label("color");
      ColorPicker cp = new ColorPicker(new Color(lbl.getColorR(), lbl.getColorG(), lbl.getColorB(), lbl.getAlpha()));
      cp.setOnAction(e -> {
        double or = lbl.getColorR(), og = lbl.getColorG(), ob = lbl.getColorB(), oa = lbl.getAlpha();
        Color c = cp.getValue();
        if (commands != null) commands.pushAndExecute(new FunctionalCommand(() -> lbl.setColor(c.getRed(), c.getGreen(), c.getBlue(), c.getOpacity()), () -> lbl.setColor(or, og, ob, oa)));
        else lbl.setColor(c.getRed(), c.getGreen(), c.getBlue(), c.getOpacity());
      });
      rowColor.getChildren().addAll(lColor, cp);

      var alpha = makeNumberFieldCmd("alpha", lbl::getAlpha, v -> { lbl.setColor(lbl.getColorR(), lbl.getColorG(), lbl.getColorB(), v); });

      getChildren().addAll(rowText, size, cbBold, rowAlign, rowColor, alpha);
    } else if (selected instanceof Sprite2D sprite) {
      HBox rowImage = new HBox(6);
      Label lImage = new Label("image");
      TextField tfImage = new TextField(sprite.getImagePath() == null ? "" : sprite.getImagePath());
      tfImage.setOnAction(e -> {
        String oldV = sprite.getImagePath() == null ? "" : sprite.getImagePath(); String newV = tfImage.getText();
        if (commands != null) commands.pushAndExecute(new FunctionalCommand(() -> sprite.setImagePath(newV), () -> sprite.setImagePath(oldV)));
        else sprite.setImagePath(newV);
        setStatus.accept("Updated image path");
      });
      rowImage.getChildren().addAll(lImage, tfImage);

      var width = makeNumberFieldCmd("width", sprite::getWidth, v -> { sprite.setSize(v, sprite.getHeight()); });
      var height = makeNumberFieldCmd("height", sprite::getHeight, v -> { sprite.setSize(sprite.getWidth(), v); });
      var alpha2 = makeNumberFieldCmd("alpha", sprite::getAlpha, v -> { sprite.setAlpha(v); });
      var originX = makeNumberFieldCmd("originX", sprite::getOriginX, v -> { sprite.setOrigin(v, sprite.getOriginY()); });
      var originY = makeNumberFieldCmd("originY", sprite::getOriginY, v -> { sprite.setOrigin(sprite.getOriginX(), v); });

      getChildren().addAll(rowImage, width, height, alpha2, originX, originY);
    } else if (selected instanceof ParticleEmitter2D emitter) {
      var emRate = makeNumberFieldCmd("emissionRate", emitter::getEmissionRate, emitter::setEmissionRate);
      var minLife = makeNumberFieldCmd("minLife", emitter::getMinLife, v -> emitter.setLifeRange(v, emitter.getMaxLife()));
      var maxLife = makeNumberFieldCmd("maxLife", emitter::getMaxLife, v -> emitter.setLifeRange(emitter.getMinLife(), v));
      var minSize = makeNumberFieldCmd("minSize", emitter::getMinSize, v -> emitter.setSizeRange(v, emitter.getMaxSize(), emitter.getEndSizeScale()));
      var maxSize = makeNumberFieldCmd("maxSize", emitter::getMaxSize, v -> emitter.setSizeRange(emitter.getMinSize(), v, emitter.getEndSizeScale()));
      var endScale = makeNumberFieldCmd("endSizeScale", emitter::getEndSizeScale, v -> emitter.setSizeRange(emitter.getMinSize(), emitter.getMaxSize(), v));
      var minSpeed = makeNumberFieldCmd("minSpeed", emitter::getMinSpeed, v -> emitter.setSpeedRange(v, emitter.getMaxSpeed()));
      var maxSpeed = makeNumberFieldCmd("maxSpeed", emitter::getMaxSpeed, v -> emitter.setSpeedRange(emitter.getMinSpeed(), v));
      var minAngle = makeNumberFieldCmd("minAngle", emitter::getMinAngle, v -> emitter.setAngleRange(v, emitter.getMaxAngle()));
      var maxAngle = makeNumberFieldCmd("maxAngle", emitter::getMaxAngle, v -> emitter.setAngleRange(emitter.getMinAngle(), v));
      var gravityY = makeNumberFieldCmd("gravityY", emitter::getGravityY, emitter::setGravity);

      HBox rowStart = new HBox(6);
      Label lStart = new Label("startColor");
      ColorPicker cpStart = new ColorPicker(new Color(emitter.getStartR(), emitter.getStartG(), emitter.getStartB(), emitter.getStartA()));
      cpStart.setOnAction(e -> {
        double or = emitter.getStartR(), og = emitter.getStartG(), ob = emitter.getStartB(), oa = emitter.getStartA();
        Color c = cpStart.getValue();
        if (commands != null) commands.pushAndExecute(new FunctionalCommand(() -> emitter.setStartColor(c.getRed(), c.getGreen(), c.getBlue(), c.getOpacity()), () -> emitter.setStartColor(or, og, ob, oa)));
        else emitter.setStartColor(c.getRed(), c.getGreen(), c.getBlue(), c.getOpacity());
      });
      rowStart.getChildren().addAll(lStart, cpStart);

      HBox rowEnd = new HBox(6);
      Label lEnd = new Label("endColor");
      ColorPicker cpEnd = new ColorPicker(new Color(emitter.getEndR(), emitter.getEndG(), emitter.getEndB(), emitter.getEndA()));
      cpEnd.setOnAction(e -> {
        double or = emitter.getEndR(), og = emitter.getEndG(), ob = emitter.getEndB(), oa = emitter.getEndA();
        Color c = cpEnd.getValue();
        if (commands != null) commands.pushAndExecute(new FunctionalCommand(() -> emitter.setEndColor(c.getRed(), c.getGreen(), c.getBlue(), c.getOpacity()), () -> emitter.setEndColor(or, og, ob, oa)));
        else emitter.setEndColor(c.getRed(), c.getGreen(), c.getBlue(), c.getOpacity());
      });
      rowEnd.getChildren().addAll(lEnd, cpEnd);

      HBox rowTexture = new HBox(6);
      Label lTex = new Label("texture");
      TextField tfTex = new TextField(emitter.getTexture() == null ? "" : emitter.getTexture());
      tfTex.setOnAction(e -> {
        String oldV = emitter.getTexture() == null ? "" : emitter.getTexture(); String newV = tfTex.getText();
        if (commands != null) commands.pushAndExecute(new FunctionalCommand(() -> emitter.setTexture(newV), () -> emitter.setTexture(oldV)));
        else emitter.setTexture(newV);
      });
      rowTexture.getChildren().addAll(lTex, tfTex);

      CheckBox cbAdd = new CheckBox("additive"); cbAdd.setSelected(emitter.isAdditive()); cbAdd.setOnAction(e -> {
        boolean oldV = emitter.isAdditive(); boolean newV = cbAdd.isSelected();
        if (commands != null) commands.pushAndExecute(new FunctionalCommand(() -> emitter.setAdditive(newV), () -> emitter.setAdditive(oldV)));
        else emitter.setAdditive(newV);
      });

      getChildren().addAll(emRate, minLife, maxLife, minSize, maxSize, endScale, minSpeed, maxSpeed, minAngle, maxAngle, gravityY, rowStart, rowEnd, rowTexture, cbAdd);
    }
  }

  private HBox makeNumberFieldCmd(String label, DoubleSupplier getter, DoubleConsumer setter) {
    HBox row = new HBox(6);
    Label l = new Label(label);
    TextField tf = new TextField(Double.toString(getter.getAsDouble()));
    Runnable commit = () -> {
      try {
        double v = Double.parseDouble(tf.getText());
        if (commands != null) {
          commands.pushAndExecute(new com.jvn.editor.commands.SetDoublePropertyCommand(getter, setter, v));
        } else {
          setter.accept(v);
        }
        setStatus.accept("Updated " + label + " = " + tf.getText());
      } catch (Exception ignored) {}
    };
    tf.setOnAction(e -> commit.run());
    tf.setOnKeyReleased(e -> { if ("ENTER".equals(e.getCode().toString())) commit.run(); });
    row.getChildren().addAll(l, tf);
    return row;
  }
}
