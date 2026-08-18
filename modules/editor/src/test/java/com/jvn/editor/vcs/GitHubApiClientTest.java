package com.jvn.editor.vcs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class GitHubApiClientTest {

  private static final String SAMPLE_CREATE_RESPONSE = """
      {
        "id": 123456789,
        "name": "my-project",
        "full_name": "octocat/my-project",
        "private": true,
        "html_url": "https://github.com/octocat/my-project",
        "clone_url": "https://github.com/octocat/my-project.git",
        "ssh_url": "git@github.com:octocat/my-project.git"
      }
      """;

  private static final String SAMPLE_ERROR_RESPONSE = """
      {
        "message": "Repository creation failed.",
        "errors": [
          {"resource": "Repository", "code": "custom", "field": "name", "message": "name already exists on this account"}
        ],
        "documentation_url": "https://docs.github.com/rest/repos/repos#create-a-repository-for-the-authenticated-user"
      }
      """;

  private static final String SAMPLE_DEVICE_CODE_RESPONSE = """
      {
        "device_code": "3584d83530557fdd1f46af8289938c8ef79f9dc5",
        "user_code": "WDJB-MJHT",
        "verification_uri": "https://github.com/login/device",
        "expires_in": 900,
        "interval": 5
      }
      """;

  private static final String SAMPLE_PENDING_RESPONSE = "{\"error\":\"authorization_pending\"}";
  private static final String SAMPLE_SLOW_DOWN_RESPONSE = "{\"error\":\"slow_down\",\"interval\":10}";
  private static final String SAMPLE_TOKEN_SUCCESS_RESPONSE = """
      {
        "access_token": "gho_16C7e42F292c6912E7710c838347Ae178B4a",
        "token_type": "bearer",
        "scope": "repo"
      }
      """;

  @Test
  void extractsCloneUrlFromCreateRepositoryResponse() {
    assertEquals("https://github.com/octocat/my-project.git",
        GitHubApiClient.extractField(SAMPLE_CREATE_RESPONSE, "clone_url"));
  }

  @Test
  void extractsHtmlUrlFromCreateRepositoryResponse() {
    assertEquals("https://github.com/octocat/my-project",
        GitHubApiClient.extractField(SAMPLE_CREATE_RESPONSE, "html_url"));
  }

  @Test
  void extractsMessageFromErrorResponse() {
    assertEquals("Repository creation failed.",
        GitHubApiClient.extractField(SAMPLE_ERROR_RESPONSE, "message"));
  }

  @Test
  void returnsNullWhenFieldIsMissing() {
    assertNull(GitHubApiClient.extractField(SAMPLE_CREATE_RESPONSE, "does_not_exist"));
  }

  @Test
  void returnsNullForNullInput() {
    assertNull(GitHubApiClient.extractField(null, "clone_url"));
  }

  @Test
  void handlesEscapedCharactersInValue() {
    String json = "{\"message\":\"line one\\nline two\"}";
    assertEquals("line one\nline two", GitHubApiClient.extractField(json, "message"));
  }

  @Test
  void extractsDeviceCodeFields() {
    assertEquals("3584d83530557fdd1f46af8289938c8ef79f9dc5",
        GitHubApiClient.extractField(SAMPLE_DEVICE_CODE_RESPONSE, "device_code"));
    assertEquals("WDJB-MJHT", GitHubApiClient.extractField(SAMPLE_DEVICE_CODE_RESPONSE, "user_code"));
    assertEquals("https://github.com/login/device",
        GitHubApiClient.extractField(SAMPLE_DEVICE_CODE_RESPONSE, "verification_uri"));
  }

  @Test
  void extractsIntFieldsFromDeviceCodeResponse() {
    assertEquals(900, GitHubApiClient.extractIntField(SAMPLE_DEVICE_CODE_RESPONSE, "expires_in"));
    assertEquals(5, GitHubApiClient.extractIntField(SAMPLE_DEVICE_CODE_RESPONSE, "interval"));
  }

  @Test
  void extractIntFieldReturnsNullWhenFieldIsMissing() {
    assertNull(GitHubApiClient.extractIntField(SAMPLE_DEVICE_CODE_RESPONSE, "does_not_exist"));
  }

  @Test
  void extractsErrorFromPendingResponse() {
    assertEquals("authorization_pending", GitHubApiClient.extractField(SAMPLE_PENDING_RESPONSE, "error"));
  }

  @Test
  void extractsIntervalFromSlowDownResponse() {
    assertEquals("slow_down", GitHubApiClient.extractField(SAMPLE_SLOW_DOWN_RESPONSE, "error"));
    assertEquals(10, GitHubApiClient.extractIntField(SAMPLE_SLOW_DOWN_RESPONSE, "interval"));
  }

  @Test
  void extractsAccessTokenFromSuccessResponse() {
    assertEquals("gho_16C7e42F292c6912E7710c838347Ae178B4a",
        GitHubApiClient.extractField(SAMPLE_TOKEN_SUCCESS_RESPONSE, "access_token"));
  }
}
