package emu.grasscutter.database;

import static org.junit.jupiter.api.Assertions.*;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoCommandException;
import com.mongodb.ServerAddress;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import dev.morphia.Datastore;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.bson.BsonDocument;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.junit.jupiter.api.Test;

class DatabaseIndexSafetyTest {
    @Test
    void entityIndexErrorsPreserveExistingIndexes() {
        var conflict = mongoIndexError(85);
        var calls = new AtomicInteger();
        var datastore = proxy(Datastore.class, (ignored, method, args) -> {
            assertEquals("ensureIndexes", method.getName());
            calls.incrementAndGet();
            throw conflict;
        });
        var failure = assertThrows(IllegalStateException.class, () -> DatabaseManager.ensureIndexes(datastore));
        assertSame(conflict, failure.getCause());
        assertEquals(1, calls.get());
    }

    @Test
    void rawSpawnIndexMatchesOpenWorldIdentityLookup() {
        var collection = proxy(MongoCollection.class, (ignored, method, args) -> {
            assertEquals("createIndex", method.getName());
            BsonDocument keys = ((Bson) args[0]).toBsonDocument(Document.class, MongoClientSettings.getDefaultCodecRegistry());
            assertEquals(List.of("ownerUid", "sceneId", "groupId", "configId"), new ArrayList<>(keys.keySet()));
            var options = (com.mongodb.client.model.IndexOptions) args[1];
            assertEquals("capacity_open_world_spawns_identity_v1", options.getName());
            return options.getName();
        });
        DatabaseManager.ensureOpenWorldSpawnIndexes(spawnDatabase(collection));
    }

    private static MongoDatabase spawnDatabase(MongoCollection<?> collection) {
        return proxy(MongoDatabase.class, (ignored, method, args) -> {
            assertEquals("getCollection", method.getName());
            assertEquals("open_world_spawns", args[0]);
            return collection;
        });
    }

    private static MongoCommandException mongoIndexError(int code) {
        return new MongoCommandException(
                new BsonDocument()
                        .append("ok", new org.bson.BsonInt32(0))
                        .append("code", new org.bson.BsonInt32(code))
                        .append("errmsg", new org.bson.BsonString("index creation failed")),
                new ServerAddress("127.0.0.1", 27017));
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler));
    }
}
