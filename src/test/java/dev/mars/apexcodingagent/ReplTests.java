package dev.mars.apexcodingagent;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lightweight unit tests for the REPL loop control flow.
 * No Spring context needed — the model is a recording lambda.
 */
class ReplTests {

	final List<String> prompts = new ArrayList<>();

	Function<String, String> modelReplying(String reply) {
		return input -> {
			prompts.add(input);
			return reply;
		};
	}

	String run(String input, Function<String, String> model) {
		Scanner scanner = new Scanner(new ByteArrayInputStream(input.getBytes()));
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		ReplRunner.runRepl(model, scanner, new PrintStream(output));
		return output.toString();
	}

	@Test
	void exitCommandTerminatesWithoutCallingModel() {
		String output = run("exit\n", modelReplying("unused"));

		assertThat(output).contains("APEX Coding Agent Ready");
		assertThat(prompts).isEmpty();
	}

	@Test
	void userInputIsSentToModelAndResponsePrinted() {
		String output = run("What does this code do?\nexit\n", modelReplying("Here is the answer"));

		assertThat(prompts).containsExactly("What does this code do?");
		assertThat(output).contains("Here is the answer");
	}

	@Test
	void noInteractiveTerminalExitsGracefully() {
		String output = run("", modelReplying("unused"));

		assertThat(output).contains("No interactive terminal detected");
		assertThat(prompts).isEmpty();
	}

	@Test
	void blankInputIsSkippedWithoutCallingModel() {
		run("   \n\nexit\n", modelReplying("response"));

		assertThat(prompts).isEmpty();
	}

	@Test
	void nullResponseFromModelIsHandledGracefully() {
		String output = run("hello\nexit\n", modelReplying(null));

		assertThat(output).contains("[No response from model]");
	}
}
