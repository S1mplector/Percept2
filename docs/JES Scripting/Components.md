# JES Component Reference

This is the current practical component/property map used by parser + loader.

Parser validation source:
- `scripting/src/main/java/com/jvn/scripting/jes/JesParser.java`

Runtime build source:
- `scripting/src/main/java/com/jvn/scripting/jes/JesLoader.java`

## Panel2D

Properties:
- `x`, `y`
- `w`, `h`
- `fill: rgb(...)`

## Sprite2D

Properties:
- `image`
- `x`, `y`
- `w`, `h`
- `alpha`
- `originX`, `originY`
- optional region draw: `sx`, `sy`, `sw`, `sh`, `dw`, `dh`

## Label2D

Properties:
- `text`
- `x`, `y`
- `size`
- `bold`
- `color: rgb(...)`
- `align` (`left`, `center`, `right`)

## ParticleEmitter2D

Properties:
- `x`, `y`
- `emissionRate`
- `minLife`, `maxLife`
- `minSize`, `maxSize`, `endSizeScale`
- `minSpeed`, `maxSpeed`
- `minAngle`, `maxAngle`
- `gravityY`
- `texture`
- `additive`
- `startColor`, `endColor`

## PhysicsBody2D

Properties:
- `shape` (`circle` or `box`)
- `x`, `y`
- circle: `r`
- box: `w`, `h`
- `mass`
- `restitution`
- `static`
- `sensor`
- `vx`, `vy`
- `color`
- `onTrigger`

## Character2D

Properties:
- `spriteSheet`
- `frameW`, `frameH`, `cols`
- `drawW`, `drawH`
- `x`, `y`
- optional tile-start: `startTileX`, `startTileY`
- `speed`
- `originX`, `originY`
- `animations`
- `startAnim`
- `dialogueId`
- `z`
- `controllable`

## Stats

Properties:
- `maxHp`, `hp`
- `maxMp`, `mp`
- `atk`, `def`, `speed`
- `onDeathCall`
- `removeOnDeath`

## Inventory

Properties:
- `slots`
- `items` (CSV form, supports `id*count`)

## Equipment

Properties:
- free-form slot keys mapped to item ids (e.g., `weapon: sword_iron`)

## Ai2D

Properties:
- `type`
- `target`
- `aggroRange`
- `attackRange`
- `attackIntervalMs`
- `attackAmount`
- `moveSpeed`
- `attackCooldownMs`
- `patrolRadius`
- `patrolIntervalMs`
- `requiresLineOfSight`
- `guardRadius`
- `fleeDistance`

## Button2D

Properties:
- `x`, `y`, `w`, `h`
- `text`
- `call`
- visual states: `normal`, `hover`, `pressed`
- text/font: `textColor`, `fontSize`

## Slider2D

Properties:
- `x`, `y`, `w`, `h`
- `min`, `max`, `value`
- `call`
- colors: `trackColor`, `fillColor`, `knobColor`

## Notes for Script Authors

- Unknown property on a known component type is a parse error.
- Prefer explicit numeric values for dimensions/positions.
- Keep entity names stable if timeline or external handlers reference them.
