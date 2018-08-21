package rdp.proxy.spi;

import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

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
  public ConnectionInfo getConnectionInfoWithCookie(String cookie) throws IOException {
    if (cookie.contains(COOKIE_MSTSHASH)) {
      String host = cookie.replace(COOKIE_MSTSHASH, "");
      return new ConnectionInfo(InetAddress.getByName(host), 3389);
    } else {
      return new ConnectionInfo(InetAddress.getByName(cookie), 3389);
    }
  }
}
