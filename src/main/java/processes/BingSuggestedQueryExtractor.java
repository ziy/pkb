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

public class BingSuggestedQueryExtractor {

  private static Pattern bingSidePattern = Pattern
          .compile("<h2>Related searches</h2><ul class=\"b_vList\">(.*?)</ul>");

  private static Pattern bingBottomPattern = Pattern.compile(
          "<h2>Related searches for .*</h2><div class=\"b_rich\"><div class=\"b_vlist2col\"><ul>(.*?)</ul></div></div>");

  private static Pattern liPattern = Pattern.compile("<li>(.*?)</li>");

  private static Pattern bingTitlePattern = Pattern.compile("<title>(.*) - Bing</title>");

  public static void main(String[] args) throws IOException {
    File dir = new File("data/bingrp");
    BufferedWriter bw = Files.newWriter(new File("data/bing-suggested-query.tsv"), Charsets.UTF_8);
    for (File f : dir.listFiles()) {
      List<String> lines = Files.readLines(f, Charsets.UTF_8);
      Set<String> queries = Sets.newHashSet();
      String id = Files.getNameWithoutExtension(f.getName());
      String query = null;
      for (String line : lines) {
        Matcher blockMatcher = bingSidePattern.matcher(line);
        if (blockMatcher.find()) {
          Matcher itemMatcher = liPattern.matcher(blockMatcher.group(1));
          while (itemMatcher.find())
            queries.add(itemMatcher.group(1).replaceAll("<.*?>", ""));
        }
        blockMatcher = bingBottomPattern.matcher(line);
        if (blockMatcher.find()) {
          Matcher itemMatcher = liPattern.matcher(blockMatcher.group(1));
          while (itemMatcher.find())
            queries.add(itemMatcher.group(1).replaceAll("<.*?>", ""));
        }
        Matcher matcher = bingTitlePattern.matcher(line);
        if (matcher.find()) {
          query = matcher.group(1);
        }
      }
      bw.write(id + "\t" + query + "\t" + String.join("\t", queries) + "\n");
    }
    bw.close();
  }

}
