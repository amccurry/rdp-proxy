package rdp.proxy.server.admin;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Value;

@Value
@NoArgsConstructor(force = true, access = AccessLevel.PRIVATE)
@AllArgsConstructor
@Builder(toBuilder = true)
public class ConnectionInfoAdminPage implements Comparable<ConnectionInfoAdminPage> {
  String id;
  String host;
  String rdpServer;
  String rdpClient;
  double clientToServerBandwidth;
  double serverToClientBandwidth;
  double clientToServerBandwidthOneMinute;
  double serverToClientBandwidthOneMinute;
  double clientToServerBandwidthTotal;
  double serverToClientBandwidthTotal;

  @Override
  public int compareTo(ConnectionInfoAdminPage o) {
    return host.compareTo(o.host);
  }
}
