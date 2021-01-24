package rdp.proxy.spi;

import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public abstract class BaseRdpGatewayController implements RdpGatewayController {
  public static final String COOKIE_MSTSHASH = "Cookie: mstshash=";

  public static RdpGatewayController INSTANCE = new BaseRdpGatewayController() {
  };

  @Override
  public boolean isValidUser(String user) throws IOException {
    return true;
  }

  @Override
  public List<RdpSetting> getRdpSettings(List<RdpSetting> defaultSettings, String user, String id, String rdpHostname,
      int rdpPort) throws IOException {
    return new ArrayList<>(defaultSettings);
  }

  @Override
  public String getLoadBalanceInfo(String user) throws IOException {
    return user;
  }

  @Override
  public String getFilename(String user) throws IOException {
    return user.replace('.', '-') + ".rdp";
  }

  @Override
  public Set<ConnectionInfo> getConnectionInfoWithCookie(String cookie) throws IOException {
    if (isCookieMstsHash(cookie)) {
      String host = getMstsHashValue(cookie);
      return toSet(ConnectionInfo.builder()
                                 .address(InetAddress.getByName(host))
                                 .port(3389)
                                 .build());
    } else {
      return toSet(ConnectionInfo.builder()
                                 .address(InetAddress.getByName(cookie))
                                 .port(3389)
                                 .build());
    }
  }

  public static String getMstsHashValue(String cookie) {
    return cookie.replace(COOKIE_MSTSHASH, "");
  }

  public static boolean isCookieMstsHash(String cookie) {
    return cookie.contains(COOKIE_MSTSHASH);
  }

  protected Set<ConnectionInfo> toSet(ConnectionInfo connectionInfo) {
    return new HashSet<>(Arrays.asList(connectionInfo));
  }
}
