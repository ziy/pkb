package processes;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import com.google.common.base.Charsets;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.SetMultimap;
import com.google.common.io.Files;

public class QueryLogMatcher {

  public static DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd kk:mm:ss");

  public static void main(String[] args) throws IOException {
    Map<String, String> string2id = Maps.newHashMap();
    List<String> lines = Files.readLines(new File("data/wikihow-id-summary.tsv"), Charsets.UTF_8);
    for (String line : lines) {
      String[] segs = line.split("\t");
      string2id.put(toAlphabeticString(segs[1]), segs[0]);
    }
    BufferedReader br = new BufferedReader(new InputStreamReader(
            new GZIPInputStream(new FileInputStream("../user-ct-test-collection.txt.gz")),
            Charsets.UTF_8));
    String line;
    LocalDateTime taskTimeout = LocalDateTime.MIN;
    String userId = null;
    SetMultimap<String, String> task2subs = HashMultimap.create();
    String task = null;
    boolean flag = false;
    while ((line = br.readLine()) != null) {
      String[] segs = line.split("\t", 4);
      String query = segs[1].toLowerCase();
      if (!query.contains(" ")) {
        continue;
      }
      String string = toAlphabeticString(query);
      if (string2id.containsKey(string)) {
        task = string2id.get(string) + "\t" + segs[1];
        taskTimeout = LocalDateTime.parse(segs[2], dtf).plusMinutes(30);
        userId = segs[0];
        flag = true;
        System.out.println(string2id.get(string));
      } else if (flag && userId.equals(segs[0])
              && LocalDateTime.parse(segs[2], dtf).isBefore(taskTimeout)) {
        task2subs.put(task, segs[1]);
      } else {
        flag = false;
      }
    }
    br.close();
    BufferedWriter bw = Files.newWriter(new File("data/log-matched-query.tsv"), Charsets.UTF_8);
    for (String t : task2subs.keySet()) {
      bw.write(t + "\t" + String.join("\t", task2subs.get(t)) + "\n");
    }
    bw.close();
  }

  public static String toAlphabeticString(String string) {
    return string.chars().filter(i -> Character.isLetterOrDigit(i))
            .map(i -> Character.toLowerCase(i))
            .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
            .toString();
  }

}
