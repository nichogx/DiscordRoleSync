# Contributing

Contributions are very welcome, as long as the changes are relevant for the plugin and high quality. Please ask me (open an issue or ask on Discord) first, if you're not sure if the feature you want to implement will be accepted.

Want to take a stab at something, but not sure what to work on? Feel free to take an [open issue](https://gitlab.com/nichogx/DiscordRoleSync/-/issues)! I do my best to keep them shovel-ready.

## Refactors

This code was written a while ago, in a time when I was not that familiar with Java. There is a lot of room for improvement. Refactors are very, very welcome, especially if it makes the code more testable (with unit tests).

## Getting a Review

Please make sure you practice good code higiene. Make a descriptive merge request and please describe what you have done to test your changes.

## Versioning

Versions are automatically defined by CI.

- When tagged, the tag will be the version. This is how production releases are built and versions defined.
- When built from master, the version will be `master-<sha>`. This is not meant for distribution.
- When built from a branch, the version will be `branch-<sha>`. This is not meant for distribution.
- When built locally, the version will be `develop`. This is not meant for distribution.