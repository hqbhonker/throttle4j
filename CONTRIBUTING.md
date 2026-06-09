# Contributing to throttle4j

Thanks for taking the time to contribute! throttle4j is built and improved by the community, and every issue, PR or discussion helps the project move forward.

## How to Contribute

1. **Fork** this repository to your own GitHub account.
2. **Clone** your fork locally:
   ```bash
   git clone https://github.com/<your-username>/throttle4j.git
   cd throttle4j
   ```
3. **Create a feature branch** from `main`:
   ```bash
   git checkout -b feature/amazing-feature
   ```
4. **Make your changes** with focused, well-tested commits.
5. **Commit** using the [Conventional Commits](https://www.conventionalcommits.org/) style:
   - `feat:` a new feature
   - `fix:` a bug fix
   - `docs:` documentation only changes
   - `test:` adding or updating tests
   - `refactor:` code change that neither fixes a bug nor adds a feature
   - `perf:` performance improvement
   - `chore:` build, tooling or housekeeping changes
   
   Example: `feat(core): add sliding window algorithm`
6. **Push** the branch and **open a Pull Request** against `main`.
7. Make sure CI is green and respond to review feedback.

## Code Style Guidelines

- **Indentation**: 4 spaces, no tabs.
- **Line width**: prefer 120 columns or fewer.
- **Style**: follow the [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html).
- **Public API**: every public class and method **must** have Javadoc, including `@param`, `@return` and `@throws` where applicable.
- **Naming**: classes `PascalCase`, methods/fields `camelCase`, constants `UPPER_SNAKE_CASE`.
- **Imports**: no wildcard imports; remove unused imports before committing.
- **Null-safety**: prefer `Optional` for return values, validate constructor/builder arguments with `Objects.requireNonNull`.
- **Concurrency**: document thread-safety guarantees on every public type.

## Development Setup

Requirements:
- JDK 11 or later
- Maven 3.6+
- (Optional) Docker, for running an integration Redis instance

Build the project and run the full verification including tests and coverage:

```bash
mvn clean install
```

Quick build without tests:

```bash
mvn clean install -DskipTests
```

## Running Tests

Run unit tests for all modules:

```bash
mvn test
```

Run a single module's tests:

```bash
mvn -pl throttle4j-core test
```

Generate the JaCoCo coverage report (output under each module's `target/site/jacoco`):

```bash
mvn clean verify
```

New code is expected to keep overall coverage at **60% or higher**.

## Issue Guidelines

When opening an issue, please use the appropriate template under `.github/ISSUE_TEMPLATE/`:

- **Bug Report** — include version, JDK, store type, reproduction steps and expected vs. actual behavior.
- **Feature Request** — describe the use case, the proposed API, and any alternatives considered.

Before filing an issue, please search existing issues to avoid duplicates.

## Pull Request Checklist

Before requesting review, please confirm:

- [ ] Code compiles with `mvn clean install`.
- [ ] New and existing tests pass.
- [ ] Public APIs have Javadoc.
- [ ] CHANGELOG.md is updated under `## [Unreleased]` for user-visible changes.
- [ ] Commit messages follow Conventional Commits.

## Code of Conduct

This project follows the [Contributor Covenant](https://www.contributor-covenant.org/version/2/1/code_of_conduct/) Code of Conduct. By participating you agree to uphold its terms. Report unacceptable behavior via a private email to the maintainers.

## License

By contributing, you agree that your contributions will be licensed under the [Apache License 2.0](LICENSE).
