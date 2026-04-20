# Contributing to Werkflow

Thank you for your interest in contributing to Werkflow!

## Getting Started

1. Fork the repository and clone it locally.
2. Follow the setup steps in [QUICKSTART.md](docs/QUICKSTART.md).
3. Create a feature branch: `git checkout -b feature/your-feature-name`.
4. Make your changes, commit, and open a pull request.

## Pull Request Guidelines

- Keep PRs focused — one logical change per PR.
- Include a clear description of what changed and why.
- Ensure the build passes: `npm run build` in `frontends/portal/` and `mvn verify` in the backend module.
- Add or update tests for any non-trivial logic changes.

## Code Style

- **TypeScript / React:** Follow the existing ESLint and Prettier configuration.
- **Java / Spring Boot:** Follow the Checkstyle rules defined in the project.
- Do not disable linting rules without a documented reason.

## Contributing Translations

Werkflow ships with English (`en`) and welcomes community translations.

To add a new language, see **[docs/TRANSLATION-GUIDE.md](docs/TRANSLATION-GUIDE.md)**.

The short version:

1. Copy `frontends/portal/messages/en.json` → `messages/<locale>.json`.
2. Translate all string values (keep keys unchanged).
3. Register the locale in `frontends/portal/i18n/request.ts`.
4. Open a PR titled `i18n: add <Language> (<locale>) translations`.

## Reporting Issues

Please open a GitHub issue with:
- A clear title and description.
- Steps to reproduce (for bugs).
- Expected vs actual behaviour.
- Relevant logs or screenshots.

## License

By contributing, you agree that your contributions will be licensed under the same [Apache 2.0 License](LICENSE) that covers the project.
