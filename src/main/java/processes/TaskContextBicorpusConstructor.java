package processes;

import static java.util.stream.Collectors.toSet;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import com.google.common.base.CharMatcher;
import com.google.common.base.Charsets;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Sets;
import com.google.common.collect.Table;
import com.google.common.collect.Table.Cell;
import com.google.common.io.Files;

import edu.stanford.nlp.ling.CoreAnnotations.LemmaAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TokensAnnotation;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.CoreMap;

public class TaskContextBicorpusConstructor {

  public static void main(String[] args) throws IOException {
    List<String> lines = Files.readLines(new File("data/classify-sts-corpus.tsv"), Charsets.UTF_8);
    Table<String, String, String> id2desc2loc = HashBasedTable.create();
    for (String line : lines) {
      String[] segs = line.split("\t");
      String id = segs[0];
      String desc = segs[3];
      String loc = segs[2];
      id2desc2loc.put(id, desc, loc);
    }
    // cache annotation
    Properties props = new Properties();
    props.setProperty("annotators", "tokenize, ssplit, pos, lemma");
    StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
    ListMultimap<String, CoreMap> id2sentences = ArrayListMultimap.create();
    System.out.println("caching context");
    int count = 0;
    for (String id : id2desc2loc.rowKeySet()) {
      System.out.println(count++ + "/" + id2desc2loc.rowKeySet().size());
      File[] files = new File("data/context")
              .listFiles((dir, name) -> name.startsWith(id + "-") && name.endsWith(".txt"));
      for (File file : files) {
        String context = Files.toString(file, Charsets.UTF_8).replaceAll("\\n+", ". ")
                .replace("...", ". ").replace(";", ". ").replaceAll("\\.[^\\p{ASCII}]+", ". ")
                .replaceAll("\\s+", " ").trim();
        System.out.print(context.length() + " " + file.getAbsolutePath() + " ");
        if (context.length() == 0 || CharMatcher.ASCII.countIn(context) < context.length() * 0.9
                || CharMatcher.WHITESPACE.countIn(context) < context.length() * 0.1) {
          System.out.println("No");
          continue;
        }
        System.out.println("Yes");
        Annotation document = new Annotation(context);
        pipeline.annotate(document);
        id2sentences.putAll(id, document.get(SentencesAnnotation.class));
      }
    }
    ListMultimap<String, CoreMap> desc2sentences = ArrayListMultimap.create();
    System.out.println("caching desc");
    count = 0;
    for (String desc : id2desc2loc.columnKeySet()) {
      System.out.println(count++ + "/" + id2desc2loc.columnKeySet().size());
      Annotation document = new Annotation(desc);
      pipeline.annotate(document);
      desc2sentences.putAll(desc, document.get(SentencesAnnotation.class));
    }
    // find matching
    BufferedWriter bw = Files.newWriter(new File("data/classify-apkbc-corpus.tsv"), Charsets.UTF_8);
    count = 0;
    System.out.println("finding match");
    for (Cell<String, String, String> cell : id2desc2loc.cellSet()) {
      System.out.println(count++ + "/" + id2desc2loc.size());
      String id = cell.getRowKey();
      String desc = cell.getColumnKey();
      String loc = cell.getValue();
      for (CoreMap descSentence : desc2sentences.get(desc)) {
        Set<String> descLemmas = descSentence.get(TokensAnnotation.class).stream()
                .map(token -> token.get(LemmaAnnotation.class)).collect(toSet());
        List<String> contextSentenceStrings = new ArrayList<>();
        List<Boolean> labels = new ArrayList<>();
        for (CoreMap contextSentence : id2sentences.get(id)) {
          contextSentenceStrings.add(contextSentence.toString());
          Set<String> contextLemmas = contextSentence.get(TokensAnnotation.class).stream()
                  .map(token -> token.get(LemmaAnnotation.class)).collect(toSet());
          double overlap = Sets.intersection(contextLemmas, descLemmas).size()
                  / (double) descLemmas.size();
          if ((loc.equals("SUMMARY") && descLemmas.size() > 2 && overlap >= 1.0)
                  || (loc.equals("EXPLANATION") && descLemmas.size() > 2 && overlap >= 0.7)) {
            labels.add(true);
          } else {
            labels.add(false);
          }
        }
        // write
        Set<Integer> indexes = IntStream.range(0, labels.size()).boxed().filter(i -> labels.get(i))
                .collect(toSet());
        IntStream.range(0, labels.size()).boxed()
                .filter(i -> labels.get(i) && loc.equals("EXPLANATION"))
                .flatMap(i -> Stream.of(i - 1, i + 1)).filter(i -> i >= 0 && i < labels.size())
                .forEach(indexes::add);
        for (int index : indexes) {
          bw.write(id + "\t" + loc + "\t" + descSentence.toString() + "\t"
                  + contextSentenceStrings.get(index) + "\t" + labels.get(index) + "\n");
        }
      }
    }
    bw.close();
  }

}
