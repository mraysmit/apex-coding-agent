package dev.mars.apexcodingagent.orchestration;

import dev.mars.apexcodingagent.orchestration.model.GenerationRequest;
import dev.mars.apexcodingagent.orchestration.model.GenerationResult;

/**
 * Generates APEX YAML rule configurations from natural language requirements.
 * Implemented by {@link ApexGenerationService}; callers depend on this interface
 * so they can be tested with a plain lambda.
 */
@FunctionalInterface
public interface ApexRuleGenerator {

    GenerationResult generate(GenerationRequest request);
}
