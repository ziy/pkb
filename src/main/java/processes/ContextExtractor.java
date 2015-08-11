package processes;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.client.fluent.Request;

import com.google.common.base.Charsets;
import com.google.common.io.Files;

import de.l3s.boilerpipe.BoilerpipeExtractor;
import de.l3s.boilerpipe.extractors.CommonExtractors;

public class ContextExtractor {

  public static Pattern pattern = Pattern.compile("<a href=\"([^>\"]*)\" onmousedown=\"");

  public static void main(String[] args) throws InterruptedException, IOException {
    List<String> lines = Files.readLines(new File("data/query.tsv"), Charsets.UTF_8);
    Set<String> ids = new HashSet<>();
    for (String line : lines) {
      ids.add(line.split("\t")[0]);
    }
    BoilerpipeExtractor extractor = CommonExtractors.ARTICLE_EXTRACTOR;
    ExecutorService es = Executors.newFixedThreadPool(10);
    System.out.println(ids.size());
    DecimalFormat df = new DecimalFormat("00");
    for (String id : ids) {
      String googleHtml = Files.toString(new File("data/googlerp", id + ".html"), Charsets.UTF_8);
      Matcher matcher = pattern.matcher(googleHtml);
      int count = 0;
      while (matcher.find()) {
        count++;
        // check existence
        File docHtmlFile = new File("data/context", id + "-" + df.format(count) + ".html");
        File docTextFile = new File("data/context", id + "-" + df.format(count) + ".txt");
        if (docHtmlFile.exists() && docTextFile.exists()) {
          continue;
        }
        // get url
        String url = matcher.group(1);
        if (url.contains("wikihow") || url.contains("google")) {
          continue;
        }
        es.execute(() -> {
          System.out.println(id + " " + url);
          // download url
          try {
            String docHtml = Request.Get(url).connectTimeout(2000).socketTimeout(2000).execute()
                    .returnContent().asString();
            Files.write(docHtml, docHtmlFile, Charsets.UTF_8);
            String docText = extractor.getText(docHtml);
            Files.write(docText, docTextFile, Charsets.UTF_8);
          } catch (Exception e) {
            e.printStackTrace();
          }
        });
      }
    }
    es.shutdown();
    if (!es.awaitTermination(5, TimeUnit.MINUTES)) {
      System.out.println("Timeout occurs for one or some concept retrieval service.");
    }
  }

}
