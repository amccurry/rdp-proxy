package rdp.proxy.server.relay;

import java.net.InetAddress;
import java.net.Socket;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Value;

@Value
@NoArgsConstructor(force = true, access = AccessLevel.PRIVATE)
@AllArgsConstructor
@Builder(toBuilder = true)
public class SocketInfo {

  InetAddress inetAddress;
  int port;
  long start = System.nanoTime();

  public static SocketInfo create(Socket socket) {
    return SocketInfo.builder()
                     .inetAddress(socket.getInetAddress())
                     .port(socket.getPort())
                     .build();
  }

  public String getInfo() {
    return toString() + " lifetime " + (System.nanoTime() - start) / 1_000_000_000L + " sec";
  }

  @Override
  public String toString() {
    return inetAddress + ":" + port;
  }

}
