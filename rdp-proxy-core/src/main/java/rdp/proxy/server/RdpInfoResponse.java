package rdp.proxy.server;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Value;

@Value
@NoArgsConstructor(force = true, access = AccessLevel.PRIVATE)
@AllArgsConstructor
@Builder(toBuilder = true)
public class RdpInfoResponse {
  @JsonProperty("user")
  String user;

  @JsonProperty("loadbalanceinfo")
  String loadBalanceInfo;
  
  @JsonProperty("hostname")
  String hostname;
  
  @JsonProperty("port")
  int port;
}
