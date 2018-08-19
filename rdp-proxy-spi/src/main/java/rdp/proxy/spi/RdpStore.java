package rdp.proxy.spi;

import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

public interface RdpStore {

  public RdpStore INSTANCE = new RdpStore() {
  };

  default boolean isValidUser(String user) throws IOException {
    return true;
  }

  default List<RdpSetting> getRdpSettings(List<RdpSetting> defaultSettings, String user, String id, String rdpHostname,
      int rdpPort) throws IOException {
    return new ArrayList<>(defaultSettings);
  }

  default String getLoadBalanceInfo(String user) throws IOException {
    return user;
  }

  default String getFilename(String user) throws IOException {
    return user.replace('.', '-') + ".rdp";
  }

  default ConnectionInfo startRdpSessionIfMissingWithId(String id) throws IOException {
    return new ConnectionInfo(InetAddress.getByName(id), 3389);
  }

  default ConnectionInfo startRdpSessionIfMissingWithCookie(String cookie) throws IOException {
    String host = cookie.replace("Cookie: mstshash=", "");
    return new ConnectionInfo(InetAddress.getByName(host), 3389);
  }

}
