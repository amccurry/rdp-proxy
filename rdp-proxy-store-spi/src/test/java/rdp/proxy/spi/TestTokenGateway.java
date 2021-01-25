package rdp.proxy.spi;

import java.io.IOException;
import java.net.InetAddress;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.google.common.base.Splitter;

import spark.Request;

public class TestTokenGateway extends BaseRdpGatewayController {

  private final Map<String, String> _userToTokenMap = new ConcurrentHashMap<>();

  public TestTokenGateway() {
    _userToTokenMap.put("red", "5b6c69f4-fa38-427e-952e-235b633214a9");
  }

  @Override
  public List<RdpSetting> getRdpSettings(List<RdpSetting> defaultSettings, String user, String id, String rdpHostname,
      int rdpPort) throws IOException {
    return defaultSettings;
  }

  @Override
  public Set<ConnectionInfo> getConnectionInfoWithCookie(String cookie) throws IOException {
    return toSet(ConnectionInfo.builder()
                               .address(InetAddress.getByName("192.168.1.192"))
                               .port(3389)
                               .build());
  }

  @Override
  public boolean isTokenAuthenticationEnabled() throws IOException {
    return true;
  }

  @Override
  public String getTokenAuthentication(String username, Request request) throws IOException {
    String token = _userToTokenMap.get(username);
    if (token == null) {
      // _userToTokenMap.put(username, token = UUID.randomUUID()
      //
      _userToTokenMap.put(username, token = "5b6c69f4-fa38-427e-952e-235b633214a9");

    }
    return token;
  }

  @Override
  public boolean isTokenValid(String token) throws IOException {
    List<String> list = Splitter.on('|')
                                .splitToList(token);
    if (list.size() != 2) {
      return false;
    }
    String username = list.get(0);
    String s = _userToTokenMap.get(username);
    if (s == null) {
      return false;
    }
    return s.equals(list.get(1));
  }

}
