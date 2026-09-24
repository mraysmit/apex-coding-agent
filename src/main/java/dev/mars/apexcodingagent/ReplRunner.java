package dev.mars.apexcodingagent;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.PrintStream;
import java.util.Map;
import java.util.Scanner;
import java.util.function.Function;

/**
 * Interactive command-line REPL over the coding-agent {@link ChatClient}.
 * Disabled by default in application.yaml so the web UI stays running;
 * enable with {@code --app.repl.enabled=true}.
 */
@Component
@ConditionalOnProperty(name = "app.repl.enabled", havingValue = "true", matchIfMissing = true)
class ReplRunner implements CommandLineRunner {

	private final ChatClient chatClient;

	ReplRunner(ChatClient chatClient) {
		this.chatClient = chatClient;
	}

	@Override
	public void run(String... args) {
		runRepl(this::ask, new Scanner(System.in), System.out);
	}

	private String ask(String input) {
		return chatClient.prompt(input)
				.toolContext(Map.of("workingDir", System.getProperty("user.dir")))
				.call().content();
	}

	/**
	 * Runs the read-eval-print loop until "exit" or end of input.
	 *
	 * @param model sends one user message to the model and returns its reply (may be null)
	 */
	static void runRepl(Function<String, String> model, Scanner scanner, PrintStream out) {
		out.println("🤖 APEX Coding Agent Ready. Ask me anything about your codebase!");

		while (true) {
			out.print("\n> ");
			out.flush();
			if (!scanner.hasNextLine()) {
				out.println("\nNo interactive terminal detected. Run with: java -jar target/apex-coding-agent-0.0.1-SNAPSHOT.jar");
				break;
			}
			String input = scanner.nextLine();
			if ("exit".equalsIgnoreCase(input.trim())) break;
			if (input.isBlank()) continue;

			String response = model.apply(input);
			out.println("\n" + (response != null ? response : "[No response from model]"));
		}
	}
}
