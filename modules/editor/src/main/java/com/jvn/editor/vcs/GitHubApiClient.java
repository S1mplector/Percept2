package com.jvn.editor.vcs;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Minimal client for the GitHub REST API calls needed to create a repository directly,
 * replacing a dependency on the separate {@code gh} CLI tool, plus the OAuth Device Flow
 * calls used for interactive sign-in.
 */
public final class GitHubApiClient {
  private static final String API_BASE = "https://api.github.com";
  private static final String API_VERSION = "2022-11-28";
  private static final String CLIENT_ID = "Ov23li5jSweK7oYnLFop";
  private static final String DEVICE_CODE_URL = "https://github.com/login/device/code";
  private static final String DEVICE_TOKEN_URL = "https://github.com/login/oauth/access_token";

  private final HttpClient httpClient;

  public GitHubApiClient() {
    this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build());
  }

  public GitHubApiClient(HttpClient httpClient) {
    this.httpClient = httpClient;
  }

  public boolean verifyToken(String token) throws GitHubApiException {
    HttpRequest request = requestBuilder("/user", token).GET().build();
    HttpResponse<String> response = send(request);
    if (response.statusCode() == 200) return true;
    if (response.statusCode() == 401) return false;
    throw new GitHubApiException(response.statusCode(), extractMessage(response.body()));
  }

  public CreatedRepo createRepository(String token, String name, boolean isPrivate) throws GitHubApiException {
    if (token == null || token.isBlank()) throw new GitHubApiException(0, "No GitHub token configured.");
    if (name == null || name.isBlank()) throw new GitHubApiException(0, "Repository name cannot be empty.");

    String body = "{\"name\":\"" + jsonEscape(name.trim()) + "\",\"private\":" + isPrivate + "}";
    HttpRequest request = requestBuilder("/user/repos", token)
        .POST(HttpRequest.BodyPublishers.ofString(body))
        .build();
    HttpResponse<String> response = send(request);

    if (response.statusCode() != 201) {
      throw new GitHubApiException(response.statusCode(), extractMessage(response.body()));
    }

    String cloneUrl = extractField(response.body(), "clone_url");
    String htmlUrl = extractField(response.body(), "html_url");
    if (cloneUrl == null) {
      throw new GitHubApiException(response.statusCode(), "GitHub did not return a clone URL for the new repository.");
    }
    return new CreatedRepo(htmlUrl, cloneUrl);
  }

  public DeviceCodeResponse requestDeviceCode() throws GitHubApiException {
    String body = "client_id=" + urlEncode(CLIENT_ID) + "&scope=" + urlEncode("repo");
    HttpRequest request = deviceRequestBuilder(DEVICE_CODE_URL, body).build();
    HttpResponse<String> response = send(request);

    if (response.statusCode() != 200) {
      throw new GitHubApiException(response.statusCode(), extractMessage(response.body()));
    }

    String deviceCode = extractField(response.body(), "device_code");
    String userCode = extractField(response.body(), "user_code");
    String verificationUri = extractField(response.body(), "verification_uri");
    Integer expiresIn = extractIntField(response.body(), "expires_in");
    Integer interval = extractIntField(response.body(), "interval");
    if (deviceCode == null || userCode == null || verificationUri == null || expiresIn == null || interval == null) {
      throw new GitHubApiException(response.statusCode(), "GitHub did not return a complete device code response.");
    }
    return new DeviceCodeResponse(deviceCode, userCode, verificationUri, expiresIn, interval);
  }

  public DeviceTokenResult pollDeviceToken(String deviceCode) throws GitHubApiException {
    String body = "client_id=" + urlEncode(CLIENT_ID)
        + "&device_code=" + urlEncode(deviceCode)
        + "&grant_type=" + urlEncode("urn:ietf:params:oauth:grant-type:device_code");
    HttpRequest request = deviceRequestBuilder(DEVICE_TOKEN_URL, body).build();
    HttpResponse<String> response = send(request);

    String accessToken = extractField(response.body(), "access_token");
    if (accessToken != null && !accessToken.isBlank()) {
      return new DeviceTokenResult(DeviceTokenResult.Status.SUCCESS, accessToken, 0, null);
    }

    String error = extractField(response.body(), "error");
    if (error == null) {
      throw new GitHubApiException(response.statusCode(), extractMessage(response.body()));
    }
    return switch (error) {
      case "authorization_pending" -> new DeviceTokenResult(DeviceTokenResult.Status.PENDING, null, 0, null);
      case "slow_down" -> {
        Integer newInterval = extractIntField(response.body(), "interval");
        yield new DeviceTokenResult(DeviceTokenResult.Status.SLOW_DOWN, null, newInterval == null ? 5 : newInterval, null);
      }
      case "expired_token" -> new DeviceTokenResult(DeviceTokenResult.Status.EXPIRED, null, 0, "The code expired before it was approved.");
      case "access_denied" -> new DeviceTokenResult(DeviceTokenResult.Status.DENIED, null, 0, "Sign-in was declined.");
      default -> new DeviceTokenResult(DeviceTokenResult.Status.ERROR, null, 0, extractMessage(response.body()));
    };
  }

  private HttpRequest.Builder deviceRequestBuilder(String url, String formBody) {
    return HttpRequest.newBuilder()
        .uri(URI.create(url))
        .timeout(Duration.ofSeconds(20))
        .header("Accept", "application/json")
        .header("Content-Type", "application/x-www-form-urlencoded")
        .POST(HttpRequest.BodyPublishers.ofString(formBody));
  }

  private static String urlEncode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  private HttpRequest.Builder requestBuilder(String path, String token) {
    return HttpRequest.newBuilder()
        .uri(URI.create(API_BASE + path))
        .timeout(Duration.ofSeconds(20))
        .header("Authorization", "Bearer " + token)
        .header("Accept", "application/vnd.github+json")
        .header("X-GitHub-Api-Version", API_VERSION)
        .header("Content-Type", "application/json; charset=utf-8");
  }

  private HttpResponse<String> send(HttpRequest request) throws GitHubApiException {
    try {
      return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    } catch (IOException ex) {
      throw new GitHubApiException(0, "Could not reach GitHub: " + ex.getMessage());
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new GitHubApiException(0, "GitHub request was interrupted.");
    }
  }

  private static String extractMessage(String responseBody) {
    String message = extractField(responseBody, "message");
    return message == null || message.isBlank() ? "Unexpected response from GitHub." : message;
  }

  /** Extracts a single top-level string field from a flat JSON object without a full parser. */
  static String extractField(String json, String field) {
    if (json == null) return null;
    String needle = "\"" + field + "\"";
    int keyIndex = json.indexOf(needle);
    if (keyIndex < 0) return null;
    int colonIndex = json.indexOf(':', keyIndex + needle.length());
    if (colonIndex < 0) return null;

    int i = colonIndex + 1;
    while (i < json.length() && Character.isWhitespace(json.charAt(i))) i++;
    if (i >= json.length() || json.charAt(i) != '"') return null;
    i++;

    StringBuilder sb = new StringBuilder();
    while (i < json.length()) {
      char c = json.charAt(i);
      if (c == '"') break;
      if (c == '\\' && i + 1 < json.length()) {
        char next = json.charAt(i + 1);
        switch (next) {
          case 'n' -> sb.append('\n');
          case 't' -> sb.append('\t');
          case 'r' -> sb.append('\r');
          case '"', '\\', '/' -> sb.append(next);
          default -> sb.append(next);
        }
        i += 2;
      } else {
        sb.append(c);
        i++;
      }
    }
    return sb.toString();
  }

  /** Extracts a single top-level bare-number field (e.g. {@code "interval": 5}) without a full parser. */
  static Integer extractIntField(String json, String field) {
    if (json == null) return null;
    String needle = "\"" + field + "\"";
    int keyIndex = json.indexOf(needle);
    if (keyIndex < 0) return null;
    int colonIndex = json.indexOf(':', keyIndex + needle.length());
    if (colonIndex < 0) return null;

    int i = colonIndex + 1;
    while (i < json.length() && Character.isWhitespace(json.charAt(i))) i++;
    int start = i;
    while (i < json.length() && (Character.isDigit(json.charAt(i)) || json.charAt(i) == '-')) i++;
    if (i == start) return null;

    try {
      return Integer.parseInt(json.substring(start, i));
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  private static String jsonEscape(String value) {
    StringBuilder sb = new StringBuilder();
    for (char c : value.toCharArray()) {
      switch (c) {
        case '"' -> sb.append("\\\"");
        case '\\' -> sb.append("\\\\");
        case '\n' -> sb.append("\\n");
        case '\r' -> sb.append("\\r");
        case '\t' -> sb.append("\\t");
        default -> sb.append(c);
      }
    }
    return sb.toString();
  }

  public record CreatedRepo(String htmlUrl, String cloneUrl) {}

  public record DeviceCodeResponse(String deviceCode,
                                   String userCode,
                                   String verificationUri,
                                   int expiresIn,
                                   int interval) {}

  public record DeviceTokenResult(Status status, String accessToken, int retryIntervalSeconds, String errorMessage) {
    public enum Status { SUCCESS, PENDING, SLOW_DOWN, EXPIRED, DENIED, ERROR }
  }

  public static class GitHubApiException extends Exception {
    private final int statusCode;

    public GitHubApiException(int statusCode, String message) {
      super(message);
      this.statusCode = statusCode;
    }

    public int statusCode() {
      return statusCode;
    }
  }
}
