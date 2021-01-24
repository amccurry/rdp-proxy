package rdp.proxy.server.util;

import java.io.Closeable;
import java.io.IOException;

public class Utils {
  public static void closeQuietly(Closeable closeable) {
    if (closeable != null) {
      try {
        closeable.close();
      } catch (IOException e) {

      }
    }
  }
}
