# Contributing

This is a side project in maintenance mode, and I do not have too much time to add new features.

Contributions are very welcome, as long as the changes are relevant for the plugin and high quality. Please ask me (open an issue or ask on Discord) first, if you're not sure if the feature you want to implement will be accepted.

Want to take a stab at something, but not sure what to work on? Feel free to take an [open issue](https://gitlab.com/nichogx/DiscordRoleSync/-/issues)! I do my best to keep them shovel-ready.

## Refactors

I've recently picked up support for this plugin again, and am in the process of cleaning up the code. I'm not a Java expert, so feel free to make clean-up, opinionated contributions if you'd like.

There is a lot of room for improvement. Refactors are very, very welcome, especially if it makes the code more testable (with unit tests).

## Getting a Review

Please make sure you practice good code higiene. Make a descriptive merge request and please describe what you have done to test your changes.

## Structure

This project is built using Gradle, using Java version 8. This plugin is fairly simple and I want to keep support for as many users as possible. This means everything Java 8+ and MC 1.8.8+.

There are a few Python scripts, mainly for CI, but these are not packaged as part of the final release.

### Versioning

Versions are automatically defined by CI.

- When tagged, the tag will be the version. This is how production releases are built and versions defined.
- When built from master, the version will be `master-<sha>`. This is not meant for distribution.
- When built from a branch, the version will be `branch-<sha>`. This is not meant for distribution.
- When built locally, the version will be `develop`. This is not meant for distribution.