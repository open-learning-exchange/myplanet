import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import java.util.ArrayList;
import java.util.List;

public class Benchmark {
    public static void main(String[] args) {
        JsonArray arr = new JsonArray();
        for (int i = 0; i < 100000; i++) {
            JsonObject element = new JsonObject();
            JsonObject doc = new JsonObject();
            doc.addProperty("_id", "id_" + i);
            element.add("doc", doc);
            arr.add(element);
        }

        // Warmup
        for (int i = 0; i < 20; i++) {
            testOld(arr);
            testNew(arr);
        }

        long t1 = System.currentTimeMillis();
        for (int i = 0; i < 100; i++) {
            testOld(arr);
        }
        long t2 = System.currentTimeMillis();
        for (int i = 0; i < 100; i++) {
            testNew(arr);
        }
        long t3 = System.currentTimeMillis();

        System.out.println("=========================================");
        System.out.println("Old time: " + (t2 - t1) + " ms");
        System.out.println("New time: " + (t3 - t2) + " ms");
        System.out.println("=========================================");
    }

    private static void testOld(JsonArray arr) {
        List<JsonObject> docs = new ArrayList<>();
        for (JsonElement j : arr) {
            JsonObject jsonDoc = j.getAsJsonObject();
            JsonObject doc = jsonDoc.getAsJsonObject("doc");
            docs.add(doc != null ? doc : jsonDoc);
        }
    }

    private static void testNew(JsonArray arr) {
        int size = arr.size();
        List<JsonObject> docs = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            JsonObject jsonDoc = arr.get(i).getAsJsonObject();
            JsonObject doc = jsonDoc.getAsJsonObject("doc");
            docs.add(doc != null ? doc : jsonDoc);
        }
    }
}
