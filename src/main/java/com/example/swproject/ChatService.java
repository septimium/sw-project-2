package com.example.swproject;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ChatService {

    private final ChatLanguageModel chatModel;
    private final VectorDbService vectorDbService;
    private final RdfService rdfService;

    @Autowired
    public ChatService(VectorDbService vectorDbService, RdfService rdfService) {
        this.vectorDbService = vectorDbService;
        this.rdfService = rdfService;
        
        this.chatModel = OpenAiChatModel.builder()
                .baseUrl("http://localhost:1234/v1")
                .apiKey("ignore")
                .modelName("phi-3-mini-4k-instruct")
                .temperature(0.0) 
                .build();
    }

    public String chat(String message, String userId, String bookId) {
        Embedding queryEmbedding = vectorDbService.getEmbeddingModel().embed(message).content();
        
        List<EmbeddingMatch<TextSegment>> matches = vectorDbService.getEmbeddingStore()
                .findRelevant(queryEmbedding, 5, 0.0);
                
        StringBuilder contextBuilder = new StringBuilder();
        for (EmbeddingMatch<TextSegment> match : matches) {
            contextBuilder.append("- ").append(match.embedded().text()).append("\n");
        }
        String context = contextBuilder.toString();

        String userInfo = "";
        if (userId != null && !userId.isEmpty() && !"none".equals(userId)) {
            var details = rdfService.getUserDetails(userId);
            if (details != null && !details.isEmpty()) {
                userInfo = "Current Logged-in User: " + userId + "\n"
                         + "User Reading Level: " + details.get("level") + "\n"
                         + "User Preferred Genres: " + String.join(", ", (List<String>)details.get("prefersGenres")) + "\n"
                         + "Note: If the user asks for a recommendation, recommend ONLY books from the Context that match their reading level and preferred genres.\n\n";
            }
        }
        
        String pageContext = "";
        if (bookId != null && !bookId.isEmpty() && !"none".equals(bookId)) {
            var bookDetails = rdfService.getBookDetails(bookId);
            if (bookDetails != null && !bookDetails.isEmpty()) {
                pageContext = "Current Page Viewing: The user is currently viewing the page for the book '" + bookDetails.get("title") + "' (Book ID: " + bookId + "). "
                            + "If they say 'this book' or 'the author' without naming it, they are referring to this specific book!\n\n";
            }
        }
        
        System.out.println("RAG CONTEXT SENT TO LLM:");
        System.out.println(userInfo);
        System.out.println(pageContext);
        System.out.println(context);
        System.out.println();

        String projectInfo = "System Knowledge:\n"
                + "- This is a Semantic Web Book Recommendation System using Spring Boot and RDF/Jena.\n"
                + "- Home Page: Upload RDF/XML files.\n"
                + "- Visualize Page: See the RDF graph structure containing nodes (entities) and edges (relationships).\n"
                + "- Books Page: View, add, or modify books.\n"
                + "- The chatbot gives book recommendations based on user profiles.\n\n";

        String prompt = "You are a helpful AI assistant for a Book Recommendation System.\n\n"
                + projectInfo
                + userInfo
                + pageContext
                + "Context from Database:\n" 
                + (context.isEmpty() ? "No specific books in context.\n" : context) + "\n"
                + "Rules for answering:\n"
                + "1. For general AI conversation (like 'hello' or common knowledge), answer normally.\n"
                + "2. For questions about this project, the graph, or nodes/edges, use the 'System Knowledge' provided above.\n"
                + "3. For questions about books, authors, or genres, YOU MUST ONLY USE THE 'Context from Database'. If it's not a recommandation do not include users info. Do not use your own memory for book facts. If the context says 'Gigel wrote Harry Potter', you must say Gigel.\n"
                + "4. Keep answers short and concise.\n\n"
                + "User Question: " + message + "\n\n"
                + "Answer:";

        return chatModel.generate(prompt);
    }
}