package rdp.proxy.spi;

import java.net.InetAddress;
import java.net.Proxy;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Value;

@Value
@NoArgsConstructor(force = true, access = AccessLevel.PRIVATE)
@AllArgsConstructor
@Builder(toBuilder = true)
public class ConnectionInfo {

  @Builder.Default
  Proxy proxy = Proxy.NO_PROXY;
  InetAddress address;
  int port;

}
