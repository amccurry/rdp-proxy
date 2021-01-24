package rdp.proxy.server.util;

public class ConfigUtils {

  private static final String RDP_GATEWAY_WAIT_TIME_BETWEEN_ATTEMPTS_TO_CONNECT_DEFAULT = "5";
  private static final String RDP_GATEWAY_WAIT_TIME_BETWEEN_ATTEMPTS_TO_CONNECT = "RDP_GATEWAY_WAIT_TIME_BETWEEN_ATTEMPTS_TO_CONNECT";

  public static long getRdpGatewayWaitTimeBetweenAttemptsToConnectInSeconds() {
    String value = getProperty(RDP_GATEWAY_WAIT_TIME_BETWEEN_ATTEMPTS_TO_CONNECT,
        RDP_GATEWAY_WAIT_TIME_BETWEEN_ATTEMPTS_TO_CONNECT_DEFAULT);
    return Long.parseLong(value);
  }

  private static String getProperty(String name) {
    return getProperty(name, null);
  }

  private static String getProperty(String name, String defaultValue) {
    String value = System.getenv(name);
    if (value == null) {
      return defaultValue;
    }
    return value;
  }

  public static int getRdpGatewayMaxConnectionAttempts() {
    // TODO Auto-generated method stub
    return 0;
  }

  public static long getRdpGatewayRelayCheckTime() {
    // TODO Auto-generated method stub
    return 0;
  }

  public static long getRdpGatewayRelayCheckTimeInSeconds() {
    // TODO Auto-generated method stub
    return 0;
  }

  public static int getRdpGatewaySoTimeoutInMilliSeconds() {
    // TODO Auto-generated method stub
    return 0;
  }

  public static int getRdpGatewayRemoteRdpTcpTimeout() {
    // TODO Auto-generated method stub
    return 0;
  }

}
