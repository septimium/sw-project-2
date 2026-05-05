package com.example.swproject;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class VectorDbService {

    private EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;

    @Autowired
    private RdfService rdfService;

    public VectorDbService() {
        this.embeddingStore = new InMemoryEmbeddingStore<>();
        
        this.embeddingModel = OpenAiEmbeddingModel.builder()
                .baseUrl("http://localhost:1234/v1")
                .apiKey("ignore") 
                .modelName("text-embedding-nomic-embed-text-v1.5")
                .build();
    }

    public void buildVectorDatabaseFromRdf() {
        if (!rdfService.hasActiveFile()) {
            System.out.println("No RDF file loaded. Skipping Vector DB construction.");
            return;
        }

        this.embeddingStore = new InMemoryEmbeddingStore<>();

        List<Map<String, String>> books = rdfService.getAllBooks();
        for (Map<String, String> bookInfo : books) {
            String id = bookInfo.get("id");
            Map<String, Object> details = rdfService.getBookDetails(id);
            
            StringBuilder textContent = new StringBuilder();
            textContent.append("Book ID: ").append(id).append(". ");
            textContent.append("Title: ").append(details.get("title")).append(". ");
            
            if (details.containsKey("author") && details.get("author") != null) {
                textContent.append("Author: ").append(details.get("author")).append(". ");
            }
            
            textContent.append("Suitable for Reading Level: ").append(details.get("level")).append(". ");
            
            if (details.containsKey("genres")) {
                List<String> genres = (List<String>) details.get("genres");
                if (!genres.isEmpty()) {
                    textContent.append("Genres (themes): ").append(String.join(", ", genres)).append(". ");
                }
            }

            TextSegment segment = TextSegment.from(textContent.toString());
            try {
                Embedding embedding = embeddingModel.embed(segment).content();
                embeddingStore.add(embedding, segment);
            } catch (Exception e) {
                System.err.println("Could not embed document." + e.getMessage());
            }
        }
        
        List<String> users = rdfService.getAllUsers();
        for (String userId : users) {
             Map<String, Object> details = rdfService.getUserDetails(userId);
             StringBuilder textContent = new StringBuilder();
             textContent.append("User ID: ").append(userId).append(". ");
             textContent.append("User Reading Level: ").append(details.get("level")).append(". ");
             
             if (details.containsKey("prefersGenres")) {
                List<String> genres = (List<String>) details.get("prefersGenres");
                if (!genres.isEmpty()) {
                    textContent.append("User prefers genres (themes): ").append(String.join(", ", genres)).append(". ");
                }
             }
             
             TextSegment segment = TextSegment.from(textContent.toString());
             try {
                 Embedding embedding = embeddingModel.embed(segment).content();
                 embeddingStore.add(embedding, segment);
             } catch (Exception e) {
                 System.err.println("Could not embed user document. " + e.getMessage());
             }
        }
        
        System.out.println("Vector Database successfully populated with " + books.size() + " books and " + users.size() + " users.");
    }

    public EmbeddingStore<TextSegment> getEmbeddingStore() {
        return embeddingStore;
    }

    public EmbeddingModel getEmbeddingModel() {
        return embeddingModel;
    }
}