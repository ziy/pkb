package wrappers;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public class FeatureLineUtil {

  public static Stream<String> getFeatures(String line) {
    List<String> segs = Arrays.asList(line.split(" "));
    return segs.subList(0, segs.size() - 1).stream();
  }

  public static String getFeature(String line, String featurePrefix) {
    return getFeatures(line).filter(f -> f.startsWith(featurePrefix + ":"))
            .map(f -> f.substring(featurePrefix.length() + 1)).findAny().orElse("");
  }

  public static String getLabel(String line) {
    String[] segs = line.split(" ");
    return segs[segs.length - 1];
  }

}
