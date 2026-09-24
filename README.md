# APEX Coding Agent

An AI-powered APEX rules agent with both a **Web UI** and a **CLI REPL**. Generate, validate, and execute APEX YAML business rule configurations from natural language requirements.

Built with Spring Boot 4.1.1 and Spring AI 2.0.1.

## Quick Start

```bash
export OPENAI_API_KEY=your-key-here   # Linux/macOS
set OPENAI_API_KEY=your-key-here      # Windows CMD
$env:OPENAI_API_KEY="your-key-here"   # Windows PowerShell
./mvnw spring-boot:run
```

### Web UI (default)

By default, the CLI REPL is disabled and the application starts as a web server.

Open the UI at: **http://localhost:8080**

### CLI REPL (optional)

To enable the interactive terminal REPL instead, set `app.repl.enabled=true`:

```bash
# Via command line
./mvnw spring-boot:run -Dspring-boot.run.arguments="--app.repl.enabled=true"

# Or set in application.yaml
app:
  repl:
    enabled: true
```

```
🤖 APEX Coding Agent Ready. Ask me anything about your codebase!

> explain the authentication flow
> find all usages of UserService
> run the tests and summarize failures
> what does this regex in config.yaml do?
```

Type `exit` to quit the REPL.

> **Note:** When the REPL is enabled, it reads from stdin. Running via `mvnw spring-boot:run` may not provide an interactive terminal — use the standalone jar for the best REPL experience.

### Build a Standalone Jar

```bash
./mvnw clean package -DskipTests
java -jar target/apex-coding-agent-0.0.1-SNAPSHOT.jar                    # Web UI on port 8080
java -jar target/apex-coding-agent-0.0.1-SNAPSHOT.jar --app.repl.enabled=true  # CLI REPL
```

## Configuration

Key properties in `src/main/resources/application.yaml`:

| Property | Default | Description |
|---|---|---|
| `app.repl.enabled` | `false` | Enable the interactive CLI REPL (disables web-only mode) |
| `apex.knowledge.enabled` | `false` | Enable RAG vector store for APEX knowledge base |
| `apex.knowledge.project-root` | `../apex-rules-engine` | Path to the APEX rules engine project |
| `spring.ai.openai.chat.options.model` | `gpt-4o` | LLM model to use |
| `server.port` | `8080` | Web server port (standard Spring Boot property) |

## Prerequisites

- Java 27+
- [OpenAI API key](https://platform.openai.com/api-keys)
- The sibling [`apex-rules-engine`](../apex-rules-engine) project, built and installed to your local Maven repository. `apex-core` and `apex-compiler` are `1.0-SNAPSHOT` dependencies that aren't published anywhere else, so the build fails without them:
  ```bash
  cd ../apex-rules-engine
  mvn clean install -DskipTests
  ```
  The knowledge base (`apex.knowledge.enabled=true`) also reads that project's sources from `apex.knowledge.project-root`.

## Architecture

[`Application.java`](src/main/java/dev/mars/apexcodingagent/Application.java) is the entry point. Beans are wired in [`AgentConfig.java`](src/main/java/dev/mars/apexcodingagent/AgentConfig.java), and the CLI REPL lives in [`ReplRunner.java`](src/main/java/dev/mars/apexcodingagent/ReplRunner.java). The `ChatClient` bean is configured with:

| Component | Purpose |
|---|---|
| System prompt | Loaded from `prompts/coding-agent-system.txt`, with working directory and OS injected |
| `FileSystemTools` | Read, write, and list files |
| `GrepTool` | Search file contents by pattern |
| `GlobTool` | Find files by name/path pattern |
| `ShellTools` | Run shell commands |
| `MessageWindowChatMemory` | 50-message sliding window for conversational context |
| `ToolCallingAdvisor` | Lets the model autonomously chain tool calls |

The **Web UI** is served from `src/main/resources/static/index.html` and communicates with the backend via the `ApexGenerationController` REST endpoint.

The optional **REPL** loop reads input, sends it to GPT-4o with tool context, and prints the response. The model decides which tools to invoke and in what order.

## Dependencies

| Library | Version |
|---|---|
| Spring Boot | 4.1.1 |
| Spring AI | 2.0.1 |
| [spring-ai-agent-utils](https://github.com/springaicommunity/spring-ai-agent-utils) | 0.12.0 |
| Java | 27 |

## Tests

```bash
./mvnw test
```

| Test class | What it covers |
|---|---|
| `ChatClientConfigTests` | Verifies ChatClient wiring (system prompt, tools, advisors). No LLM call. |
| `ReplTests` | REPL control flow (exit, empty input, EOF). No Spring context. |
| `ApplicationTests` | End-to-end smoke test with a real LLM call. Requires `OPENAI_API_KEY`. |

## Output

Each generation run writes its files to `generated/apex/<request-id>/` (rules, sample data, validation report). The folder is git-ignored; a sample passing run is kept in [`docs/examples/order-discount/`](docs/examples/order-discount/).

## Swapping Models

Spring AI abstracts the model provider. To switch from OpenAI to Anthropic, Ollama, or another supported backend, change the dependency and config — no code changes needed.

## Links

- [Spring AI docs](https://docs.spring.io/spring-ai/reference/)
- [spring-ai-agent-utils](https://github.com/springaicommunity/spring-ai-agent-utils)
- [OpenAI API reference](https://platform.openai.com/docs)
