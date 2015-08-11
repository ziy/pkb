package processes;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import com.google.common.base.Charsets;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import com.google.common.collect.Range;
import com.google.common.collect.RangeSet;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.TreeRangeSet;
import com.google.common.io.Files;

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

public class SearchTaskSuggestionFeatureExtractor {

  public static void main(String[] args) throws IOException {
    generateFeatureFiles("data/classify-sts-corpus.tsv", "data/classify-sts-mallet.features",
            "data/classify-sts-mallet.ids");
  }

  public static void generateFeatureFiles(String corpusFilepath, String featFilepath,
          String idsFilepath) throws IOException {
    // read file
    List<String> lines = Files.readLines(new File(corpusFilepath), Charsets.UTF_8);
    SetMultimap<String, String> text2locs = HashMultimap.create();
    SetMultimap<String, String> text2queries = HashMultimap.create();
    Map<String, String> text2id = new HashMap<>();
    lines.forEach(line -> {
      String[] segs = line.split("\t");
      String id = segs[0];
      String query = segs[1];
      String loc = segs[2];
      String text = segs[3];
      text2locs.put(text, loc);
      text2queries.put(text, toAlphabeticString(query));
      text2id.put(text, id);
    });
    BufferedWriter featWriter = Files.newWriter(new File(featFilepath), Charsets.UTF_8);
    BufferedWriter idWriter = Files.newWriter(new File(idsFilepath), Charsets.UTF_8);
    // create DF map
    Properties props = new Properties();
    props.setProperty("annotators", "tokenize, ssplit, pos, lemma");
    StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
    Multiset<String> df = HashMultiset.create();
    for (String text : text2locs.keySet()) {
      Annotation document = new Annotation(text);
      pipeline.annotate(document);
      document.get(SentencesAnnotation.class).stream()
              .map(sentence -> sentence.get(TokensAnnotation.class)).flatMap(List::stream)
              .map(token -> token.get(LemmaAnnotation.class)).distinct().forEach(df::add);
    }
    int docCount = text2locs.keySet().size();
    // generate features
    props.setProperty("annotators", "tokenize, ssplit, pos, lemma, ner, parse, sentiment");
    pipeline = new StanfordCoreNLP(props);
    TregexPattern npPattern = TregexPattern.compile("NP");
    TregexPattern vpPattern = TregexPattern.compile("VP");
    int count = 0;
    for (String text : text2locs.keySet()) {
      System.out.println(count++);
      // label
      int j = 0;
      BiMap<Integer, Integer> abpos2pos = HashBiMap.create();
      for (int i = 0; i < text.length(); i++) {
        if (Character.isLetterOrDigit(text.charAt(i))) {
          abpos2pos.put(j++, i);
        }
      }
      String abText = toAlphabeticString(text);
      RangeSet<Integer> iRanges = TreeRangeSet.create();
      for (String query : text2queries.get(text)) {
        int abBegin = -1;
        while ((abBegin = abText.indexOf(query, abBegin + 1)) >= 0) {
          int begin = abpos2pos.get(abBegin);
          int end = abpos2pos.get(abBegin + query.length() - 1) + 1;
          iRanges.add(Range.closed(begin, end));
        }
      }
      List<String> labels = new ArrayList<>();
      // nlp features
      Annotation document = new Annotation(text);
      pipeline.annotate(document);
      List<CoreMap> sentences = document.get(SentencesAnnotation.class);
      Multiset<String> tf = HashMultiset.create();
      List<Collection<String>> feats = new ArrayList<>();
      for (CoreMap sentence : sentences) {
        SetMultimap<Integer, String> begin2feats = HashMultimap.create();
        String prevLabel = "O";
        for (CoreLabel token : sentence.get(TokensAnnotation.class)) {
          int begin = token.beginPosition();
          // surface, stem, pos, ne
          String word = token.get(TextAnnotation.class);
          begin2feats.put(begin, "surface:" + word);
          String lemma = token.get(LemmaAnnotation.class);
          begin2feats.put(begin, "stem:" + lemma);
          tf.add(lemma);
          String pos = token.get(PartOfSpeechAnnotation.class);
          begin2feats.put(begin, "pos:" + pos);
          String ne = token.get(NamedEntityTagAnnotation.class);
          if (!ne.equals("O")) {
            begin2feats.put(begin, "ne");
          }
          // label
          String label;
          if (iRanges.encloses(Range.closedOpen(begin, token.endPosition()))) {
            if (prevLabel.equals("O")) {
              label = "B";
            } else {
              label = "I";
            }
          } else {
            label = "O";
          }
          labels.add(label);
          prevLabel = label;
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
        // location
        if (text2locs.get(text).contains("SUMMARY")) {
          feat.add("loc:summary");
        }
        if (text2locs.get(text).contains("EXPLANATION")) {
          feat.add("loc:explanation");
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
      for (int i = 0; i < feats.size(); i++) {
        featWriter.write(String.join(" ", feats.get(i)) + " " + labels.get(i) + "\n");
        idWriter.write(text2id.get(text) + "\n");
      }
    }
    featWriter.close();
    idWriter.close();
  }

  public static String toAlphabeticString(String string) {
    return string.chars().filter(i -> Character.isLetterOrDigit(i))
            .map(i -> Character.toLowerCase(i))
            .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
            .toString();
  }

}
