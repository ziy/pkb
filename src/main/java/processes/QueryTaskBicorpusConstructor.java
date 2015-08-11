package processes;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.google.common.base.Charsets;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.SetMultimap;
import com.google.common.io.Files;
import com.vseravno.solna.SolnaException;

import types.Task;
import types.TaskExtractor;
import types.WikidumpArticle;
import types.WikidumpXmlAnalyzer;

public class QueryTaskBicorpusConstructor {

  public static void main(String[] args) throws SolnaException, IOException {
    SetMultimap<String, String> id2related = HashMultimap.create();
    Map<String, String> id2query = Maps.newHashMap();
    Map<String, String> id2logquery = Maps.newHashMap();
    Files.newReader(new File("data/log-matched-query.tsv"), Charsets.UTF_8).lines()
            .forEach(line -> {
              String[] segs = line.split("\t");
              String id = segs[0];
              String query = segs[1];
              List<String> related = Arrays.asList(segs).subList(2, segs.length);
              id2related.putAll(id, related);
              id2query.put(id, query);
              id2logquery.put(id, query);
            });
    Files.newReader(new File("data/google-suggested-query.tsv"), Charsets.UTF_8).lines()
            .forEach(line -> {
              String[] segs = line.split("\t");
              String id = segs[0];
              String query = segs[1];
              List<String> related = Arrays.asList(segs).subList(2, segs.length);
              id2related.putAll(id, related);
              id2query.put(id, query);
            });
    Files.newReader(new File("data/bing-suggested-query.tsv"), Charsets.UTF_8).lines()
            .forEach(line -> {
              String[] segs = line.split("\t");
              String id = segs[0];
              String query = segs[1];
              List<String> related = Arrays.asList(segs).subList(2, segs.length);
              id2related.putAll(id, related);
              id2query.put(id, query);
            });
    WikidumpXmlAnalyzer analyzer = new WikidumpXmlAnalyzer("data/wikihow-matched-task.xml", null);
    List<WikidumpArticle> articles = analyzer.getArticles();
    TaskExtractor extractor = new TaskExtractor();
    extractor.addWikidumpArticles(articles);
    extractor.normalizeTasks();
    extractor.buildHierarchy();
    extractor.fillTaskIds();
    List<Task> tasks = extractor.getTasks();
    BufferedWriter bw = Files.newWriter(new File("data/classify-sts-corpus.tsv"), Charsets.UTF_8);
    for (Task task : tasks) {
      String id = task.getId();
      if (id2logquery.containsKey(id)) {
        String summary = task.getSummary();
        String explanation = task.getExplanation();
        String query = id2logquery.get(id);
        if (toAlphabeticString(summary).contains(toAlphabeticString(query))) {
          bw.write(id + "\t" + query + "\tSUMMARY\t" + summary + "\n");
        }
        if (toAlphabeticString(explanation).contains(toAlphabeticString(query))) {
          bw.write(id + "\t" + query + "\tEXPLANATION\t" + explanation + "\n");
        }
      }
      List<Task> subtasks = Lists.newArrayList(task.getSubtasks());
      task.getSubtasks().stream().map(Task::getSubtasks).forEach(subtasks::addAll);
      for (Task subtask : subtasks) {
        String summary = subtask.getSummary();
        String explanation = subtask.getExplanation();
        for (String related : id2related.get(id)) {
          if (summary != null
                  && toAlphabeticString(summary).contains(toAlphabeticString(related))) {
            bw.write(id + "\t" + related + "\tSUMMARY\t" + summary + "\n");
          }
          if (toAlphabeticString(explanation).contains(toAlphabeticString(related))) {
            bw.write(id + "\t" + related + "\tEXPLANATION\t" + explanation + "\n");
          }
        }
      }
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
