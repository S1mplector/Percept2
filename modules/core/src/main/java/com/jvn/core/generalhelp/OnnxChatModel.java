package com.jvn.core.generalhelp;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.OrtSession.SessionOptions;
import ai.onnxruntime.OrtSession.Result;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.TensorInfo;
import java.io.IOException;
import java.text.BreakIterator;
import java.nio.LongBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.SplittableRandom;

/**
 * Minimal ONNX-backed local chat model for Jane.
 *
 * <p>This adapter targets compact decoder-style ONNX models with an
 * {@code input_ids} input, an optional {@code attention_mask} input, and logits
 * output shaped like {@code [batch, sequence, vocab]} or {@code [batch, vocab]}.
 * Tokenization is Java-only through DJL's Hugging Face tokenizer binding:
 * provide a tokenizer JSON through {@code jvn.jane.onnx.tokenizer}.</p>
 */
public final class OnnxChatModel implements LocalChatModel, AutoCloseable {
  private static final String MODEL_PROPERTY = "jvn.jane.onnx.model";
  private static final String VOCAB_PROPERTY = "jvn.jane.onnx.vocab";
  private static final String TOKENIZER_PROPERTY = "jvn.jane.onnx.tokenizer";
  private static final String MAX_NEW_TOKENS_PROPERTY = "jvn.jane.onnx.maxNewTokens";
  private static final String MAX_PROMPT_TOKENS_PROPERTY = "jvn.jane.onnx.maxPromptTokens";
  private static final String TOP_K_PROPERTY = "jvn.jane.onnx.topK";
  private static final String TEMPERATURE_PROPERTY = "jvn.jane.onnx.temperature";
  private static final String MODEL_NAME_PROPERTY = "jvn.jane.onnx.name";
  private static final String REPO_ROOT_PROPERTY = "jvn.repoRoot";
  private static final String WORKSPACE_ROOT_PROPERTY = "jvn.jane.workspaceRoot";
  private static final String DEFAULT_MODEL_NAME = "Jane Qwen2.5 1.5B Instruct ONNX";
  private static final String DEFAULT_MODEL_DIR = ".jvn/jane-model/qwen2.5-1.5b-instruct";
  private static final long GPT2_EOS_TOKEN = 50256L;
  private static final long QWEN_END_OF_TEXT_TOKEN = 151643L;
  private static final long QWEN_IM_END_TOKEN = 151645L;

  private final Path modelPath;
  private final Path tokenizerPath;
  private final int maxNewTokens;
  private final int maxPromptTokens;
  private final int topK;
  private final double temperature;
  private final String modelName;
  private final HuggingFaceTokenizer tokenizer;
  private final OrtEnvironment environment;
  private final OrtSession session;

  public static Optional<OnnxChatModel> fromSystemProperties() {
    String rawModel = System.getProperty(MODEL_PROPERTY);
    String rawTokenizer = System.getProperty(TOKENIZER_PROPERTY);
    if (rawTokenizer == null || rawTokenizer.isBlank()) {
      rawTokenizer = System.getProperty(VOCAB_PROPERTY);
    }
    if (rawModel == null || rawModel.isBlank() || rawTokenizer == null || rawTokenizer.isBlank()) {
      Optional<ModelFiles> bundled = bundledModelFiles();
      if (bundled.isPresent()) {
        rawModel = bundled.get().model().toString();
        rawTokenizer = bundled.get().tokenizer().toString();
      }
    }
    if (rawModel == null || rawModel.isBlank() || rawTokenizer == null || rawTokenizer.isBlank()) {
      return Optional.empty();
    }
    try {
      return Optional.of(new OnnxChatModel(
          Path.of(rawModel),
          Path.of(rawTokenizer),
          intProperty(MAX_NEW_TOKENS_PROPERTY, 48),
          intProperty(MAX_PROMPT_TOKENS_PROPERTY, 256),
          intProperty(TOP_K_PROPERTY, 1),
          doubleProperty(TEMPERATURE_PROPERTY, 0.0),
          System.getProperty(MODEL_NAME_PROPERTY, DEFAULT_MODEL_NAME)));
    } catch (RuntimeException ex) {
      return Optional.empty();
    }
  }

  private static Optional<ModelFiles> bundledModelFiles() {
    List<Path> roots = new ArrayList<>();
    addRoot(roots, propertyPath(REPO_ROOT_PROPERTY));
    addRoot(roots, propertyPath(WORKSPACE_ROOT_PROPERTY));
    addRoot(roots, propertyPath("user.dir"));
    for (Path root : roots) {
      Path dir = root.resolve(DEFAULT_MODEL_DIR).toAbsolutePath().normalize();
      Path model = dir.resolve("model.onnx");
      Path tokenizer = dir.resolve("tokenizer.json");
      if (!Files.isRegularFile(model)) {
        model = dir.resolve("model_quantized.onnx");
      }
      if (Files.isRegularFile(model) && Files.isRegularFile(tokenizer)) {
        return Optional.of(new ModelFiles(model, tokenizer));
      }
    }
    return Optional.empty();
  }

  private static void addRoot(List<Path> roots, Path root) {
    if (root != null && !roots.contains(root)) roots.add(root);
  }

  private static Path propertyPath(String key) {
    String raw = System.getProperty(key);
    if (raw == null || raw.isBlank()) return null;
    return Path.of(raw).toAbsolutePath().normalize();
  }

  public OnnxChatModel(
      Path modelPath,
      Path tokenizerPath,
      int maxNewTokens,
      int maxPromptTokens,
      int topK,
      double temperature,
      String modelName
  ) {
    this.modelPath = requireReadable(modelPath, "model");
    this.tokenizerPath = requireReadable(tokenizerPath, "tokenizer");
    this.maxNewTokens = Math.max(1, maxNewTokens);
    this.maxPromptTokens = Math.max(16, maxPromptTokens);
    this.topK = Math.max(1, topK);
    this.temperature = temperature <= 0.0 || Double.isNaN(temperature) || Double.isInfinite(temperature)
        ? 0.0
        : Math.min(2.0, temperature);
    this.modelName = modelName == null || modelName.isBlank() ? "ONNX local chat" : modelName.trim();
    try {
      this.tokenizer = HuggingFaceTokenizer.newInstance(this.tokenizerPath);
    } catch (IOException ex) {
      throw new IllegalStateException("Failed to load Jane tokenizer: " + this.tokenizerPath, ex);
    }
    try {
      this.environment = OrtEnvironment.getEnvironment();
      this.session = environment.createSession(this.modelPath.toString(), new SessionOptions());
    } catch (OrtException ex) {
      throw new IllegalStateException("Failed to load ONNX model: " + this.modelPath, ex);
    }
  }

  @Override
  public String name() {
    return modelName;
  }

  @Override
  public boolean isAvailable() {
    return session != null && tokenizer != null;
  }

  @Override
  public String generate(String query, HelpResponse grounding, List<ChatMessage> history) {
    String prompt = buildPrompt(query, grounding, history);
    List<Long> tokens = new ArrayList<>();
    Encoding encoding = tokenizer.encode(prompt);
    for (long id : encoding.getIds()) {
      tokens.add(id);
    }
    trimPrompt(tokens, maxPromptTokens);
    if (usesPastKeyValues()) {
      return generateWithCache(tokens);
    }
    return generateWithoutCache(tokens);
  }

  private String generateWithoutCache(List<Long> tokens) {
    int promptSize = tokens.size();
    List<Long> generatedTokens = new ArrayList<>();
    SplittableRandom random = new SplittableRandom(stableSeed(tokens));
    for (int i = 0; i < maxNewTokens; i++) {
      long next = nextToken(tokens, generatedTokens, random);
      if (isStopToken(next)) break;
      tokens.add(next);
      generatedTokens.add(next);
      if (hasRepeatedTail(generatedTokens, 4)) break;
      trimLeft(tokens, maxPromptTokens + maxNewTokens);
    }
    List<Long> generated = tokens.subList(Math.min(promptSize, tokens.size()), tokens.size());
    long[] ids = generated.stream().mapToLong(Long::longValue).toArray();
    String decoded = cleanupGeneratedText(tokenizer.decode(ids));
    return isLowQuality(decoded) ? "" : decoded;
  }

  private String generateWithCache(List<Long> promptTokens) {
    List<Long> generatedTokens = new ArrayList<>();
    List<Long> currentInput = new ArrayList<>(promptTokens);
    SplittableRandom random = new SplittableRandom(stableSeed(promptTokens));
    long pastLength = 0L;
    Result cacheResult = null;
    try {
      for (int i = 0; i < maxNewTokens; i++) {
        RunInputs inputs = createRunInputs(currentInput, pastLength, cacheResult);
        Result nextResult = null;
        try {
          nextResult = session.run(inputs.inputs());
          Object value = nextResult.get("logits").orElse(nextResult.get(0)).getValue();
          long next = selectToken(lastLogits(value), currentInput, generatedTokens, random);
          if (cacheResult != null) {
            cacheResult.close();
            cacheResult = null;
          }
          cacheResult = nextResult;
          nextResult = null;
          pastLength += currentInput.size();
          if (isStopToken(next)) break;
          generatedTokens.add(next);
          if (hasRepeatedTail(generatedTokens, 4)) break;
          currentInput = List.of(next);
        } finally {
          inputs.close();
          if (nextResult != null) nextResult.close();
        }
      }
    } catch (OrtException ex) {
      throw new IllegalStateException("ONNX generation failed", ex);
    } finally {
      if (cacheResult != null) {
        cacheResult.close();
      }
    }
    long[] ids = generatedTokens.stream().mapToLong(Long::longValue).toArray();
    String decoded = cleanupGeneratedText(tokenizer.decode(ids));
    return isLowQuality(decoded) ? "" : decoded;
  }

  @Override
  public void close() {
    try {
      session.close();
    } catch (Exception ignored) {
      // Nothing else to release if the native session has already closed.
    }
  }

  private long nextToken(List<Long> tokens, List<Long> generatedTokens, SplittableRandom random) {
    long[] ids = tokens.stream().mapToLong(Long::longValue).toArray();
    long[] mask = new long[ids.length];
    java.util.Arrays.fill(mask, 1L);
    try (OnnxTensor inputIds = OnnxTensor.createTensor(environment, LongBuffer.wrap(ids), new long[] {1, ids.length});
         OnnxTensor attention = OnnxTensor.createTensor(environment, LongBuffer.wrap(mask), new long[] {1, mask.length});
         Result result = session.run(inputMap(inputIds, attention))) {
      Object value = result.get(0).getValue();
      return selectToken(lastLogits(value), tokens, generatedTokens, random);
    } catch (OrtException ex) {
      throw new IllegalStateException("ONNX generation failed", ex);
    }
  }

  private Map<String, OnnxTensor> inputMap(OnnxTensor inputIds, OnnxTensor attention) {
    Map<String, OnnxTensor> inputs = new LinkedHashMap<>();
    inputs.put("input_ids", inputIds);
    if (session.getInputNames().contains("attention_mask")) {
      inputs.put("attention_mask", attention);
    }
    return inputs;
  }

  private RunInputs createRunInputs(List<Long> currentInput, long pastLength, Result cacheResult)
      throws OrtException {
    Map<String, OnnxTensor> inputs = new LinkedHashMap<>();
    List<OnnxTensor> owned = new ArrayList<>();
    long[] ids = currentInput.stream().mapToLong(Long::longValue).toArray();
    long[] attentionMask = new long[(int) pastLength + ids.length];
    java.util.Arrays.fill(attentionMask, 1L);
    long[] positionIds = new long[ids.length];
    for (int i = 0; i < ids.length; i++) {
      positionIds[i] = pastLength + i;
    }

    OnnxTensor inputIds = OnnxTensor.createTensor(environment, LongBuffer.wrap(ids), new long[] {1, ids.length});
    OnnxTensor attention = OnnxTensor.createTensor(
        environment,
        LongBuffer.wrap(attentionMask),
        new long[] {1, attentionMask.length});
    owned.add(inputIds);
    owned.add(attention);
    inputs.put("input_ids", inputIds);
    inputs.put("attention_mask", attention);
    if (session.getInputNames().contains("position_ids")) {
      OnnxTensor positions = OnnxTensor.createTensor(
          environment,
          LongBuffer.wrap(positionIds),
          new long[] {1, positionIds.length});
      owned.add(positions);
      inputs.put("position_ids", positions);
    }
    addPastKeyValues(inputs, owned, cacheResult);
    return new RunInputs(inputs, owned);
  }

  private void addPastKeyValues(Map<String, OnnxTensor> inputs, List<OnnxTensor> owned, Result cacheResult)
      throws OrtException {
    for (String inputName : session.getInputNames()) {
      if (!inputName.startsWith("past_key_values.")) continue;
      if (cacheResult != null) {
        String presentName = inputName.replace("past_key_values.", "present.");
        OnnxValue present = cacheResult.get(presentName)
            .orElseThrow(() -> new IllegalStateException("Missing ONNX cache output: " + presentName));
        inputs.put(inputName, (OnnxTensor) present);
      } else {
        long[] shape = pastShape(inputName);
        OnnxTensor empty = OnnxTensor.createTensor(
            environment,
            java.nio.FloatBuffer.wrap(new float[0]),
            shape);
        owned.add(empty);
        inputs.put(inputName, empty);
      }
    }
  }

  private long[] pastShape(String inputName) {
    try {
      Object info = session.getInputInfo().get(inputName).getInfo();
      if (info instanceof TensorInfo tensorInfo) {
        long[] shape = tensorInfo.getShape();
        long heads = shape.length > 1 && shape[1] > 0 ? shape[1] : 2L;
        long dim = shape.length > 3 && shape[3] > 0 ? shape[3] : 64L;
        return new long[] {1L, heads, 0L, dim};
      }
    } catch (Exception ignored) {
      // Fall back to Qwen2.5's grouped-query cache shape.
    }
    return new long[] {1L, 2L, 0L, 64L};
  }

  private boolean usesPastKeyValues() {
    return session.getInputNames().stream().anyMatch(name -> name.startsWith("past_key_values."));
  }

  private static float[] lastLogits(Object value) {
    if (value instanceof float[][][] logits3d) {
      float[][] sequence = logits3d[0];
      return sequence[Math.max(0, sequence.length - 1)];
    }
    if (value instanceof float[][] logits2d) {
      return logits2d[0];
    }
    throw new IllegalStateException("Unsupported ONNX logits output: " + value.getClass().getName());
  }

  private long selectToken(
      float[] logits,
      List<Long> tokens,
      List<Long> generatedTokens,
      SplittableRandom random
  ) {
    if (temperature <= 0.0 || topK <= 1) {
      return argmax(logits, tokens, generatedTokens);
    }
    List<TokenScore> candidates = new ArrayList<>(Math.min(topK, logits.length));
    for (int i = 0; i < logits.length; i++) {
      float value = adjustedLogit(logits[i], i, tokens, generatedTokens);
      if (Float.isNaN(value) || value == Float.NEGATIVE_INFINITY) continue;
      if (candidates.size() < topK) {
        candidates.add(new TokenScore(i, value));
        candidates.sort((left, right) -> Float.compare(left.score(), right.score()));
      } else if (value > candidates.get(0).score()) {
        candidates.set(0, new TokenScore(i, value));
        candidates.sort((left, right) -> Float.compare(left.score(), right.score()));
      }
    }
    if (candidates.isEmpty()) return argmax(logits, tokens, generatedTokens);
    double max = candidates.stream().mapToDouble(TokenScore::score).max().orElse(0.0);
    double total = 0.0;
    double[] weights = new double[candidates.size()];
    for (int i = 0; i < candidates.size(); i++) {
      double weight = Math.exp((candidates.get(i).score() - max) / temperature);
      weights[i] = weight;
      total += weight;
    }
    double draw = random.nextDouble(total);
    for (int i = 0; i < candidates.size(); i++) {
      draw -= weights[i];
      if (draw <= 0.0) return candidates.get(i).token();
    }
    return candidates.get(candidates.size() - 1).token();
  }

  private static long argmax(float[] logits, List<Long> tokens, List<Long> generatedTokens) {
    int best = 0;
    float bestValue = Float.NEGATIVE_INFINITY;
    for (int i = 0; i < logits.length; i++) {
      float value = adjustedLogit(logits[i], i, tokens, generatedTokens);
      if (value > bestValue) {
        best = i;
        bestValue = value;
      }
    }
    return best;
  }

  private static float adjustedLogit(float value, int token, List<Long> tokens, List<Long> generatedTokens) {
    if (generatedTokens != null && generatedTokens.size() >= 2) {
      long previous = generatedTokens.get(generatedTokens.size() - 1);
      long beforePrevious = generatedTokens.get(generatedTokens.size() - 2);
      if (previous == token && beforePrevious == token) {
        return Float.NEGATIVE_INFINITY;
      }
    }
    int recentMatches = 0;
    if (tokens != null) {
      int start = Math.max(0, tokens.size() - 64);
      for (int i = start; i < tokens.size(); i++) {
        if (tokens.get(i) == token) recentMatches++;
      }
    }
    if (recentMatches == 0) return value;
    float penalty = 1.08f + Math.min(0.45f, recentMatches * 0.035f);
    return value >= 0 ? value / penalty : value * penalty;
  }

  private boolean isStopToken(long token) {
    return token == GPT2_EOS_TOKEN || token == QWEN_END_OF_TEXT_TOKEN || token == QWEN_IM_END_TOKEN;
  }

  private String buildPrompt(String query, HelpResponse grounding, List<ChatMessage> history) {
    if (isQwenModel()) {
      return buildQwenPrompt(query, grounding, history);
    }
    StringBuilder prompt = new StringBuilder();
    prompt.append("Jane is JVN's assistant. Answer from the JVN help context.\n");
    if (history != null && !history.isEmpty()) {
      prompt.append("\nRecent chat:\n");
      history.stream().skip(Math.max(0, history.size() - 6)).forEach(message ->
          prompt.append(message.role()).append(": ").append(message.content()).append('\n'));
    }
    if (grounding != null) {
      prompt.append("\nTAGI context:\n").append(grounding.answer()).append('\n');
      for (HelpArticle article : grounding.recommendedArticles()) {
        prompt.append("- ").append(article.title()).append(": ").append(article.summary()).append('\n');
      }
    }
    prompt.append("\nUser: ").append(query == null ? "" : query.trim()).append("\nJane:");
    return prompt.toString();
  }

  private String buildQwenPrompt(String query, HelpResponse grounding, List<ChatMessage> history) {
    StringBuilder prompt = new StringBuilder();
    prompt.append("<|im_start|>system\n");
    prompt.append("You are Jane, JVN's local assistant. Answer only about the Java Vector Nexus engine. ");
    prompt.append("Critical JVN glossary: VNS means visual novel scripting. JES means JVN Entity Scripting, the engine's scene and gameplay scripting layer; never expand it as Java Execution System. Puppeteer means timeline animation authoring. ");
    prompt.append("Answer the user's question directly in 2 to 5 concise sentences. ");
    prompt.append("Use plain text only, with no markdown headings, no numbered walkthroughs, and no article-style intro. ");
    prompt.append("Use the JVN facts as higher priority than pretrained knowledge. Synthesize the facts; do not quote or continue them. ");
    prompt.append("Do not list documentation links unless the user asks for sources. Do not invent file paths.\n");
    if (grounding != null) {
      List<String> facts = groundingFacts(query, grounding);
      if (!facts.isEmpty()) {
        prompt.append("\nJVN facts:\n");
        for (String fact : facts) {
          prompt.append("- ").append(fact).append('\n');
        }
      }
    }
    prompt.append("<|im_end|>\n");
    if (history != null && !history.isEmpty()) {
      history.stream().skip(Math.max(0, history.size() - 6)).forEach(message -> {
        String role = message.role().equals("assistant") ? "assistant" : "user";
        prompt.append("<|im_start|>").append(role).append('\n')
            .append(message.content()).append("<|im_end|>\n");
      });
    }
    prompt.append("<|im_start|>user\n")
        .append(query == null ? "" : query.trim())
        .append("<|im_end|>\n")
        .append("<|im_start|>assistant\n");
    return prompt.toString();
  }

  private static List<String> groundingFacts(String query, HelpResponse grounding) {
    if (grounding == null || grounding.recommendedArticles().isEmpty()) return List.of();
    List<String> facts = new ArrayList<>();
    for (HelpArticle article : grounding.recommendedArticles().stream().limit(3).toList()) {
      String fact = evidenceFact(query, article);
      if (!fact.isBlank() && facts.stream().noneMatch(existing -> similarFact(existing, fact))) {
        facts.add(fact);
      }
      if (facts.size() >= 4) break;
    }
    return facts;
  }

  private static String evidenceFact(String query, HelpArticle article) {
    if (article == null) return "";
    String source = article.body().isBlank() ? article.summary() : article.body();
    source = stripMarkdown(source);
    List<String> sentences = relevantSentences(query, source);
    if (sentences.isEmpty() && !article.summary().isBlank()) {
      sentences = relevantSentences(query, stripMarkdown(article.summary()));
    }
    if (sentences.isEmpty()) return "";
    return compactText(String.join(" ", sentences), 260);
  }

  private static List<String> relevantSentences(String query, String source) {
    if (source == null || source.isBlank()) return List.of();
    List<String> queryTokens = contentTokens(query);
    BreakIterator iterator = BreakIterator.getSentenceInstance(Locale.ROOT);
    iterator.setText(source);
    List<SentenceScore> scored = new ArrayList<>();
    int start = iterator.first();
    int index = 0;
    for (int end = iterator.next(); end != BreakIterator.DONE; start = end, end = iterator.next()) {
      String sentence = source.substring(start, end).replaceAll("\\s+", " ").trim();
      if (sentence.length() < 18 || looksLikeDocChrome(sentence)) continue;
      int score = 0;
      String lower = sentence.toLowerCase(Locale.ROOT);
      for (String token : queryTokens) {
        if (lower.contains(token)) score++;
      }
      if (score > 0 || scored.isEmpty()) {
        scored.add(new SentenceScore(sentence, score, index));
      }
      index++;
    }
    return scored.stream()
        .sorted((left, right) -> {
          int score = Integer.compare(right.score(), left.score());
          return score != 0 ? score : Integer.compare(left.index(), right.index());
        })
        .limit(2)
        .map(SentenceScore::sentence)
        .toList();
  }

  private static String stripMarkdown(String value) {
    if (value == null) return "";
    return value
        .replaceAll("(?m)^#{1,6}\\s+", "")
        .replaceAll("(?m)^[-*]\\s+", "")
        .replaceAll("`([^`]+)`", "$1")
        .replaceAll("\\[([^]]+)]\\([^)]+\\)", "$1")
        .replaceAll("\\s+", " ")
        .trim();
  }

  private static boolean looksLikeDocChrome(String sentence) {
    String lower = sentence.toLowerCase(Locale.ROOT);
    return lower.contains("prerequisites")
        || lower.contains("chapters")
        || lower.contains("by example")
        || lower.startsWith("open these docs")
        || lower.startsWith("recommended docs")
        || lower.startsWith("tagi consensus")
        || lower.startsWith("why this matched");
  }

  private static List<String> contentTokens(String value) {
    if (value == null || value.isBlank()) return List.of();
    List<String> tokens = new ArrayList<>();
    for (String part : value.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
      if (part.length() >= 3 && !List.of("what", "how", "does", "the", "and", "for", "with", "used").contains(part)) {
        tokens.add(part);
      }
    }
    return tokens;
  }

  private static boolean similarFact(String left, String right) {
    String a = left.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    String b = right.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    return !a.isBlank() && !b.isBlank() && (a.contains(b) || b.contains(a));
  }

  private static String compactText(String value, int limit) {
    if (value == null) return "";
    String compact = value.replaceAll("\\s+", " ").trim();
    if (compact.length() > limit) compact = compact.substring(0, Math.max(0, limit - 3)).trim() + "...";
    return compact;
  }

  private boolean isQwenModel() {
    String name = modelName == null ? "" : modelName.toLowerCase(java.util.Locale.ROOT);
    String file = modelPath == null ? "" : modelPath.toString().toLowerCase(java.util.Locale.ROOT);
    return name.contains("qwen") || file.contains("qwen");
  }

  private static void trimPrompt(List<Long> values, int maxSize) {
    if (values.size() <= maxSize) return;
    int keepPrefix = Math.min(96, maxSize / 3);
    int keepSuffix = maxSize - keepPrefix;
    List<Long> trimmed = new ArrayList<>(maxSize);
    trimmed.addAll(values.subList(0, keepPrefix));
    trimmed.addAll(values.subList(values.size() - keepSuffix, values.size()));
    values.clear();
    values.addAll(trimmed);
  }

  private static void trimLeft(List<Long> values, int maxSize) {
    while (values.size() > maxSize) {
      values.remove(0);
    }
  }

  private static long stableSeed(List<Long> values) {
    long seed = 0x9E3779B97F4A7C15L;
    if (values == null) return seed;
    for (long value : values) {
      seed ^= value + 0x9E3779B97F4A7C15L + (seed << 6) + (seed >>> 2);
    }
    return seed;
  }

  private static boolean hasRepeatedTail(List<Long> values, int repeatCount) {
    if (values == null || values.size() < repeatCount) return false;
    long last = values.get(values.size() - 1);
    for (int i = 2; i <= repeatCount; i++) {
      if (values.get(values.size() - i) != last) return false;
    }
    return true;
  }

  private static String cleanupGeneratedText(String decoded) {
    if (decoded == null) return "";
    String value = decoded.replace('\r', '\n').trim();
    int stop = firstStopIndex(value);
    if (stop >= 0) value = value.substring(0, stop).trim();
    value = value.replaceAll("(?i)^jane\\s*:\\s*", "").trim();
    value = value.replace("<|im_end|>", "").replace("<|endoftext|>", "").trim();
    return value;
  }

  private static int firstStopIndex(String value) {
    int best = -1;
    for (String marker : List.of("\nUser:", "\nJane:", "User:", "Jane:")) {
      int index = value.indexOf(marker);
      if (index >= 0 && (best < 0 || index < best)) best = index;
    }
    return best;
  }

  private static boolean isLowQuality(String value) {
    if (value == null || value.isBlank()) return true;
    if (value.indexOf('\uFFFD') >= 0) return true;
    String lower = value.trim().toLowerCase(Locale.ROOT);
    if (lower.startsWith("cannot be done")) return true;
    if (lower.matches("(?s).*\\b(vns|jes|puppeteer) by example\\s*/\\s*prerequisites\\b.*")) return true;
    String compact = value.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]", "");
    if (compact.length() < 2) return true;
    if (compact.length() >= 24 && compact.chars().distinct().count() <= 7) return true;
    for (int width = 2; width <= 10; width++) {
      if (compact.length() < width * 5) continue;
      String pattern = compact.substring(0, width);
      int matched = 0;
      for (int i = 0; i + width <= compact.length(); i += width) {
        if (compact.substring(i, i + width).equals(pattern)) matched += width;
      }
      if (matched >= compact.length() * 0.72) return true;
    }
    return hasDominantRepeatedNgram(compact);
  }

  private static boolean hasDominantRepeatedNgram(String compact) {
    if (compact.length() < 24) return false;
    for (int width = 2; width <= 8; width++) {
      Map<String, Integer> counts = new HashMap<>();
      for (int i = 0; i + width <= compact.length(); i++) {
        String token = compact.substring(i, i + width);
        counts.merge(token, 1, Integer::sum);
      }
      int max = counts.values().stream().mapToInt(Integer::intValue).max().orElse(0);
      if (max >= 5 && max * width >= compact.length() * 0.32) {
        return true;
      }
    }
    return false;
  }

  private static Path requireReadable(Path path, String label) {
    if (path == null || !Files.isRegularFile(path)) {
      throw new IllegalArgumentException("Jane ONNX " + label + " file does not exist: " + path);
    }
    return path.toAbsolutePath().normalize();
  }

  private static int intProperty(String key, int fallback) {
    String raw = System.getProperty(key);
    if (raw == null || raw.isBlank()) return fallback;
    try {
      return Integer.parseInt(raw.trim());
    } catch (NumberFormatException ex) {
      return fallback;
    }
  }

  private static double doubleProperty(String key, double fallback) {
    String raw = System.getProperty(key);
    if (raw == null || raw.isBlank()) return fallback;
    try {
      return Double.parseDouble(raw.trim());
    } catch (NumberFormatException ex) {
      return fallback;
    }
  }

  private record ModelFiles(Path model, Path tokenizer) {}

  private record TokenScore(int token, float score) {}

  private record SentenceScore(String sentence, int score, int index) {}

  private record RunInputs(Map<String, OnnxTensor> inputs, List<OnnxTensor> owned) implements AutoCloseable {
    @Override
    public void close() {
      for (OnnxTensor tensor : owned) {
        try {
          tensor.close();
        } catch (Exception ignored) {
          // Best-effort cleanup of temporary generation tensors.
        }
      }
    }
  }
}
