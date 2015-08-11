package processes;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathExpressionException;

import org.xml.sax.SAXException;

import com.google.common.base.Charsets;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Multiset;
import com.google.common.collect.Range;
import com.google.common.collect.RangeSet;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.TreeRangeSet;
import com.google.common.io.Files;

import de.l3s.boilerpipe.BoilerpipeProcessingException;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.CoreAnnotations.LemmaAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.NamedEntityTagAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.PartOfSpeechAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TextAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TokensAnnotation;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations.BasicDependenciesAnnotation;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TypedDependency;
import edu.stanford.nlp.trees.TreeCoreAnnotations.TreeAnnotation;
import edu.stanford.nlp.trees.tregex.TregexMatcher;
import edu.stanford.nlp.trees.tregex.TregexPattern;
import edu.stanford.nlp.util.CoreMap;

public class AutomaticProceduralKnowledgeBaseConstructionFeatureExtractor {

  public static void main(String[] args) throws IOException, InterruptedException,
          XPathExpressionException, ParserConfigurationException, SAXException,
          ClassNotFoundException, URISyntaxException, BoilerpipeProcessingException {
    generateFeatureFiles("data/classify-apkbc-corpus.tsv",
            "data/classify-apkbc-mallet-summary.features",
            "data/classify-apkbc-mallet-explanation.features", "data/classify-apkbc-mallet.ids");
  }

  public static void generateFeatureFiles(String corpusFilepath, String summaryFeatFilepath,
          String explanationFeatFilepath, String idsFilepath)
                  throws IOException, InterruptedException {
    // read file
    List<String> lines = Files.readLines(new File(corpusFilepath), Charsets.UTF_8);
    ListMultimap<String, CoreMap> text2sentences = Multimaps
            .synchronizedListMultimap(ArrayListMultimap.create());
    Properties props = new Properties();
    props.setProperty("annotators", "tokenize, ssplit, pos, lemma, ner, parse");
    StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
    ExecutorService es = Executors.newFixedThreadPool(15);
    // ExecutorService es = Executors.newSingleThreadExecutor();
    lines.stream().map(line -> line.split("\t")[3]).distinct()
            .sorted(Comparator.comparing(String::length, Comparator.reverseOrder()))
            .forEach(text -> {
              es.execute(() -> {
                Annotation document = new Annotation(text);
                pipeline.annotate(document);
                List<CoreMap> sentences = document.get(SentencesAnnotation.class);
                text2sentences.putAll(text, sentences);
                System.out.println("done " + text2sentences.size() + "/" + lines.size());
              });
            });
    es.shutdown();
    es.awaitTermination(10, TimeUnit.HOURS);
    // create DF map
    Multiset<String> df = HashMultiset.create();
    for (Collection<CoreMap> sentences : text2sentences.asMap().values()) {
      sentences.stream().map(sentence -> sentence.get(TokensAnnotation.class)).flatMap(List::stream)
              .map(token -> token.get(LemmaAnnotation.class)).distinct().forEach(df::add);
    }
    int docCount = text2sentences.keySet().size();
    BufferedWriter summaryFeatWriter = Files.newWriter(new File(summaryFeatFilepath),
            Charsets.UTF_8);
    BufferedWriter explanationFeatWriter = Files.newWriter(new File(explanationFeatFilepath),
            Charsets.UTF_8);
    BufferedWriter idWriter = Files.newWriter(new File(idsFilepath), Charsets.UTF_8);
    // generate features
    TregexPattern npPattern = TregexPattern.compile("NP");
    TregexPattern vpPattern = TregexPattern.compile("VP");
    int count = 0;
    for (String line : lines) {
      System.out.println(count++ + "/" + lines.size());
      String[] segs = line.split("\t");
      String query = segs[2];
      String loc = segs[1];
      String text = segs[3];
      String id = segs[0];
      boolean label = Boolean.parseBoolean(segs[4]);
      List<CoreMap> sentences = text2sentences.get(text);
      // label
      int j = 0;
      BiMap<Integer, Integer> abpos2pos = HashBiMap.create();
      for (int i = 0; i < text.length(); i++) {
        if (Character.isLetterOrDigit(text.charAt(i))) {
          abpos2pos.put(j++, i);
        }
      }
      String abText = toAlphabeticString(text);
      RangeSet<Integer> gsSummaryRanges = TreeRangeSet.create();
      if (label && loc.equals("SUMMARY")) {
        String abQuery = toAlphabeticString(query);
        int abBegin = -1;
        while ((abBegin = abText.indexOf(abQuery, abBegin + 1)) >= 0) {
          int begin = abpos2pos.get(abBegin);
          int end = abpos2pos.get(abBegin + abQuery.length() - 1) + 1;
          gsSummaryRanges.add(Range.closedOpen(begin, end));
        }
      }
      boolean gsExplanation = loc.equals("EXPLANATION") && label;
      List<String> summaryLabels = new ArrayList<>();
      List<String> explanationLabels = new ArrayList<>();
      // nlp features
      Multiset<String> tf = HashMultiset.create();
      List<Collection<String>> feats = new ArrayList<>();
      List<String> contextLemmas = new ArrayList<>();
      for (CoreMap sentence : sentences) {
        String prevSummaryLabel = "O";
        String prevExplanationLabel = "O";
        SetMultimap<Integer, String> begin2feats = HashMultimap.create();
        for (CoreLabel token : sentence.get(TokensAnnotation.class)) {
          int begin = token.beginPosition();
          // surface, stem, pos, ne
          String word = token.get(TextAnnotation.class);
          begin2feats.put(begin, "surface:" + word);
          String lemma = token.get(LemmaAnnotation.class);
          begin2feats.put(begin, "stem:" + lemma);
          tf.add(lemma);
          contextLemmas.add(lemma);
          String pos = token.get(PartOfSpeechAnnotation.class);
          begin2feats.put(begin, "pos:" + pos);
          String ne = token.get(NamedEntityTagAnnotation.class);
          if (!ne.equals("O")) {
            begin2feats.put(begin, "ne");
          }
          // summary label
          String summaryLabel;
          if (gsSummaryRanges.encloses(Range.closedOpen(begin, token.endPosition()))) {
            if (prevSummaryLabel.equals("O")) {
              summaryLabel = "B";
            } else {
              summaryLabel = "I";
            }
          } else {
            summaryLabel = "O";
          }
          summaryLabels.add(summaryLabel);
          prevSummaryLabel = summaryLabel;
          // explanation label
          String explanationLabel;
          if (gsExplanation) {
            if (prevExplanationLabel.equals("O")) {
              explanationLabel = "B";
            } else {
              explanationLabel = "I";
            }
          } else {
            explanationLabel = "O";
          }
          explanationLabels.add(explanationLabel);
          prevExplanationLabel = explanationLabel;
        }
        // np
        Tree tree = sentence.get(TreeAnnotation.class);
        TregexMatcher matcher = npPattern.matcher(tree);
        while (matcher.findNextMatchingNode()) {
          matcher.getMatch().getLeaves().stream().map(node -> (CoreLabel) node.label())
                  .map(CoreLabel::beginPosition).forEach(pos -> begin2feats.put(pos, "np"));
        }
        // vp
        matcher = vpPattern.matcher(tree);
        while (matcher.findNextMatchingNode()) {
          matcher.getMatch().getLeaves().stream().map(node -> (CoreLabel) node.label())
                  .map(CoreLabel::beginPosition).forEach(pos -> begin2feats.put(pos, "vp"));
        }
        // dep
        SemanticGraph basicDeps = sentence.get(BasicDependenciesAnnotation.class);
        Collection<TypedDependency> typedDeps = basicDeps.typedDependencies();
        typedDeps.stream().forEach(typedDep -> begin2feats.put(typedDep.dep().beginPosition(),
                "dep:" + typedDep.reln().toString()));
        // finalize
        begin2feats.asMap().entrySet().stream().sorted(Comparator.comparing(Map.Entry::getKey))
                .map(Map.Entry::getValue).forEach(feats::add);
      }
      for (Collection<String> feat : feats) {
        // tf-idf
        String stem = feat.stream().filter(f -> f.startsWith("stem:")).map(f -> f.substring(5))
                .findAny().orElse(null);
        if (stem != null) {
          double tfidf = tf.count(stem) * Math.log(docCount / (1.0 + df.count(stem)));
          feat.add("tfidf:" + (tfidf > 20 ? 20 : (int) tfidf));
        }
      }
      // context -1
      for (int i = 1; i < feats.size(); i++) {
        Collection<String> feat = feats.get(i);
        feats.get(i - 1).stream()
                .filter(f -> f.startsWith("surface:") || f.startsWith("stem:")
                        || f.startsWith("tfidf:") || f.startsWith("pos:"))
                .forEach(f -> feat.add("-1/" + f));
      }
      feats.get(0).add("-1/NULL");
      // context +1
      for (int i = 0; i < feats.size() - 1; i++) {
        Collection<String> feat = feats.get(i);
        feats.get(i + 1).stream()
                .filter(f -> f.startsWith("surface:") || f.startsWith("stem:")
                        || f.startsWith("tfidf:") || f.startsWith("pos:"))
                .forEach(f -> feat.add("+1/" + f));
      }
      feats.get(feats.size() - 1).add("+1/NULL");
      // write
      for (int i = 0; i < Math.min(feats.size(), summaryLabels.size()); i++) {
        summaryFeatWriter.write(String.join(" ", feats.get(i)) + " " + summaryLabels.get(i) + "\n");
        explanationFeatWriter
                .write(String.join(" ", feats.get(i)) + " " + explanationLabels.get(i) + "\n");
        idWriter.write(id + "\n");
      }
    }
    summaryFeatWriter.close();
    explanationFeatWriter.close();
    idWriter.close();
  }

  public static String toAlphabeticString(String string) {
    return string.chars().filter(i -> Character.isLetterOrDigit(i))
            .map(i -> Character.toLowerCase(i))
            .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
            .toString();
  }
  
}
