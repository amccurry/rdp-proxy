package rdp.proxy.server;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.ToString;
import lombok.Value;

@Value
@AllArgsConstructor
@Builder
@ToString
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
