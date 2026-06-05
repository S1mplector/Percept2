package com.jvn.core.generalhelp;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;

/** Google AI Studio Gemini backend for Jane. Reads credentials from local configuration only. */
public final class GeminiChatModel implements LocalChatModel {
  private static final String API_KEY_PROPERTY = "jvn.jane.gemini.apiKey";
  private static final String MODEL_PROPERTY = "jvn.jane.gemini.model";
  private static final String ENDPOINT_PROPERTY = "jvn.jane.gemini.endpoint";
  private static final String MAX_OUTPUT_TOKENS_PROPERTY = "jvn.jane.gemini.maxOutputTokens";
  private static final String TEMPERATURE_PROPERTY = "jvn.jane.gemini.temperature";
  private static final String TIMEOUT_SECONDS_PROPERTY = "jvn.jane.gemini.timeoutSeconds";
  private static final String REPO_ROOT_PROPERTY = "jvn.repoRoot";
  private static final String WORKSPACE_ROOT_PROPERTY = "jvn.jane.workspaceRoot";
  private static final String DEFAULT_MODEL = "gemini-3.1-flash-lite";
  private static final String DEFAULT_ENDPOINT = "https://generativelanguage.googleapis.com/v1beta";
  private static final String LOCAL_CONFIG = ".jvn/jane-settings.properties";
  private static final String LEGACY_LOCAL_CONFIG = ".jvn/jane-gemini.properties";

  private final String apiKey;
  private final String model;
  private final String endpoint;
  private final int maxOutputTokens;
  private final double temperature;
  private final Duration requestTimeout;
  private final HttpClient client;

  public static Optional<GeminiChatModel> fromSystemProperties() {
    Properties local = loadLocalConfig();
    String apiKey = firstNonBlank(
        System.getProperty(API_KEY_PROPERTY),
        env("JVN_JANE_GEMINI_API_KEY"),
        env("GEMINI_API_KEY"),
        env("GOOGLE_AI_API_KEY"),
        env("GOOGLE_API_KEY"),
        local.getProperty("gemini.apiKey"),
        local.getProperty("gemini.api_key"),
        local.getProperty("apiKey"),
        local.getProperty("api_key"));
    if (apiKey == null || apiKey.isBlank()) {
      return Optional.empty();
    }
    String model = firstNonBlank(
        System.getProperty(MODEL_PROPERTY),
        env("JVN_JANE_GEMINI_MODEL"),
        local.getProperty("gemini.model"),
        local.getProperty("model"),
        DEFAULT_MODEL);
    String endpoint = firstNonBlank(
        System.getProperty(ENDPOINT_PROPERTY),
        env("JVN_JANE_GEMINI_ENDPOINT"),
        local.getProperty("gemini.endpoint"),
        local.getProperty("endpoint"),
        DEFAULT_ENDPOINT);
    int maxOutputTokens = intValue(firstNonBlank(
        System.getProperty(MAX_OUTPUT_TOKENS_PROPERTY),
        env("JVN_JANE_GEMINI_MAX_OUTPUT_TOKENS"),
        local.getProperty("gemini.maxOutputTokens"),
        local.getProperty("maxOutputTokens")), 768);
    double temperature = doubleValue(firstNonBlank(
        System.getProperty(TEMPERATURE_PROPERTY),
        env("JVN_JANE_GEMINI_TEMPERATURE"),
        local.getProperty("gemini.temperature"),
        local.getProperty("temperature")), 0.2);
    int timeoutSeconds = intValue(firstNonBlank(
        System.getProperty(TIMEOUT_SECONDS_PROPERTY),
        env("JVN_JANE_GEMINI_TIMEOUT_SECONDS"),
        local.getProperty("gemini.timeoutSeconds"),
        local.getProperty("timeoutSeconds")), 45);
    return Optional.of(new GeminiChatModel(
        apiKey,
        model,
        endpoint,
        maxOutputTokens,
        temperature,
        timeoutSeconds));
  }

  public static String defaultModel() {
    return DEFAULT_MODEL;
  }

  public static String localSettingsRelativePath() {
    return LOCAL_CONFIG;
  }

  public GeminiChatModel(
      String apiKey,
      String model,
      String endpoint,
      int maxOutputTokens,
      double temperature,
      int timeoutSeconds
  ) {
    this.apiKey = require(apiKey, "Gemini API key");
    this.model = normalizeModel(model);
    this.endpoint = normalizeEndpoint(endpoint);
    this.maxOutputTokens = Math.max(1, maxOutputTokens);
    this.temperature = Double.isFinite(temperature) ? Math.max(0.0, Math.min(2.0, temperature)) : 0.2;
    this.requestTimeout = Duration.ofSeconds(Math.max(5, timeoutSeconds));
    this.client = HttpClient.newBuilder()
        .connectTimeout(requestTimeout)
        .build();
  }

  @Override
  public String name() {
    return "Jane Gemini " + model;
  }

  @Override
  public boolean isAvailable() {
    return !apiKey.isBlank() && !model.isBlank();
  }

  @Override
  public String generate(String query, HelpResponse grounding, List<ChatMessage> history) {
    String requestBody = buildRequestBody(query, grounding, history);
    HttpRequest request = HttpRequest.newBuilder(endpointUri())
        .timeout(requestTimeout)
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
        .build();
    try {
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        throw new IllegalStateException("Gemini API request failed with HTTP " + response.statusCode()
            + ": " + compactText(firstJsonValue(response.body(), "message"), 180));
      }
      String text = String.join("\n", jsonValues(response.body(), "text")).trim();
      return compactGeneratedText(text);
    } catch (IOException ex) {
      throw new IllegalStateException("Gemini API request failed", ex);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Gemini API request was interrupted", ex);
    }
  }

  private URI endpointUri() {
    String encodedModel = URLEncoder.encode(model, StandardCharsets.UTF_8).replace("+", "%20");
    String encodedKey = URLEncoder.encode(apiKey, StandardCharsets.UTF_8);
    return URI.create(endpoint + "/models/" + encodedModel + ":generateContent?key=" + encodedKey);
  }

  private String buildRequestBody(String query, HelpResponse grounding, List<ChatMessage> history) {
    StringBuilder json = new StringBuilder(2048);
    json.append('{');
    json.append("\"systemInstruction\":{\"parts\":[{\"text\":")
        .append(jsonString(systemInstruction()))
        .append("}]},");
    json.append("\"contents\":[{\"role\":\"user\",\"parts\":[{\"text\":")
        .append(jsonString(buildPrompt(query, grounding, history)))
        .append("}]}],");
    json.append("\"generationConfig\":{")
        .append("\"temperature\":").append(formatNumber(temperature)).append(',')
        .append("\"maxOutputTokens\":").append(maxOutputTokens)
        .append("}");
    json.append('}');
    return json.toString();
  }

  private String systemInstruction() {
    return """
        You are Jane, JVN's assistant. Answer only about the Java Vector Nexus engine and its authoring workflows.
        Critical glossary: VNS means visual novel scripting. JES means JVN Entity Scripting, the engine's scene and gameplay scripting layer. Puppeteer means timeline animation authoring.
        Use the supplied TAGI context as higher priority than general model knowledge. Be direct, practical, and concise. Do not invent file paths or unsupported APIs.
        """.trim();
  }

  private String buildPrompt(String query, HelpResponse grounding, List<ChatMessage> history) {
    StringBuilder prompt = new StringBuilder();
    if (history != null && !history.isEmpty()) {
      prompt.append("Recent chat:\n");
      history.stream().skip(Math.max(0, history.size() - 6)).forEach(message -> {
        String role = "assistant".equalsIgnoreCase(message.role()) ? "Jane" : "User";
        prompt.append(role).append(": ").append(message.content()).append('\n');
      });
      prompt.append('\n');
    }
    if (grounding != null) {
      prompt.append("TAGI answer:\n").append(compactText(grounding.answer(), 900)).append("\n\n");
      if (!grounding.recommendedArticles().isEmpty()) {
        prompt.append("Recommended docs:\n");
        for (HelpArticle article : grounding.recommendedArticles().stream().limit(5).toList()) {
          prompt.append("- ").append(article.title());
          if (!article.summary().isBlank()) {
            prompt.append(": ").append(compactText(article.summary(), 240));
          }
          if (!article.path().isBlank()) {
            prompt.append(" (").append(article.path()).append(')');
          }
          prompt.append('\n');
        }
        prompt.append('\n');
      }
    }
    prompt.append("User question:\n").append(query == null ? "" : query.trim()).append('\n');
    prompt.append("\nAnswer as Jane in 2 to 6 concise paragraphs. Include a short practical next step when useful.");
    return prompt.toString();
  }

  private static Properties loadLocalConfig() {
    List<Path> roots = new ArrayList<>();
    addRoot(roots, propertyPath(REPO_ROOT_PROPERTY));
    addRoot(roots, propertyPath(WORKSPACE_ROOT_PROPERTY));
    addRoot(roots, propertyPath("user.dir"));
    String configured = System.getProperty("jvn.jane.gemini.config");
    if (configured != null && !configured.isBlank()) {
      Properties props = readProperties(Path.of(configured));
      if (!props.isEmpty()) return props;
    }
    for (Path root : roots) {
      Properties props = new Properties();
      mergeProperties(props, readProperties(root.resolve(LEGACY_LOCAL_CONFIG)));
      mergeProperties(props, readProperties(root.resolve(LOCAL_CONFIG)));
      if (!props.isEmpty()) return props;
    }
    return new Properties();
  }

  private static void mergeProperties(Properties target, Properties source) {
    if (target == null || source == null || source.isEmpty()) return;
    for (String name : source.stringPropertyNames()) {
      target.setProperty(name, source.getProperty(name));
    }
  }

  private static Properties readProperties(Path path) {
    Properties props = new Properties();
    if (path == null) return props;
    Path normalized = path.toAbsolutePath().normalize();
    if (!Files.isRegularFile(normalized)) return props;
    try (var input = Files.newInputStream(normalized)) {
      props.load(input);
    } catch (IOException ignored) {
      // Jane can still use env vars, system properties, ONNX, or the built-in expert model.
    }
    return props;
  }

  private static void addRoot(List<Path> roots, Path root) {
    if (root != null && !roots.contains(root)) roots.add(root);
  }

  private static Path propertyPath(String key) {
    String raw = System.getProperty(key);
    if (raw == null || raw.isBlank()) return null;
    return Path.of(raw).toAbsolutePath().normalize();
  }

  private static String firstJsonValue(String json, String key) {
    return jsonValues(json, key).stream().findFirst().orElse("");
  }

  private static List<String> jsonValues(String json, String key) {
    List<String> out = new ArrayList<>();
    if (json == null || json.isBlank() || key == null || key.isBlank()) return out;
    String needle = "\"" + key + "\"";
    int index = 0;
    while (index >= 0 && index < json.length()) {
      index = json.indexOf(needle, index);
      if (index < 0) break;
      int colon = json.indexOf(':', index + needle.length());
      if (colon < 0) break;
      int start = nextNonWhitespace(json, colon + 1);
      if (start < 0 || start >= json.length() || json.charAt(start) != '"') {
        index = colon + 1;
        continue;
      }
      ParsedString parsed = parseJsonString(json, start);
      if (parsed != null) {
        out.add(parsed.value());
        index = parsed.nextIndex();
      } else {
        index = start + 1;
      }
    }
    return out;
  }

  private static int nextNonWhitespace(String value, int start) {
    for (int i = Math.max(0, start); i < value.length(); i++) {
      if (!Character.isWhitespace(value.charAt(i))) return i;
    }
    return -1;
  }

  private static ParsedString parseJsonString(String value, int quoteIndex) {
    if (value == null || quoteIndex < 0 || quoteIndex >= value.length() || value.charAt(quoteIndex) != '"') {
      return null;
    }
    StringBuilder out = new StringBuilder();
    for (int i = quoteIndex + 1; i < value.length(); i++) {
      char ch = value.charAt(i);
      if (ch == '"') return new ParsedString(out.toString(), i + 1);
      if (ch != '\\') {
        out.append(ch);
        continue;
      }
      if (++i >= value.length()) break;
      char esc = value.charAt(i);
      switch (esc) {
        case '"', '\\', '/' -> out.append(esc);
        case 'b' -> out.append('\b');
        case 'f' -> out.append('\f');
        case 'n' -> out.append('\n');
        case 'r' -> out.append('\r');
        case 't' -> out.append('\t');
        case 'u' -> {
          if (i + 4 >= value.length()) return null;
          String hex = value.substring(i + 1, i + 5);
          try {
            out.append((char) Integer.parseInt(hex, 16));
          } catch (NumberFormatException ex) {
            return null;
          }
          i += 4;
        }
        default -> out.append(esc);
      }
    }
    return null;
  }

  private static String jsonString(String value) {
    String text = value == null ? "" : value;
    StringBuilder out = new StringBuilder(text.length() + 16);
    out.append('"');
    for (int i = 0; i < text.length(); i++) {
      char ch = text.charAt(i);
      switch (ch) {
        case '"' -> out.append("\\\"");
        case '\\' -> out.append("\\\\");
        case '\b' -> out.append("\\b");
        case '\f' -> out.append("\\f");
        case '\n' -> out.append("\\n");
        case '\r' -> out.append("\\r");
        case '\t' -> out.append("\\t");
        default -> {
          if (ch < 0x20) {
            out.append(String.format(Locale.ROOT, "\\u%04x", (int) ch));
          } else {
            out.append(ch);
          }
        }
      }
    }
    out.append('"');
    return out.toString();
  }

  private static String compactGeneratedText(String value) {
    String text = value == null ? "" : value.trim();
    text = text.replaceAll("(?i)^Jane:\\s*", "").trim();
    return compactText(text, 6000);
  }

  private static String compactText(String value, int maxLength) {
    if (value == null || value.isBlank()) return "";
    String compact = value.replaceAll("[\\t\\x0B\\f\\r]+", " ").replaceAll(" *\\n *", "\n").trim();
    if (compact.length() <= maxLength) return compact;
    return compact.substring(0, Math.max(0, maxLength - 3)).trim() + "...";
  }

  private static String require(String value, String label) {
    if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required");
    return value.trim();
  }

  private static String normalizeEndpoint(String raw) {
    String value = raw == null || raw.isBlank() ? DEFAULT_ENDPOINT : raw.trim();
    while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
    return value;
  }

  private static String normalizeModel(String raw) {
    String value = require(raw, "Gemini model");
    return value.startsWith("models/") ? value.substring("models/".length()) : value;
  }

  private static String firstNonBlank(String... values) {
    if (values == null) return null;
    for (String value : values) {
      if (value != null && !value.isBlank()) return value.trim();
    }
    return null;
  }

  private static String env(String key) {
    String value = System.getenv(key);
    return value == null || value.isBlank() ? null : value.trim();
  }

  private static int intValue(String raw, int fallback) {
    if (raw == null || raw.isBlank()) return fallback;
    try {
      return Integer.parseInt(raw.trim());
    } catch (NumberFormatException ex) {
      return fallback;
    }
  }

  private static double doubleValue(String raw, double fallback) {
    if (raw == null || raw.isBlank()) return fallback;
    try {
      return Double.parseDouble(raw.trim());
    } catch (NumberFormatException ex) {
      return fallback;
    }
  }

  private static String formatNumber(double value) {
    if (Math.abs(value - Math.rint(value)) < 1e-9) {
      return Long.toString((long) Math.rint(value));
    }
    String text = String.format(Locale.ROOT, "%.4f", value);
    return text.replaceAll("0+$", "").replaceAll("\\.$", "");
  }

  private record ParsedString(String value, int nextIndex) {}
}
