package wrappers;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import java.io.File;
import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import com.google.common.base.Charsets;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Lists;
import com.google.common.io.Files;

import eval.Evaluator;

public class LibLinearWrapper {

  public static void trainTestLiblinear(File featureFile, List<String> ids, String kernal)
          throws IOException, InterruptedException {
    List<String> lines = Files.readLines(featureFile, Charsets.UTF_8);
    Set<String> features = lines.stream().flatMap(FeatureLineUtil::getFeatures).collect(toSet());
    int id = 1;
    Map<String, Integer> f2id = new HashMap<>();
    for (String f : features) {
      f2id.put(f, id++);
    }
    Set<String> labels = lines.stream().map(FeatureLineUtil::getLabel).collect(toSet());
    id = 0;
    BiMap<String, Integer> l2id = HashBiMap.create();
    for (String l : labels) {
      l2id.put(l, id++);
    }
    BiMap<Integer, String> id2l = l2id.inverse();
    int nfold = 10;
    int nPartition = (int) Math.ceil(lines.size() / (double) nfold);
    List<List<String>> cvLines = Lists.partition(lines, nPartition);
    List<List<String>> cvIds = Lists.partition(ids, nPartition);
    Evaluator evaluator = new Evaluator();
    for (int i = 0; i < nfold; i++) {
      // execute train
      File trainFile = File.createTempFile("liblinear-", ".train");
      List<String> trainLines = Stream
              .concat(cvLines.subList(0, i).stream(), cvLines.subList(i + 1, nfold).stream())
              .flatMap(List::stream).map(line -> convertToLibSvmString(line, f2id, l2id))
              .collect(toList());
      Files.write(String.join("\n", trainLines), trainFile, Charsets.UTF_8);
      File modelFile = File.createTempFile("liblinear-", ".model");
      List<String> trainCommand = Arrays.asList("liblinear-train", "-s", kernal,
              trainFile.getAbsolutePath(), modelFile.getAbsolutePath());
      Process trainProcess = new ProcessBuilder(trainCommand).redirectError(Redirect.INHERIT)
              .start();
      trainProcess.waitFor();
      // execute test
      File testFile = File.createTempFile("liblinear-", ".test");
      List<String> testLines = cvLines.get(i).stream()
              .map(line -> convertToLibSvmString(line, f2id, l2id)).collect(toList());
      Files.write(String.join("\n", testLines), testFile, Charsets.UTF_8);
      File predictFile = File.createTempFile("liblinear-", ".pred");
      List<String> testCommand = Arrays.asList("liblinear-predict", testFile.getAbsolutePath(),
              modelFile.getAbsolutePath(), predictFile.getAbsolutePath());
      Process testProcess = new ProcessBuilder(testCommand).redirectError(Redirect.INHERIT).start();
      testProcess.waitFor();
      // post process
      List<String> gsLabels = cvLines.get(i).stream()
              .map(line -> line.substring(line.lastIndexOf(" ") + 1).trim()).collect(toList());
      List<String> predictLabels = Files.readLines(predictFile, Charsets.UTF_8).stream()
              .map(String::trim).map(Integer::parseInt).map(id2l::get).collect(toList());
      List<String> testIds = cvIds.get(i);
      List<String> tokens = cvLines.get(i).stream()
              .map(line -> FeatureLineUtil.getFeature(line, "stem")).collect(toList());
      evaluator.addLabelListPairs(gsLabels, predictLabels, testIds, i);
      evaluator.addTokenListPairs(gsLabels, predictLabels, testIds, tokens, i);
    }
    evaluator.print();
  }

  public static String convertToLibSvmString(String line, Map<String, Integer> f2id,
          Map<String, Integer> l2id) {
    return l2id.get(FeatureLineUtil.getLabel(line)) + " " + FeatureLineUtil.getFeatures(line)
            .map(f2id::get).sorted().map(fid -> fid + ":1").collect(joining(" "));
  }

}
