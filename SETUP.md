# Enterprise Test Automation Framework — Setup Guide

## Prerequisites

| Tool | Version | Notes |
|------|---------|-------|
| Java (JDK) | 17+ | Temurin recommended |
| Maven | 3.9+ | |
| Chrome/Firefox | Latest | For UI tests |
| PostgreSQL | 14+ | For test data + Healenium store |

---

## 1. Clone and Install

```bash
git clone https://github.com/YOUR-ORG/enterprise-test-framework.git
cd enterprise-test-framework/java-test-framework
mvn install -DskipTests
```

---

## 2. Configure Secrets

Never commit secrets. Set them as environment variables:

```bash
# Jira
export JIRA_API_TOKEN="your-jira-api-token"
export JIRA_USER_EMAIL="you@company.com"

# Database (PostgreSQL)
export DB_HOST="localhost"
export DB_NAME="testdb"
export DB_USER="postgres"
export DB_PASSWORD="your-password"

# GitHub Copilot (Enterprise)
export GITHUB_COPILOT_TOKEN="your-copilot-token"

# AWS Data Lake (optional)
export AWS_ACCESS_KEY_ID="your-key"
export AWS_SECRET_ACCESS_KEY="your-secret"
export S3_BUCKET_NAME="your-bucket"
export ATHENA_DATABASE="your-athena-db"
```

Or create a `.env` file (already in `.gitignore`):

```properties
JIRA_API_TOKEN=your-token
JIRA_USER_EMAIL=you@company.com
...
```

---

## 3. Update config.properties

Edit `src/main/resources/config.properties`:

```properties
base.url=https://your-app.example.com
jira.base.url=https://your-company.atlassian.net
jira.project.key=QA
jira.test.label=automated
```

---

## 4. Healenium Setup (Auto-Healing)

Healenium requires its own PostgreSQL database. Start it using Docker:

```bash
docker run -d \
  --name healenium-db \
  -e POSTGRES_DB=healenium \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=your-password \
  -p 5433:5432 \
  postgres:14
```

Then update config.properties:
```properties
healenium.db.host=localhost
healenium.db.port=5433
healenium.db.name=healenium
healenium.enabled=true
```

---

## 5. Run Tests

### Smoke tests (fast)
```bash
mvn test -DsuiteXmlFile=testng-smoke.xml
```

### All tests
```bash
mvn test
```

### Specific environment
```bash
mvn test -Denv=staging -Dbrowser=chrome -Dheadless=true
```

### Specific Jira issues
```bash
mvn test -Djira.issue.keys=QA-123,QA-456
```

### With GitHub Copilot code generation enabled
```bash
mvn test -Dcopilot.enabled=true
```

---

## 6. View Reports

### Allure (recommended)
```bash
mvn allure:report
mvn allure:serve   # opens in browser
```

### Screenshots
Automatically saved to `target/screenshots/` on failure.

---

## 7. GitHub Actions Setup

Add the following secrets to your GitHub repository:
- `JIRA_API_TOKEN`
- `JIRA_USER_EMAIL`
- `DB_HOST`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`
- `COPILOT_TOKEN`
- `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` (optional)

The pipeline runs automatically on push to `main` / `develop` and publishes
Allure reports to GitHub Pages.

---

## 8. MCP Server

The MCP server starts automatically with the test suite on port `8765`.
AI agents can call its tools via HTTP:

```bash
# List available tools
curl http://localhost:8765/mcp/tools

# Fetch Jira issue context
curl -X POST http://localhost:8765/mcp/call \
  -H "Content-Type: application/json" \
  -d '{"tool":"jira_get_issue","parameters":{"issueKey":"QA-123"}}'

# Query database
curl -X POST http://localhost:8765/mcp/call \
  -H "Content-Type: application/json" \
  -d '{"tool":"db_query","parameters":{"sql":"SELECT COUNT(*) FROM users"}}'
```

---

## Project Structure

```
java-test-framework/
├── pom.xml                           Maven build + all dependencies
├── testng.xml                        Default TestNG suite (all tests)
├── testng-smoke.xml                  Fast smoke suite
├── .github/workflows/
│   └── test-automation.yml           CI/CD pipeline (GitHub Actions)
└── src/
    ├── main/java/com/framework/
    │   ├── config/
    │   │   └── FrameworkConfig.java   Central config loader
    │   ├── jira/
    │   │   ├── JiraClient.java        Jira REST API v3 client
    │   │   ├── JiraIssue.java         Jira issue model
    │   │   ├── JiraTestMapper.java    Maps issues to test context
    │   │   └── JiraTestContext.java   Enriched test context
    │   ├── api/
    │   │   ├── RestAssuredBase.java   REST Assured base + token store
    │   │   └── ApiRequestBuilder.java Fluent request builder
    │   ├── ui/
    │   │   ├── DriverFactory.java     Thread-safe WebDriver + Healenium
    │   │   ├── BasePage.java          Page Object base with smart waits
    │   │   └── healing/
    │   │       └── HealingElementFinder.java  Multi-locator fallback
    │   ├── database/
    │   │   ├── PostgreSQLManager.java HikariCP pool + query helpers
    │   │   └── DataLakeManager.java   AWS Athena + S3 integration
    │   ├── mcp/
    │   │   ├── MCPServer.java         MCP HTTP server
    │   │   ├── MCPTool.java           Tool interface
    │   │   └── tools/
    │   │       ├── JiraTool.java      Jira MCP tools
    │   │       ├── DatabaseTool.java  DB MCP tools
    │   │       └── TestPlanTool.java  Test plan MCP tools
    │   ├── ai/
    │   │   └── CopilotClient.java     GitHub Copilot Enterprise client
    │   ├── reporting/
    │   │   └── TestReporter.java      Allure + Jira result publisher
    │   └── utils/
    │       ├── ScreenshotUtil.java    Screenshot capture + Allure attach
    │       └── DataProvider.java      CSV / Excel data loader
    └── test/java/com/framework/
        ├── base/
        │   └── BaseTest.java          Test base class + lifecycle
        ├── api/
        │   └── SampleApiTest.java     Sample REST Assured tests
        ├── ui/
        │   └── SampleUITest.java      Sample Selenium UI tests
        └── data/
            └── DatabaseValidationTest.java  Sample DB + Data Lake tests
```
