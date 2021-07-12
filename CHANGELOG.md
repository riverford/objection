# Change Log
All notable changes to this project will be documented in this file. This change log follows the conventions of [keepachangelog.com](http://keepachangelog.com/).

## Unreleased

### Added
- ClojureScript support! Remember to `:require-macros`/`:include-macros`!
- `with-open` macro that behaves similarly to `clojure.core/with-open`, but supports objection objects.
### Changed
- `defsingleton` now takes an (optional) options map with an optional `:reload?` flag.

## [0.1.1]

### Fixed

- When 're-registering' an alias/id etc, pref to associate properties with original object
  not the alias. This is more consistent with the other operations in the lib.

## [0.1.0]

Initial Release