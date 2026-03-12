# Enterprise Test Automation Framework

  Advanced Java test automation framework featuring:

  - **REST Assured** – API testing with fluent builder and schema validation
  - **Selenium + Healenium** – UI testing with AI-powered auto-healing locators
  - **Jira MCP Integration** – Automatically fetches test context, acceptance criteria, and updates tickets
  - **PostgreSQL** – Dynamic test data management with HikariCP connection pool
  - **AWS Data Lake** – Athena query validation and S3 partition checks
  - **GitHub Copilot Enterprise** – AI-assisted test generation and failure analysis
  - **Allure Reporting** – Rich test reports with Jira traceability
  - **GitHub Actions CI/CD** – Automated pipeline with parallel API and UI test jobs

  ## Quick Start

  ```bash
  git clone https://github.com/pavansubbarao/enterprise-test-automation-framework.git
  cd enterprise-test-automation-framework
  # Set your secrets (see SETUP.md)
  mvn test -DsuiteXmlFile=testng-smoke.xml
  ```

  ## GitHub Actions Setup

  The CI/CD workflow file is in `github-workflows/test-automation.yml`.

  **To activate it**, rename the folder:

  ```bash
  git clone https://github.com/pavansubbarao/enterprise-test-automation-framework.git
  cd enterprise-test-automation-framework
  mkdir -p .github/workflows
  cp github-workflows/test-automation.yml .github/workflows/
  git add .github/
  git commit -m "Activate GitHub Actions pipeline"
  git push
  ```

  Then add these secrets in your GitHub repo → Settings → Secrets:

  | Secret | Description |
  |--------|-------------|
  | `JIRA_API_TOKEN` | Jira API token |
  | `JIRA_USER_EMAIL` | Your Jira email |
  | `DB_HOST` | PostgreSQL host |
  | `DB_NAME` | PostgreSQL database name |
  | `DB_USER` | PostgreSQL username |
  | `DB_PASSWORD` | PostgreSQL password |
  | `COPILOT_TOKEN` | GitHub Copilot Enterprise token |

  ## GitHub Copilot Enterprise + SSO

  For Copilot Enterprise behind your company SSO, see [COPILOT_SSO_GUIDE.md](COPILOT_SSO_GUIDE.md).

  ## Documentation

  - [SETUP.md](SETUP.md) – Full setup and configuration guide
  - [COPILOT_SSO_GUIDE.md](COPILOT_SSO_GUIDE.md) – How to connect Copilot Enterprise via SSO

  ## Structure

  ```
  src/main/java/com/framework/
  ├── config/         Central configuration loader
  ├── jira/           Jira REST API client + test context mapper
  ├── api/            REST Assured base + fluent request builder
  ├── ui/             Selenium WebDriver factory + BasePage + auto-healing
  ├── database/       PostgreSQL manager + AWS Data Lake (Athena)
  ├── mcp/            MCP server + Jira/DB/TestPlan tools
  ├── ai/             GitHub Copilot Enterprise client
  ├── reporting/      Allure + Jira result publisher
  └── utils/          Screenshot util + CSV/Excel data provider
  ```
  