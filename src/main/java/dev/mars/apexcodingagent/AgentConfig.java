package dev.mars.apexcodingagent;

import org.springaicommunity.agent.tools.FileSystemTools;
import org.springaicommunity.agent.tools.GlobTool;
import org.springaicommunity.agent.tools.GrepTool;
import org.springaicommunity.agent.tools.ShellTools;
import dev.mars.apexcodingagent.tools.ApexCompileTool;
import dev.mars.apexcodingagent.tools.ApexExecuteTool;
import dev.mars.apexcodingagent.tools.ApexExpectationTool;
import dev.mars.apexcodingagent.tools.ApexKnowledgeSearchTool;
import dev.mars.apexcodingagent.tools.ApexSyntaxTool;
import dev.mars.apexcodingagent.tools.ApexExampleRetrievalTool;
import dev.mars.apexcodingagent.orchestration.ApexDescribeCommand;
import dev.mars.apexcodingagent.orchestration.ApexDescriptionService;
import dev.mars.apexcodingagent.orchestration.ApexGenerateCommand;
import dev.mars.apexcodingagent.orchestration.ApexGenerationService;
import dev.mars.apexcodingagent.rag.ApexKnowledgeIngester;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * Wires the knowledge base, the APEX generation/description services, and the
 * interactive coding-agent {@link ChatClient} used by the REPL.
 */
@Configuration
class AgentConfig {

	private static final String SYSTEM_PROMPT_RESOURCE = "/prompts/coding-agent-system.txt";
	private static final String CONVERSATION_ID = "default";

	@Bean
	@ConditionalOnProperty(name = "apex.knowledge.enabled", havingValue = "true", matchIfMissing = true)
	SimpleVectorStore apexVectorStore(EmbeddingModel embeddingModel,
									  @Value("${apex.knowledge.project-root:../apex-rules-engine}") String projectRoot,
									  @Value("${apex.knowledge.vector-store-path:knowledge/apex-vector-store.json}") String storePath) {
		ApexKnowledgeIngester ingester = ApexKnowledgeIngester.builder()
				.embeddingModel(embeddingModel)
				.apexProjectRoot(Path.of(projectRoot))
				.vectorStorePath(Path.of(storePath))
				.build();
		return ingester.loadOrBuild();
	}

	@Bean
	ApexGenerationService apexGenerationService(ChatModel chatModel,
												@Nullable SimpleVectorStore apexVectorStore) {
		var builder = ApexGenerationService.builder()
				.chatModel(chatModel)
				.outputDir(Path.of("generated", "apex"))
				.maxAttempts(3);
		if (apexVectorStore != null) {
			builder.vectorStore(apexVectorStore);
		}
		return builder.build();
	}

	@Bean
	ApexDescriptionService apexDescriptionService(ChatModel chatModel) {
		return ApexDescriptionService.builder()
				.chatModel(chatModel)
				.build();
	}

	@Bean
	ChatClient chatClient(ChatClient.Builder chatClientBuilder, ApexGenerationService apexGenerationService,
						  ApexDescriptionService apexDescriptionService,
						  @Nullable SimpleVectorStore apexVectorStore) {
		var tools = new java.util.ArrayList<Object>();
		tools.add(FileSystemTools.builder().build());
		tools.add(GrepTool.builder().build());
		tools.add(GlobTool.builder().build());
		tools.add(ShellTools.builder().build());
		tools.add(ApexCompileTool.builder().build());
		tools.add(ApexExecuteTool.builder().build());
		tools.add(ApexExpectationTool.builder().build());
		tools.add(ApexSyntaxTool.builder().build());
		tools.add(ApexExampleRetrievalTool.builder().build());
		if (apexVectorStore != null) {
			tools.add(ApexKnowledgeSearchTool.builder().vectorStore(apexVectorStore).build());
		}
		tools.add(new ApexGenerateCommand(apexGenerationService));
		tools.add(new ApexDescribeCommand(apexDescriptionService));

		return chatClientBuilder.clone()
				.defaultSystem(systemPrompt())
				.defaultTools(tools.toArray())
				.defaultAdvisors(advisors -> advisors
						.advisors(
								ToolCallingAdvisor.builder().conversationHistoryEnabled(false).build(),
								MessageChatMemoryAdvisor.builder(
										MessageWindowChatMemory.builder().maxMessages(50).build()
								).build())
						// Spring AI 2.0 no longer defaults the conversation id; the REPL is a single conversation
						.param(ChatMemory.CONVERSATION_ID, CONVERSATION_ID))
				.build();
	}

	static String systemPrompt() {
		try (InputStream is = AgentConfig.class.getResourceAsStream(SYSTEM_PROMPT_RESOURCE)) {
			if (is == null) {
				throw new IllegalStateException("System prompt resource not found: " + SYSTEM_PROMPT_RESOURCE);
			}
			return new String(is.readAllBytes(), StandardCharsets.UTF_8)
					.replace("{{workingDir}}", System.getProperty("user.dir"))
					.replace("{{osName}}", System.getProperty("os.name"));
		} catch (IOException e) {
			throw new IllegalStateException("Failed to load system prompt", e);
		}
	}
}
