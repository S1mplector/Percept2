# Editor

JavaFX-based editor to preview and edit JES scenes.

## Features
- **File Management**
  - Open/Reload JES files
  - Export scenes back to JES format (JesExporter)
  
- **Viewport**
  - Real-time FxBlitter2D rendering
  - Click to select any entity type (Panel2D, Label2D, Sprite2D, PhysicsBody2D)
  - Selection overlay highlighting
  - Keyboard/mouse input bridged to JesScene2D
  
- **Scene Graph Panel** (Left)
  - Lists all named entities from JesScene2D.names()
  - Click to select entities
  - Alphabetically sorted
  
- **Inspector Panel** (Right)
  - **Common**: position (x, y) for all entities
  - **Panel2D**: width, height
  - **Label2D**: text, size, bold, align, color picker
  - **Sprite2D**: image path, width, height, alpha, origin (x,y)
  - **PhysicsBody2D**: mass, restitution
  - Live updates on change

## Usage
- Run: `./gradlew :editor:run`
- File → Open… to select a `.jes` file
- Select entities by:
  - Clicking in the canvas
  - Clicking names in the Scene Graph
- Edit properties in the Inspector
- Test input bindings (D=debug, C=circle, B=box)

## Roadmap
- Save functionality with file chooser
- Undo/redo system
- Multi-select with Shift/Ctrl
- Transform gizmos (move, rotate, scale)
- Camera controls (pan, zoom)
- Component add/remove
- Copy/paste entities
