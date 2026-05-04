package com.example.swproject;

import org.apache.jena.query.*;
import org.apache.jena.rdf.model.*;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class RdfService {

    private String activeFilePath = null;
    private final String NS = "http://example.org/books#";
    private final String DC = "http://purl.org/dc/elements/1.1/";

    public void setActiveFilePath(String path) {
        this.activeFilePath = path;
    }

    public boolean hasActiveFile() {
        return activeFilePath != null && new java.io.File(activeFilePath).exists();
    }

    private Model getModel() {
        Model model = ModelFactory.createDefaultModel();
        if (activeFilePath == null) return model;
        try (FileInputStream fis = new FileInputStream(activeFilePath)) {
            model.read(fis, null, "RDF/XML");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return model;
    }

    private void saveModel(Model model) {
        if (activeFilePath == null) return;
        try (FileOutputStream fos = new FileOutputStream(activeFilePath)) {
            model.write(fos, "RDF/XML");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public List<Map<String, String>> getAllBooks() {
        List<Map<String, String>> books = new ArrayList<>();
        Model model = getModel();
        String queryString = "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> " +
                "PREFIX ex: <" + NS + "> " +
                "PREFIX dc: <" + DC + "> " +
                "SELECT ?book ?title " +
                "WHERE { ?book rdf:type ex:Book . ?book dc:title ?title . }";

        try (QueryExecution qExec = QueryExecutionFactory.create(queryString, model)) {
            ResultSet results = qExec.execSelect();
            while (results.hasNext()) {
                QuerySolution soln = results.nextSolution();
                Map<String, String> book = new HashMap<>();
                book.put("id", soln.getResource("book").getLocalName());
                book.put("title", soln.getLiteral("title").getString());
                books.add(book);
            }
        }
        return books;
    }

    public Map<String, Object> getBookDetails(String id) {
        Model model = getModel();
        Resource bookRes = model.getResource(NS + id);
        Map<String, Object> details = new HashMap<>();
        List<String> genres = new ArrayList<>();

        if (bookRes == null) return null;

        StmtIterator iter = bookRes.listProperties();
        while (iter.hasNext()) {
            Statement stmt = iter.nextStatement();
            String propName = stmt.getPredicate().getLocalName();
            String value = stmt.getObject().isLiteral() ? stmt.getObject().asLiteral().getString() : stmt.getObject().toString();

            if (propName.equals("title")) {
                details.put("title", value);
            } else if (propName.equals("hasGenre")) {
                genres.add(value);
            } else if (propName.equals("suitableForLevel")) {
                details.put("level", value);
            }
        }
        details.put("genres", genres);
        details.put("id", id);
        return details;
    }

    public void addBook(String id, String title, String level, String genre) {
        Model model = getModel();
        Resource newBook = model.createResource(NS + id);
        newBook.addProperty(model.createProperty("http://www.w3.org/1999/02/22-rdf-syntax-ns#type"), model.createResource(NS + "Book"));
        newBook.addProperty(model.createProperty(DC + "title"), title);
        newBook.addProperty(model.createProperty(NS + "suitableForLevel"), level);
        newBook.addProperty(model.createProperty(NS + "hasGenre"), genre);
        saveModel(model);
    }

    public void modifyReadingLevel(String id, String newLevel) {
        Model model = getModel();
        Resource book = model.getResource(NS + id);
        Property levelProp = model.createProperty(NS + "suitableForLevel");

        book.removeAll(levelProp);
        book.addProperty(levelProp, newLevel);
        saveModel(model);
    }
}
