package rdp.proxy.spi;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;
import java.util.Set;

public interface RdpGatewayController {

  /**
   * Is used to validate the given user is valid before responding with rdp
   * information.
   * 
   * @param user
   * @return boolean
   * @throws IOException
   */
  boolean isValidUser(String user) throws IOException;

  /**
   * Get the loadBalanceInfo attribute for a rdp file sent to a client.
   * 
   * @param user
   * @return string
   * @throws IOException
   */
  String getLoadBalanceInfo(String user) throws IOException;

  /**
   * This call calculates the rdp attributes to be sent to the client via the
   * rdp file.
   * 
   * @param defaultSettings
   * @param user
   * @param loadBalanceInfo
   * @param rdpHostname
   * @param rdpPort
   * @return list
   * @throws IOException
   */
  List<RdpSetting> getRdpSettings(List<RdpSetting> defaultSettings, String user, String loadBalanceInfo,
      String rdpHostname, int rdpPort) throws IOException;

  /**
   * The rdp filename at download.
   * 
   * @param user
   * @return string
   * @throws IOException
   */
  String getFilename(String user) throws IOException;

  /**
   * Gets the connection info given the rdp connection rdp, should contain the
   * loadBalanceInfo attribute.
   * 
   * @param cookie
   * @return
   * @throws IOException
   */
  Set<ConnectionInfo> getConnectionInfoWithCookie(String cookie) throws IOException;

  /**
   * Creates a session hook for the given cookie. Close on the {@link Closeable}
   * will be called on session termination.
   * 
   * @param cookie
   * @return
   */
  default Closeable createSession(String cookie) throws IOException {
    return () -> {
    };
  }

}
