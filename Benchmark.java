import java.util.ArrayList;
import java.util.List;

public class Benchmark {
    public static void main(String[] args) {
        List<String> links = new ArrayList<>();
        for (int i = 0; i < 100000; i++) {
            links.add("link_" + i);
        }
        String baseUrl = "http://example.com";
        List<String> concatenatedLinks1 = new ArrayList<>();
        List<String> concatenatedLinks2 = new ArrayList<>();

        long t0 = System.currentTimeMillis();
        for (String link : links) {
            String concatenatedLink = baseUrl + "/" + link;
            concatenatedLinks1.add(concatenatedLink);
        }
        long t1 = System.currentTimeMillis();

        long t2 = System.currentTimeMillis();
        StringBuilder builder = new StringBuilder(baseUrl).append("/");
        int baseLen = builder.length();
        for (String link : links) {
            builder.append(link);
            concatenatedLinks2.add(builder.toString());
            builder.setLength(baseLen);
        }
        long t3 = System.currentTimeMillis();

        System.out.println("String template: " + (t1 - t0) + " ms");
        System.out.println("StringBuilder reused: " + (t3 - t2) + " ms");
    }
}
