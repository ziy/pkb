package processes;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

import java.io.File;
import java.io.IOException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.google.common.io.Files;

import eval.Evaluator;
import wrappers.FeatureLineUtil;
import wrappers.HmmTagger;
import wrappers.LibLinearWrapper;
import wrappers.MalletWrapper;

public class ClassficationExperiment {

  public static void main(String[] args)
          throws ClassNotFoundException, IOException, InterruptedException {
    // query
    if (args.length == 0 || args[0].equals("QUERY")) {
      System.out.println("QUERY");
      String featFilepath = "data/classify-sts-mallet.features";
      String idsFilepath = "data/classify-sts-mallet.ids";
//      experimentCrf(featFilepath, idsFilepath, false);
//      experimentHmm(featFilepath, idsFilepath);
//      experimentTfidf(featFilepath, idsFilepath);
//      experimentLiblinear(featFilepath, idsFilepath);
      String modelFilepath = "model/model-sts.crf";
      String modelPrintFilepath = "model/model-sts.txt";
      MalletWrapper.createCrfModel(featFilepath, modelFilepath, modelPrintFilepath);
    }
    // summary
    if (args.length == 0 || args[0].equals("SUMMARY")) {
      System.out.println("SUMMARY");
      String featFilepath = "data/classify-apkbc-mallet-summary.features";
      String idsFilepath = "data/classify-apkbc-mallet.ids";
//      experimentCrf(featFilepath, idsFilepath, false);
//      experimentHmm(featFilepath, idsFilepath);
//      experimentTfidf(featFilepath, idsFilepath);
//      experimentLiblinear(featFilepath, idsFilepath);
      String modelFilepath = "model/model-apkbc-summary.crf";
      String modelPrintFilepath = "model/model-apkbc-summary.txt";
      MalletWrapper.createCrfModel(featFilepath, modelFilepath, modelPrintFilepath);
    }
    // explanation
    if (args.length == 0 || args[0].equals("EXPLANATION")) {
      System.out.println("EXPLANATION");
      String featFilepath = "data/classify-apkbc-mallet-explanation.features";
      String idsFilepath = "data/classify-apkbc-mallet.ids";
//      experimentCrf(featFilepath, idsFilepath, false);
//      experimentHmm(featFilepath, idsFilepath);
//      experimentTfidf(featFilepath, idsFilepath);
//      experimentLiblinear(featFilepath, idsFilepath);
      String modelFilepath = "model/model-apkbc-explanation.crf";
      String modelPrintFilepath = "model/model-apkbc-explanation.txt";
      MalletWrapper.createCrfModel(featFilepath, modelFilepath, modelPrintFilepath);
    }
  }

  public static void experimentLiblinear(String featureFilepath, String idsFilepath)
          throws ClassNotFoundException, IOException, InterruptedException {
    File allFeatureFile = new File(featureFilepath);
    File idsFile = new File(idsFilepath);
    List<String> ids = Files.readLines(idsFile, Charsets.UTF_8);
    // svm
    System.out.println("svm");
    LibLinearWrapper.trainTestLiblinear(allFeatureFile, ids, "1");
    // log reg
    System.out.println("log-reg");
    LibLinearWrapper.trainTestLiblinear(allFeatureFile, ids, "6");
  }

  public static void experimentTfidf(String featureFilepath, String idsFilepath)
          throws ClassNotFoundException, IOException, InterruptedException {
    // tf-idf
    File allFeatureFile = new File(featureFilepath);
    File idsFile = new File(idsFilepath);
    List<String> lines = Files.readLines(allFeatureFile, Charsets.UTF_8);
    System.out.println("tfidf");
    int nfold = 10;
    int nPartition = (int) Math.ceil(lines.size() / (double) nfold);
    List<List<String>> cvLines = Lists.partition(lines, nPartition);
    List<String> ids = Files.readLines(idsFile, Charsets.UTF_8);
    List<List<String>> cvIds = Lists.partition(ids, nPartition);
    Evaluator evaluator = new Evaluator();
    for (int i = 0; i < nfold; i++) {
      // train
      List<String> trainLines = Stream
              .concat(cvLines.subList(0, i).stream(), cvLines.subList(i + 1, nfold).stream())
              .flatMap(List::stream).collect(toList());
      Map<Integer, Double> thresh2f1 = new HashMap<>();
      for (int thresh = 0; thresh < 21; thresh++) {
        List<String> gsLabels = trainLines.stream().map(line -> FeatureLineUtil.getLabel(line))
                .collect(toList());
        Integer immutableThresh = thresh;
        List<String> predictlabels = trainLines.stream()
                .map(line -> FeatureLineUtil.getFeature(line, "tfidf")).map(Integer::parseInt)
                .map(t -> t > immutableThresh ? "B" : "O").collect(toList());
        IntStream.range(1, predictlabels.size())
                .filter(j -> !predictlabels.get(j - 1).equals("O")
                        && predictlabels.get(j).equals("B"))
                .forEach(j -> predictlabels.set(j, "I"));
        Evaluator trainEvaluator = new Evaluator();
        trainEvaluator.addLabelListPairs(gsLabels, predictlabels);
        thresh2f1.put(thresh, evaluator.getAverageMacroF1());
      }
      int thresh = thresh2f1.entrySet().stream().max(Comparator.comparing(Map.Entry::getValue))
              .get().getKey();
      // test
      List<String> testLines = cvLines.get(i);
      List<String> predictLabels = testLines.stream()
              .map(line -> FeatureLineUtil.getFeature(line, "tfidf")).map(Integer::parseInt)
              .map(t -> t > thresh ? "B" : "O").collect(toList());
      IntStream.range(1, predictLabels.size()).filter(
              j -> !predictLabels.get(j - 1).equals("O") && predictLabels.get(j).equals("B"))
              .forEach(j -> predictLabels.set(j, "I"));
      // post-process
      List<String> testIds = cvIds.get(i);
      List<String> gsLabels = testLines.stream().map(line -> FeatureLineUtil.getLabel(line))
              .collect(toList());
      List<String> tokens = testLines.stream().map(line -> FeatureLineUtil.getFeature(line, "stem"))
              .collect(toList());
      evaluator.addLabelListPairs(gsLabels, predictLabels, testIds, i);
      evaluator.addTokenListPairs(gsLabels, predictLabels, testIds, tokens, i);
    }
    evaluator.print();
  }

  public static void experimentHmm(String featureFilepath, String idsFilepath)
          throws ClassNotFoundException, IOException, InterruptedException {
    // hmm
    System.out.println("hmm");
    File allFeatureFile = new File(featureFilepath);
    File idsFile = new File(idsFilepath);
    List<String> lines = Files.readLines(allFeatureFile, Charsets.UTF_8);
    // preparation
    List<String> tokens = lines.stream().map(line -> FeatureLineUtil.getFeature(line, "stem"))
            .collect(toList());
    int nfold = 10;
    int nPartition = (int) Math.ceil(lines.size() / (double) nfold);
    List<List<String>> cvTokens = Lists.partition(tokens, nPartition);
    lines = lines.stream().map(line -> FeatureLineUtil.getFeature(line, "surface") + " "
            + FeatureLineUtil.getLabel(line)).collect(toList());
    List<List<String>> cvLines = Lists.partition(lines, nPartition);
    List<String> ids = Files.readLines(idsFile, Charsets.UTF_8);
    List<List<String>> cvIds = Lists.partition(ids, nPartition);
    Evaluator evaluator = new Evaluator();
    ExecutorService es = Executors.newCachedThreadPool();
    // ExecutorService es = Executors.newSingleThreadExecutor();
    // ExecutorService es = Executors.newFixedThreadPool(4);
    for (int i = 0; i < nfold; i++) {
      // execute train, test
      File trainFile = File.createTempFile("hmm-", ".train");
      List<String> trainLines = Stream
              .concat(cvLines.subList(0, i).stream(), cvLines.subList(i + 1, nfold).stream())
              .flatMap(List::stream).collect(toList());
      Files.write(String.join("\n", trainLines), trainFile, Charsets.UTF_8);
      File testFile = File.createTempFile("hmm-", ".test");
      List<String> testLines = cvLines.get(i);
      Files.write(String.join("\n", testLines), testFile, Charsets.UTF_8);
      List<String> testIds = cvIds.get(i);
      List<String> gsLabels = testLines.stream().map(line -> FeatureLineUtil.getLabel(line))
              .collect(toList());
      List<String> testTokens = cvTokens.get(i);
      Integer cv = i;
      es.execute(() -> {
        List<String> predictLabels;
        try {
          predictLabels = HmmTagger.run(trainFile.getAbsolutePath(), testFile.getAbsolutePath());
          // post process
          evaluator.addLabelListPairs(gsLabels, predictLabels, testIds, cv);
          evaluator.addTokenListPairs(gsLabels, predictLabels, testIds, testTokens, cv);
        } catch (Exception e) {
          e.printStackTrace();
        }
      });
    }
    es.shutdown();
    es.awaitTermination(30, TimeUnit.MINUTES);
    evaluator.print();
  }

  public static void experimentCrf(String featureFilepath, String idsFilepath, boolean doLocation)
          throws ClassNotFoundException, IOException, InterruptedException {
    File allFeatureFile = new File(featureFilepath);
    File idsFile = new File(idsFilepath);
    List<String> ids = Files.readLines(idsFile, Charsets.UTF_8);
    List<String> lines = Files.readLines(allFeatureFile, Charsets.UTF_8);
    List<String> tokens = lines.stream().map(line -> FeatureLineUtil.getFeature(line, "stem"))
            .collect(toList());
    String featureText;
    File featureFile;
    // crf
    System.out.println("crf");
    MalletWrapper.trainTestMallet(allFeatureFile, ids, tokens);
    // pos
    System.out.println("pos");
    featureText = lines.stream()
            .map(line -> FeatureLineUtil.getFeatures(line)
                    .filter(f -> f.startsWith("pos:") || f.startsWith("-1/pos:")
                            || f.startsWith("+1/pos:"))
                    .collect(joining(" ")) + " " + FeatureLineUtil.getLabel(line))
            .collect(joining("\n"));
    featureFile = File.createTempFile("pos-", ".txt");
    Files.write(featureText, featureFile, Charsets.UTF_8);
    MalletWrapper.trainTestMallet(featureFile, ids, tokens);
    // parsing
    System.out.println("parsing");
    featureText = lines.stream()
            .map(line -> FeatureLineUtil.getFeatures(line)
                    .filter(f -> f.startsWith("dep:") || f.equals("np") || f.equals("vp")
                            || f.equals("ne"))
                    .collect(joining(" ")) + " " + FeatureLineUtil.getLabel(line))
            .collect(joining("\n"));
    featureFile = File.createTempFile("parsing-", ".txt");
    Files.write(featureText, featureFile, Charsets.UTF_8);
    MalletWrapper.trainTestMallet(featureFile, ids, tokens);
    if (doLocation) {
      // location
      System.out.println("location");
      featureText = lines.stream()
              .map(line -> FeatureLineUtil.getFeatures(line).filter(f -> f.startsWith("loc:"))
                      .collect(joining(" ")) + " " + FeatureLineUtil.getLabel(line))
              .collect(joining("\n"));
      featureFile = File.createTempFile("loc-", ".txt");
      Files.write(featureText, featureFile, Charsets.UTF_8);
      MalletWrapper.trainTestMallet(featureFile, ids, tokens);
    }
    // word
    System.out.println("word");
    featureText = lines.stream().map(line -> FeatureLineUtil.getFeatures(line)
            .filter(f -> f.startsWith("surface:") || f.startsWith("stem:") || f.startsWith("tfidf:")
                    || f.startsWith("-1/surface:") || f.startsWith("-1/stem:")
                    || f.startsWith("-1/tfidf:") || f.startsWith("+1/surface:")
                    || f.startsWith("+1/stem:") || f.startsWith("+1/tfidf:"))
            .collect(joining(" ")) + " " + FeatureLineUtil.getLabel(line)).collect(joining("\n"));
    featureFile = File.createTempFile("word-", ".txt");
    Files.write(featureText, featureFile, Charsets.UTF_8);
    MalletWrapper.trainTestMallet(featureFile, ids, tokens);
    // pos LOO
    System.out.println("pos loo");
    featureText = lines.stream()
            .map(line -> FeatureLineUtil.getFeatures(line)
                    .filter(f -> !f.startsWith("pos:") && !f.startsWith("-1/pos:")
                            && !f.startsWith("+1/pos:"))
                    .collect(joining(" ")) + " " + FeatureLineUtil.getLabel(line))
            .collect(joining("\n"));
    featureFile = File.createTempFile("pos-loo-", ".txt");
    Files.write(featureText, featureFile, Charsets.UTF_8);
    MalletWrapper.trainTestMallet(featureFile, ids, tokens);
    // parsing LOO
    System.out.println("parsing loo");
    featureText = lines.stream()
            .map(line -> FeatureLineUtil.getFeatures(line)
                    .filter(f -> !f.startsWith("dep:") && !f.equals("np") && !f.equals("vp")
                            && !f.equals("ne"))
                    .collect(joining(" ")) + " " + FeatureLineUtil.getLabel(line))
            .collect(joining("\n"));
    featureFile = File.createTempFile("parsing-loo-", ".txt");
    Files.write(featureText, featureFile, Charsets.UTF_8);
    MalletWrapper.trainTestMallet(featureFile, ids, tokens);
    if (doLocation) {
      // location LOO
      System.out.println("location loo");
      featureText = lines.stream()
              .map(line -> FeatureLineUtil.getFeatures(line).filter(f -> !f.startsWith("loc:"))
                      .collect(joining(" ")) + " " + FeatureLineUtil.getLabel(line))
              .collect(joining("\n"));
      featureFile = File.createTempFile("loc-loo-", ".txt");
      Files.write(featureText, featureFile, Charsets.UTF_8);
      MalletWrapper.trainTestMallet(featureFile, ids, tokens);
    }
    // word LOO
    System.out.println("word loo");
    featureText = lines.stream()
            .map(line -> FeatureLineUtil.getFeatures(line)
                    .filter(f -> !f.startsWith("surface:") && !f.startsWith("stem:")
                            && !f.startsWith("tfidf:") && !f.startsWith("-1/surface:")
                            && !f.startsWith("-1/stem:") && !f.startsWith("-1/tfidf:")
                            && !f.startsWith("+1/surface:") && !f.startsWith("+1/stem:")
                            && !f.startsWith("+1/tfidf:"))
                    .collect(joining(" ")) + " " + FeatureLineUtil.getLabel(line))
            .collect(joining("\n"));
    featureFile = File.createTempFile("word-loo-", ".txt");
    Files.write(featureText, featureFile, Charsets.UTF_8);
    MalletWrapper.trainTestMallet(featureFile, ids, tokens);
    // local
    System.out.println("local");
    featureText = lines.stream()
            .map(line -> FeatureLineUtil.getFeatures(line)
                    .filter(f -> !f.startsWith("-1/") && !f.startsWith("+1/")).collect(joining(" "))
                    + " " + FeatureLineUtil.getLabel(line))
            .collect(joining("\n"));
    featureFile = File.createTempFile("local-", ".txt");
    Files.write(featureText, featureFile, Charsets.UTF_8);
    MalletWrapper.trainTestMallet(featureFile, ids, tokens);
    // context
    System.out.println("context");
    featureText = lines.stream()
            .map(line -> FeatureLineUtil.getFeatures(line)
                    .filter(f -> f.startsWith("-1/") || f.startsWith("+1/")).collect(joining(" "))
                    + " " + FeatureLineUtil.getLabel(line))
            .collect(joining("\n"));
    featureFile = File.createTempFile("context-", ".txt");
    Files.write(featureText, featureFile, Charsets.UTF_8);
    MalletWrapper.trainTestMallet(featureFile, ids, tokens);
  }

}
