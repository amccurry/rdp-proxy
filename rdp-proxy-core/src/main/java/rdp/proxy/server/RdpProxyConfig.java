package rdp.proxy.server;

import java.io.IOException;
import java.net.InetAddress;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Value;

@AllArgsConstructor
@Value
@Builder
public class RdpProxyConfig {

  @Default
  int rdpPort = 3388;

  @Default
  String rdpBindAddress = "0.0.0.0";

  @Default
  int rdpHttpPort = 5000;

  @Default
  String rdpHttpBindAddress = "0.0.0.0";

  String rdpHostnameAdvertised;

  String rdpProxySetupClassname;

  String rdpMetaStoreClassname;

  @Default
  int rdpBacklog = 100;

  public String getRdpHostname() throws IOException {
    String hostnameAdvertised = getRdpHostnameAdvertised();
    if (hostnameAdvertised != null) {
      return hostnameAdvertised;
    }
    return InetAddress.getLocalHost()
                      .getHostName();
  }

}
