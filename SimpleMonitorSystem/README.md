# 介绍
SimpleMonitorSystem是一个仅用于测试跨链监管功能是否正常的简单监管系统服务。使用mvn clean package编译后会在文件夹根目录生成可执行的jar文件。

## MonitorAPI
包含与monitor-node相同的proto文件。

## MonitorSystemServer

与监管节点建立grpc通信的监管服务。运行后可选择接收到监管节点发送来的消息后返回成功（success）或失败（fail）。

### Linux后台运行

服务固定监听`50051`端口，并从项目根目录的`tls_certs`读取TLS证书。首次运行前生成证书：

```shell
cd /path/to/SimpleMonitorSystem
chmod +x init_tls_certs.sh bin/*.sh
./init_tls_certs.sh
```

普通后台模式：

```shell
./bin/start.sh
./bin/stop.sh
```

论文时延实验可只对监管验证接口增加固定延迟；默认值为`0`，正式Xun实验使用`500ms`：

```shell
JAVA_OPTS="-Dmonitor.verify.delay.ms=500" ./bin/start.sh
```

该参数仅作用于`verifyCrossChainMessageInMonitorSystem`，不会延迟旁路留存接口
`relayUcpToMonitorSystem`。负数或非整数配置会阻止服务启动。

systemd服务模式（支持开机启动和异常自动重启）：

```shell
sudo ./bin/start.sh -s
sudo systemctl status simple-monitor-system --no-pager
sudo journalctl -u simple-monitor-system -f
sudo ./bin/stop.sh
```

systemd模式会把服务文件安装到`/etc/systemd/system/simple-monitor-system.service`。
后台模式没有交互式终端，监管验证结果保持默认的成功状态；前台直接运行Jar时仍可输入`success`或`fail`切换结果。

前台模式还支持输入`500`或`503`，用于模拟监管系统内部错误和暂时不可用。`verifyCrossChainMessageInMonitorSystem`会先校验`rawUcp`，无法解析时返回`code=400`且不填充判定响应；`relayUcpToMonitorSystem`始终返回`code=0`且不填充判定响应。

## MonitorSystemClient

仅用于测试MonitorSystemServer功能是否正常。

## MonitorOrderClient

向监管节点发送监管指令的服务。通过修改MonitorOrderClient.java中第45行构造监管指令的代码，并编译运行来向监管节点发送不同的监管指令。
