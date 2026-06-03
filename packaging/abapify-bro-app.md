# GitHub App `abapify-bro` (packaging sync)

Cross-repo sync from **openadt** Release to `abapify/scoop-bucket` and `abapify/homebrew-openadt` uses the org app [**abapify-bro**](https://github.com/organizations/abapify/settings/apps/abapify-bro). No server deployment — only GitHub settings and repo secrets.

## App permissions (Repository permissions)

| Permission   | Access         |
| ------------ | -------------- |
| **Contents** | Read and write |
| **Metadata** | Read           |

`repository_dispatch` and Contents API updates use **Contents** only; **Actions** write is not required.

Webhook: **not required** (inactive is fine).

## Install the app

Org **abapify** → App **abapify-bro** → **Install App** → select:

- `abapify/openadt`
- `abapify/scoop-bucket`
- `abapify/homebrew-openadt`

## Secrets on `abapify/openadt`

| Name                          | Where                                                                                                                                         |
| ----------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------- |
| `ABAPIFY_BRO_APP_CLIENT_ID`   | Repository **variable**. App settings → **Client ID** (e.g. `Iv1.…`) — this is what `actions/create-github-app-token` expects as `client-id`. |
| `ABAPIFY_BRO_APP_PRIVATE_KEY` | Repository **secret**. App → **Generate a private key** → paste full `.pem` contents.                                                         |

Release workflow uses [`actions/create-github-app-token`](https://github.com/actions/create-github-app-token) when `ABAPIFY_BRO_APP_CLIENT_ID` is set.

## Local / one-off sync

From a machine with `gh auth login` and write access to the bucket/tap repos:

```bash
GH_TOKEN=$(gh auth token) bash tools/sync-scoop-bucket/sync.sh
GH_TOKEN=$(gh auth token) bash tools/sync-homebrew-tap/sync.sh
```

Legacy optional PAT secrets `OPENADT_SCOOP_BUCKET_TOKEN` / `OPENADT_HOMEBREW_TAP_TOKEN` still work if the app is not configured yet.
