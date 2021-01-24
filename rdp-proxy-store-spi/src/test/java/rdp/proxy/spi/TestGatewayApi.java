package rdp.proxy.spi;

import java.io.IOException;
import java.net.InetAddress;
import java.util.List;
import java.util.Set;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;

public class TestGatewayApi extends BaseRdpStore {

  private static final String USERNAME = "username";
  private static final String FULL_ADDRESS = "full address";

  @Override
  public List<RdpSetting> getRdpSettings(List<RdpSetting> defaultSettings, String user, String id, String rdpHostname,
      int rdpPort) throws IOException {
    Builder<RdpSetting> builder = ImmutableList.builder();
    return builder.addAll(defaultSettings)
                  .add(RdpSetting.create(FULL_ADDRESS, "192.168.1.242:3390"))
                  .add(RdpSetting.create(USERNAME, user + "|password"))
                  .build();
  }

  @Override
  public Set<ConnectionInfo> getConnectionInfoWithCookie(String cookie) throws IOException {
    // InetAddress address = InetAddress.getByName("192.168.1.242");
    // System.out.println(address.getHostAddress());
    // System.out.println(address.getHostName());

    return toSet(ConnectionInfo.builder()
                               .address(InetAddress.getLocalHost())
                               .port(3389)
                               .build());
  }

}
