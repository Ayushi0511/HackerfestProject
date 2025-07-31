package de.telekom.ai.etl;

import de.telekom.ai.etl.service.EtlPipeline;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;

import java.util.List;

@SpringBootApplication
public class AiEtlApplication {

    public static void main(String[] args) {
        ApplicationContext ac = SpringApplication.run(AiEtlApplication.class, args);

       // ac.getBean(EtlPipeline.class).runIngestion();
        List<Document> results = ac.getBean(VectorStore.class)
                .similaritySearch(SearchRequest.builder()
                        .query("Carmen").topK(1).build());

        results.stream()
                .map(Document::getFormattedContent)
                .forEach(System.out::println);
    }

}
