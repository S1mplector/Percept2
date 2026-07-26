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
  fillText: (...args) => drawingCalls.push(["fillText", ...args]),
  set fillStyle(value) {
    drawingCalls.push(["fillStyle", value]);
  },
  set font(value) {
    drawingCalls.push(["font", value]);
  },
};

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
globalThis.window = { devicePixelRatio: 2 };
globalThis.document = {
  title: "",
  getElementById(id) {
    if (id === "jvn-canvas") return canvas;
    if (id === "jvn-status") return status;
    if (id === "jvn-config") return config;
    return null;
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

scheduledFrames.shift()(16.667);

const renderedTitle = drawingCalls.some(
  ([operation, text]) => operation === "fillText" && text === "Bundle Smoke",
);
if (!renderedTitle) {
  throw new Error("First animation frame did not render the configured title");
}
if (scheduledFrames.length !== 1) {
  throw new Error("Game loop did not schedule the next animation frame");
}

console.log("Web bundle smoke test passed");
