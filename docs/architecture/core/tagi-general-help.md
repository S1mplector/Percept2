# TAGI General Help And Jane

TAGI general help is the engine's local grounding layer for Jane, JVN's assistant.
It ranks indexed JVN documentation with three deterministic agents and merges their
votes into one consensus answer. Jane wraps that grounded answer in a chatbot
session and can optionally use Gemini or a lightweight ONNX model for answer
synthesis.

TAGI stands for three-agent general intelligence in this feature:

- Intent agent: matches the user's question against document titles, summaries, and paths.
- Evidence agent: searches document body text and extracts the strongest local snippet.
- Workflow agent: prefers docs that fit the likely task flow, such as tutorials, examples, diagnostics, VNS, JES, menus, timelines, or packaging.

TAGI itself does not call an LLM, remote API, embedding service, or network
endpoint. It uses lexical scoring, domain rules, and weighted consensus over a
loaded help corpus. Jane can add synthesis on top with `GeminiChatModel` when a
Gemini API key is configured, or `OnnxChatModel` when a local model is configured
and Gemini is not. Otherwise it falls back to TAGI-grounded formatting.

## Engine API

The core engine exposes the subsystem through:

```java
Engine engine = new Engine(ApplicationConfig.builder().build());
engine.generalHelp().addArticle(HelpArticle.of(
    "vns-choices",
    "VNS Choices",
    "Reference for branching choices in visual novel scripts.",
    "docs/scripting/vns/language/vns-choices.md",
    markdownBody));

HelpResponse response = engine.generalHelp().ask("How do I add VNS choices?");
JaneChatResponse chat = engine.jane().ask("How do I add VNS choices?");
```

`HelpResponse` includes:

- the synthesized answer
- confidence
- recommended articles
- each agent vote
- final consensus scores

`JaneChatResponse` includes:

- Jane's chat answer
- the model/backend name
- whether a configured generative model was used
- the TAGI grounding response
- the short chat transcript

## Jane Training Corpus

Jane is trained through grounding data, not neural fine-tuning. The training
corpus is built by `JaneTrainingCorpus` from:

- curated expert articles covering JVN's architecture, VNS, JES, timelines,
  Puppeteer, menu/layout UI, runtime assets, editor workflows, and diagnostics
- whole Markdown docs indexed by the Help Center
- heading-level Markdown chunks, so Jane can retrieve a specific section instead
  of only a whole file

This gives Jane useful baseline knowledge even before the Help Center has indexed
a workspace, then upgrades her with the current engine and project docs when the
Help Center refreshes.

The curated training articles are intentionally short and directional. They teach
Jane what subsystem owns a problem and which docs to recommend; the detailed
answer still comes from indexed documentation and TAGI votes.

Jane always has a local model available:

- default: `JaneExpertLocalModel`, a built-in local expert model grounded by TAGI
- fallback: `TagiGroundedChatModel`, a deterministic formatter if another model fails
- optional: `GeminiChatModel`, loaded first when a Gemini API key is configured
- optional: `OnnxChatModel`, loaded when ONNX model and tokenizer paths are configured and Gemini is not

## Console Chat

Run Jane from the terminal:

```bash
./gradlew :editor:runJane --console=plain
```

The console launcher indexes workspace Markdown, trains Jane with whole docs and
heading chunks, then starts an interactive prompt. Use `/clear` to clear chat
history and `/exit` to quit.

## Gemini Chat Model

`GeminiChatModel` is Jane's Google AI Studio backend. When configured, it is used
before ONNX and the built-in expert model. The default model is:

```text
gemini-3.1-flash-lite
```

Configure it through an environment variable:

```bash
export GEMINI_API_KEY=...
export JVN_JANE_GEMINI_MODEL=gemini-3.1-flash-lite
./gradlew :editor:runJane --console=plain
```

Or use a local ignored file at `.jvn/jane-gemini.properties`:

```properties
apiKey=...
model=gemini-3.1-flash-lite
```

Do not commit the local properties file. Jane also accepts these JVM properties
for launch wrappers and local experiments:

```bash
-Djvn.jane.gemini.apiKey=...
-Djvn.jane.gemini.model=gemini-3.1-flash-lite
-Djvn.jane.gemini.endpoint=https://generativelanguage.googleapis.com/v1beta
```

## ONNX Chat Model

`OnnxChatModel` is a direct Java ONNX Runtime integration for compact local
decoder-style models. It is used only when Gemini is not configured. It expects:

- an ONNX model file
- a Hugging Face tokenizer JSON file
- an `input_ids` input
- an optional `attention_mask` input
- logits output shaped like `[batch, sequence, vocab]` or `[batch, vocab]`

Configure it with JVM system properties:

```bash
-Djvn.jane.onnx.model=/absolute/path/to/model.onnx
-Djvn.jane.onnx.tokenizer=/absolute/path/to/tokenizer.json
-Djvn.jane.onnx.name=Jane Tiny ONNX
-Djvn.jane.onnx.maxPromptTokens=256
-Djvn.jane.onnx.maxNewTokens=48
```

If the model or tokenizer is missing, Jane still works through the built-in
TAGI-grounded backend.

## Editor Integration

The Help Center indexes workspace and project Markdown files, loads them into TAGI,
trains Jane with whole docs plus heading chunks, and exposes an Ask Jane field in
the preview pane. Questions are answered locally from the indexed docs, with
clickable recommendations back into the Help Center.

This makes TAGI best suited for:

- finding relevant engine docs
- explaining which doc to open first
- routing users through VNS, JES, timeline, menu, packaging, and diagnostic material
- offline help inside the editor
- optional Gemini or ONNX synthesis when a compatible backend is configured

Jane is grounded by documentation. It is not intended to replace human-authored
docs or silently reason beyond the indexed corpus.
