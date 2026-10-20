# Changelog

All notable changes to the IrisScript VS Code extension are documented in this file.

This changelog follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
Versions follow [Semantic Versioning](https://semver.org/) where applicable.

## [0.0.3] - 2026-10-20

### Fixed
- Fixed diagnostics for references to declarations using the global keyword across multiple IrisScript files.
- Fixed project-wide diagnostics so errors can be detected in .iris files that are not currently open in the editor.
- Improved diagnostic source mapping so reported errors can be associated with the correct source file.
- Improved diagnostic location reporting, including line, column, and underline span where available.
- Fixed diagnostic handling for unsaved editor contents so diagnostics can reflect the current buffer without requiring the file to be saved first.

### Changed
- Updated project diagnostics to analyze IrisScript files together, allowing cross-file declarations to be resolved consistently.
- Improved diagnostic refresh behavior when project files are opened, edited, saved, or created.

## [Unreleased]

### Added

### Changed

### Fixed