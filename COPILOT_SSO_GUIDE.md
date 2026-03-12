# GitHub Copilot Enterprise — SSO Connection Guide

  This guide explains how to connect the test framework to your company's
  GitHub Copilot Enterprise instance authenticated via corporate SSO (e.g. Microsoft/Azure AD, Okta, etc.).

  ---

  ## How Copilot Enterprise SSO Works

  Your enterprise GitHub organisation enforces SSO login via your company's identity provider
  (Microsoft, Okta, Google, etc.). When you authenticate at `office.com` or your company portal,
  that session is linked to your GitHub account via SAML/OIDC.

  The test framework calls the Copilot API using a **Personal Access Token (PAT)**
  that must be SSO-authorised by your enterprise admin.

  ---

  ## Step-by-Step: Get Your Copilot Enterprise Token

  ### 1. Log in via SSO
  - Go to [github.com](https://github.com) and sign in
  - When prompted, complete your company SSO authentication
    (this redirects you to your identity provider — e.g. Microsoft login at `login.microsoftonline.com`)

  ### 2. Create a Personal Access Token
  - Go to **GitHub Settings → Developer settings → Personal access tokens → Tokens (classic)**
  - Click **Generate new token (classic)**
  - Set expiry (recommend 90 days or your company policy)
  - Select scopes:
    - `copilot` — GitHub Copilot access
    - `read:org` — read organisation membership (required for enterprise features)

  ### 3. Authorise the Token for SSO
  - After generating, you'll see a **"Configure SSO"** button next to your token
  - Click it → click **"Authorize"** next to your company organisation
  - This is the critical step — without it, the token will be rejected by Copilot Enterprise

  ### 4. Set the Token in the Framework

  **Option A — Environment variable (recommended):**
  ```bash
  export GITHUB_COPILOT_TOKEN="ghp_your_token_here"
  mvn test
  ```

  **Option B — GitHub Actions secret:**
  Add `COPILOT_TOKEN` in your repo → Settings → Secrets → Actions

  **Option C — Local .env file (never commit this):**
  ```properties
  GITHUB_COPILOT_TOKEN=ghp_your_token_here
  ```

  ---

  ## Copilot Enterprise API Endpoint

  Your enterprise may use a custom endpoint. Update `config.properties`:

  ```properties
  # Default (GitHub.com Copilot)
  copilot.api.url=https://api.githubcopilot.com

  # GitHub Enterprise Server (self-hosted)
  copilot.api.url=https://HOSTNAME/api/v3/copilot

  # Some enterprise orgs use:
  copilot.api.url=https://api.github.com
  ```

  ---

  ## Using Copilot Inside VS Code / Visual Studio (Enterprise Subscription)

  If you use Copilot via VS Code at your company:

  1. The framework's `CopilotClient.java` uses the same API as VS Code's Copilot extension
  2. Your enterprise Copilot subscription covers API usage — no extra billing
  3. The token is the same PAT you'd use for any GitHub enterprise operation

  ### VS Code Integration
  You can open this repo directly in VS Code and GitHub Copilot will:
  - Suggest completions for test methods
  - Explain framework code in Copilot Chat
  - Help write new page objects and API test classes

  The framework's `CopilotClient.java` is a programmatic version of the same capability.

  ---

  ## Verify the Connection

  After setting the token, run:

  ```bash
  mvn test -Dcopilot.enabled=true -Dtest=DatabaseValidationTest#testDatabaseConnection --no-transfer-progress
  ```

  Or run a quick connectivity check directly:

  ```java
  CopilotClient copilot = new CopilotClient();
  String response = copilot.complete("Say hello in one sentence.");
  System.out.println(response);
  ```

  ---

  ## Common Issues

  | Issue | Fix |
  |-------|-----|
  | `401 Unauthorized` | Token is missing or expired — regenerate |
  | `403 Forbidden` | Token not SSO-authorised — go to token settings → Configure SSO → Authorize |
  | `404 Not Found` | Wrong `copilot.api.url` — check your enterprise endpoint |
  | `No Copilot access` | Your GitHub account needs an Enterprise Copilot seat — contact your IT/admin |

  ---

  ## Further Help

  - [GitHub Copilot Enterprise docs](https://docs.github.com/en/copilot/github-copilot-enterprise)
  - [Authorise PAT for SAML SSO](https://docs.github.com/en/authentication/authenticating-with-saml-single-sign-on/authorizing-a-personal-access-token-for-use-with-saml-single-sign-on)
  