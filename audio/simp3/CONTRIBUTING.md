# Contributing to SiMP3

First off, thank you for considering contributing to SiMP3! It's people like you that make SiMP3 such a great tool.

## Table of Contents

- [Code of Conduct](#code-of-conduct)
- [Getting Started](#getting-started)
- [How Can I Contribute?](#how-can-i-contribute)
- [Development Process](#development-process)
- [Style Guidelines](#style-guidelines)
- [Commit Messages](#commit-messages)
- [Pull Request Process](#pull-request-process)

## Code of Conduct

This project and everyone participating in it is governed by our Code of Conduct. By participating, you are expected to uphold this code. Please be respectful and constructive in all interactions.

## Getting Started

1. **Fork the repository** on GitHub
2. **Clone your fork** locally:
   ```bash
   git clone https://github.com/your-username/simp3.git
   cd simp3
   ```
3. **Add the upstream repository**:
   ```bash
   git remote add upstream https://github.com/original-owner/simp3.git
   ```
4. **Create a branch** for your feature or fix:
   ```bash
   git checkout -b feature/your-feature-name
   ```

## How Can I Contribute?

### Reporting Bugs

Before creating bug reports, please check existing issues to avoid duplicates. When creating a bug report, include:

- **Clear title and description**
- **Steps to reproduce**
- **Expected behavior**
- **Actual behavior**
- **Screenshots** (if applicable)
- **System information** (OS, Java version, etc.)

### Suggesting Additions

Enhancement suggestions are welcome! Please provide:

- **Use case** - Why is this enhancement needed?
- **Proposed solution** - How should it work?
- **Alternatives considered** - What other solutions did you think about?
- **Additional context** - Mockups, examples, etc.

### Code Contributions

#### Your First Code Contribution

Unsure where to begin? Look for these labels in our issues:

- `good first issue` - Simple issues perfect for beginners
- `help wanted` - Issues where we need community help
- `documentation` - Help improve our docs

#### Development Setup

1. **Install prerequisites**:
   - JDK 17 or higher
   - Maven 3.6+
   - Your favorite IDE

2. **Build the project**:
   ```bash
   mvn clean compile
   ```

3. **Run tests**:
   ```bash
   mvn test
   ```

4. **Run the application**:
   ```bash
   mvn javafx:run
   ```

## Development Process

### 1. Before You Start

- Check if an issue already exists for your change
- For major changes, open an issue first to discuss
- Ensure your fork is up to date with the main branch

### 2. Making Changes

- Write clean, readable code
- Add/update tests as needed
- Update documentation if required
- Test your changes thoroughly

### 3. Committing

- Make small, focused commits
- Write clear commit messages (see below)
- Keep your branch up to date with main

## Style Guidelines

### Java Code Style

```java
// Use meaningful names
public class AudioPlayerService {  // Good
public class APS {                 // Bad

// Document public methods
/**
 * Plays the specified track.
 * 
 * @param track The track to play
 * @throws AudioException if playback fails
 */
public void playTrack(Song track) throws AudioException {
    // Implementation
}

// Use consistent formatting
if (condition) {
    doSomething();
} else {
    doSomethingElse();
}
```

### Key Guidelines

- **Indentation**: 4 spaces (no tabs)
- **Line length**: Max 120 characters
- **Naming**: 
  - Classes: `PascalCase`
  - Methods/variables: `camelCase`
  - Constants: `UPPER_SNAKE_CASE`
- **JavaDoc**: Required for all public classes and methods
- **Imports**: No wildcard imports

### UI/UX Guidelines

- Keep the interface clean and intuitive
- Ensure keyboard navigation works
- Test with different screen sizes
- Follow existing design patterns

## Commit Messages

We follow the [Conventional Commits](https://www.conventionalcommits.org/) specification:

```
<type>(<scope>): <subject>

<body>

<footer>
```

### Types

- `feat`: New feature
- `fix`: Bug fix
- `docs`: Documentation changes
- `style`: Code style changes (formatting, etc.)
- `refactor`: Code refactoring
- `test`: Test additions or modifications
- `chore`: Build process or auxiliary tool changes

### Examples

```
feat(player): add shuffle functionality

Implemented shuffle mode for playlists with Fisher-Yates algorithm.
Shuffle state persists between sessions.

Closes #123
```

```
fix(library): resolve duplicate song entries

Songs were being added twice when scanning nested folders.
Added path normalization and duplicate checking.

Fixes #456
```

## Pull Request Process

1. **Update your branch**:
   ```bash
   git fetch upstream
   git rebase upstream/main
   ```

2. **Push to your fork**:
   ```bash
   git push origin feature/your-feature-name
   ```

3. **Create Pull Request**:
   - Use a clear, descriptive title
   - Reference any related issues
   - Describe what changes you made and why
   - Include screenshots for UI changes
   - Ensure all tests pass

4. **Code Review**:
   - Respond to feedback constructively
   - Make requested changes
   - Push additional commits to your branch

5. **Merge**:
   - Once approved, your PR will be merged
   - Delete your branch after merge

### PR Checklist

- [ ] Code follows project style guidelines
- [ ] Tests pass locally (`mvn test`)
- [ ] Documentation updated (if needed)
- [ ] Commit messages follow conventions
- [ ] PR description is clear and complete

## Testing

### Running Tests

```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=AudioPlayerServiceTest

# Run with coverage
mvn test jacoco:report
```

### Writing Tests

- Write unit tests for new functionality
- Maintain or improve code coverage
- Use meaningful test names
- Follow AAA pattern (Arrange, Act, Assert)

```java
@Test
void shouldPlayNextTrackWhenCurrentEnds() {
    // Arrange
    Playlist playlist = createTestPlaylist();
    audioService.setPlaylist(playlist);
    
    // Act
    audioService.playTrack(playlist.getSongs().get(0));
    audioService.nextTrack();
    
    // Assert
    assertEquals(playlist.getSongs().get(1), audioService.getCurrentSong());
    assertTrue(audioService.isPlaying());
}
```

## Questions?

Feel free to:
- Open an issue for questions
- Join our [discussions](https://github.com/yourusername/simp3/discussions)
- Contact the maintainers

Thank you for contributing to SiMP3!