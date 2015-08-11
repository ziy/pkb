package processes;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.base.Charsets;
import com.google.common.collect.Sets;
import com.google.common.io.Files;

public class GoogleSuggestedQueryExtractor {

  private static Pattern googlePattern = Pattern.compile("<p class=\"_e4b\">(.*?)</p>");

  private static Pattern googleTitlePattern = Pattern
          .compile("<title>(.*) - Google Search</title>");

  public static void main(String[] args) throws IOException {
    File dir = new File("data/googlerp");
    BufferedWriter bw = Files.newWriter(new File("data/google-suggested-query.tsv"),
            Charsets.UTF_8);
    for (File f : dir.listFiles()) {
      List<String> lines = Files.readLines(f, Charsets.UTF_8);
      Set<String> queries = Sets.newHashSet();
      String id = Files.getNameWithoutExtension(f.getName());
      String query = null;
      for (String line : lines) {
        Matcher matcher = googlePattern.matcher(line);
        while (matcher.find()) {
          queries.add(matcher.group(1).replaceAll("<.*?>", ""));
        }
        matcher = googleTitlePattern.matcher(line);
        if (matcher.find()) {
          query = matcher.group(1);
        }
      }
      bw.write(id + "\t" + query + "\t" + String.join("\t", queries) + "\n");
    }
    bw.close();
  }

}
