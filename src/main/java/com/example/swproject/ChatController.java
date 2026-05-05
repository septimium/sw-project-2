package com.example.swproject;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ChatController {

    @Autowired
    private ChatService chatService;

    @Autowired
    private RdfService rdfService;

    @GetMapping("/users")
    public List<String> getUsers() {
        return rdfService.getAllUsers();
    }

    @PostMapping("/chat")
    public String chat(@RequestBody Map<String, String> request) {
        String userMessage = request.get("message");
        String userId = request.get("userId");
        
        if (userMessage == null || userMessage.trim().isEmpty()) {
            return "Please provide a valid message.";
        }
        try {
            return chatService.chat(userMessage, userId);
        } catch (Exception e) {
            e.printStackTrace();
            return "Error connecting to LMStudio.";
        }
    }
}