package dev.mars.apexcodingagent.orchestration;

import dev.mars.apexcodingagent.orchestration.model.DescriptionRequest;
import dev.mars.apexcodingagent.orchestration.model.DescriptionResult;

/**
 * Derives a business description from an APEX YAML configuration.
 * Implemented by {@link ApexDescriptionService}; callers depend on this interface
 * so they can be tested with a plain lambda.
 */
@FunctionalInterface
public interface ApexRuleDescriber {

    DescriptionResult describe(DescriptionRequest request);
}
