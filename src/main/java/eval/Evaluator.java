package eval;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

import org.apache.commons.collections4.ListUtils;

import com.google.common.base.CharMatcher;
import com.google.common.base.Charsets;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multisets;
import com.google.common.collect.Sets;
import com.google.common.io.Resources;

public class Evaluator {

  private Map<Integer, Double> macroPrecisions;

  private Map<Integer, Double> macroRecalls;

  private Map<Integer, Double> macroF1s;

  private Map<Integer, Double> microPrecisions;

  private Map<Integer, Double> microRecalls;

  private Map<Integer, Double> microF1s;

  private Map<Integer, Double> rouge2s;

  private Map<Integer, Double> rougeS4s;

  private Map<Integer, Double> rougeSU4s;

  private Map<Integer, Double> rougeLs;

  private Set<String> stoplist;

  private CharMatcher matcher = CharMatcher.JAVA_LETTER_OR_DIGIT;

  private boolean useStoplist;

  public Evaluator() throws IOException {
    macroPrecisions = Collections.synchronizedMap(new HashMap<>());
    macroRecalls = Collections.synchronizedMap(new HashMap<>());
    macroF1s = Collections.synchronizedMap(new HashMap<>());
    microPrecisions = Collections.synchronizedMap(new HashMap<>());
    microRecalls = Collections.synchronizedMap(new HashMap<>());
    microF1s = Collections.synchronizedMap(new HashMap<>());
    rouge2s = Collections.synchronizedMap(new HashMap<>());
    rougeS4s = Collections.synchronizedMap(new HashMap<>());
    rougeSU4s = Collections.synchronizedMap(new HashMap<>());
    rougeLs = Collections.synchronizedMap(new HashMap<>());
    stoplist = new HashSet<>(Resources
            .readLines(Resources.getResource("stopwords-rouge-default.txt"), Charsets.UTF_8));
    useStoplist = true;
  }

  public void addTokenListPairs(List<String> gsLabels, List<String> predictLabels,
          List<String> groupIds, List<String> tokens, int cv) {
    // micro
    List<Double> idGroupRouge2 = new ArrayList<>();
    List<Double> idGroupRougeS4 = new ArrayList<>();
    List<Double> idGroupRougeSU4 = new ArrayList<>();
    List<Double> idGroupRougeL = new ArrayList<>();
    Map<String, Set<Integer>> idGroups = IntStream.range(0, groupIds.size()).boxed()
            .collect(groupingBy(i -> {
              String token = tokens.get(i).toLowerCase();
              return useStoplist && (stoplist.contains(token) || matcher.matchesNoneOf(token)) ? "_"
                      : groupIds.get(i);
            } , toSet()));
    idGroups.entrySet().stream().filter(entry -> !entry.getKey().equals("_")).forEach(entry -> {
      Set<Integer> idGroup = entry.getValue();
      List<String> groupTokens = subList(tokens, idGroup);
      List<String> groupGsLabels = subList(gsLabels, idGroup);
      Set<IntPair> groupGsSpans = getSpans(groupGsLabels);
      if (groupGsSpans.isEmpty())
        return;
      List<String> groupPredictLabels = subList(predictLabels, idGroup);
      Set<IntPair> groupPredictSpans = getSpans(groupPredictLabels);
      // rouge-2
      List<String> groupGsNgrams = getNGrams(groupGsSpans, groupTokens, 2);
      List<String> groupPredictNgrams = getNGrams(groupPredictSpans, groupTokens, 2);
      int tpNgram = Multisets.intersection(HashMultiset.create(groupGsNgrams),
              HashMultiset.create(groupPredictNgrams)).size();
      double r2prec = safeDivide(tpNgram, groupPredictNgrams.size(), 1);
      double r2reca = safeDivide(tpNgram, groupGsNgrams.size(), 1);
      idGroupRouge2.add(safeDivide(2 * r2prec * r2reca, r2prec + r2reca, 0));
      // rouge-s4
      List<String> groupGsSgrams = getSkipGrams(groupGsSpans, groupTokens, 4);
      List<String> groupPredictSgrams = getSkipGrams(groupPredictSpans, groupTokens, 4);
      int tpSgram = Multisets.intersection(HashMultiset.create(groupGsSgrams),
              HashMultiset.create(groupPredictSgrams)).size();
      double s4prec = safeDivide(tpSgram, groupPredictSgrams.size(), 1);
      double s4reca = safeDivide(tpSgram, groupGsSgrams.size(), 1);
      idGroupRougeS4.add(safeDivide(2 * s4prec * s4reca, s4prec + s4reca, 0));
      // rouge-su4
      List<String> groupGsUnigrams = getNGrams(groupGsSpans, groupTokens, 1);
      List<String> groupGsSUgrams = ImmutableList
              .copyOf(Iterables.concat(groupGsSgrams, groupGsUnigrams));
      List<String> groupPredictUnigrams = getNGrams(groupPredictSpans, groupTokens, 1);
      List<String> groupPredictSUgrams = ImmutableList
              .copyOf(Iterables.concat(groupPredictSgrams, groupPredictUnigrams));
      int tpSUgram = Multisets.intersection(HashMultiset.create(groupGsSUgrams),
              HashMultiset.create(groupPredictSUgrams)).size();
      double su4prec = safeDivide(tpSUgram, groupPredictSUgrams.size(), 1);
      double su4reca = safeDivide(tpSUgram, groupGsSUgrams.size(), 1);
      idGroupRougeSU4.add(safeDivide(2 * su4prec * su4reca, su4prec + su4reca, 0));
      // rouge-l
      int lcsLength = ListUtils.longestCommonSubsequence(groupGsUnigrams, groupPredictUnigrams)
              .size();
      s4prec = safeDivide(lcsLength, groupGsUnigrams.size(), 1);
      s4reca = safeDivide(lcsLength, groupPredictUnigrams.size(), 1);
      idGroupRougeL.add(safeDivide(2 * s4prec * s4reca, s4prec + s4reca, 0));
    });
    rouge2s.put(cv, average(idGroupRouge2));
    rougeS4s.put(cv, average(idGroupRougeS4));
    rougeSU4s.put(cv, average(idGroupRougeSU4));
    rougeLs.put(cv, average(idGroupRougeL));
  }

  private List<String> getSkipGrams(Collection<IntPair> spans, List<String> tokens,
          int maxSkipDistance) {
    List<String> ngrams = new ArrayList<>();
    for (IntPair span : spans) {
      for (int i = span.getK(); i < span.getV() - 1; i++) {
        for (int j = i + 1; j < Math.min(i + 1 + maxSkipDistance, span.getV()); j++) {
          ngrams.add(tokens.get(i) + tokens.get(j));
        }
      }
    }
    return ngrams;
  }

  private List<String> getNGrams(Collection<IntPair> spans, List<String> tokens, int n) {
    List<String> ngrams = new ArrayList<>();
    for (IntPair span : spans) {
      for (int i = span.getK(); i < span.getV() - (n - 1); i++) {
        ngrams.add(String.join("", tokens.subList(i, i + n)));
      }
    }
    return ngrams;
  }

  public void addLabelListPairs(List<String> gsLabels, List<String> predictLabels) {
    addLabelListPairs(gsLabels, predictLabels, ImmutableList.of(), 0);
  }

  public void addLabelListPairs(List<String> gsLabels, List<String> predictLabels,
          List<String> groupIds, int cv) {
    // macro
    Set<IntPair> gs = getSpans(gsLabels);
    if (gs.isEmpty())
      return;
    Set<IntPair> predict = getSpans(predictLabels);
    int tp = Sets.intersection(gs, predict).size();
    double prec = safeDivide(tp, predict.size(), 1);
    double reca = safeDivide(tp, gs.size(), 1);
    double f1 = safeDivide(2 * prec * reca, prec + reca, 0);
    macroPrecisions.put(cv, prec);
    macroRecalls.put(cv, reca);
    macroF1s.put(cv, f1);
    // micro
    List<Double> idGroupPrecisions = new ArrayList<>();
    List<Double> idGroupRecalls = new ArrayList<>();
    List<Double> idGroupF1s = new ArrayList<>();
    Map<String, Set<Integer>> idGroups = IntStream.range(0, groupIds.size()).boxed()
            .collect(groupingBy(groupIds::get, toSet()));
    idGroups.values().forEach(idGroup -> {
      Set<IntPair> groupGs = getSpans(subList(gsLabels, idGroup));
      if (groupGs.isEmpty())
        return;
      Set<IntPair> groupPredict = getSpans(subList(predictLabels, idGroup));
      int groupTp = Sets.intersection(groupGs, groupPredict).size();
      double groupPrec = safeDivide(groupTp, groupPredict.size(), 1);
      double groupReca = safeDivide(groupTp, groupGs.size(), 1);
      double grpupF1 = safeDivide(2 * groupPrec * groupReca, groupPrec + groupReca, 0);
      idGroupPrecisions.add(groupPrec);
      idGroupRecalls.add(groupReca);
      idGroupF1s.add(grpupF1);
    });
    microPrecisions.put(cv, average(idGroupPrecisions));
    microRecalls.put(cv, average(idGroupRecalls));
    microF1s.put(cv, average(idGroupF1s));
  }

  private static double average(Collection<Double> values) {
    return values.stream().mapToDouble(x -> x).average().orElse(0);
  }

  private static <T> List<T> subList(List<T> orig, Collection<Integer> indexes) {
    return indexes.stream().map(orig::get).collect(toList());
  }

  private static double safeDivide(double x, double y, double or) {
    return y == 0.0 ? or : x / y;
  }

  public static Set<IntPair> getSpans(List<String> labels) {
    int[] begins = IntStream.range(0, labels.size()).filter(i -> labels.get(i).startsWith("B"))
            .toArray();
    int[] ends = IntStream.range(0, begins.length).map(i -> getEnd(labels, begins[i] + 1, "I"))
            .toArray();
    return IntStream.range(0, begins.length).mapToObj(i -> new IntPair(begins[i], ends[i]))
            .collect(toSet());
  }

  private static int getEnd(List<String> labels, int begin, String labelI) {
    return IntStream.range(begin, labels.size()).filter(i -> !labels.get(i).startsWith(labelI))
            .findFirst().orElse(labels.size());
  }

  public List<Double> getMacroPrecisions() {
    return macroPrecisions.entrySet().stream().sorted(Comparator.comparing(Map.Entry::getKey))
            .map(Map.Entry::getValue).collect(toList());
  }

  public List<Double> getMacroRecalls() {
    return macroRecalls.entrySet().stream().sorted(Comparator.comparing(Map.Entry::getKey))
            .map(Map.Entry::getValue).collect(toList());
  }

  public List<Double> getMacroF1s() {
    return macroF1s.entrySet().stream().sorted(Comparator.comparing(Map.Entry::getKey))
            .map(Map.Entry::getValue).collect(toList());
  }

  public List<Double> getMicroPrecisions() {
    return microPrecisions.entrySet().stream().sorted(Comparator.comparing(Map.Entry::getKey))
            .map(Map.Entry::getValue).collect(toList());
  }

  public List<Double> getMicroRecalls() {
    return microRecalls.entrySet().stream().sorted(Comparator.comparing(Map.Entry::getKey))
            .map(Map.Entry::getValue).collect(toList());
  }

  public List<Double> getMicroF1s() {
    return microF1s.entrySet().stream().sorted(Comparator.comparing(Map.Entry::getKey))
            .map(Map.Entry::getValue).collect(toList());
  }

  public List<Double> getRouge2() {
    return rouge2s.entrySet().stream().sorted(Comparator.comparing(Map.Entry::getKey))
            .map(Map.Entry::getValue).collect(toList());
  }

  public List<Double> getRougeS4() {
    return rougeS4s.entrySet().stream().sorted(Comparator.comparing(Map.Entry::getKey))
            .map(Map.Entry::getValue).collect(toList());
  }

  public List<Double> getRougeSU4() {
    return rougeSU4s.entrySet().stream().sorted(Comparator.comparing(Map.Entry::getKey))
            .map(Map.Entry::getValue).collect(toList());
  }

  public List<Double> getRougeL() {
    return rougeLs.entrySet().stream().sorted(Comparator.comparing(Map.Entry::getKey))
            .map(Map.Entry::getValue).collect(toList());
  }

  public double getAverageMacroPrecision() {
    return average(macroPrecisions.values());
  }

  public double getAverageMacroRecall() {
    return average(macroRecalls.values());
  }

  public double getAverageMacroF1() {
    return average(macroF1s.values());
  }

  public double getAverageMicroPrecision() {
    return average(microPrecisions.values());
  }

  public double getAverageMicroRecall() {
    return average(microRecalls.values());
  }

  public double getAverageMicroF1() {
    return average(microF1s.values());
  }

  public double getAverageRouge2() {
    return average(rouge2s.values());
  }

  public double getAverageRougeS4() {
    return average(rougeS4s.values());
  }

  public double getAverageRougeSU4() {
    return average(rougeSU4s.values());
  }

  public double getAverageRougeL() {
    return average(rougeLs.values());
  }

  public void print() {
    System.out.println(getMacroPrecisions().stream().map(String::valueOf).collect(joining("\t")));
    System.out.println(getMacroRecalls().stream().map(String::valueOf).collect(joining("\t")));
    System.out.println(getMacroF1s().stream().map(String::valueOf).collect(joining("\t")));
    System.out.println(getMicroPrecisions().stream().map(String::valueOf).collect(joining("\t")));
    System.out.println(getMicroRecalls().stream().map(String::valueOf).collect(joining("\t")));
    System.out.println(getMicroF1s().stream().map(String::valueOf).collect(joining("\t")));
    System.out.println(getRouge2().stream().map(String::valueOf).collect(joining("\t")));
    System.out.println(getRougeS4().stream().map(String::valueOf).collect(joining("\t")));
    System.out.println(getRougeSU4().stream().map(String::valueOf).collect(joining("\t")));
    System.out.println(getRougeL().stream().map(String::valueOf).collect(joining("\t")));
    System.out.println("Macro P: " + getAverageMacroPrecision());
    System.out.println("Macro R: " + getAverageMacroRecall());
    System.out.println("Macro F1: " + getAverageMacroF1());
    System.out.println("Micro P: " + getAverageMicroPrecision());
    System.out.println("Micro R: " + getAverageMicroRecall());
    System.out.println("Micro F1: " + getAverageMicroF1());
    System.out.println("Rouge-2: " + getAverageRouge2());
    System.out.println("Rouge-S4: " + getAverageRougeS4());
    System.out.println("Rouge-SU4: " + getAverageRougeSU4());
    System.out.println("Rouge-L: " + getAverageRougeL());
  }

}
