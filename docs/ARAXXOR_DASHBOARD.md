# Araxxor Fight Dashboard

The dashboard is a read-only local view of Araxxor telemetry from `~/.runelite/logs/client.log`.

Start it from the repository root:

```powershell
.\gradlew.bat :client:openAraxxorDashboard
```

It opens `http://127.0.0.1:8082/` and refreshes every 750 ms. Keep the command running while using the dashboard; press `Ctrl+C` to stop it. The game client may run in a separate terminal with `./gradlew :client:run`.

For a different port or to suppress automatic browser opening:

```powershell
.\gradlew.bat :client:openAraxxorDashboard --args="--port=8090 --no-open"
```

The server binds only to loopback, accepts only local GET requests, and exposes filtered Araxxor events rather than the full client log.
