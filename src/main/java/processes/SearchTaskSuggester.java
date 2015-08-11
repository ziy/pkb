package processes;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.IntStream;

import com.google.common.base.Charsets;
import com.google.common.base.Strings;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.io.Files;

import eval.Evaluator;
import eval.IntPair;
import types.Task;
import types.TaskExtractor;
import types.WikidumpArticle;
import types.WikidumpXmlAnalyzer;
import wrappers.FeatureLineUtil;

public class SearchTaskSuggester {

  public static void main(String[] args) throws IOException, InterruptedException {
    // input
    File inputFile = new File("data/e2e-input.tsv");
    BufferedReader br = Files.newReader(inputFile, Charsets.UTF_8);
    BiMap<String, String> query2id = HashBiMap.create();
    br.lines().map(line -> line.split("\t")).forEach(segs -> query2id.put(segs[1], segs[0]));
    // wikihow
    WikidumpXmlAnalyzer analyzer = new WikidumpXmlAnalyzer("data/wikihow-matched-task.xml", null);
    List<WikidumpArticle> articles = analyzer.getArticles();
    TaskExtractor extractor = new TaskExtractor();
    extractor.addWikidumpArticles(articles);
    extractor.normalizeTasks();
    extractor.buildHierarchy();
    extractor.fillTaskIds();
    List<Task> tasks = extractor.getTasks();
    Map<String, Task> id2task = tasks.stream().collect(toMap(Task::getId, Function.identity()));
    // generate pair files from wikihow article
    File corpusFile = File.createTempFile("e2e-sts-corpus-", ".tsv");
    BufferedWriter allFeaturesWriter = Files.newWriter(corpusFile, Charsets.UTF_8);
    for (String query : query2id.keySet()) {
      String id = query2id.get(query);
      Task task = id2task.get(id);
      for (Task subtask : task.getSubtasks()) {
        String summary = subtask.getSummary();
        if (!(summary = Strings.nullToEmpty(summary).trim().replaceAll("\\s+", " ")).isEmpty())
          allFeaturesWriter.write(String.join("\t", id, "x", "SUMMARY", summary) + "\n");
        String explanation = subtask.getExplanation();
        if (!(explanation = Strings.nullToEmpty(explanation).trim().replaceAll("\\s+", " "))
                .isEmpty())
          allFeaturesWriter.write(String.join("\t", id, "x", "EXPLANATION", explanation) + "\n");
      }
    }
    allFeaturesWriter.close();
    // generate features
    File allFeaturesFile = File.createTempFile("e2e-sts-mallet-", ".features");
    File idsFile = File.createTempFile("e2e-sts-mallet-", ".ids");
    SearchTaskSuggestionFeatureExtractor.generateFeatureFiles(corpusFile.getAbsolutePath(),
            allFeaturesFile.getAbsolutePath(), idsFile.getAbsolutePath());
    List<String> lines = Files.readLines(allFeaturesFile, Charsets.UTF_8);
    List<String> ids = Files.readLines(idsFile, Charsets.UTF_8);
    Map<String, Set<Integer>> idGroups = IntStream.range(0, ids.size()).boxed()
            .collect(groupingBy(ids::get, toSet()));
    File modelFile = new File("model/model-sts.crf");
    File commandDir = new File("mallet-2.0.7");
    BufferedWriter outputWriter = Files.newWriter(new File("data/e2e-sts-result.tsv"),
            Charsets.UTF_8);
    for (String id : idGroups.keySet()) {
      // test
      String featureLines = idGroups.get(id).stream().sorted().map(lines::get)
              .collect(joining("\n"));
      File featuresFile = File.createTempFile("e2e-sts-mallet-", ".features");
      Files.write(featureLines, featuresFile, Charsets.UTF_8);
      List<String> testCommand = Arrays.asList("java", "-cp", "mallet.jar:mallet-deps.jar",
              "cc.mallet.fst.SimpleTagger", "--n-best", "500", "--model-file",
              modelFile.getAbsolutePath(), featuresFile.getAbsolutePath());
      File predictFile = File.createTempFile("e2e-sts-mallet-", ".predict");
      Process testProcess = new ProcessBuilder(testCommand).directory(commandDir)
              .redirectOutput(Redirect.to(predictFile)).redirectError(Redirect.INHERIT).start();
      testProcess.waitFor();
      // digest
      List<String> tokens = Files.readLines(featuresFile, Charsets.UTF_8).stream()
              .map(line -> FeatureLineUtil.getFeature(line, "surface")).collect(toList());
      List<String[]> labels = Files.readLines(predictFile, Charsets.UTF_8).stream()
              .map(line -> line.split(" ")).collect(toList());
      int nbest = labels.get(0).length;
      List<IntPair> spans = IntStream.range(0, nbest)
              .mapToObj(i -> labels.stream().filter(l -> l.length >= nbest).map(l -> l[i])
                      .collect(toList()))
              .map(Evaluator::getSpans).flatMap(Set::stream).distinct().collect(toList());
      outputWriter.write(id + "\tORIGINAL\t" + query2id.inverse().get(id) + "\n");
      spans.stream().map(span -> String.join(" ", tokens.subList(span.getK(), span.getV())))
              .map(String::toLowerCase).distinct().limit(8).forEach(t -> {
                try {
                  outputWriter.write(id + "\tSUGGESTED\t" + t + "\n");
                } catch (Exception e) {
                  e.printStackTrace();
                }
              });
    }
    outputWriter.close();
  }

}
