import { createRequire } from "node:module";
import { resolve } from "node:path";

const bundlePath = process.argv[2];
if (!bundlePath) {
  throw new Error("Usage: node scripts/test-web-runtime-bundle.mjs <jvn-web.js>");
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
  }

  addEventListener(type, listener) {
    (this._listeners[type] ||= []).push(listener);
  }

  toString() {
    return this._src;
  }

  set src(value) {
    this._src = value;
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

class HTMLCanvasElement {
  constructor() {
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
}

const canvas = new HTMLCanvasElement();
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
globalThis.document = {
  title: "",
  getElementById(id) {
    if (id === "jvn-canvas") return canvas;
    if (id === "jvn-status") return status;
    if (id === "jvn-config") return config;
    return null;
  },
  createElement(tag) {
    if (tag === "img") return new HTMLImageElement();
    throw new Error(`Unsupported createElement tag in smoke harness: ${tag}`);
  },
};
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

const renderedDialogue = drawingCalls.some(
  ([operation, text]) =>
    operation === "fillText" && String(text).includes("Hello from the JVN web scene bootstrap"),
);
if (!renderedDialogue) {
  throw new Error("Expected the fixture dialogue line to be rendered via fillText");
}

const renderedChoiceOptionA = drawingCalls.some(
  ([operation, text]) => operation === "fillText" && String(text).includes("Wave back"),
);
const renderedChoiceOptionB = drawingCalls.some(
  ([operation, text]) => operation === "fillText" && String(text).includes("Stay quiet"),
);
if (!renderedChoiceOptionA || !renderedChoiceOptionB) {
  throw new Error("Expected both fixture choice options to be rendered via fillText");
}

if (scheduledFrames.length !== 1) {
  throw new Error("Game loop did not schedule the next animation frame after frame 2");
}

console.log("Web bundle smoke test passed");
