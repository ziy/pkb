package types;


public class WikidumpArticle {

  public static final String TITLE_ELEMENT = "title";

  public static final String ID_ELEMENT = "id";

  public static final String NAMESPACE_ELEMENT = "ns";

  public static final String REDIRECT_ELEMENT = "redirect";

  public static final String TEXT_ELEMENT = "text";

  private String title;

  private int id;

  private String text;

  public WikidumpArticle(String title, int id, String text) {
    super();
    this.title = title;
    this.id = id;
    this.text = text;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public int getId() {
    return id;
  }

  public void setId(int id) {
    this.id = id;
  }

  public String getText() {
    return text;
  }

  public void setText(String text) {
    this.text = text;
  }

}
