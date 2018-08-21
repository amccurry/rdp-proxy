package rdp.proxy.spi.config;

public class ConfigUtil {
  public static <T> T loadProperty(String name, Loader<T> loader) {
    String value = getProperty(name);
    if (value != null) {
      return loader.load(value);
    }
    return null;
  }

  public static String getProperty(String name) {
    String property = System.getProperty(name);
    if (property != null) {
      return property;
    }
    return System.getenv(name);
  }

  public static interface Loader<T> {
    T load(String value);
  }
}
