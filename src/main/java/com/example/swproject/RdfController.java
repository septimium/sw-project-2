package com.example.swproject;

import edu.uci.ics.jung.algorithms.layout.CircleLayout;
import edu.uci.ics.jung.algorithms.layout.FRLayout;
import edu.uci.ics.jung.algorithms.layout.Layout;
import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.SparseMultigraph;
import edu.uci.ics.jung.visualization.VisualizationImageServer;
import edu.uci.ics.jung.visualization.renderers.Renderer.VertexLabel.Position;
import com.google.common.base.Function;
import org.apache.jena.rdf.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Base64;
import java.util.UUID;

@Controller
public class RdfController {

    @Autowired
    private RdfService rdfService;

    private String currentFilePath = null;

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("chatStarters", java.util.Arrays.asList(
            "What can you do?",
            "How do I upload a book file?",
            "Can you explain what this project is about?"
        ));
        return "index";
    }

    @PostMapping("/upload")
    public String handleFileUpload(@RequestParam("file") MultipartFile file, RedirectAttributes redirectAttributes) {
        if (file.isEmpty()) {
            redirectAttributes.addFlashAttribute("message", "Please select a file to upload.");
            return "redirect:/";
        }
        try {
            String fileName = file.getOriginalFilename();
            File dest = new File(fileName);
            try (FileOutputStream fos = new FileOutputStream(dest)) {
                fos.write(file.getBytes());
            }
            currentFilePath = fileName;
            rdfService.setActiveFilePath(fileName);

            redirectAttributes.addFlashAttribute("message", "Successfully uploaded " + file.getOriginalFilename() + "!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("message", "Error uploading file!");
        }
        return "redirect:/";
    }

    @GetMapping("/visualize")
    public String visualize(ModelMap modelMap) {
        modelMap.addAttribute("chatStarters", java.util.Arrays.asList(
            "Explain the graph structure",
            "What do the edges represent?",
            "How does FRLayout work?"
        ));
        
        if (currentFilePath == null) {
            modelMap.addAttribute("error", "No RDF file uploaded yet.");
            return "visualize";
        }
        File file = new File(currentFilePath);
        if (!file.exists()) {
            modelMap.addAttribute("error", "The uploaded RDF file is missing.");
            return "visualize";
        }

        try {
            org.apache.jena.rdf.model.Model rdfModel = ModelFactory.createDefaultModel();
            try (FileInputStream fis = new FileInputStream(file)) {
                rdfModel.read(fis, null, "RDF/XML");
            }

            Graph<String, String> graph = new SparseMultigraph<>();
            StmtIterator iter = rdfModel.listStatements();
            
            while (iter.hasNext()) {
                Statement stmt = iter.nextStatement();
                String subject = formatNode(stmt.getSubject().toString());
                String object = formatNode(stmt.getObject().toString());
                String predicate = stmt.getPredicate().getLocalName();

                graph.addVertex(subject);
                graph.addVertex(object);
                String edge = predicate + " [" + UUID.randomUUID().toString().substring(0,4) + "]";
                graph.addEdge(edge, subject, object);
            }

            Layout<String, String> layout = new FRLayout<>(graph);
            layout.setSize(new Dimension(1600, 900));
            VisualizationImageServer<String, String> server = new VisualizationImageServer<>(layout, new Dimension(1600, 900));

            server.getRenderContext().setVertexLabelTransformer(s -> s);
            server.getRenderContext().setEdgeLabelTransformer(e -> e.substring(0, e.lastIndexOf(" [")));
            server.getRenderer().getVertexLabelRenderer().setPosition(Position.CNTR);
            
            Function<String, Paint> vertexPaint = s -> Color.CYAN;
            server.getRenderContext().setVertexFillPaintTransformer(vertexPaint);
            server.getRenderContext().setVertexShapeTransformer(s -> new Ellipse2D.Double(-40, -15, 80, 30));

            BufferedImage image = new BufferedImage(1600, 900, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2d = image.createGraphics();
            g2d.setColor(Color.WHITE);
            g2d.fillRect(0, 0, 1600, 900);
            server.paint(g2d);
            g2d.dispose();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "png", baos);
            String base64Image = Base64.getEncoder().encodeToString(baos.toByteArray());

            modelMap.addAttribute("graphImage", base64Image);

        } catch (Exception e) {
            e.printStackTrace();
            modelMap.addAttribute("error", "Error creating graph: " + e.getMessage());
        }

        return "visualize";
    }

    @GetMapping("/books")
    public String listBooks(Model model) {
        model.addAttribute("books", rdfService.getAllBooks());
        model.addAttribute("chatStarters", java.util.Arrays.asList(
            "What genres are available?",
            "Can you suggest a book?",
            "How do I add a new book?"
        ));
        return "books";
    }

    @GetMapping("/book/{id}")
    public String bookDetails(@PathVariable String id, Model model) {
        model.addAttribute("book", rdfService.getBookDetails(id));
        model.addAttribute("chatStarters", java.util.Arrays.asList(
            "Give me a summary of this book.",
            "Who is the author of this book?",
            "What does this reading level mean?"
        ));
        return "book";
    }

    @PostMapping("/book/add")
    public String addBook(@RequestParam String id, @RequestParam String title, @RequestParam String genre, @RequestParam String level) {
        rdfService.addBook(id, title, level, genre);
        return "redirect:/books";
    }

    @PostMapping("/book/modify")
    public String modifyBook(@RequestParam String id, @RequestParam String level) {
        rdfService.modifyReadingLevel(id, level);
        return "redirect:/book/" + id;
    }

    private String formatNode(String node) {
        if (node.contains("#")) return node.substring(node.lastIndexOf("#") + 1);
        if (node.contains("/")) return node.substring(node.lastIndexOf("/") + 1);
        return node;
    }
}
