package ru.linachan.ctf;

import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;

public class CTFDataBase {

    private MongoClient dbClient;
    private MongoDatabase dbInstance;

    public CTFDataBase(String dbUri, String dbName) {
        dbClient = new MongoClient(new MongoClientURI(dbUri));
        dbInstance = dbClient.getDatabase(dbName);
    }

    public MongoDatabase getDataBase() {
        return dbInstance;
    }

    public MongoCollection<Document> getCollection(String collection) {
        return dbInstance.getCollection(collection);
    }
}