# GitHub Copilot path-specific instructions

Per [GitHub repository custom instructions](https://docs.github.com/en/copilot/how-tos/copilot-on-github/customize-copilot/add-custom-instructions/add-repository-instructions):

| File                                                     | Scope                                                                                    |
| -------------------------------------------------------- | ---------------------------------------------------------------------------------------- |
| [../copilot-instructions.md](../copilot-instructions.md) | Repository-wide (always loaded on GitHub)                                                |
| [act.instructions.md](act.instructions.md)               | `apps/`, `specs/`, `tools/`, `.agents/`, `.github/`, `packaging/` — **`/act` workflow**  |
| [review.instructions.md](review.instructions.md)         | Same paths + `.codacy/` — **review APIs** (Codacy vs GitHub Code Scanning vs Dependabot) |

Copilot code review uses instructions from the **base branch** of the PR. Merge instruction updates to `main` before expecting them on open PRs.
