package rdp.proxy.server.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@AllArgsConstructor
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
