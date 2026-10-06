package com.salarywise.config;

import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

/** Smart Saving Plan has no local database fallback. MongoDB must be reachable. */
@Component
public class MongoStartupCheck implements ApplicationRunner {
    private final MongoTemplate mongoTemplate;

    public MongoStartupCheck(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        MongoDatabase database = mongoTemplate.getDb();
        database.runCommand(new Document("ping", 1));
    }
}
