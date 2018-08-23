package rdp.proxy.server;

import java.io.Closeable;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;

import javax.servlet.http.HttpServletResponse;

import com.codahale.metrics.MetricRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;

import rdp.proxy.server.admin.ConnectionInfoAdminPage;
import rdp.proxy.server.admin.ConnectionInfoAdminPage.ConnectionInfoAdminPageBuilder;
import rdp.proxy.server.metrics.JsonCounter;
import rdp.proxy.server.metrics.JsonHistogram;
import rdp.proxy.server.metrics.JsonMeter;
import rdp.proxy.server.metrics.JsonReport;
import rdp.proxy.server.metrics.JsonReporter;
import rdp.proxy.server.metrics.JsonTimer;
import rdp.proxy.server.metrics.SetupJvmMetrics;
import rdp.proxy.server.relay.ConnectionProxyInstance;
import rdp.proxy.server.relay.RdpConnectionRelay;
import rdp.proxy.server.relay.SocketInfo;
import rdp.proxy.server.util.Utils;
import rdp.proxy.spi.RdpSetting;
import rdp.proxy.spi.RdpStore;
import spark.ModelAndView;
import spark.ResponseTransformer;
import spark.Route;
import spark.Service;
import spark.template.freemarker.FreeMarkerEngine;

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
    Object lock = new Object();
    RdpProxyConfig config = Utils.getConfig();
    synchronized (lock) {
      try (RdpProxy proxy = new RdpProxy(config)) {
        proxy.initGateway();
        proxy.initAdmin();
        proxy.start();
        lock.wait();
      }
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
    _reporter.start(0, 5, TimeUnit.SECONDS);
    _relay = new RdpConnectionRelay(_config, _store, _metrics);
    SetupJvmMetrics.setup(_metrics);
  }

  public boolean isListening() {
    return _relay.isListening();
  }

  public void start() {
    _relay.startListening();
  }

  public void initAdmin() {
    ResponseTransformer jsonTransformer = model -> new ObjectMapper().writeValueAsString(model);
    _adminService.get("/stats", (Route) (request, response) -> _reporter.getReport(), jsonTransformer);
    _adminService.post("/kill/:id", (request, response) -> {
      String id = request.params("id");
      _relay.kill(id);
      response.redirect("/");
      return null;
    });
    _adminService.post("/listen/enable", (request, response) -> {
      _relay.startListening();
      response.redirect("/");
      return null;
    });
    _adminService.post("/listen/disable", (request, response) -> {
      _relay.stopListening();
      response.redirect("/");
      return null;
    });
    _adminService.get("/", (request, response) -> {
      Map<String, Object> attributes = new HashMap<>();
      JsonReport jsonReport = _reporter.getReport();
      Map<String, JsonHistogram> histograms = jsonReport.getHistograms();

      JsonHistogram heapUsedHistogram = histograms.get("jvm.heap.used.histogram");
      attributes.put("heapUsedHistogramMean", heapUsedHistogram == null ? 0 : (long) heapUsedHistogram.getMean());

      JsonHistogram heapMaxHistogram = histograms.get("jvm.heap.max.histogram");
      attributes.put("heapMaxHistogramMean", heapMaxHistogram == null ? 0 : (long) heapMaxHistogram.getMean());

      Map<String, JsonCounter> counters = jsonReport.getCounters();
      JsonCounter counter = counters.get(RdpConnectionRelay.RDP_CONNECTIONS_COUNTER);
      attributes.put("connectionCount", counter == null ? 0 : counter.getCount());

      Map<String, JsonTimer> timers = jsonReport.getTimers();
      JsonTimer clientToServer = timers.get(RdpConnectionRelay.RDP_CONNECTION_TIMER_CLIENT_TO_SERVER);
      attributes.put("clientToServerAvgRelayLatency", clientToServer == null ? 0 : toMs(clientToServer.getMean()));
      attributes.put("clientToServer99RelayLatency", clientToServer == null ? 0 : toMs(clientToServer.getP99th()));
      attributes.put("clientToServer999RelayLatency", clientToServer == null ? 0 : toMs(clientToServer.getP999th()));

      JsonTimer serverToClient = timers.get(RdpConnectionRelay.RDP_CONNECTION_TIMER_SERVER_TO_CLIENT);
      attributes.put("serverToClientAvgRelayLatency", clientToServer == null ? 0 : toMs(serverToClient.getMean()));
      attributes.put("serverToClient99RelayLatency", clientToServer == null ? 0 : toMs(serverToClient.getP99th()));
      attributes.put("serverToClient999RelayLatency", clientToServer == null ? 0 : toMs(serverToClient.getP999th()));

      Map<String, JsonMeter> meters = jsonReport.getMeters();
      JsonMeter clientToServerMeterServer = meters.get(RdpConnectionRelay.RDP_CONNECTION_METER_CLIENT_TO_SERVER);
      JsonMeter serverToClientMeterServer = meters.get(RdpConnectionRelay.RDP_CONNECTION_METER_SERVER_TO_CLIENT);
      attributes.put("clientToServerBandwidth",
          clientToServerMeterServer == null ? 0 : (long) clientToServerMeterServer.getOneMinuteRate());
      attributes.put("serverToClientBandwidth",
          serverToClientMeterServer == null ? 0 : (long) serverToClientMeterServer.getOneMinuteRate());

      Map<String, ConnectionProxyInstance> connectionMap = _relay.getConnectionMap();
      List<ConnectionInfoAdminPage> list = new ArrayList<>();
      for (Entry<String, ConnectionProxyInstance> e : connectionMap.entrySet()) {
        String id = e.getKey();
        ConnectionProxyInstance connectionProxyInstance = e.getValue();
        SocketInfo rdpServer = connectionProxyInstance.getServer();
        SocketInfo rdpClient = connectionProxyInstance.getClient();
        String hostName = rdpServer.getInetAddress()
                                   .getHostName();

        ConnectionInfoAdminPageBuilder builder = ConnectionInfoAdminPage.builder()
                                                                        .id(id);

        JsonMeter clientToServerMeter = meters.get(RdpConnectionRelay.getClientToServerBandwidthName(id));
        if (clientToServerMeter != null) {
          builder.clientToServerBandwidth(toKiB(clientToServerMeter.getMeanRate()))
                 .clientToServerBandwidthOneMinute(toKiB(clientToServerMeter.getOneMinuteRate()))
                 .clientToServerBandwidthTotal(toKiB(clientToServerMeter.getCount()));
        }

        JsonMeter serverToClientMeter = meters.get(RdpConnectionRelay.getServerToClientBandwidthName(id));
        if (serverToClientMeter != null) {
          builder.serverToClientBandwidth(toKiB(serverToClientMeter.getMeanRate()))
                 .serverToClientBandwidthOneMinute(toKiB(serverToClientMeter.getOneMinuteRate()))
                 .serverToClientBandwidthTotal(toKiB(serverToClientMeter.getCount()));
        }

        list.add(builder.host(hostName)
                        .rdpClient(rdpClient.getInfo())
                        .rdpServer(rdpServer.getInfo())
                        .build());
      }
      Collections.sort(list);
      attributes.put("connections", list);

      attributes.put("listening", _relay.isListening());

      return new ModelAndView(attributes, "index.ftl");

    }, new FreeMarkerEngine());
  }

  private double toKiB(double rate) {
    long r = (long) rate;
    return (double) r / 1024.0;
  }

  private double toMs(double nanoSec) {
    return nanoSec / 1_000_000.0;
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
    Utils.closeQuietly(() -> _gatewayService.stop());
    Utils.closeQuietly(() -> _adminService.stop());
    Utils.closeQuietly(_relay);
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
