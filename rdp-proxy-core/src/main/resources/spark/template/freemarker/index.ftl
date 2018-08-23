<html>
<title>RDP Gateway</title>
<body>
<h3>RDP</h3>
<table border=1>
    <tr><th>Connection Count</th><td>${connectionCount}</td></tr>
    <tr><th>Client to Server c=>s</th><td>&nbsp;</td></tr>
    <tr><th>Bandwith Byte/s</th><td>${clientToServerBandwidth}</td></tr>
    <tr><th>Avg. Relay Latency</th><td>${clientToServerAvgRelayLatency} ms</td></tr>
    <tr><th>99% Relay Latency</th><td>${clientToServer99RelayLatency} ms</td></tr>
    <tr><th>99.9% Relay Latency</th><td>${clientToServer999RelayLatency} ms</td></tr>
    <tr><th>Server to Client s=>c</th><td>&nbsp;</td></tr>
    <tr><th>Bandwith Byte/s</th><td>${serverToClientBandwidth}</td></tr>
    <tr><th>Avg. Relay Latency</th><td>${serverToClientAvgRelayLatency} ms</td></tr>
    <tr><th>99% Relay Latency</th><td>${serverToClient99RelayLatency} ms</td></tr>
    <tr><th>99.9% Relay Latency</th><td>${serverToClient999RelayLatency} ms</td></tr>    
</table>

<h3>Connections</h3>
<table border=1>
   <tr>
     <th>Host</th>
     <th>Id</th>
     <th>client info</th>
     <th>c=>s Avg</th>
     <th>c=>s 1 Min Avg</th>
     <th>c=>s Total</th>
     <th>server info</th>
     <th>s=>c Avg</th>
     <th>s=>c 1 Min Avg</th>
     <th>s=>c Total</th>
     <th>Kill</th>
    </tr>
    <#list connections as connection>
    <tr>
      <th>${connection.host}</th>
      <td>${connection.id}</td>
      <td>${connection.rdpClient}</td>
      <td>${connection.clientToServerBandwidth} KiB/s</td>
      <td>${connection.clientToServerBandwidthOneMinute} KiB/s</td>
      <td>${connection.clientToServerBandwidthTotal} KiB</td>
      <td>${connection.rdpServer}</td>
      <td>${connection.serverToClientBandwidth} KiB/s</td>
      <td>${connection.serverToClientBandwidthOneMinute} KiB/s</td>
      <td>${connection.serverToClientBandwidthTotal} KiB</td>
      <form action="/kill/${connection.id}" method="post">
        <td><input type="submit" name="kill" value="Kill"/></td>
      </form>
    </tr>
	</#list>
</table>

<h3>JVM</h3>
<table border=1>
    <tr><th>Heap Used</th><td>${heapUsedHistogramMean}</td></tr>
    <tr><th>Heap Max</th><td>${heapMaxHistogramMean}</td></tr>
</table>
<br/>

<#if listening>
<form action="/listen/disable" method="post">
<input type="submit" name="disable" value="Disable Listener"/>
</form>
<#else>
<form action="/listen/enable" method="post">
<input type="submit" name="enable" value="Enable Listener"/>
</form>
</#if>



</body>
</html>