import { createRequire } from "node:module";
import { resolve, dirname, join } from "node:path";
import { readFileSync } from "node:fs";

const bundlePath = process.argv[2];
if (!bundlePath) {
  throw new Error("Usage: node scripts/test-web-runtime-bundle.mjs <jvn-web.js>");
}

const distRoot = dirname(dirname(bundlePath)); // .../distributions/web (parent of js/)

function readPngDimensions(relativeUrl) {
  try {
    const filePath = join(distRoot, relativeUrl);
    const data = readFileSync(filePath);
    // PNG signature is 8 bytes; IHDR chunk follows immediately: 4-byte length,
    // 4-byte type "IHDR", then 4-byte width + 4-byte height, both big-endian,
    // at byte offsets 16 and 20 respectively.
    if (data.length < 24) return null;
    const isPng = data[0] === 0x89 && data[1] === 0x50 && data[2] === 0x4e && data[3] === 0x47;
    if (!isPng) return null;
    const width = data.readUInt32BE(16);
    const height = data.readUInt32BE(20);
    if (width <= 0 || height <= 0) return null;
    return { width, height };
  } catch {
    return null;
  }
}

const drawingCalls = [];
const scheduledFrames = [];

const context2d = {
  scale: (...args) => drawingCalls.push(["scale", ...args]),
  fillRect: (...args) => drawingCalls.push(["fillRect", ...args]),
  strokeRect: (...args) => drawingCalls.push(["strokeRect", ...args]),
  fillText: (...args) => drawingCalls.push(["fillText", ...args]),
  drawImage: (...args) => drawingCalls.push(["drawImage", ...args]),
  save: (...args) => drawingCalls.push(["save", ...args]),
  restore: (...args) => drawingCalls.push(["restore", ...args]),
  translate: (...args) => drawingCalls.push(["translate", ...args]),
  rotate: (...args) => drawingCalls.push(["rotate", ...args]),
  transform: (...args) => drawingCalls.push(["transform", ...args]),
  beginPath: (...args) => drawingCalls.push(["beginPath", ...args]),
  closePath: (...args) => drawingCalls.push(["closePath", ...args]),
  moveTo: (...args) => drawingCalls.push(["moveTo", ...args]),
  lineTo: (...args) => drawingCalls.push(["lineTo", ...args]),
  arc: (...args) => drawingCalls.push(["arc", ...args]),
  rect: (...args) => drawingCalls.push(["rect", ...args]),
  clip: (...args) => drawingCalls.push(["clip", ...args]),
  fill: (...args) => drawingCalls.push(["fill", ...args]),
  stroke: (...args) => drawingCalls.push(["stroke", ...args]),
  measureText: (...args) => {
    drawingCalls.push(["measureText", ...args]);
    return { width: 0 };
  },
  setLineDash: (...args) => drawingCalls.push(["setLineDash", ...args]),
  set fillStyle(value) {
    drawingCalls.push(["fillStyle", value]);
  },
  set strokeStyle(value) {
    drawingCalls.push(["strokeStyle", value]);
  },
  set lineWidth(value) {
    drawingCalls.push(["lineWidth", value]);
  },
  set globalAlpha(value) {
    drawingCalls.push(["globalAlpha", value]);
  },
  set font(value) {
    drawingCalls.push(["font", value]);
  },
  set textAlign(value) {
    drawingCalls.push(["textAlign", value]);
  },
  set textBaseline(value) {
    drawingCalls.push(["textBaseline", value]);
  },
  set globalCompositeOperation(value) {
    drawingCalls.push(["globalCompositeOperation", value]);
  },
  set lineCap(value) {
    drawingCalls.push(["lineCap", value]);
  },
  set lineJoin(value) {
    drawingCalls.push(["lineJoin", value]);
  },
  set lineDashOffset(value) {
    drawingCalls.push(["lineDashOffset", value]);
  },
};

class HTMLImageElement {
  constructor() {
    this._src = "";
    this._listeners = {};
    this._naturalWidth = 0;
    this._naturalHeight = 0;
  }

  addEventListener(type, listener) {
    (this._listeners[type] ||= []).push(listener);
  }

  toString() {
    return this._src;
  }

  get naturalWidth() {
    return this._naturalWidth;
  }

  get naturalHeight() {
    return this._naturalHeight;
  }

  set src(value) {
    this._src = value;
    const dims = readPngDimensions(value);
    if (dims) {
      this._naturalWidth = dims.width;
      this._naturalHeight = dims.height;
    }
    // Synchronously fire "load" so the web runtime's image cache resolves
    // on the same tick that requested it, matching an instantly-cached
    // browser image load closely enough for this DOM-stub smoke test.
    const listeners = this._listeners["load"] || [];
    for (const listener of listeners) listener({ target: this });
  }

  get src() {
    return this._src;
  }
}

class SimpleEventTarget {
  constructor() {
    this._listeners = {};
  }

  addEventListener(type, listener) {
    (this._listeners[type] ||= []).push(listener);
  }

  dispatchEvent(type, eventLike) {
    const listeners = this._listeners[type] || [];
    for (const listener of listeners) listener(eventLike);
  }
}

class HTMLCanvasElement extends SimpleEventTarget {
  constructor() {
    super();
    this.width = 1280;
    this.height = 720;
    this.style = {
      setProperty: (name, value) => drawingCalls.push(["style", name, value]),
    };
  }

  getContext(kind) {
    if (kind !== "2d") return null;
    return context2d;
  }

  getBoundingClientRect() {
    return { left: 0, top: 0, width: this.width, height: this.height };
  }
}

const canvas = new HTMLCanvasElement();

// Self-check: verify the new event-listener plumbing works before relying on
// it for the real click/keydown tests below.
let selfCheckFired = false;
canvas.addEventListener("click", () => { selfCheckFired = true; });
canvas.dispatchEvent("click", { clientX: 0, clientY: 0, button: 0, preventDefault() {} });
if (!selfCheckFired) {
  throw new Error("Harness self-check failed: canvas.addEventListener/dispatchEvent plumbing is broken");
}
const rect = canvas.getBoundingClientRect();
if (typeof rect.left !== "number" || typeof rect.top !== "number") {
  throw new Error("Harness self-check failed: canvas.getBoundingClientRect() did not return left/top numbers");
}
const status = { innerText: "Starting…" };
const config = {
  textContent: JSON.stringify({
    title: "Bundle Smoke",
    width: 960,
    height: 540,
    fixedUpdateMs: 16,
    fixedUpdateMaxSteps: 5,
    timeScale: 1,
  }),
};

globalThis.HTMLCanvasElement = HTMLCanvasElement;
globalThis.HTMLImageElement = HTMLImageElement;
globalThis.window = { devicePixelRatio: 2 };
globalThis.document = Object.assign(new SimpleEventTarget(), {
  title: "",
  getElementById(id) {
    if (id === "jvn-canvas") return canvas;
    if (id === "jvn-status") return status;
    if (id === "jvn-config") return config;
    return null;
  },
  createElement(tag) {
    if (tag === "img") return new HTMLImageElement();
    if (tag === "canvas") return new HTMLCanvasElement();
    throw new Error(`Unsupported createElement tag in smoke harness: ${tag}`);
  },
});
globalThis.window.document = globalThis.document;
globalThis.requestAnimationFrame = (callback) => {
  scheduledFrames.push(callback);
  return scheduledFrames.length;
};
globalThis.window.requestAnimationFrame = globalThis.requestAnimationFrame;

const require = createRequire(import.meta.url);
const runtime = require(resolve(bundlePath));
if (typeof runtime.main !== "function") {
  throw new Error("Generated TeaVM bundle does not export main()");
}

await runtime.main([]);

if (scheduledFrames.length !== 1) {
  throw new Error(`Expected one scheduled frame after startup, got ${scheduledFrames.length}`);
}
if (canvas.width !== 1920 || canvas.height !== 1080) {
  throw new Error(`Expected a 2x backing store, got ${canvas.width}x${canvas.height}`);
}
if (!document.title.startsWith("Bundle Smoke")) {
  throw new Error(`Expected configured document title, got ${document.title}`);
}
if (!status.innerText.includes("Engine loop online")) {
  throw new Error(`Expected successful status text, got ${status.innerText}`);
}

// Drive two frames: the first frame's drawImage calls trigger the
// WebImageCache's async (here, synchronously-stubbed) image loads; a
// second frame is needed for the now-cached images to actually draw.
scheduledFrames.shift()(16.667);
if (scheduledFrames.length !== 1) {
  throw new Error("Game loop did not schedule the next animation frame after frame 1");
}
scheduledFrames.shift()(33.334);

const drewBackground = drawingCalls.some(
  ([operation, path]) => operation === "drawImage" && String(path).includes("bg/game.png"),
);
if (!drewBackground) {
  throw new Error("Expected the fixture background to be drawn via drawImage");
}

const drewCharacterLayer = drawingCalls.some(
  ([operation, path]) =>
    operation === "drawImage" && String(path).includes("characters/lavender"),
);
if (!drewCharacterLayer) {
  throw new Error("Expected a fixture character layer to be drawn via drawImage");
}

// Dialogue text is drawn one glyph per fillText call (VnDialogueRenderer.drawStyledLines
// renders character-by-character to support per-glyph text effects like shake/wave/rainbow),
// so no single fillText call carries the whole line — concatenate consecutive single-character
// fillText calls and look for the expected substring in the joined result instead.
const dialogueGlyphs = drawingCalls
  .filter(([operation, text]) => operation === "fillText" && String(text).length === 1)
  .map(([, text]) => text)
  .join("");
const renderedDialogue = dialogueGlyphs.includes("Hello from the JVN web scene bootstrap");
if (!renderedDialogue) {
  throw new Error(
    `Expected the fixture dialogue line to be rendered via per-glyph fillText calls, got: ${JSON.stringify(dialogueGlyphs)}`,
  );
}

// Real click/keyboard routing now exists (sub-project 3) — synthesize an
// actual DOM click at the choice's on-screen position instead of using a
// debug-only hook. The fixture's choice is rendered somewhere in the lower
// portion of the canvas; clicking anywhere that lands on "Option A"/"Wave back"
// per VnChoiceOverlayRenderer's layout advances/selects it. Since this harness
// has no way to introspect exact layout geometry, click at the known
// dialogue-box/advance-affordance position first to advance past the dialogue
// line (if not already fully revealed), mirroring what a player does. (The
// keyboard-driven equivalent of this same ADVANCE action is verified
// separately, further below, via a real DOM SPACE keydown -- see that
// section's comment for why SPACE isn't used here instead.)
//
// Coordinate space: WebMain's toSceneCoordinates() computes
// `clientX - rect.left` with no pixel-scale division, and this harness's
// getBoundingClientRect() stub returns the canvas BACKING STORE size
// (1920x1080) as `rect.width/height` with `rect.left/top = 0`. Meanwhile
// SceneInputRouter.handleClick()/getHoveredChoiceIndex() are called with
// WebCanvasRenderSurface's LOGICAL width/height (960x540 — backing store
// divided by devicePixelRatio=2). So clientX/clientY here must be supplied
// in backing-store pixels (i.e. logicalCoordinate * pixelScale) to land at
// a given logical scene coordinate — derived from tracing WebMain.toSceneCoordinates()
// and WebCanvasRenderSurface's pixelScale/getWidth()/getHeight() (see task-10-report.md
// for the full derivation). This has since been empirically confirmed against the
// actual compiled bundle's rendered fillRect geometry (see the assertion below that
// checks the click target against the real "Wave back" button rect) once the
// pre-existing generateJavaScript blocker was fixed — see task-10-report.md's
// "Final verification" section.
const PIXEL_SCALE = 2;
canvas.dispatchEvent("click", {
  clientX: canvas.width / 2,
  clientY: canvas.height - 40,
  button: 0,
  preventDefault() {},
});
if (scheduledFrames.length !== 1) {
  throw new Error("Game loop did not schedule the next animation frame after the synthesized click");
}
scheduledFrames.shift()(50.0);

const renderedChoiceOptionA = drawingCalls.some(
  ([operation, text]) => operation === "fillText" && String(text).includes("Wave back"),
);
const renderedChoiceOptionB = drawingCalls.some(
  ([operation, text]) => operation === "fillText" && String(text).includes("Stay quiet"),
);
if (!renderedChoiceOptionA || !renderedChoiceOptionB) {
  throw new Error("Expected both fixture choice options to be rendered via fillText");
}

// Independently confirm the (480, 240) logical target used for the
// choice-selecting click below is inside the actual rendered choice-0 hit
// box, not just analytically derived. VnChoiceOverlayRenderer draws each
// choice's button background via a single fillRect(x, y, w, h) call; per
// VnUiLayoutSpec defaults with this fixture's 2-option choice on a 960x540
// logical canvas, the two choice-button rects are identifiable among all
// fillRect calls by their expected width (choiceWidth = 960 * 0.6 = 576)
// and height (choiceHeight = 50), and appear in choice-index order.
// Verifying against the actual drawn rectangles (rather than trusting the
// analytical derivation alone) is what makes the click coordinates below a
// verified, not incidental, hit.
const choiceButtonRects = drawingCalls
  .filter(([operation, , , width, height]) => operation === "fillRect" && width === 576 && height === 50)
  .slice(0, 2);
if (choiceButtonRects.length !== 2) {
  throw new Error(
    `Expected exactly two 576x50 choice-button fillRect calls, got: ${JSON.stringify(
      drawingCalls.filter(([operation]) => operation === "fillRect"),
    )}`,
  );
}
const [, choiceOneRect] = choiceButtonRects;
const [waveBackButtonOp, waveBackX, waveBackY, waveBackWidth, waveBackHeight] = choiceButtonRects[0];
if (waveBackButtonOp !== "fillRect") {
  throw new Error(`Expected a fillRect call for the "Wave back" choice button, got: ${JSON.stringify(choiceButtonRects[0])}`);
}

if (scheduledFrames.length !== 1) {
  throw new Error("Game loop did not schedule the next animation frame after frame 3");
}

// Select the "Wave back" choice option via a second synthesized click. Per
// VnUiLayoutSpec defaults (choiceXCenter=0.5, choiceWidthFactor=0.6,
// choiceHeight=50, choiceGap=10, choiceYStart=-1 meaning auto-vertical-center),
// with the fixture's 2-option choice and a 960x540 logical canvas:
//   totalHeight = 2*50 + 10 = 110; startY = (540-110)/2 = 215
//   choice 0 ("Wave back") logical y-range [215, 265], center y ~= 240
//   choiceWidth = 960*0.6 = 576; choiceX = 960*0.5 - 288 = 192, center x ~= 480
// Convert logical -> backing-store pixels (the coordinate space clientX/clientY
// must be supplied in, see note above) by multiplying by PIXEL_SCALE.
const choiceOptionALogicalX = 480;
const choiceOptionALogicalY = 240;

// Confirm (480, 240) actually lands inside the *rendered* "Wave back" button
// rectangle (verified above, not just the analytically-derived expectation) —
// this is what makes the click below load-bearing rather than an incidental
// pass. This assertion would have caught a wrong PIXEL_SCALE, a wrong choice
// index assumption, or a VnUiLayoutSpec default drifting from what's
// documented above.
if (
  choiceOptionALogicalX < waveBackX ||
  choiceOptionALogicalX > waveBackX + waveBackWidth ||
  choiceOptionALogicalY < waveBackY ||
  choiceOptionALogicalY > waveBackY + waveBackHeight
) {
  throw new Error(
    `Choice-click target (${choiceOptionALogicalX}, ${choiceOptionALogicalY}) does not fall within the rendered ` +
      `"Wave back" button rect [${waveBackX}, ${waveBackY}, ${waveBackWidth}, ${waveBackHeight}] — coordinate derivation is stale.`,
  );
}
// Also confirm it does NOT fall inside the other option's rect, ruling out
// an accidental hit on "Stay quiet" instead (which would also make the test
// pass for the wrong reason).
const [, choiceTwoX, choiceTwoY, choiceTwoWidth, choiceTwoHeight] = choiceOneRect;
if (
  choiceOptionALogicalX >= choiceTwoX &&
  choiceOptionALogicalX <= choiceTwoX + choiceTwoWidth &&
  choiceOptionALogicalY >= choiceTwoY &&
  choiceOptionALogicalY <= choiceTwoY + choiceTwoHeight
) {
  throw new Error("Choice-click target unexpectedly also falls within the \"Stay quiet\" button rect");
}

const drawingCallCountBeforeChoiceClick = drawingCalls.length;
canvas.dispatchEvent("click", {
  clientX: choiceOptionALogicalX * PIXEL_SCALE,
  clientY: choiceOptionALogicalY * PIXEL_SCALE,
  button: 0,
  preventDefault() {},
});
if (scheduledFrames.length !== 1) {
  throw new Error("Game loop did not schedule the next animation frame after the choice-selecting click");
}
scheduledFrames.shift()(66.667);

// Confirm the click was actually consumed as real scene input: the choice
// overlay (both option fillText draws) must no longer be rendered on the
// next frame, proving VnScene left the CHOICE node. This alone doesn't
// distinguish selectChoice(0) from the SceneInputRouter's miss-fallback
// (advanceFromClick(), which also unconditionally leaves a CHOICE node —
// see VnScene.advanceFromClick()/advance()), since the fixture's two choice
// branches (label_wave/label_quiet) both jump straight to [end] with no
// distinguishing content. The fillRect-bounds checks above are what
// actually prove these specific coordinates are correct/load-bearing; this
// check additionally proves the click was processed as scene input at all
// (not silently dropped).
const postClickCalls = drawingCalls.slice(drawingCallCountBeforeChoiceClick);
const stillShowingChoiceOverlay = postClickCalls.some(
  ([operation, text]) =>
    operation === "fillText" && (String(text).includes("Wave back") || String(text).includes("Stay quiet")),
);
if (stillShowingChoiceOverlay) {
  throw new Error("Expected the choice overlay to be gone after the choice-selecting click advanced past it");
}

// Verify real DOM keyboard routing with a genuinely observable engine-state
// change (not just proof that a listener callback fired). The backtick key
// (` `) is bound to InputActions.ROLLBACK by the default profile and,
// unlike SPACE (which is ALSO bound to InputActions.MENU_CONFIRM, so a
// single SPACE keydown fires WebMain.dispatchAction's ADVANCE branch twice
// in the same event -- once per matching binding -- which on this fixture's
// single-choice script advances two full VnScene nodes per keypress and
// would blow straight through the CHOICE node this file's click-based tests
// above still need to land on), ROLLBACK has exactly one binding, making it
// a clean single-effect action to assert on without disturbing any other
// assertion's required scene state.
//
// Per the WHATWG UI Events spec, a browser's real KeyboardEvent.key value
// for the backtick/backquote key is the literal "`" character (DOM
// KeyboardEvent.code is "Backquote", but .key is "`" — WebMain's listener
// reads .key). This dispatch intentionally uses that spec-accurate raw
// value, exercising the exact same DOM-key-to-InputCode translation
// (WebMain.canonicalProfileKeyName) that production code runs, rather than
// a harness-only convenience string.
//
// By this point the click-based flow above has advanced VnScene through the
// dialogue line, into the CHOICE node, and past it to [end] (via the
// choice-selecting click), so a real rollback snapshot exists to roll back
// into. This assertion checks for the dialogue content REAPPEARING (the
// "Lavender" name-box label and the dialogue line's glyphs, both absent
// once the scene reached [end] -- confirmed by the "end" render drawing no
// dialogue/name-box text) rather than the HUD toast text SceneInputRouter.
// rollback() also shows ("Rolled back", via vn.getState().showHudMessage()):
// that toast's line-wrapping (HudToastLayout.compute) uses a "\R"
// regex construct TeaVM's classlib regex engine does not support, which
// throws a caught-and-logged (silently swallowed by WebGameLoop.onFrame's
// catch block) PatternSyntaxException on this target -- a real, pre-existing,
// separate TeaVM-compatibility defect unrelated to the keyboard-mapping fix
// under test here, discovered while building this assertion. Asserting on
// the dialogue content instead avoids that landmine while still proving
// ROLLBACK genuinely fired: VnScene.rollback() only succeeds (returns true)
// when a snapshot exists AND actually restores it via previous.applyTo(state).
// Before WebMain's DOM-key-to-profile-key mapping fix, ke.getKey() ("`")
// upper-cased to "`" (unchanged -- toUpperCase() does not touch punctuation),
// which never matched the profile's InputCode.key("BACK_QUOTE") binding, so
// this keydown would have silently done nothing and the scene would still
// be sitting at the blank [end] state with no dialogue content restored.
const drawingCallCountBeforeRollbackKeydown = drawingCalls.length;
document.dispatchEvent("keydown", { key: "`", code: "Backquote", preventDefault() {} });
if (scheduledFrames.length !== 1) {
  throw new Error("Game loop did not schedule the next animation frame after the synthesized BACK_QUOTE keydown");
}
scheduledFrames.shift()(83.334);

const postRollbackKeydownCalls = drawingCalls.slice(drawingCallCountBeforeRollbackKeydown);
const restoredNameBox = postRollbackKeydownCalls.some(
  ([operation, text]) => operation === "fillText" && String(text) === "Lavender",
);
const restoredDialogueGlyphs = postRollbackKeydownCalls
  .filter(([operation, text]) => operation === "fillText" && String(text).length === 1)
  .map(([, text]) => text)
  .join("");
const restoredDialogueText = restoredDialogueGlyphs.includes("Hello from the JVN web scene bootstrap");
if (!restoredNameBox || !restoredDialogueText) {
  throw new Error(
    'Expected the synthesized BACK_QUOTE keydown to trigger InputActions.ROLLBACK and restore the ' +
      'dialogue line/name-box that were on screen before the choice was made -- this means the DOM ' +
      'KeyboardEvent.key value ("`") was not correctly translated to the InputActions profile\'s ' +
      `"BACK_QUOTE" binding, so ROLLBACK never fired. Got drawing calls: ${JSON.stringify(postRollbackKeydownCalls)}`,
  );
}

console.log("Web bundle smoke test passed");
