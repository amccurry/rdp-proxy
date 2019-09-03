package rdp.proxy.spi;

import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public abstract class BaseRdpStore implements RdpStore {
  public static final String COOKIE_MSTSHASH = "Cookie: mstshash=";

  public static RdpStore INSTANCE = new BaseRdpStore() {
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
      return toSet(new ConnectionInfo(InetAddress.getByName(host), 3389));
    } else {
      return toSet(new ConnectionInfo(InetAddress.getByName(cookie), 3389));
    }
  }

  public static String getMstsHashValue(String cookie) {
    return cookie.replace(COOKIE_MSTSHASH, "");
  }

  public static boolean isCookieMstsHash(String cookie) {
    return cookie.contains(COOKIE_MSTSHASH);
  }

  private Set<ConnectionInfo> toSet(ConnectionInfo connectionInfo) {
    return new HashSet<>(Arrays.asList(connectionInfo));
  }
}
