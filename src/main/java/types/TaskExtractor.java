package types;

import static java.util.stream.Collectors.toList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import com.google.common.base.Strings;

public class TaskExtractor {

  private List<Task> tasks;

  public TaskExtractor() {
    this.tasks = new ArrayList<>();
  }

  public void addWikidumpArticles(List<WikidumpArticle> articles) {
    System.out.println("Loaded " + articles.size() + " articles");
    articles.stream().map(TaskExtractor::createTask).filter(Objects::nonNull)
            .forEachOrdered(tasks::add);
    System.out.println("Added " + tasks.size() + " tasks");
  }

  private static Pattern stubMarkers = Pattern.compile("\\{\\{stub\\|.+?\\}\\}",
          Pattern.CASE_INSENSITIVE);

  private static Pattern headerPattern = Pattern.compile("== *.+ *==");

  private static Pattern stepsPattern = Pattern.compile("== *steps *==", Pattern.CASE_INSENSITIVE);

  public static Task createTask(WikidumpArticle article) {
    // extract lines
    String text = article.getText();
    List<String> lines = Arrays.stream(text.split("\n")).map(String::trim)
            .filter(line -> !line.isEmpty()).collect(toList());
    // extract description lines
    OptionalInt firstHeaderLineNoOptional = IntStream.range(0, lines.size())
            .filter(i -> headerPattern.matcher(lines.get(i)).matches()).findFirst();
    int firstHeaderLineNo = firstHeaderLineNoOptional.isPresent()
            ? firstHeaderLineNoOptional.getAsInt() : lines.size();
    List<String> intros = IntStream.range(0, firstHeaderLineNo).mapToObj(lines::get)
            .map(String::trim).collect(toList());
    boolean containsStub = intros.stream().anyMatch(line -> stubMarkers.matcher(line).find());
    if (containsStub) {
      return null;
    }
    String explanation = Collections.max(intros, Comparator.comparing(String::length));
    if (Strings.isNullOrEmpty(explanation)) {
      return null;
    }
    // extract step lines
    OptionalInt stepsStartLineNoOptional = IntStream.range(firstHeaderLineNo, lines.size())
            .filter(i -> stepsPattern.matcher(lines.get(i)).matches()).findFirst();
    int stepsStartLineNo = stepsStartLineNoOptional.isPresent()
            ? stepsStartLineNoOptional.getAsInt() : lines.size();
    OptionalInt stepsEndLineNoOptional = IntStream.range(stepsStartLineNo + 1, lines.size())
            .filter(i -> headerPattern.matcher(lines.get(i)).matches()).findFirst();
    int stepsEndLineNo = stepsEndLineNoOptional.isPresent() ? stepsEndLineNoOptional.getAsInt()
            : lines.size();
    List<String> taskLines = IntStream.range(stepsStartLineNo + 1, stepsEndLineNo)
            .mapToObj(lines::get).filter(line -> line.startsWith("#"))
            .map(line -> line.substring(1)).map(String::trim).collect(toList());
    if (taskLines.isEmpty()) {
      return null;
    }
    return new Task(article.getTitle(), String.valueOf(article.getId()), explanation,
            taskLines.stream().map(Task::new).collect(toList()));
  }

  public void normalizeTasks() {
    tasks = tasks.stream().map(TaskExtractor::normalizeTask).filter(Objects::nonNull)
            .collect(toList());
    System.out.println("Normalized " + tasks.size() + " tasks");
  }

  private static Task normalizeTask(Task noisyTask) {
    String normalizedExplanation = normalizeExplanation(noisyTask.getExplanation());
    if (Strings.isNullOrEmpty(normalizedExplanation)) {
      return null;
    }
    List<Task> normalizedSubtasks = noisyTask.getSubtasks().stream()
            .map(TaskExtractor::normalizeTask).filter(Objects::nonNull).collect(toList());
    return new Task(noisyTask.getSummary(), noisyTask.getId(), normalizedExplanation,
            normalizedSubtasks);
  }

  public void buildHierarchy() {
    tasks = tasks.stream().map(TaskExtractor::buildHierarchy).collect(toList());
    System.out.println("Created " + tasks.size() + " hierarchical tasks");
  }

  private static Task buildHierarchy(Task flatTask) {
    List<Task> hierarchicalSubtasks = new ArrayList<>();
    Task curHierarchicalTask = null;
    for (Task flatSubtask : flatTask.getSubtasks()) {
      String explanation = flatSubtask.getExplanation();
      if (curHierarchicalTask == null
              || (!explanation.startsWith("*") && !explanation.startsWith("#"))) {
        curHierarchicalTask = new Task(explanation);
        hierarchicalSubtasks.add(curHierarchicalTask);
      } else {
        explanation = explanation.substring(1).trim();
        if (!explanation.isEmpty()) {
          curHierarchicalTask.addFactor(new Task(explanation));
        }
      }
    }
    List<Task> multiHierarchicalSubtasks = hierarchicalSubtasks.stream()
            .map(TaskExtractor::buildHierarchy).collect(toList());
    return new Task(flatTask.getSummary(), flatTask.getId(), flatTask.getExplanation(),
            multiHierarchicalSubtasks);
  }

  public void fillTaskIds() {
    tasks.stream().forEachOrdered(TaskExtractor::fillFactorIds);
  }

  private static void fillFactorIds(Task task) {
    List<Task> subtasks = task.getSubtasks();
    IntStream.range(0, subtasks.size())
            .forEachOrdered(i -> subtasks.get(i).setId(task.getId() + "." + (i + 1)));
    subtasks.forEach(TaskExtractor::fillFactorIds);
  }

  private static Pattern htmlMarkers = Pattern.compile("</?br.*?>", Pattern.CASE_INSENSITIVE);

  private static String validValue = "[^\\|\\}]+";

  private static String validCapturedField = "\\|(" + validValue + ")";

  private static String validNonCapturedField = "(?:\\|" + validValue + ")";

  private static Pattern mediaMarkers = Pattern.compile(
          "\\[\\[Image:.+?\\]\\]|\\{\\{large ?image\\|.+?\\}\\}|\\{\\{whvid\\|.+?\\}\\}",
          Pattern.CASE_INSENSITIVE);

  private static Pattern dateMarkers = Pattern.compile("\\{\\{" + validValue + validNonCapturedField
          + "*\\|date=" + validValue + validNonCapturedField + "*\\}\\}", Pattern.CASE_INSENSITIVE);

  private static Pattern upToFiveFieldsConvertMarkers = Pattern.compile("\\{\\{convert"
          + Strings.repeat(validCapturedField, 2) + validNonCapturedField + "{0,3}\\}\\}",
          Pattern.CASE_INSENSITIVE);

  private static Pattern sixPlusFieldsConvertMarkers = Pattern.compile("\\{\\{convert"
          + Strings.repeat(validCapturedField, 3) + validNonCapturedField + "{3,}\\}\\}",
          Pattern.CASE_INSENSITIVE);

  private static Pattern buttonKeypressMarkers = Pattern
          .compile("\\{\\{(?:button|keypress)\\|(.*?)\\}\\}", Pattern.CASE_INSENSITIVE);

  private static Pattern twoFieldsMarkers = Pattern
          .compile("\\{\\{(?:" + validValue + ")\\|(" + validValue + ")\\}\\}");

  private static Pattern otherBraceletMarkers = Pattern
          .compile("\\{\\{.+?\\}\\}|\\{\\{.+?\\}|\\{.+?\\}\\}");

  private static Pattern internalLinkMarkers = Pattern.compile("\\[\\[(?:" + validValue + "\\|)?("
          + validValue + "?)" + validNonCapturedField + "*\\]\\]", Pattern.CASE_INSENSITIVE);

  private static Pattern externalLinkMarkers = Pattern.compile("\\[[^ ]+ (.*?)\\]");

  private static Pattern fontMarkers = Pattern.compile(
          "<(?:b|tt|strong|nowiki|code|em|big|center|sup|sub|i|small|u)[^>]*>" + "(.*?)"
                  + "</(?:b|tt|strong|nowiki|code|em|big|center|sup|sub|i|small|u)>",
          Pattern.CASE_INSENSITIVE);

  private static Pattern refMarkers = Pattern.compile("<ref[^>]*>.*?</ref>|<ref[^>]*/>",
          Pattern.CASE_INSENSITIVE);

  private static Pattern htmlCommentMarkers = Pattern.compile("<!--.*?-->");

  private static Pattern otherHtmlMarkers = Pattern.compile("<[^>]+?>");

  private static Pattern quotesMarkers = Pattern.compile("'''?");

  public static String normalizeExplanation(String noisyExplanation) {
    String normalizedExplanation = new String(noisyExplanation);
    normalizedExplanation = htmlMarkers.matcher(normalizedExplanation).replaceAll("");
    normalizedExplanation = mediaMarkers.matcher(normalizedExplanation).replaceAll("");
    normalizedExplanation = dateMarkers.matcher(normalizedExplanation).replaceAll("");
    normalizedExplanation = upToFiveFieldsConvertMarkers.matcher(normalizedExplanation)
            .replaceAll("$1 $2");
    normalizedExplanation = sixPlusFieldsConvertMarkers.matcher(normalizedExplanation)
            .replaceAll("$1 $2 $3");
    normalizedExplanation = buttonKeypressMarkers.matcher(normalizedExplanation).replaceAll("$1");
    normalizedExplanation = twoFieldsMarkers.matcher(normalizedExplanation).replaceAll("$1");
    normalizedExplanation = otherBraceletMarkers.matcher(normalizedExplanation).replaceAll("");
    normalizedExplanation = internalLinkMarkers.matcher(normalizedExplanation).replaceAll("$1");
    normalizedExplanation = externalLinkMarkers.matcher(normalizedExplanation).replaceAll("$1");
    normalizedExplanation = refMarkers.matcher(normalizedExplanation).replaceAll("");
    normalizedExplanation = fontMarkers.matcher(normalizedExplanation).replaceAll("$1");
    normalizedExplanation = htmlCommentMarkers.matcher(normalizedExplanation).replaceAll("");
    normalizedExplanation = otherHtmlMarkers.matcher(normalizedExplanation).replaceAll("");
    // normalizedDescription = quotesMarkers.matcher(normalizedDescription).replaceAll("\"");
    normalizedExplanation = quotesMarkers.matcher(normalizedExplanation).replaceAll("");
    return normalizedExplanation.trim();
  }

  public List<Task> getTasks() {
    return tasks;
  }

}
