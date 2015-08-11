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
import java.net.URISyntaxException;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import org.apache.http.client.fluent.Request;
import org.xml.sax.SAXException;

import com.google.common.base.CharMatcher;
import com.google.common.base.Charsets;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.SetMultimap;
import com.google.common.io.Files;

import de.l3s.boilerpipe.BoilerpipeExtractor;
import de.l3s.boilerpipe.BoilerpipeProcessingException;
import de.l3s.boilerpipe.extractors.CommonExtractors;
import edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.CoreMap;
import eval.Evaluator;
import eval.IntPair;
import wrappers.FeatureLineUtil;

public class AutomaticProceduralKnowledgeBaseConstructor {

  public static void main(String[] args) throws IOException, InterruptedException,
          BoilerpipeProcessingException, SAXException, URISyntaxException {
    collectSuggestedQueries();
    downloadSearchResult();
    automaticProceduralKnowledgeBaseConstruction();
  }

  public static void automaticProceduralKnowledgeBaseConstruction()
          throws IOException, InterruptedException {
    // input
    File inputFile = new File("data/e2e-input.tsv");
    BufferedReader br = Files.newReader(inputFile, Charsets.UTF_8);
    Map<String, String> id2query = new HashMap<>();
    br.lines().map(line -> line.split("\t")).forEach(segs -> id2query.put(segs[0], segs[1]));
    // load suggested query uuids
    File suggestedQueriesUuidFile = new File("data/e2e-apkbc-suggested-query.tsv");
    Map<String, String> suggest2uuid = Files.newReader(suggestedQueriesUuidFile, Charsets.UTF_8)
            .lines().map(line -> line.split("\t")).collect(toMap(segs -> segs[1], segs -> segs[0]));
    SetMultimap<String, String> id2uuids = HashMultimap.create();
    File idSuggestedFile = new File("data/google-suggested-query.tsv");
    Files.newReader(idSuggestedFile, Charsets.UTF_8).lines().map(line -> line.split("\t"))
            .filter(segs -> id2query.containsKey(segs[0]))
            .forEach(segs -> Arrays.asList(segs).subList(2, segs.length).stream()
                    .filter(str -> !str.isEmpty())
                    .forEach(suggest -> id2uuids.put(segs[0], suggest2uuid.get(suggest))));
    // context
    File contextDir = new File("data/e2e-context");
    Properties props = new Properties();
    props.setProperty("annotators", "tokenize, ssplit");
    StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
    ListMultimap<String, CoreMap> iduuid2sentences = Multimaps
            .synchronizedListMultimap(ArrayListMultimap.create());
    ExecutorService es = Executors.newFixedThreadPool(7);
    for (Map.Entry<String, String> entry : id2uuids.entries()) {
      String id = entry.getKey();
      String uuid = entry.getValue();
      List<File> files = Arrays.stream(contextDir.listFiles()).filter(f -> {
        String name = f.getName();
        return name.startsWith(uuid + "-") && name.endsWith(".txt");
      }).sorted(Comparator.comparing(File::getName)).collect(toList());
      for (File file : files) {
        String c = Files.toString(file, Charsets.UTF_8).replaceAll("\\n+", ". ")
                .replace("...", ". ").replace(";", ". ").replaceAll("\\.[^\\p{ASCII}]+", ". ")
                .replaceAll("\\s+", " ").trim();
        if (c.length() == 0 || CharMatcher.ASCII.countIn(c) < c.length() * 0.9
                || CharMatcher.WHITESPACE.countIn(c) < c.length() * 0.1) {
          continue;
        }
        es.execute(() -> {
          Annotation document = new Annotation(c);
          pipeline.annotate(document);
          List<CoreMap> sentences = document.get(SentencesAnnotation.class);
          sentences.stream()
                  .filter(sentence -> sentence.toString().length() <= 500
                          && sentence.toString().length() >= 5)
                  .forEach(sentence -> iduuid2sentences.put(id + "/" + uuid, sentence));
        });
      }
    }
    es.shutdown();
    es.awaitTermination(1, TimeUnit.HOURS);
    System.out.println(iduuid2sentences.size());
    // generate pair files from context
    File corpusFile = File.createTempFile("e2e-apkbc-corpus-", ".tsv");
    BufferedWriter bw = Files.newWriter(corpusFile, Charsets.UTF_8);
    for (Map.Entry<String, Collection<CoreMap>> entry : iduuid2sentences.asMap().entrySet()) {
      String iduuid = entry.getKey();
      for (CoreMap sentence : entry.getValue()) {
        bw.write(String.join("\t", iduuid, "-", "x", sentence.toString(), "-") + "\n");
      }
    }
    bw.close();
    // generate features
    File iduuidsFile = File.createTempFile("e2e-apkbc-features-", ".ids");
    File allFeaturesFile = File.createTempFile("e2e-apkbc-mallet-", ".tsv");
    File tmpFile = File.createTempFile("e2e-apkbc-", ".tmp");
    AutomaticProceduralKnowledgeBaseConstructionFeatureExtractor.generateFeatureFiles(
            corpusFile.getAbsolutePath(), allFeaturesFile.getAbsolutePath(),
            tmpFile.getAbsolutePath(), iduuidsFile.getAbsolutePath());
    List<String> lines = Files.readLines(allFeaturesFile, Charsets.UTF_8);
    List<String> iduuids = Files.readLines(iduuidsFile, Charsets.UTF_8);
    Map<String, Set<Integer>> iduuidGroups = IntStream.range(0, iduuids.size()).boxed()
            .collect(groupingBy(iduuids::get, toSet()));
    BufferedWriter outputWriter = Files.newWriter(new File("data/e2e-apkbc-result.tsv"),
            Charsets.UTF_8);
    for (String iduuid : iduuidGroups.keySet()) {
      String featureLines = iduuidGroups.get(iduuid).stream().sorted().map(lines::get)
              .collect(joining("\n"));
      File featuresFile = File.createTempFile("e2e-apkbc-mallet-", ".features");
      Files.write(featureLines, featuresFile, Charsets.UTF_8);
      // test summary
      File summaryModelFile = new File("model/model-apkbc-summary.crf");
      List<String> summaryTestCommand = Arrays.asList("java", "-cp", "mallet.jar:mallet-deps.jar",
              "cc.mallet.fst.SimpleTagger", "--n-best", "50", "--model-file",
              summaryModelFile.getAbsolutePath(), featuresFile.getAbsolutePath());
      File commandDir = new File("mallet-2.0.7");
      File summaryPredictFile = File.createTempFile("e2e-apkbc-mallet-", ".predict");
      Process summaryTestProcess = new ProcessBuilder(summaryTestCommand).directory(commandDir)
              .redirectOutput(Redirect.to(summaryPredictFile)).redirectError(Redirect.INHERIT)
              .start();
      summaryTestProcess.waitFor();
      // test explanation
      File explanationModelFile = new File("model/model-apkbc-explanation.crf");
      List<String> explanationTestCommand = Arrays.asList("java", "-cp",
              "mallet.jar:mallet-deps.jar", "cc.mallet.fst.SimpleTagger", "--n-best", "50",
              "--model-file", explanationModelFile.getAbsolutePath(),
              featuresFile.getAbsolutePath());
      File explanationPredictFile = File.createTempFile("e2e-apkbc-", ".pred");
      Process explanationTestProcess = new ProcessBuilder(explanationTestCommand)
              .directory(commandDir).redirectOutput(Redirect.to(explanationPredictFile))
              .redirectError(Redirect.INHERIT).start();
      explanationTestProcess.waitFor();
      // digest
      String[] segs = iduuid.split("/");
      List<String> tokens = Files.readLines(featuresFile, Charsets.UTF_8).stream()
              .map(line -> FeatureLineUtil.getFeature(line, "surface")).collect(toList());
      outputWriter.write(segs[0] + "\t" + segs[1] + "\tORIGINAL\t" + id2query.get(segs[0]) + "\n");
      List<String[]> summaryLabels = Files.readLines(summaryPredictFile, Charsets.UTF_8).stream()
              .map(line -> line.split(" ")).collect(toList());
      int summaryNbest = summaryLabels.get(0).length;
      List<IntPair> summarySpans = IntStream.range(0, summaryNbest)
              .mapToObj(i -> summaryLabels.stream().filter(l -> l.length >= summaryNbest)
                      .map(l -> l[i]).collect(toList()))
              .map(Evaluator::getSpans).flatMap(Set::stream).distinct().collect(toList());
      summarySpans.stream().map(span -> String.join(" ", tokens.subList(span.getK(), span.getV())))
              .distinct().limit(1).forEach(t -> {
                try {
                  outputWriter.write(segs[0] + "\t" + segs[1] + "\tSUGGESTED SUMMARY\t" + t + "\n");
                } catch (Exception e) {
                  e.printStackTrace();
                }
              });
      List<String[]> explanationLabels = Files.readLines(explanationPredictFile, Charsets.UTF_8)
              .stream().map(line -> line.split(" ")).collect(toList());
      int explanationNbest = explanationLabels.get(0).length;
      List<IntPair> explanationSpans = IntStream.range(0, explanationNbest)
              .mapToObj(i -> explanationLabels.stream().filter(l -> l.length >= explanationNbest)
                      .map(l -> l[i]).collect(toList()))
              .map(Evaluator::getSpans).flatMap(Set::stream).distinct().collect(toList());
      explanationSpans.stream()
              .map(span -> String.join(" ", tokens.subList(span.getK(), span.getV()))).distinct()
              .limit(1).forEach(t -> {
                try {
                  outputWriter
                          .write(segs[0] + "\t" + segs[1] + "\tSUGGESTED EXPLANATION\t" + t + "\n");
                } catch (Exception e) {
                  e.printStackTrace();
                }
              });
    }
    outputWriter.close();
  }

  public static void downloadSearchResult() throws IOException, BoilerpipeProcessingException,
          SAXException, URISyntaxException, InterruptedException {
    List<String> lines = Files.readLines(new File("data/e2e-apkbc-suggested-query.tsv"),
            Charsets.UTF_8);
    Set<String> ids = new HashSet<>();
    for (String line : lines) {
      ids.add(line.split("\t")[0]);
    }
    Pattern pattern = Pattern.compile("<a href=\"([^>\"]*)\" onmousedown=\"");
    BoilerpipeExtractor extractor = CommonExtractors.ARTICLE_EXTRACTOR;
    ExecutorService es = Executors.newFixedThreadPool(10);
    System.out.println(ids.size());
    DecimalFormat df = new DecimalFormat("00");
    for (String id : ids) {
      String googleHtml = Files.toString(new File("data/e2e-googlerp", id + ".html"),
              Charsets.UTF_8);
      Matcher matcher = pattern.matcher(googleHtml);
      int count = 0;
      while (matcher.find()) {
        count++;
        // check existence
        File docHtmlFile = new File("data/e2e-context", id + "-" + df.format(count) + ".html");
        File docTextFile = new File("data/e2e-context", id + "-" + df.format(count) + ".txt");
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
    if (!es.awaitTermination(5, TimeUnit.SECONDS)) {
      System.out.println("Timeout occurs for one or some concept retrieval service.");
    }
  }

  public static void collectSuggestedQueries() throws IOException {
    Set<String> ids = Files.newReader(new File("data/e2e-input.tsv"), Charsets.UTF_8).lines()
            .map(line -> line.split("\t")[0]).collect(toSet());
    Set<String> queries = Files
            .newReader(new File("data/google-suggested-query.tsv"), Charsets.UTF_8).lines()
            .map(line -> line.split("\t")).filter(segs -> ids.contains(segs[0]))
            .flatMap(segs -> Arrays.asList(segs).subList(2, segs.length).stream())
            .filter(query -> !query.isEmpty()).collect(toSet());
    System.out.println(queries.size());
    BufferedWriter bw = Files.newWriter(new File("data/e2e-apkbc-suggested-query.tsv"),
            Charsets.UTF_8);
    for (String query : queries) {
      bw.write(UUID.randomUUID().toString() + "\t" + query + "\n");
    }
    bw.close();
  }

}
