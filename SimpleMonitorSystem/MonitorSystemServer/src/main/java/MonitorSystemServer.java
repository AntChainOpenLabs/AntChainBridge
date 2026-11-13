import io.grpc.Server;
import io.grpc.stub.StreamObserver;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import com.alipay.antchain.bridge.commons.core.base.UniformCrosschainPacket;
import com.alipay.antchain.bridge.ptc.committee.monitor.system.grpc.*;

import java.io.File;
import java.io.IOException;
import java.util.Scanner;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

public class MonitorSystemServer {

    private static final Logger LOGGER = createLogger();

    private Server server;

    static final String VERIFY_DELAY_PROPERTY = "monitor.verify.delay.ms";

    interface DelayStrategy {
        void delay(long delayMillis) throws InterruptedException;
    }

    private final long verifyDelayMillis;

    private final DelayStrategy delayStrategy;

    enum VerifyMode {
        SUCCESS,
        FAILURE,
        INTERNAL_ERROR,
        UNAVAILABLE
    }

    // 控制 verify 的返回结果，默认返回成功
    private volatile VerifyMode verifyMode = VerifyMode.SUCCESS;

    public MonitorSystemServer() {
        this(readVerifyDelayMillis(), Thread::sleep);
    }

    MonitorSystemServer(long verifyDelayMillis, DelayStrategy delayStrategy) {
        if (verifyDelayMillis < 0) {
            throw new IllegalArgumentException(VERIFY_DELAY_PROPERTY + " must not be negative");
        }
        if (delayStrategy == null) {
            throw new IllegalArgumentException("delayStrategy must not be null");
        }
        this.verifyDelayMillis = verifyDelayMillis;
        this.delayStrategy = delayStrategy;
    }

    public void start(int port) throws Exception {
        server = NettyServerBuilder.forPort(port)
                .useTransportSecurity(
                        new File("tls_certs/monitor-system.crt"),
                        new File("tls_certs/monitor-system.key")
                )
                .addService(new MonitorSystemServiceImpl())
                .build()
                .start();

        logInfo("gRPC TLS server started, listening on " + port);

        // 添加 Ctrl+C 优雅关闭 hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logInfo("Shutting down gRPC server...");
            MonitorSystemServer.this.stop();
            logInfo("Server shut down.");
        }));

        // 仅在交互式终端中启动控制线程。systemd/nohup 后台运行时没有可用的 stdin，
        // 此时保持默认的验证成功行为，避免 Scanner.nextLine() 因 EOF 退出并打印异常。
        if (System.console() != null) {
            new Thread(this::startCommandListener, "CommandListenerThread").start();
        } else {
            logInfo("No interactive console detected; command listener disabled and verify result defaults to success.");
        }
    }

    private void startCommandListener() {
        Scanner scanner = new Scanner(System.in);
        logInfo("请输入 'success'、'fail'、'500' 或 '503' 来控制 verifyCrossChainMessageInMonitorSystem 返回结果：");
        while (true) {
            String input = scanner.nextLine();
            if ("success".equalsIgnoreCase(input)) {
                verifyMode = VerifyMode.SUCCESS;
                logInfo("切换为：返回成功");
            } else if ("fail".equalsIgnoreCase(input)) {
                verifyMode = VerifyMode.FAILURE;
                logInfo("切换为：返回失败");
            } else if ("500".equals(input)) {
                verifyMode = VerifyMode.INTERNAL_ERROR;
                logInfo("切换为：返回内部错误(code=500)");
            } else if ("503".equals(input)) {
                verifyMode = VerifyMode.UNAVAILABLE;
                logInfo("切换为：返回服务不可用(code=503)");
            } else {
                logInfo("无效指令，请输入 'success'、'fail'、'500' 或 '503'");
            }
        }
    }

    void setVerifyMode(VerifyMode verifyMode) {
        this.verifyMode = verifyMode;
    }

    public void stop() {
        if (server != null) {
            server.shutdown();
        }
    }

    public void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }

    class MonitorSystemServiceImpl extends MonitorSystemServiceGrpc.MonitorSystemServiceImplBase {
        @Override
        public void heartbeat(Empty request, StreamObserver<MonitorSystemResponse> responseObserver) {
            MonitorSystemResponse response = MonitorSystemResponse.newBuilder()
                    .setCode(0)
                    .setErrorMsg("")
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }

        @Override
        public void verifyCrossChainMessageInMonitorSystem(
                VerifyCrossChainMessageInMonitorSystemRequest request,
                StreamObserver<MonitorSystemResponse> responseObserver) {

            MonitorSystemResponse response;
            try {
                response = buildVerifyResponse(request);
            } catch (RuntimeException e) {
                logWarning("Unexpected error while processing verify request", e);
                response = errorResponse(500, "internal monitor system error: " + errorMessage(e));
            }

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }

        MonitorSystemResponse buildVerifyResponse(VerifyCrossChainMessageInMonitorSystemRequest request) {

            applyVerificationDelay();

            byte[] rawUcp = request.getRawUcp().toByteArray();
            logInfo("Received verifyCrossChainMessageInMonitorSystem request:");
            logInfo("ucpId: " + request.getUcpId());
            logInfo("rawUcp (hex): " + bytesToHex(rawUcp));

            if (verifyMode == VerifyMode.INTERNAL_ERROR) {
                return errorResponse(500, "simulated internal monitor system error");
            }
            if (verifyMode == VerifyMode.UNAVAILABLE) {
                return errorResponse(503, "simulated monitor system unavailable");
            }

            try {
                if (UniformCrosschainPacket.decode(rawUcp) == null) {
                    return errorResponse(400, "failed to decode rawUcp: decoded UCP is null");
                }
            } catch (RuntimeException e) {
                return errorResponse(400, "failed to decode rawUcp: " + errorMessage(e));
            }

            MonitorSystemResponse.Builder responseBuilder = MonitorSystemResponse.newBuilder()
                    .setCode(0)
                    .setErrorMsg("");
            if (verifyMode == VerifyMode.SUCCESS) {
                responseBuilder.setVerifyCrossChainMessageInMonitorSystemResp(
                        VerifyCrossChainMessageInMonitorSystemResponse.newBuilder()
                                .setResult(0)
                                .setMsg("ok")
                                .build());
                logInfo("返回：成功");
            } else {
                responseBuilder.setVerifyCrossChainMessageInMonitorSystemResp(
                        VerifyCrossChainMessageInMonitorSystemResponse.newBuilder()
                                .setResult(1)
                                .setMsg("simulated regulation rejection: matched test rule")
                                .build());
                logInfo("返回：失败");
            }

            return responseBuilder.build();
        }

        @Override
        public void relayUcpToMonitorSystem(
                RelayUcpToMonitorSystemRequest request,
                StreamObserver<MonitorSystemResponse> responseObserver) {

            responseObserver.onNext(buildRelayResponse(request));
            responseObserver.onCompleted();
        }

        MonitorSystemResponse buildRelayResponse(RelayUcpToMonitorSystemRequest request) {

            try {
                byte[] rawUcp = request.getRawUcp().toByteArray();
                logInfo("Received relayUcpToMonitorSystem request:");
                logInfo("ucpId: " + request.getUcpId());
                logInfo("rawUcp (hex): " + bytesToHex(rawUcp));
            } catch (RuntimeException e) {
                // relay 是旁路留存接口，内部处理失败只记录日志，不能影响跨链流程。
                logWarning("Failed to process relay request, returning success as required", e);
            }

            return MonitorSystemResponse.newBuilder()
                    .setCode(0)
                    .setErrorMsg("")
                    .build();
        }

        private String bytesToHex(byte[] bytes) {
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02X", b));
            }
            return sb.toString();
        }

        private MonitorSystemResponse errorResponse(int code, String errorMsg) {
            return MonitorSystemResponse.newBuilder()
                    .setCode(code)
                    .setErrorMsg(errorMsg)
                    .build();
        }

        private String errorMessage(RuntimeException e) {
            return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        }
    }

    private void applyVerificationDelay() {
        if (verifyDelayMillis == 0) {
            return;
        }
        try {
            delayStrategy.delay(verifyDelayMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("monitor verification delay interrupted", e);
        }
    }

    static long readVerifyDelayMillis() {
        String configured = System.getProperty(VERIFY_DELAY_PROPERTY, "0").trim();
        try {
            long value = Long.parseLong(configured);
            if (value < 0) {
                throw new IllegalArgumentException(VERIFY_DELAY_PROPERTY + " must not be negative: " + value);
            }
            return value;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    VERIFY_DELAY_PROPERTY + " must be an integer number of milliseconds: " + configured,
                    e
            );
        }
    }

    private static void logInfo(String message) {
        LOGGER.info(message);
    }

    private static void logWarning(String message, Throwable throwable) {
        LOGGER.log(Level.WARNING, message, throwable);
    }

    private static Logger createLogger() {
        Logger logger = Logger.getLogger(MonitorSystemServer.class.getName());
        logger.setUseParentHandlers(false);
        logger.setLevel(Level.INFO);

        for (Handler handler : logger.getHandlers()) {
            logger.removeHandler(handler);
        }

        Formatter formatter = new Formatter() {
            @Override
            public String format(LogRecord record) {
                return String.format("%1$tF %1$tT [%2$s] %3$s%n",
                        record.getMillis(),
                        record.getLevel().getName(),
                        record.getMessage());
            }
        };

        ConsoleHandler consoleHandler = new ConsoleHandler();
        consoleHandler.setLevel(Level.INFO);
        consoleHandler.setFormatter(formatter);
        logger.addHandler(consoleHandler);

        try {
            File logDir = new File("logs");
            if (!logDir.exists() && !logDir.mkdirs()) {
                throw new IOException("failed to create logs directory");
            }
            FileHandler fileHandler = new FileHandler("logs/monitor-system.log", true);
            fileHandler.setLevel(Level.INFO);
            fileHandler.setFormatter(formatter);
            logger.addHandler(fileHandler);
        } catch (IOException e) {
            consoleHandler.publish(new LogRecord(Level.WARNING,
                    "failed to initialize file logger: " + e.getMessage()));
        }

        return logger;
    }

    public static void main(String[] args) throws Exception {
        int port = 50051;
        MonitorSystemServer server = new MonitorSystemServer();
        server.start(port);
        server.blockUntilShutdown(); // 阻塞，直到关闭
    }
}
