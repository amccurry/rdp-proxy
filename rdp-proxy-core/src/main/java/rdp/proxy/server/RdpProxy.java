package rdp.proxy.server;

import java.io.Closeable;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.servlet.http.HttpServletResponse;

import com.codahale.metrics.MetricRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;

import rdp.proxy.server.metrics.JsonReporter;
import rdp.proxy.server.metrics.SetupJvmMetrics;
import rdp.proxy.server.relay.RdpConnectionRelay;
import rdp.proxy.server.util.Utils;
import rdp.proxy.spi.RdpSetting;
import rdp.proxy.spi.RdpStore;
import spark.ResponseTransformer;
import spark.Route;
import spark.Service;

public class RdpProxy implements Closeable {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final String CONTENT_DISPOSITION = "Content-Disposition";
  private static final String CONTENT_TYPE = "Content-Type";
  private static final String LOADBALANCEINFO = "loadbalanceinfo";
  private static final String USERNAME = "username";
  private static final String FULL_ADDRESS = "full address";
  private static final ResponseTransformer JSON_TRANSFORMER = (ResponseTransformer) model -> OBJECT_MAPPER.writeValueAsString(
      model);

  public static void main(String[] args) throws Exception {
    RdpProxyConfig config = Utils.getConfig();
    try (RdpProxy proxy = new RdpProxy(config)) {
      proxy.initGateway();
      proxy.initAdmin();
      proxy.join();
    }
  }

  private final Service _gatewayService;
  private final RdpStore _store;
  private final RdpProxyConfig _config;
  private final String _hostnameAdvertised;
  private final int _rdpPort;
  private final RdpConnectionRelay _relay;
  private final Service _adminService;
  private final MetricRegistry _metrics = new MetricRegistry();
  private final JsonReporter _reporter;

  public RdpProxy(RdpProxyConfig config) throws Exception {
    _config = config;
    _hostnameAdvertised = config.getRdpHostname();
    _rdpPort = _config.getRdpPort();
    _gatewayService = Utils.igniteGatewayService(config);
    _adminService = Utils.igniteAdminService(config);
    _store = Utils.getRdpMetaStore(config);
    _reporter = new JsonReporter(_metrics);
    _reporter.start(5, TimeUnit.SECONDS);
    _relay = new RdpConnectionRelay(_config, _store, _metrics);
    SetupJvmMetrics.setup(_metrics);
  }

  public void join() throws InterruptedException {
    _relay.exec();
  }

  public void initAdmin() {
    ResponseTransformer jsonTransformer = model -> new ObjectMapper().writeValueAsString(model);
    _adminService.get("/stats", (Route) (request, response) -> _reporter.getReport(), jsonTransformer);
  }

  public void initGateway() throws IOException {
    List<RdpSetting> defaultSettings = Utils.getRdpDefaults();
    Route infoRoute = (Route) (request, response) -> {
      String user = request.params("user");
      if (!_store.isValidUser(user)) {
        _gatewayService.halt(404);
      }

      String loadBalanceInfo = _store.getLoadBalanceInfo(user);

      List<RdpSetting> rdpSettings = _store.getRdpSettings(defaultSettings, user, loadBalanceInfo, _hostnameAdvertised,
          _rdpPort);
      Map<String, RdpSetting> rdpSettingsMap = toMap(rdpSettings);

      addIfMissing(rdpSettingsMap, RdpSetting.create(FULL_ADDRESS, _hostnameAdvertised + ":" + _rdpPort));

      RdpSetting fullAddress = rdpSettingsMap.get(FULL_ADDRESS);

      return RdpInfoResponse.builder()
                            .user(user)
                            .loadBalanceInfo(loadBalanceInfo)
                            .hostname(fullAddress.getStringValue())
                            .port(_rdpPort)
                            .build();
    };

    Route rdpFileRoute = (request, response) -> {
      String user = request.params("user");
      if (!_store.isValidUser(user)) {
        _gatewayService.halt(404);
      }
      String loadBalanceInfo = _store.getLoadBalanceInfo(user);

      List<RdpSetting> rdpSettings = _store.getRdpSettings(defaultSettings, user, loadBalanceInfo, _hostnameAdvertised,
          _rdpPort);
      Map<String, RdpSetting> rdpSettingsMap = toMap(rdpSettings);

      // full address:s:localhost:3389
      addIfMissing(rdpSettingsMap, RdpSetting.create(FULL_ADDRESS, _hostnameAdvertised + ":" + _rdpPort));

      // username:s:red
      addIfMissing(rdpSettingsMap, RdpSetting.create(USERNAME, user));

      // loadbalanceinfo:s:
      addIfMissing(rdpSettingsMap, RdpSetting.create(LOADBALANCEINFO, loadBalanceInfo));

      String filename = _store.getFilename(user);
      response.header(CONTENT_TYPE, "application/rdp; charset=utf-8");
      response.header(CONTENT_DISPOSITION, "attachment; filename=" + filename);
      HttpServletResponse servletResponse = response.raw();
      PrintWriter printWriter = servletResponse.getWriter();
      for (RdpSetting setting : rdpSettingsMap.values()) {
        printWriter.println(setting + "\r");
      }
      printWriter.flush();
      return null;
    };

    _gatewayService.get("/rdp-info/:user", infoRoute);
    _gatewayService.get("/rdp-json/:user", infoRoute, JSON_TRANSFORMER);
    _gatewayService.get("/rdp-file/:user", rdpFileRoute);
  }

  @Override
  public void close() throws IOException {
    _gatewayService.stop();
  }

  private void addIfMissing(Map<String, RdpSetting> rdpSettingsMap, RdpSetting setting) {
    if (!rdpSettingsMap.containsKey(setting.getName())) {
      rdpSettingsMap.put(setting.getName(), setting);
    }
  }

  private Map<String, RdpSetting> toMap(List<RdpSetting> rdpSettings) {
    Map<String, RdpSetting> map = new HashMap<>();
    for (RdpSetting rdpSetting : rdpSettings) {
      map.put(rdpSetting.getName(), rdpSetting);
    }
    return map;
  }
}
