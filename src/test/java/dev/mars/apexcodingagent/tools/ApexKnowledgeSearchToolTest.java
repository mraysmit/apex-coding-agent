package dev.mars.apexcodingagent.tools;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for ApexKnowledgeSearchTool semantic search functionality.
 */
class ApexKnowledgeSearchToolTest {

    FakeVectorStore vectorStore;
    ApexKnowledgeSearchTool searchTool;

    @BeforeEach
    void setUp() {
        vectorStore = new FakeVectorStore();
        searchTool = ApexKnowledgeSearchTool.builder()
                .vectorStore(vectorStore)
                .build();
    }

    @Test
    void semanticSearch_returnsFormattedResults() {
        Document doc1 = new Document("enrichment groups allow grouping related enrichments",
                Map.of("source", "docs/guide.md", "contentType", "documentation",
                        "section", "## Enrichment Groups"));
        Document doc2 = new Document("metadata:\n  type: rule-config\nenrichment-groups:\n  - id: eg1",
                Map.of("source", "examples/enrichment-group.yaml", "contentType", "yaml-example",
                        "category", "enrichments", "docType", "rule-config"));

        vectorStore.onSearch = request -> List.of(doc1, doc2);

        String result = searchTool.semanticSearch("enrichment groups", 5);

        assertThat(result).contains("Found 2 relevant results");
        assertThat(result).contains("docs/guide.md");
        assertThat(result).contains("examples/enrichment-group.yaml");
        assertThat(result).contains("Enrichment Groups");
        assertThat(result).contains("documentation");
        assertThat(result).contains("yaml-example");
    }

    @Test
    void semanticSearch_noResults() {
        vectorStore.onSearch = request -> List.of();

        String result = searchTool.semanticSearch("nonexistent topic", 5);

        assertThat(result).contains("No matching documents found");
    }

    @Test
    void semanticSearch_nullResults() {
        vectorStore.onSearch = request -> null;

        String result = searchTool.semanticSearch("anything", 5);

        assertThat(result).contains("No matching documents found");
    }

    @Test
    void semanticSearch_defaultsTopKWhenNull() {
        vectorStore.onSearch = request -> List.of();

        // Should not throw even with null topK
        String result = searchTool.semanticSearch("query", null);
        assertThat(result).isNotNull();
        assertThat(vectorStore.requests).singleElement()
                .extracting(SearchRequest::getTopK).isEqualTo(5);
    }

    @Test
    void semanticSearch_defaultsTopKWhenZero() {
        vectorStore.onSearch = request -> List.of();

        String result = searchTool.semanticSearch("query", 0);
        assertThat(result).isNotNull();
        assertThat(vectorStore.requests).singleElement()
                .extracting(SearchRequest::getTopK).isEqualTo(5);
    }

    @Test
    void semanticSearch_handlesException() {
        vectorStore.onSearch = request -> {
            throw new RuntimeException("Connection timeout");
        };

        String result = searchTool.semanticSearch("query", 5);

        assertThat(result).contains("Error performing semantic search");
        assertThat(result).contains("Connection timeout");
    }

    @Test
    void semanticSearch_truncatesLongContent() {
        String longContent = "x".repeat(3000);
        Document doc = new Document(longContent, Map.of("source", "file.md"));

        vectorStore.onSearch = request -> List.of(doc);

        String result = searchTool.semanticSearch("query", 1);

        assertThat(result).contains("truncated");
        // Should not contain the full 3000-char string
        assertThat(result.length()).isLessThan(longContent.length());
    }

    @Test
    void semanticSearch_includesAllMetadataFields() {
        Document doc = new Document("content",
                Map.of("source", "path/file.yaml",
                        "contentType", "yaml-example",
                        "section", "Rules",
                        "docType", "rule-config",
                        "category", "validation",
                        "features", "rules,enrichments"));

        vectorStore.onSearch = request -> List.of(doc);

        String result = searchTool.semanticSearch("query", 1);

        assertThat(result).contains("Source: path/file.yaml");
        assertThat(result).contains("Type: yaml-example");
        assertThat(result).contains("Section: Rules");
        assertThat(result).contains("DocType: rule-config");
        assertThat(result).contains("Category: validation");
        assertThat(result).contains("Features: rules,enrichments");
    }

    // ---- Builder Tests ----

    @Test
    void builder_requiresVectorStore() {
        assertThatIllegalStateException()
                .isThrownBy(() -> ApexKnowledgeSearchTool.builder().build())
                .withMessageContaining("vectorStore is required");
    }

    @Test
    void builder_createsToolSuccessfully() {
        VectorStore store = new FakeVectorStore();
        ApexKnowledgeSearchTool tool = ApexKnowledgeSearchTool.builder()
                .vectorStore(store)
                .build();
        assertThat(tool).isNotNull();
    }

    // ---- Test double ----

    /** Records search requests and answers them with {@link #onSearch}. Write operations are unsupported. */
    static class FakeVectorStore implements VectorStore {

        final List<SearchRequest> requests = new ArrayList<>();
        Function<SearchRequest, List<Document>> onSearch = request -> List.of();

        @Override
        public List<Document> similaritySearch(SearchRequest request) {
            requests.add(request);
            return onSearch.apply(request);
        }

        @Override
        public void add(List<Document> documents) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void delete(List<String> idList) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void delete(Filter.Expression filterExpression) {
            throw new UnsupportedOperationException();
        }
    }
}
