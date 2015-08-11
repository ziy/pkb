package processes;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.util.List;

import com.google.common.base.Charsets;
import com.google.common.io.Files;

import types.Task;
import types.TaskExtractor;
import types.WikidumpArticle;
import types.WikidumpXmlAnalyzer;

public class WikiHowIdSummaryExtractor {

  public static void main(String[] args) throws IOException {
    WikidumpXmlAnalyzer analyzer = new WikidumpXmlAnalyzer("../wikihowcom-20141208-current.xml",
            null);
    List<WikidumpArticle> articles = analyzer.getArticles();
    TaskExtractor extractor = new TaskExtractor();
    extractor.addWikidumpArticles(articles);
    extractor.normalizeTasks();
    extractor.buildHierarchy();
    extractor.fillTaskIds();
    List<Task> tasks = extractor.getTasks();
    BufferedWriter bw = Files.newWriter(new File("data/wikihow-id-summary.tsv"), Charsets.UTF_8);
    for (Task task : tasks) {
      bw.write(task.getId() + "\t" + task.getSummary() + "\n");
    }
    bw.close();
  }

}
