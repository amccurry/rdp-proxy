package rdp.proxy.server.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;

import rdp.proxy.server.RdpProxyConfig;
import rdp.proxy.server.RdpProxyConfig.RdpProxyConfigBuilder;
import rdp.proxy.service.spi.RdpProxyServiceSetup;
import rdp.proxy.spi.BaseRdpStore;
import rdp.proxy.spi.RdpSetting;
import rdp.proxy.spi.RdpStore;
import rdp.proxy.spi.config.ConfigUtil;
import spark.Service;

@SuppressWarnings("unchecked")
public class Utils {

  public static final String RDP_GATEWAY_HTTP_BIND_ADDRESS = "RDP_GATEWAY_HTTP_BIND_ADDRESS";
  public static final String RDP_ADMIN_HTTP_PORT = "RDP_ADMIN_HTTP_PORT";
  public static final String RDP_GATEWAY_HTTP_PORT = "RDP_GATEWAY_HTTP_PORT";
  public static final String RDP_ADMIN_HTTP_BIND_ADDRESS = "RDP_ADMIN_HTTP_BIND_ADDRESS";
  public static final String RDP_SO_TIMEOUT = "RDP_SO_TIMEOUT";
  public static final String RDP_REMOTE_TCP_TIMEOUT = "RDP_REMOTE_TCP_TIMEOUT";
  public static final String RDP_RELAY_BUFFER_SIZE = "RDP_RELAY_BUFFER_SIZE";
  public static final String RDP_BACKLOG = "RDP_BACKLOG";
  public static final String RDP_PROXY_SETUP_CLASSNAME = "RDP_PROXY_SETUP_CLASSNAME";
  public static final String RDP_META_STORE_CLASSNAME = "RDP_META_STORE_CLASSNAME";
  public static final String RDP_HOSTNAME_ADVERTISED = "RDP_HOSTNAME_ADVERTISED";
  public static final String RDP_BIND_ADDRESS = "RDP_BIND_ADDRESS";
  public static final String RDP_PORT = "RDP_PORT";

  private static final String DEFAULT_RDP = "/default.rdp";

  public static RdpProxyServiceSetup getRdpProxySetup(RdpProxyConfig config) throws Exception {
    String rdpProxySetupClassname = config.getRdpProxySetupClassname();
    if (rdpProxySetupClassname == null) {
      return RdpProxyServiceSetup.INSTANCE;
    }
    Class<? extends RdpProxyServiceSetup> clazz = (Class<? extends RdpProxyServiceSetup>) Class.forName(
        rdpProxySetupClassname);
    return clazz.newInstance();
  }

  public static RdpStore getRdpMetaStore(RdpProxyConfig config) throws Exception {
    String rdpMetaStoreClassname = config.getRdpMetaStoreClassname();
    if (rdpMetaStoreClassname == null) {
      return BaseRdpStore.INSTANCE;
    }
    Class<? extends RdpStore> clazz = (Class<? extends RdpStore>) Class.forName(rdpMetaStoreClassname);
    return clazz.newInstance();
  }

  public static RdpProxyConfig getConfig() {
    RdpProxyConfigBuilder builder = RdpProxyConfig.builder();
    {
      Integer prop = ConfigUtil.loadProperty(RDP_PORT, value -> Integer.parseInt(value));
      if (prop != null) {
        builder.rdpPort(prop);
      }
    }
    {
      String prop = ConfigUtil.loadProperty(RDP_BIND_ADDRESS, value -> value);
      if (prop != null) {
        builder.rdpBindAddress(prop);
      }
    }
    {
      Integer prop = ConfigUtil.loadProperty(RDP_GATEWAY_HTTP_PORT, value -> Integer.parseInt(value));
      if (prop != null) {
        builder.rdpGatewayHttpPort(prop);
      }
    }
    {
      Integer prop = ConfigUtil.loadProperty(RDP_ADMIN_HTTP_PORT, value -> Integer.parseInt(value));
      if (prop != null) {
        builder.rdpAdminHttpPort(prop);
      }
    }

    {
      String prop = ConfigUtil.loadProperty(RDP_GATEWAY_HTTP_BIND_ADDRESS, value -> value);
      if (prop != null) {
        builder.rdpGatewayHttpBindAddress(prop);
      }
    }
    {
      String prop = ConfigUtil.loadProperty(RDP_ADMIN_HTTP_BIND_ADDRESS, value -> value);
      if (prop != null) {
        builder.rdpAdminHttpBindAddress(prop);
      }
    }
    {
      String prop = ConfigUtil.loadProperty(RDP_HOSTNAME_ADVERTISED, value -> value);
      if (prop != null) {
        builder.rdpHostnameAdvertised(prop);
      }
    }
    {
      String prop = ConfigUtil.loadProperty(RDP_PROXY_SETUP_CLASSNAME, value -> value);
      if (prop != null) {
        builder.rdpProxySetupClassname(prop);
      }
    }
    {
      String prop = ConfigUtil.loadProperty(RDP_META_STORE_CLASSNAME, value -> value);
      if (prop != null) {
        builder.rdpMetaStoreClassname(prop);
      }
    }
    {
      Integer prop = ConfigUtil.loadProperty(RDP_BACKLOG, value -> Integer.parseInt(value));
      if (prop != null) {
        builder.rdpBacklog(prop);
      }
    }
    {
      Integer prop = ConfigUtil.loadProperty(RDP_RELAY_BUFFER_SIZE, value -> Integer.parseInt(value));
      if (prop != null) {
        builder.rdpRelayBufferSize(prop);
      }
    }
    {
      Integer prop = ConfigUtil.loadProperty(RDP_REMOTE_TCP_TIMEOUT, value -> Integer.parseInt(value));
      if (prop != null) {
        builder.rdpRemoteTcpTimeout(prop);
      }
    }
    {
      Integer prop = ConfigUtil.loadProperty(RDP_SO_TIMEOUT, value -> Integer.parseInt(value));
      if (prop != null) {
        builder.rdpSoTimeout(prop);
      }
    }

    return builder.build();
  }

  public static Service igniteGatewayService(RdpProxyConfig config) throws Exception {
    RdpProxyServiceSetup setup = getRdpProxySetup(config);
    Service service = Service.ignite();
    service.port(config.getRdpGatewayHttpPort());
    service.ipAddress(config.getRdpGatewayHttpBindAddress());
    setup.serviceGatewaySetup(service);
    return service;
  }

  public static Service igniteAdminService(RdpProxyConfig config) throws Exception {
    RdpProxyServiceSetup setup = getRdpProxySetup(config);
    Service service = Service.ignite();
    service.port(config.getRdpAdminHttpPort());
    service.ipAddress(config.getRdpAdminHttpBindAddress());
    setup.serviceAdminSetup(service);
    return service;
  }

  public static List<RdpSetting> getRdpDefaults() throws IOException {
    InputStream inputStream = Utils.class.getResourceAsStream(DEFAULT_RDP);
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
