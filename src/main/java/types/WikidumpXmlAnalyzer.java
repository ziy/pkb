package types;

import static java.util.stream.Collectors.toList;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.SequenceInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.vseravno.solna.SolnaException;
import com.vseravno.solna.SolnaHandler;
import com.vseravno.solna.SolnaParser;

public class WikidumpXmlAnalyzer {

  private static final String PAGE_ELEMENT = "/mediawiki/page";

  private static final String XML_HEAD = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>";

  public static class WikidumpArticleHandler implements SolnaHandler<Element> {

    private Set<String> validNamespaces;

    private List<WikidumpArticle> articles;

    private int count;

    public WikidumpArticleHandler(Set<String> validNamespaces) {
      this.validNamespaces = validNamespaces;
      this.articles = new ArrayList<>();
      this.count = 0;
    }

    public void handle(Element element) {
      count++;
      NodeList ns = element.getElementsByTagName("ns");
      if (validNamespaces != null && !validNamespaces.contains(ns.item(0).getTextContent())) {
        return;
      }
      NodeList redirect = element.getElementsByTagName("redirect");
      if (redirect != null && redirect.getLength() > 0) {
        return;
      }
      String title = element.getElementsByTagName("title").item(0).getTextContent();
      int id = Integer.parseInt(element.getElementsByTagName("id").item(0).getTextContent());
      NodeList revisionElements = element.getElementsByTagName("revision");
      String text = revisionElements.item(revisionElements.getLength() - 1).getTextContent();
      articles.add(new WikidumpArticle(title, id, text));
    }

    public List<WikidumpArticle> getArticles() {
      return articles;
    }

    public int getCount() {
      return count;
    }

  }

  private List<WikidumpArticle> articles;

  public WikidumpXmlAnalyzer(String historyFilepath, Set<String> validNamespaces)
          throws SolnaException, IOException {
    WikidumpArticleHandler handler = new WikidumpArticleHandler(validNamespaces);
    SolnaParser parser = new SolnaParser();
    parser.addHandler(PAGE_ELEMENT, handler);
    try (ByteArrayInputStream his = new ByteArrayInputStream(XML_HEAD.getBytes());
            FileInputStream fis = new FileInputStream(historyFilepath);
            SequenceInputStream sis = new SequenceInputStream(his, fis)) {
      parser.parse(sis);
    }
    articles = handler.getArticles();
    System.out.println("Read " + handler.getCount() + " articles");
    System.out.println(
            "Extracted " + articles.size() + " valid articles filtered by namespace and redirect");
  }

  public void filterByIds(Set<Integer> ids) {
    articles = articles.stream().filter(article -> ids.contains(article.getId())).collect(toList());
  }

  public void toTitlesFile(String outputFilepath) throws IOException {
    String titles = articles.stream().map(WikidumpArticle::getTitle).sorted()
            .collect(Collectors.joining("\n"));
    Files.write(titles, new File(outputFilepath), Charsets.UTF_8);
  }

  public List<WikidumpArticle> getArticles() {
    return articles;
  }

}
