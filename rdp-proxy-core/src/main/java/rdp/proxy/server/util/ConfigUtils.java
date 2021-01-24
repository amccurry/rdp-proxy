package rdp.proxy.server.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;

import lombok.SneakyThrows;
import rdp.proxy.spi.BaseRdpStore;
import rdp.proxy.spi.RdpGatewayApi;
import rdp.proxy.spi.RdpSetting;
import spark.Service;

public class ConfigUtils {

  private static final String DEFAULT_RDP = "/default.rdp";

  private static final String RDP_GATEWAY_API_CLASSNAME = "RDP_GATEWAY_API_CLASSNAME";
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
    return Integer.parseInt(getProperty("RDP_GATEWAY_MAX_CONNECTION_ATTEMPTS", "10"));
  }

  public static long getRdpGatewayRelayCheckTimeInSeconds() {
    return Integer.parseInt(getProperty("RDP_GATEWAY_RELAY_CHECK_TIME_IN_SECONDS", "1"));
  }

  public static int getRdpGatewayClientSoTimeoutInMilliSeconds() {
    return Integer.parseInt(
        getProperty("RDP_GATEWAY_CLIENT_SO_TIMEOUT_IN_MILLI_SECONDS", Long.toString(TimeUnit.SECONDS.toMillis(60))));
  }

  public static int getRdpGatewayServerSoTimeoutInMilliSeconds() {
    return Integer.parseInt(
        getProperty("RDP_GATEWAY_SERVER_SO_TIMEOUT_IN_MILLI_SECONDS", Long.toString(TimeUnit.SECONDS.toMillis(60))));
  }

  public static int getRdpGatewayRemoteRdpTcpConnectTimeoutInMilliSeconds() {
    return Integer.parseInt(getProperty("RDP_GATEWAY_REMOTE_CONNECT_TIMEOUT_IN_MILLI_SECONDS",
        Long.toString(TimeUnit.SECONDS.toMillis(10))));
  }

  public static String getRdpGatewayBindAddress() {
    return getProperty("RDP_GATEWAY_BIND_ADDRESS", "0.0.0.0");
  }

  public static int getRdpGatewayPort() {
    return Integer.parseInt(getProperty("RDP_GATEWAY_PORT", "3389"));
  }

  public static int getRdpGatewayBacklog() {
    return Integer.parseInt(getProperty("RDP_GATEWAY_BACKLOG", "100"));
  }

  @SneakyThrows
  public static String getRdpGatewayAdvertisedHostname() {
    return getProperty("RDP_GATEWAY_ADVERTISED_HOSTNAME", InetAddress.getLocalHost()
                                                                     .getHostName());
  }

  public static int getRdpGatewayAdvertisedPort() {
    return Integer.parseInt(getProperty("RDP_GATEWAY_ADVERTISED_PORT", "3389"));
  }

  public static Service createGatewayService() {
    Service service = Service.ignite();
    service.port(getRdpGatewayHttpPort());
    service.ipAddress(getRdpGatewayHttpBindAddress());
    return service;
  }

  public static String getRdpGatewayHttpBindAddress() {
    return getProperty("RDP_GATEWAY_HTTP_BIND_ADDRESS", "0.0.0.0");
  }

  public static int getRdpGatewayHttpPort() {
    return Integer.parseInt(getProperty("RDP_GATEWAY_HTTP_PORT", "8080"));
  }

  public static Service createAdminService() {
    Service service = Service.ignite();
    service.port(getRdpGatewayAdminPort());
    service.ipAddress(getRdpGatewayAdminBindAddress());
    return service;
  }

  public static String getRdpGatewayAdminBindAddress() {
    return getProperty("RDP_GATEWAY_ADMIN_BIND_ADDRESS", "0.0.0.0");
  }

  public static int getRdpGatewayAdminPort() {
    return Integer.parseInt(getProperty("RDP_GATEWAY_ADMIN_PORT", "8081"));
  }

  @SneakyThrows
  public static RdpGatewayApi createRdpGatewayApi() {
    String classname = getProperty(RDP_GATEWAY_API_CLASSNAME);
    if (classname == null) {
      return BaseRdpStore.INSTANCE;
    }
    @SuppressWarnings("unchecked")
    Class<? extends RdpGatewayApi> clazz = (Class<? extends RdpGatewayApi>) Class.forName(classname);
    return clazz.newInstance();
  }

  public static List<RdpSetting> getRdpDefaults() throws IOException {
    InputStream inputStream = ConfigUtils.class.getResourceAsStream(DEFAULT_RDP);
    Builder<RdpSetting> builder = ImmutableList.builder();
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
      String line;
      while ((line = reader.readLine()) != null) {
        String trim = line.trim();
        if (!trim.isEmpty()) {
          builder.add(RdpSetting.parse(trim));
        }
      }
    }
    return builder.build();
  }
}
