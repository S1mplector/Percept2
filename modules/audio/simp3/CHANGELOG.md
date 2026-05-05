# Changelog

All notable changes to SiMP3 will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Audio visualizer with circular spectrum display
  - Real-time frequency visualization for MP3 and M4A formats
  - Customizable colors and effects
  - Toggle via right-click context menu or 'V' keyboard shortcut
  - Smooth animations with configurable rotation
  - Performance-optimized rendering at 60 FPS
- Immediate update checks on application launch (0 second delay)
- Comprehensive logging for all update operations
- Update settings in Settings dialog with manual check option
- Enhanced README with badges and better structure
- Contributing guidelines
- GitHub Actions CI/CD workflows

### Changed
- Improved update service with better error handling
- Enhanced settings dialog UI layout

### Fixed
- Update service now properly starts on application initialization

## [1.0.0] - 2025-01-11

### Added
- Initial release of SiMP3
- Multi-format audio playback (MP3, WAV, FLAC)
- Music library management with automatic scanning
- Playlist creation and management
- Smart search and filtering
- Favorites system
- Activity tracking and statistics
- Mini player mode
- Audio visualizer with customizable colors
- Album art display
- Pinboard for quick access
- Auto-update system via GitHub Releases
- Keyboard shortcuts
- Shuffle and repeat modes
- Volume normalization
- Drag-and-drop playlist reordering
- First-run wizard
- Missing files detection
- Settings persistence

### Technical Features
- Built with JavaFX 17+
- Maven-based build system
- JSON-based data persistence
- Modular architecture
- Comprehensive error handling
- Resource-efficient design

## [0.9.0-beta] - 2024-12-15

### Added
- Beta release for testing
- Core playback functionality
- Basic library management
- Simple playlist support

### Known Issues
- Audio visualizer performance on older systems
- Some FLAC files may not play correctly
- Playlist drag-and-drop can be finicky

---

## Version History

- **1.0.0** - First stable release with all core features
- **0.9.0-beta** - Beta release for community testing

[Unreleased]: https://github.com/yourusername/simp3/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/yourusername/simp3/releases/tag/v1.0.0
[0.9.0-beta]: https://github.com/yourusername/simp3/releases/tag/v0.9.0-beta