# M.A.R.S.

## Run

You can run the executables from console specifying the JAR path, using the correct \<VERSION\>, and the main class name as follows.

### Communication test apps

These apps are meant for testing send and revceive of all the messages.

```
java --enable-preview -cp tower-<VERSION>.jar mars.tower.comms.TowerMsgMain

java --enable-preview -cp platform-comm-<VERSION>.jar mars.platform.comms.PlatformMsgMain

java --enable-preview -cp mc-comm-<VERSION>.jar mars.mc.comms.McMsgMain
```

### Applications

The `TowerMain` application is the real application.

The `PlatformMain` application is the platform simulator.

The `Mc1` application is a sample of a mission controller.

```
java --enable-preview -cp tower-<VERSION>.jar mars.tower.TowerMain

java --enable-preview -cp mc-sample-<VERSION>.jar Mc1

java --enable-preview -cp platform-sim-<VERSION>.jar platform.sim.PlatformMain
```