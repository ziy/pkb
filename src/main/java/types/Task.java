package types;

import java.util.ArrayList;
import java.util.List;

import com.google.common.base.Strings;

public class Task {

  private String id;

  private String summary;

  private String explanation;

  private List<Task> subtasks;

  public Task(String explanation) {
    this(null, null, explanation);
  }

  public Task(String id, String explanation) {
    this(null, id, explanation);
  }

  public Task(String summary, String id, String explanation) {
    this(summary, id, explanation, new ArrayList<>());
  }

  public Task(String summary, String id, String explanation, List<Task> subtasks) {
    super();
    this.summary = summary;
    this.id = id;
    this.explanation = explanation;
    this.subtasks = subtasks;
  }

  @Override
  public String toString() {
    StringBuffer sb = new StringBuffer();
    sb.append("# " + summary + " /" + id + "/\n");
    sb.append(explanation + "\n");
    subtasks.stream().flatMap(task -> task.getTaskLines(0).stream())
            .map(line -> line/* .replaceAll("[<>]", "") */ + "\n").forEach(sb::append);
    return sb.toString();
  }

  public List<String> getTaskLines(int depth) {
    List<String> taskStringList = new ArrayList<>();
    taskStringList.add(Strings.repeat("  ", depth) + "* " + explanation);
    subtasks.stream().map(task -> task.getTaskLines(depth + 1)).flatMap(List::stream)
            .forEachOrdered(taskStringList::add);
    return taskStringList;
  }

  public String getSummary() {
    return summary;
  }

  public String getId() {
    return id;
  }

  public String getExplanation() {
    return explanation;
  }

  public List<Task> getSubtasks() {
    return subtasks;
  }

  public void addFactor(Task task) {
    this.subtasks.add(task);
  }

  public void setId(String id) {
    this.id = id;
  }

}
