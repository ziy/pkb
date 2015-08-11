package wrappers;

import static java.util.stream.Collectors.toList;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.PrintWriter;
import java.lang.ProcessBuilder.Redirect;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.google.common.io.Files;

import cc.mallet.fst.CRF;
import eval.Evaluator;

public class MalletWrapper {

  public static void trainTestMallet(File featureFile, List<String> ids, List<String> tokens)
          throws IOException, InterruptedException, ClassNotFoundException {
    // preparation
    int nfold = 10;
    List<String> lines = Files.readLines(featureFile, Charsets.UTF_8);
    int nPartition = (int) Math.ceil(lines.size() / (double) nfold);
    List<List<String>> cvLines = Lists.partition(lines, nPartition);
    List<List<String>> cvIds = Lists.partition(ids, nPartition);
    List<List<String>> cvTokens = Lists.partition(tokens, nPartition);
    Evaluator evaluator = new Evaluator();
    ExecutorService es = Executors.newCachedThreadPool();
    // ExecutorService es = Executors.newSingleThreadExecutor();
    // ExecutorService es = Executors.newFixedThreadPool(3);
    for (int i = 0; i < nfold; i++) {
      // prepare train
      File trainFile = File.createTempFile("crf-", ".train");
      List<String> trainLines = Stream
              .concat(cvLines.subList(0, i).stream(), cvLines.subList(i + 1, nfold).stream())
              .flatMap(List::stream).collect(toList());
      Files.write(String.join("\n", trainLines), trainFile, Charsets.UTF_8);
      File modelFile = File.createTempFile("crf-", ".model");
      List<String> trainCommand = Arrays.asList("java", "-cp", "mallet.jar:mallet-deps.jar",
              "cc.mallet.fst.SimpleTagger", "--train", "true", "--model-file",
              modelFile.getAbsolutePath(), "--weights", "sparse", "--fully-connected", "false",
              trainFile.getAbsolutePath());
      File commandDir = new File("mallet-2.0.7");
      // prepare test
      File testFile = File.createTempFile("crf-", ".test");
      List<String> testLines = cvLines.get(i);
      Files.write(String.join("\n", testLines), testFile, Charsets.UTF_8);
      File predictFile = File.createTempFile("crf-", ".pred");
      List<String> testCommand = Arrays.asList("java", "-cp", "mallet.jar:mallet-deps.jar",
              "cc.mallet.fst.SimpleTagger", "--model-file", modelFile.getAbsolutePath(),
              testFile.getAbsolutePath());
      List<String> testIds = cvIds.get(i);
      List<String> gsLabels = testLines.stream()
              .map(line -> line.substring(line.lastIndexOf(" ") + 1).trim()).collect(toList());
      List<String> testTokens = cvTokens.get(i);
      Integer cv = i;
      es.execute(() -> {
        try {
          // execute train
          System.out.println(String.join(" ", trainCommand));
          Process trainProcess = new ProcessBuilder(trainCommand).directory(commandDir)
                  .redirectError(Redirect.INHERIT).start();
          trainProcess.waitFor();
          // execute test
          System.out.println(String.join(" ", testCommand) + " > " + predictFile.getAbsolutePath());
          Process testProcess = new ProcessBuilder(testCommand).directory(commandDir)
                  .redirectOutput(Redirect.to(predictFile)).redirectError(Redirect.INHERIT).start();
          testProcess.waitFor();
          // post process
          List<String> predictLabels = Files.readLines(predictFile, Charsets.UTF_8).stream()
                  .map(String::trim).collect(toList());
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

  public static void createCrfModel(String featFilepath, String modelFilepath,
          String modelPrintFilepath)
                  throws IOException, InterruptedException, ClassNotFoundException {
    // preparation
    File featFile = new File(featFilepath);
    File modelFile = new File(modelFilepath);
    List<String> trainCommand = Arrays.asList("java", "-cp", "mallet.jar:mallet-deps.jar",
            "cc.mallet.fst.SimpleTagger", "--train", "true", "--model-file",
            modelFile.getAbsolutePath(), "--weights", "sparse", "--fully-connected", "false",
            featFile.getAbsolutePath());
    File commandDir = new File("mallet-2.0.7");
    // execute train
    Process trainProcess = new ProcessBuilder(trainCommand).directory(commandDir)
            .redirectError(Redirect.INHERIT).start();
    trainProcess.waitFor();
    // read model
    @SuppressWarnings("resource")
    ObjectInputStream ois = new ObjectInputStream(new FileInputStream(modelFile));
    CRF crf = (CRF) ois.readObject();
    PrintWriter out = new PrintWriter(modelPrintFilepath);
    crf.print(out);
    out.close();
  }

}
