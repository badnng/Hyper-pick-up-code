package com.Badnng.moe.wearable;

import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;

import com.xiaomi.xms.wearable.Status;
import com.xiaomi.xms.wearable.Wearable;
import com.xiaomi.xms.wearable.auth.AuthApi;
import com.xiaomi.xms.wearable.auth.Permission;
import com.xiaomi.xms.wearable.message.MessageApi;
import com.xiaomi.xms.wearable.message.OnMessageReceivedListener;
import com.xiaomi.xms.wearable.notify.NotifyApi;
import com.xiaomi.xms.wearable.node.DataItem;
import com.xiaomi.xms.wearable.node.DataQueryResult;
import com.xiaomi.xms.wearable.node.DataSubscribeResult;
import com.xiaomi.xms.wearable.node.Node;
import com.xiaomi.xms.wearable.node.NodeApi;
import com.xiaomi.xms.wearable.service.OnServiceConnectionListener;
import com.xiaomi.xms.wearable.service.ServiceApi;
import com.xiaomi.xms.wearable.tasks.OnFailureListener;
import com.xiaomi.xms.wearable.tasks.OnSuccessListener;
import com.xiaomi.xms.wearable.tasks.Task;

import java.util.List;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Official XMS Wearable SDK bridge.
 *
 * The implementation intentionally follows the official demo's API boundary:
 * Wearable.getNodeApi(), getAuthApi() and getMessageApi(), with Task callbacks
 * and no hand-written AIDL or reflection.
 */
public final class XiaomiWearableBridge implements WearableSdkBridge {
    private static final String TAG = "XiaomiWearableBridge";
    private static final String[] SERVICE_PACKAGES = {"com.mi.health", "com.xiaomi.wearable"};
    private static final long CALL_TIMEOUT_MS = 3500L;
    /**
     * XMS 在向监听器广播消息时不允许同时调用 unregisterListener；服务端会返回
     * "beginBroadcast() called while already in a broadcast"。注销是异步 Binder 操作，
     * 因此需要短暂退避重试，不能把一次瞬时冲突当成永久失败。
     */
    private static final int REMOVE_LISTENER_RETRY_COUNT = 8;
    private static final long REMOVE_LISTENER_RETRY_DELAY_MS = 180L;
    /**
     * 自启动冷路径下 XMS 服务绑定尚未完成，节点发现可能短暂失败；
     * 发送系统通知时最多重试 3 次、间隔 600ms 等待服务就绪。
     */
    private static final int NOTIFY_NODE_RETRY_COUNT = 3;
    private static final long NOTIFY_NODE_RETRY_DELAY_MS = 600L;

    private final Object apiLock = new Object();
    private final Context appContext;
    private final NodeApi nodeApi;
    private final AuthApi authApi;
    private final MessageApi messageApi;
    private final NotifyApi notifyApi;
    private final ServiceApi serviceApi;
    private volatile Node currentNode;
    private volatile boolean permissionGranted;
    private volatile boolean notifyPermissionGranted;
    private volatile boolean deviceManagerPermissionGranted;
    private volatile OnMessageReceivedListener messageListener;
    private volatile String listenerNodeId;
    private volatile com.xiaomi.xms.wearable.node.OnDataChangedListener connectionListener;
    private volatile String connectionListenerNodeId;
    private volatile boolean serviceConnectionListenerRegistered;
    private volatile boolean serviceConnectedOnce;
    private volatile String lastError;
    /** 保留最近一次 Binder 失败异常，供包装异常的重试判断检查 cause 链。 */
    private volatile Throwable lastFailure;
    /** XMS 在 RemoteCallbackList 广播期间不允许回调重入；回调线程只复制数据并立即返回。 */
    private final ExecutorService messageCallbackExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "WearableMessageDispatch");
        thread.setDaemon(true);
        return thread;
    });

    /**
     * Mi Fitness reconnects do not restore MessageApi's remote Binder listener,
     * while the SDK keeps its process-local callback reference. Invalidate our
     * local registration flag on every reconnect so the manager can perform the
     * required removeListener -> addListener repair.
     */
    private final OnServiceConnectionListener serviceConnectionListener =
            new OnServiceConnectionListener() {
                @Override
                public void onServiceConnected() {
                    // ⚠️ 不在回调里持有 apiLock：XMS 回调线程（DefaultDispatch）在回调期间
                    // 可能长时间 park（实测 3.3s+），若同时持锁，任何触碰 apiLock 的线程
                    // （含主线程）都会被连带阻塞，造成冷启动后 UI 长时间无响应。
                    // 回调内只更新 volatile 字段，无需同步块。
                    boolean reconnect = serviceConnectedOnce;
                    serviceConnectedOnce = true;
                    log("Mi Fitness 服务已连接: reconnect=" + reconnect);
                    if (reconnect) {
                        invalidateRemoteListeners("Mi Fitness 服务重新连接");
                    }
                }

                @Override
                public void onServiceDisconnected() {
                    // 同样无锁化；只清理 volatile 标志，等待 manager 重新发现节点后自愈。
                    log("Mi Fitness 服务已断开");
                    invalidateRemoteListeners("Mi Fitness 服务断开");
                }
            };

    public XiaomiWearableBridge(Context context) {
        appContext = context.getApplicationContext();
        NodeApi node = null;
        AuthApi auth = null;
        MessageApi message = null;
        NotifyApi notify = null;
        ServiceApi service = null;
        try {
            node = Wearable.getNodeApi(appContext);
            auth = Wearable.getAuthApi(appContext);
            message = Wearable.getMessageApi(appContext);
            notify = Wearable.getNotifyApi(appContext);
            service = Wearable.getServiceApi(appContext);
        } catch (Throwable error) {
            lastError = "小米穿戴 SDK 初始化失败: " + error.getClass().getSimpleName();
            Log.w(TAG, "官方 SDK 初始化失败", error);
        }
        nodeApi = node;
        authApi = auth;
        messageApi = message;
        notifyApi = notify;
        serviceApi = service;
        log("初始化完成: nodeApi=" + (nodeApi != null)
                + ", authApi=" + (authApi != null)
                + ", messageApi=" + (messageApi != null)
                + ", notifyApi=" + (notifyApi != null)
                + ", serviceApi=" + (serviceApi != null));
    }

    @Override
    public boolean isSdkAvailable() {
        if (nodeApi == null || authApi == null || messageApi == null) {
            log("SDK 可用性检查失败: API 未初始化");
            return false;
        }
        // AAR 的 AndroidManifest 只声明了可查询的穿戴服务包，并没有公开
        // 一个可供 resolveService() 探测的 XMS_WEARABLE_SERVICE Action。
        // 之前用自定义 Action 探测会稳定得到 false，进而让同步管理器跳过所有发送。
        PackageManager packageManager = appContext.getPackageManager();
        for (String packageName : SERVICE_PACKAGES) {
            try {
                packageManager.getPackageInfo(packageName, 0);
                log("SDK 服务包探测成功: package=" + packageName);
                return true;
            } catch (PackageManager.NameNotFoundException error) {
                log("SDK 服务包未安装: package=" + packageName);
            } catch (Throwable error) {
                log("SDK 服务包探测异常: package=" + packageName + ", " + errorMessage(error));
            }
        }
        log("SDK 服务探测失败: 未发现 Mi Fitness/Xiaomi Wearable 包");
        return false;
    }

    @Override
    public boolean isPermissionGranted() {
        return notifyPermissionGranted && deviceManagerPermissionGranted;
    }

    @Override
    public boolean isNotifyPermissionGranted() {
        return notifyPermissionGranted;
    }

    @Override
    public boolean isDeviceManagerPermissionGranted() {
        return deviceManagerPermissionGranted;
    }

    @Override
    public String getLastError() {
        return lastError;
    }

    /** 官方 demo 的 NodeApi.getConnectedNodes()。 */
    @Override
    public String findNodeId(Context context) {
        synchronized (apiLock) {
            ensureServiceConnectionListener();
            log("开始查找已连接手表");
            if (nodeApi == null) {
                lastError = "小米穿戴 SDK 不可用";
                log("查找手表失败: nodeApi=null");
                return null;
            }
            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<List<Node>> nodes = new AtomicReference<>();
            try {
                Task<List<Node>> task = nodeApi.getConnectedNodes();
                if (task == null) {
                    lastError = "获取已连接设备失败";
                    log("getConnectedNodes 返回 null");
                    return null;
                }
                log("getConnectedNodes Task 已创建");
                task.addOnSuccessListener(new OnSuccessListener<List<Node>>() {
                    @Override
                    public void onSuccess(List<Node> result) {
                        nodes.set(result);
                        StringBuilder detail = new StringBuilder();
                        if (result != null) {
                            for (Node node : result) {
                                if (detail.length() > 0) detail.append("; ");
                                detail.append(node == null ? "null" : (node.id + "/" + node.name));
                            }
                        }
                        log("getConnectedNodes 成功: count="
                                + (result == null ? 0 : result.size()) + ", nodes=" + detail);
                        latch.countDown();
                    }
                }).addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(Exception error) {
                        log("getConnectedNodes 失败: " + errorMessage(error));
                        setFailure("获取已连接设备失败", error);
                        latch.countDown();
                    }
                });
                if (!await(latch, "获取已连接设备超时")) return null;
            } catch (Throwable error) {
                setFailure("获取已连接设备失败", error);
                return null;
            }

            List<Node> result = nodes.get();
            currentNode = result != null && !result.isEmpty() ? result.get(0) : null;
            if (currentNode == null || currentNode.id == null) {
                permissionGranted = false;
                notifyPermissionGranted = false;
                deviceManagerPermissionGranted = false;
                if (lastError == null) lastError = "未发现已连接的手表";
                log("未发现已连接手表");
                return null;
            }

            notifyPermissionGranted = queryPermission(currentNode.id, Permission.NOTIFY);
            deviceManagerPermissionGranted =
                    queryPermission(currentNode.id, Permission.DEVICE_MANAGER);
            permissionGranted = notifyPermissionGranted && deviceManagerPermissionGranted;
            log("选择手表节点: id=" + currentNode.id + ", name=" + currentNode.name
                    + ", notify=" + notifyPermissionGranted
                    + ", deviceManager=" + deviceManagerPermissionGranted
                    + ", permissionGranted=" + permissionGranted);
            return currentNode.id;
        }
    }

    /** 查询单项权限授权状态。 */
    private boolean queryPermission(String nodeId, Permission permission) {
        log("查询手表单项权限: nodeId=" + nodeId + ", permission=" + permission.getName());
        if (authApi == null) {
            lastError = "小米穿戴授权 API 不可用";
            log("查询手表权限失败: authApi=null");
            return false;
        }
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean granted = new AtomicBoolean(false);
        try {
            Task<Boolean> task = authApi.checkPermission(nodeId, permission);
            if (task == null) {
                lastError = "查询手表权限失败";
                log("checkPermission 返回 null: permission=" + permission.getName());
                return false;
            }
            task.addOnSuccessListener(new OnSuccessListener<Boolean>() {
                @Override
                public void onSuccess(Boolean result) {
                    granted.set(result != null && result);
                    log("checkPermission 成功: permission=" + permission.getName()
                            + ", granted=" + granted.get());
                    latch.countDown();
                }
            }).addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(Exception error) {
                    log("checkPermission 失败: " + errorMessage(error));
                    setFailure("查询手表权限失败", error);
                    latch.countDown();
                }
            });
            await(latch, "查询手表权限超时");
        } catch (Throwable error) {
            log("checkPermission 抛出异常: " + errorMessage(error));
            setFailure("查询手表权限失败", error);
        }
        log("查询手表单项权限结束: nodeId=" + nodeId
                + ", permission=" + permission.getName() + ", granted=" + granted.get());
        return granted.get();
    }

    /** 请求通知权限（NOTIFY），不依赖手表端快应用。 */
    @Override
    public boolean requestNotifyPermission(Context context) {
        boolean granted = requestPermissionInternal(permission -> Permission.NOTIFY.getName()
                .equals(permission.getName()), "申请通知权限");
        notifyPermissionGranted = granted;
        permissionGranted = notifyPermissionGranted && deviceManagerPermissionGranted;
        return granted;
    }

    /** 请求设备管理权限（DEVICE_MANAGER），依赖手表端快应用已安装。 */
    @Override
    public boolean requestDeviceManagerPermission(Context context) {
        boolean granted = requestPermissionInternal(permission -> Permission.DEVICE_MANAGER.getName()
                .equals(permission.getName()), "申请设备管理权限");
        deviceManagerPermissionGranted = granted;
        permissionGranted = notifyPermissionGranted && deviceManagerPermissionGranted;
        return granted;
    }

    /** 官方 demo 的 AuthApi.requestPermission(nodeId, Permission...)，按单权限申请。 */
    private boolean requestPermissionInternal(
            java.util.function.Predicate<Permission> targetPermission,
            String action
    ) {
        synchronized (apiLock) {
            log(action + "开始: nodeId=" + currentNodeId());
            // 自启动/恢复期节点可能尚未发现：先同步补一次节点发现，
            // 否则 Mi Fitness 的权限控制项无法建立/校验，授权请求直接失败。
            if (authApi != null && (currentNode == null || currentNode.id == null)) {
                String discovered = findNodeId(contextOrApp());
                if (discovered == null) {
                    lastError = "未发现可授权的手表";
                    log(action + "失败: 自动发现手表节点失败");
                    return false;
                }
            }
            if (authApi == null || currentNode == null || currentNode.id == null) {
                lastError = "未发现可授权的手表";
                log(action + "失败: 没有当前节点或 authApi");
                return false;
            }
            String nodeId = currentNode.id;
            Permission permission = targetPermission.test(Permission.NOTIFY)
                    ? Permission.NOTIFY
                    : Permission.DEVICE_MANAGER;
            CountDownLatch latch = new CountDownLatch(1);
            AtomicBoolean granted = new AtomicBoolean(false);
            try {
                Task<Permission[]> task = authApi.requestPermission(nodeId, permission);
                if (task == null) {
                    lastError = action + "失败";
                    log("requestPermission 返回 null");
                    return false;
                }
                task.addOnSuccessListener(new OnSuccessListener<Permission[]>() {
                    @Override
                    public void onSuccess(Permission[] result) {
                        granted.set(hasGranted(result, targetPermission));
                        lastError = granted.get() ? null : "手表权限未授予";
                        log(action + "成功: permissions=" + permissionArray(result)
                                + ", granted=" + granted.get());
                        latch.countDown();
                    }
                }).addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(Exception error) {
                        log(action + "失败: " + errorMessage(error));
                        setFailure(action + "失败", error);
                        latch.countDown();
                    }
                });
                await(latch, action + "超时");
            } catch (Throwable error) {
                log(action + "抛出异常: " + errorMessage(error));
                setFailure(action + "失败", error);
            }
            log(action + "结束: nodeId=" + nodeId + ", granted=" + granted.get());
            return granted.get();
        }
    }

    private static boolean hasGranted(
            Permission[] result,
            java.util.function.Predicate<Permission> targetPermission
    ) {
        if (result == null) return false;
        for (Permission permission : result) {
            if (permission != null && targetPermission.test(permission)) return true;
        }
        return false;
    }

    /** 官方 demo 的 MessageApi.sendMessage(nodeId, bytes)。 */
    @Override
    public boolean sendMessage(Context context, byte[] bytes) {
        synchronized (apiLock) {
            String nodeId = currentNodeId();
            log("发送消息开始: nodeId=" + nodeId + ", " + describeBytes(bytes));
            if (messageApi == null || nodeId == null || bytes == null) {
                lastError = "消息发送条件不满足";
                log("发送消息跳过: messageApi=" + (messageApi != null)
                        + ", nodeId=" + nodeId + ", bytes=" + (bytes != null));
                return false;
            }
            CountDownLatch latch = new CountDownLatch(1);
            AtomicBoolean sent = new AtomicBoolean(false);
            try {
                Task<Void> task = messageApi.sendMessage(nodeId, bytes);
                if (task == null) {
                    lastError = "消息发送失败";
                    log("sendMessage 返回 null");
                    return false;
                }
                log("sendMessage Task 已创建: nodeId=" + nodeId);
                task.addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void ignored) {
                        sent.set(true);
                        lastError = null;
                        log("发送消息成功: nodeId=" + nodeId + ", bytes=" + bytes.length);
                        latch.countDown();
                    }
                }).addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(Exception error) {
                        log("发送消息失败: nodeId=" + nodeId + ", " + errorMessage(error));
                        setFailure("消息发送失败", error);
                        latch.countDown();
                    }
                });
                await(latch, "消息发送超时");
            } catch (Throwable error) {
                log("sendMessage 抛出异常: " + errorMessage(error));
                setFailure("消息发送失败", error);
            }
            log("发送消息结束: nodeId=" + nodeId + ", sent=" + sent.get()
                    + ", lastError=" + lastError);
            return sent.get();
        }
    }

    /** 官方 demo 的 NotifyApi.sendNotify(nodeId, title, message)。 */
    @Override
    public boolean sendSystemNotify(Context context, String title, String message) {
        synchronized (apiLock) {
            String nodeId = currentNodeId();
            log("发送系统通知开始: nodeId=" + nodeId + ", title=" + title
                    + ", message=" + message);
            // 自启动恢复期节点可能尚未发现（manager 的 refreshNode 还没完成）：
            // 先同步补一次节点发现（IO 线程调用，API 锁可重入），确保通知真正走到 SDK。
            // XMS 服务绑定是异步的，首次发现失败时等待片刻重试，覆盖自启动冷路径。
            if (nodeId == null) {
                String discovered = null;
                for (int attempt = 1; attempt <= NOTIFY_NODE_RETRY_COUNT && discovered == null; attempt++) {
                    discovered = findNodeId(context);
                    if (discovered == null && attempt < NOTIFY_NODE_RETRY_COUNT) {
                        log("发送系统通知: 自动发现第 " + attempt + " 次失败，等待 XMS 服务绑定后重试");
                        try {
                            Thread.sleep(NOTIFY_NODE_RETRY_DELAY_MS);
                        } catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
                if (discovered == null) {
                    lastError = "未发现已连接的手表";
                    log("发送系统通知跳过: 自动发现手表节点失败");
                    return false;
                }
                nodeId = discovered;
            }
            if (notifyApi == null || nodeId == null || title == null || message == null) {
                lastError = "系统通知发送条件不满足";
                log("发送系统通知跳过: notifyApi=" + (notifyApi != null)
                        + ", nodeId=" + nodeId + ", title=" + (title != null)
                        + ", message=" + (message != null));
                return false;
            }
            // 匿名回调捕获要求 effectively final：复制一份发送用节点 id。
            final String sendNodeId = nodeId;
            CountDownLatch latch = new CountDownLatch(1);
            AtomicBoolean sent = new AtomicBoolean(false);
            try {
                Task<Status> task = notifyApi.sendNotify(sendNodeId, title, message);
                if (task == null) {
                    lastError = "发送系统通知失败";
                    log("sendNotify 返回 null");
                    return false;
                }
                log("sendNotify Task 已创建: nodeId=" + sendNodeId);
                task.addOnSuccessListener(new OnSuccessListener<Status>() {
                    @Override
                    public void onSuccess(Status status) {
                        if (status != null && status.isSuccess()) {
                            sent.set(true);
                            lastError = null;
                            log("发送系统通知成功: nodeId=" + sendNodeId + ", title=" + title);
                        } else {
                            sent.set(false);
                            lastError = "发送系统通知失败: status=" + (status == null ? "null" : status.toString());
                            log("发送系统通知状态失败: nodeId=" + sendNodeId + ", title=" + title
                                    + ", status=" + (status == null ? "null" : status.toString()));
                        }
                        latch.countDown();
                    }
                }).addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(Exception error) {
                        log("发送系统通知失败: nodeId=" + sendNodeId + ", " + errorMessage(error));
                        setFailure("发送系统通知失败", error);
                        latch.countDown();
                    }
                });
                await(latch, "发送系统通知超时");
            } catch (Throwable error) {
                log("sendNotify 抛出异常: " + errorMessage(error));
                setFailure("发送系统通知失败", error);
            }
            log("发送系统通知结束: nodeId=" + nodeId + ", sent=" + sent.get()
                    + ", lastError=" + lastError);
            return sent.get();
        }
    }

    /** 官方 demo 的 MessageApi.addListener(nodeId, listener)。 */
    @Override
    public void setMessageListener(Context context, final MessageListener listener) {
        synchronized (apiLock) {
            ensureServiceConnectionListener();
            String nodeId = currentNodeId();
            log("注册消息监听开始: nodeId=" + nodeId
                    + ", oldRegistered=" + isListenerRegistered());
            if (messageApi == null || nodeId == null || listener == null) {
                log("注册消息监听跳过: messageApi=" + (messageApi != null)
                        + ", nodeId=" + nodeId + ", listener=" + (listener != null));
                return;
            }
            final OnMessageReceivedListener callback = new OnMessageReceivedListener() {
                @Override
                public void onMessageReceived(String messageNodeId, byte[] data) {
                    if (data == null) {
                        log("收到手表消息回调: nodeId=" + messageNodeId + ", bytes=null");
                        return;
                    }
                    try {
                        final byte[] copy = data.clone();
                        // 这里只做 clone + 入队，避免在 Mi Fitness 的 RemoteCallbackList
                        // 广播栈中执行 JSON/Room/日志等耗时工作，触发 beginBroadcast 重入。
                        messageCallbackExecutor.execute(() -> {
                            try {
                                log("收到手表消息回调: nodeId=" + messageNodeId + ", " + describeBytes(copy));
                                listener.onMessage(copy);
                                log("手表消息已异步转交上层: nodeId=" + messageNodeId
                                        + ", bytes=" + copy.length);
                            } catch (Throwable error) {
                                log("异步处理手表消息抛出异常: " + errorMessage(error));
                                Log.w(TAG, "异步处理手表消息失败", error);
                            }
                        });
                        log("手表消息已排入异步队列: nodeId=" + messageNodeId
                                + ", bytes=" + data.length);
                    } catch (Throwable error) {
                        log("上层处理手表消息抛出异常: " + errorMessage(error));
                        Log.w(TAG, "处理手表消息失败", error);
                    }
                }
            };
            boolean registered = addMessageListenerOnce(nodeId, callback);
            if (!registered && isAlreadyRegisteredMessage(lastError)) {
                // AAR 的 apiClient 是进程级单例。Mi Fitness 服务重连后可能残留旧的
                // listener 引用：再次 addListener 只返回 “you have registered”，但旧
                // 回调并未重新挂回服务端。先强制注销，再用当前回调重新注册。
                log("检测到僵尸消息监听，执行 removeListener -> addListener 修复: nodeId=" + nodeId);
                boolean removed = removeMessageListenerWithRetry(nodeId, "修复旧手表消息监听");
                if (removed) {
                    registered = addMessageListenerOnce(nodeId, callback);
                } else {
                    log("僵尸消息监听注销失败，无法重新注册: nodeId=" + nodeId);
                }
            }
            if (registered) {
                messageListener = callback;
                listenerNodeId = nodeId;
                lastError = null;
                log("注册消息监听完成: nodeId=" + nodeId + ", registered=true");
            } else {
                messageListener = null;
                listenerNodeId = null;
                log("注册消息监听完成: nodeId=" + nodeId + ", registered=false");
            }
        }
    }

    @Override
    public boolean isListenerRegistered() {
        return messageListener != null && listenerNodeId != null;
    }

    /**
     * Submit one official addListener call and wait for its Task result.
     * The AAR stores the callback in its process-wide apiClient only after the
     * service acknowledges the registration, so a successful Task is the only
     * point at which the bridge reports the listener as active.
     */
    private boolean addMessageListenerOnce(
            String nodeId,
            OnMessageReceivedListener callback
    ) {
        if (messageApi == null || nodeId == null || callback == null) {
            lastError = "注册手表消息监听条件不满足";
            return false;
        }
        try {
            Task<Void> task = messageApi.addListener(nodeId, callback);
            if (task == null) {
                lastError = "注册手表消息监听失败";
                log("addListener 返回 null: nodeId=" + nodeId);
                return false;
            }
            log("addListener Task 已创建: nodeId=" + nodeId);
            CountDownLatch latch = new CountDownLatch(1);
            AtomicBoolean registered = new AtomicBoolean(false);
            task.addOnSuccessListener(new OnSuccessListener<Void>() {
                @Override
                public void onSuccess(Void ignored) {
                    registered.set(true);
                    lastError = null;
                    log("addListener 成功回调: nodeId=" + nodeId);
                    latch.countDown();
                }
            }).addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(Exception error) {
                    setFailure("注册手表消息监听失败", error);
                    log("addListener 失败回调: nodeId=" + nodeId + ", " + errorMessage(error));
                    latch.countDown();
                }
            });
            await(latch, "注册手表消息监听超时");
            return registered.get();
        } catch (Throwable error) {
            setFailure("注册手表消息监听失败", error);
            log("addListener 抛出异常: nodeId=" + nodeId + ", " + errorMessage(error));
            return false;
        }
    }

    /** 官方 demo 的 MessageApi.removeListener(nodeId)。 */
    @Override
    public void removeMessageListener(Context context) {
        synchronized (apiLock) {
            // addListener 可能已经在 SDK 服务端成功，但 Task 回调在进程切换/超时
            // 时没有回到本地，导致 listenerNodeId 为空。此时也必须按当前节点清理，
            // 否则下一次 addListener 会持续得到 "you have registered"。
            String nodeId = listenerNodeId != null ? listenerNodeId : currentNodeId();
            boolean localRegistered = isListenerRegistered();
            log("注销消息监听开始: nodeId=" + nodeId
                    + ", localRegistered=" + localRegistered);
            boolean removed = false;
            if (messageApi != null && nodeId != null) {
                removed = removeMessageListenerWithRetry(nodeId, "注销手表消息监听");
            }
            // 本地有注册但注销失败时保留状态，避免马上再次 addListener；
            // 下次看门狗会继续观察，AAR 自身仍持有的监听也不会被误判为丢失。
            if (removed || !localRegistered) {
                messageListener = null;
                listenerNodeId = null;
            }
            log("注销消息监听结束: nodeId=" + nodeId + ", removed=" + removed
                    + ", localRegistered=" + isListenerRegistered());
        }
    }

    /**
     * 在 XMS 正在 beginBroadcast 时，延迟并重试 unregisterListener。
     * 该错误只表示服务端当前处于一次广播临界区，不表示监听已损坏。
     */
    private boolean removeMessageListenerWithRetry(String nodeId, String action) {
        if (messageApi == null || nodeId == null) return false;
        for (int attempt = 1; attempt <= REMOVE_LISTENER_RETRY_COUNT; attempt++) {
            boolean removed = awaitVoidTask(
                    messageApi.removeListener(nodeId),
                    action + "（第" + attempt + "次）"
            );
            if (removed) return true;
            if (!isBroadcastInProgressMessage(lastError)
                    && !isBroadcastInProgressCause(lastFailure)) return false;
            try {
                Thread.sleep(REMOVE_LISTENER_RETRY_DELAY_MS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                lastError = action + "被中断";
                return false;
            }
        }
        return false;
    }

    /** 官方 demo 的 NodeApi.query(nodeId, DataItem.ITEM_CONNECTION)。 */
    @Override
    public Boolean queryConnection(Context context, String nodeId) {
        synchronized (apiLock) {
            log("查询互联状态开始: nodeId=" + nodeId);
            if (nodeApi == null || nodeId == null) {
                log("查询互联状态跳过: nodeApi=" + (nodeApi != null));
                return null;
            }
            try {
                Task<DataQueryResult> task = nodeApi.query(nodeId, DataItem.ITEM_CONNECTION);
                if (task == null) {
                    log("query 返回 null: nodeId=" + nodeId);
                    return null;
                }
                CountDownLatch latch = new CountDownLatch(1);
                AtomicReference<Boolean> result = new AtomicReference<>();
                task.addOnSuccessListener(new OnSuccessListener<DataQueryResult>() {
                    @Override
                    public void onSuccess(DataQueryResult value) {
                        result.set(value != null && value.isConnected);
                        log("query 成功: nodeId=" + nodeId + ", value="
                                + (value == null ? "null" : ("connected=" + value.isConnected
                                + ", battery=" + value.battery)));
                        latch.countDown();
                    }
                }).addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(Exception error) {
                        log("query 失败: nodeId=" + nodeId + ", " + errorMessage(error));
                        setFailure("查询手表互联状态失败", error);
                        latch.countDown();
                    }
                });
                if (!await(latch, "查询手表互联状态超时")) return null;
                log("查询互联状态结束: nodeId=" + nodeId + ", result=" + result.get());
                return result.get();
            } catch (Throwable error) {
                log("query 抛出异常: nodeId=" + nodeId + ", " + errorMessage(error));
                setFailure("查询手表互联状态失败", error);
                return null;
            }
        }
    }

    /** 官方 NodeApi.isWearAppInstalled(nodeId)：手机端查询手表端快应用是否已安装。 */
    @Override
    public Boolean isWatchAppInstalled(Context context, String nodeId) {
        synchronized (apiLock) {
            log("查询手表端应用安装状态: nodeId=" + nodeId);
            if (nodeApi == null || nodeId == null) {
                log("查询手表端应用安装状态跳过: nodeApi=" + (nodeApi != null));
                return null;
            }
            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<Boolean> installed = new AtomicReference<>();
            try {
                Task<Boolean> task = nodeApi.isWearAppInstalled(nodeId);
                if (task == null) {
                    log("isWearAppInstalled 返回 null: nodeId=" + nodeId);
                    return null;
                }
                task.addOnSuccessListener(new OnSuccessListener<Boolean>() {
                    @Override
                    public void onSuccess(Boolean result) {
                        installed.set(result);
                        log("isWearAppInstalled 成功: nodeId=" + nodeId
                                + ", installed=" + result);
                        latch.countDown();
                    }
                }).addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(Exception error) {
                        log("isWearAppInstalled 失败: " + errorMessage(error));
                        setFailure("查询手表端应用安装状态失败", error);
                        latch.countDown();
                    }
                });
                if (!await(latch, "查询手表端应用安装状态超时")) return null;
            } catch (Throwable error) {
                log("isWearAppInstalled 抛出异常: " + errorMessage(error));
                setFailure("查询手表端应用安装状态失败", error);
                return null;
            }
            log("查询手表端应用安装状态结束: nodeId=" + nodeId
                    + ", installed=" + installed.get());
            return installed.get();
        }
    }

    /** 官方 demo 的 NodeApi.subscribe(nodeId, DataItem.ITEM_CONNECTION, listener)。 */
    @Override
    public boolean setConnectionListener(Context context, String nodeId, ConnectionListener listener) {
        synchronized (apiLock) {
            log("注册连接状态监听开始: nodeId=" + nodeId
                    + ", oldRegistered=" + isConnectionListenerRegistered());
            if (nodeApi == null || nodeId == null || listener == null) {
                log("注册连接状态监听跳过: nodeApi=" + (nodeApi != null)
                        + ", nodeId=" + nodeId + ", listener=" + (listener != null));
                return false;
            }
            com.xiaomi.xms.wearable.node.OnDataChangedListener callback =
                    new com.xiaomi.xms.wearable.node.OnDataChangedListener() {
                        @Override
                        public void onDataChanged(
                                String changedNodeId,
                                DataItem item,
                                DataSubscribeResult data
                        ) {
                            log("收到连接状态回调: nodeId=" + changedNodeId
                                    + ", item=" + (item == null ? "null" : item.getType())
                                    + ", status=" + (data == null ? "null" : data.getConnectedStatus()));
                            if (item == null || item.getType() != DataItem.ITEM_CONNECTION.getType()) return;
                            boolean connected = data != null &&
                                    data.getConnectedStatus() ==
                                            DataSubscribeResult.RESULT_CONNECTION_CONNECTED;
                            listener.onConnectionChanged(changedNodeId, connected);
                        }
                    };
            try {
                Task<Void> task = nodeApi.subscribe(nodeId, DataItem.ITEM_CONNECTION, callback);
                if (task == null) {
                    log("subscribe 返回 null: nodeId=" + nodeId);
                    return false;
                }
                log("subscribe Task 已创建: nodeId=" + nodeId);
                CountDownLatch latch = new CountDownLatch(1);
                AtomicBoolean registered = new AtomicBoolean(false);
                task.addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void ignored) {
                        registered.set(true);
                        log("subscribe 成功回调: nodeId=" + nodeId);
                        latch.countDown();
                    }
                }).addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(Exception error) {
                        log("subscribe 失败回调: nodeId=" + nodeId + ", " + errorMessage(error));
                        setFailure("注册手表状态监听失败", error);
                        latch.countDown();
                    }
                });
                await(latch, "注册手表状态监听超时");
                if (registered.get()) {
                    connectionListener = callback;
                    connectionListenerNodeId = nodeId;
                    lastError = null;
                    return true;
                }
            } catch (Throwable error) {
                log("subscribe 抛出异常: nodeId=" + nodeId + ", " + errorMessage(error));
                setFailure("注册手表状态监听失败", error);
            }
            connectionListener = null;
            connectionListenerNodeId = null;
            log("注册连接状态监听结束: nodeId=" + nodeId + ", registered=false");
            return false;
        }
    }

    @Override
    public boolean isConnectionListenerRegistered() {
        return connectionListener != null && connectionListenerNodeId != null;
    }

    /** 官方 demo 的 NodeApi.unsubscribe(nodeId, DataItem.ITEM_CONNECTION)。 */
    @Override
    public void removeConnectionListener(Context context) {
        synchronized (apiLock) {
            String nodeId = connectionListenerNodeId != null
                    ? connectionListenerNodeId
                    : currentNodeId();
            log("注销连接状态监听开始: nodeId=" + nodeId
                    + ", localRegistered=" + isConnectionListenerRegistered());
            boolean removed = false;
            if (nodeApi != null && nodeId != null) {
                removed = awaitVoidTask(nodeApi.unsubscribe(nodeId, DataItem.ITEM_CONNECTION), "注销手表状态监听");
            }
            connectionListener = null;
            connectionListenerNodeId = null;
            log("注销连接状态监听结束: nodeId=" + nodeId + ", removed=" + removed);
        }
    }

    @Override
    public void release() {
        synchronized (apiLock) {
            removeMessageListener(contextOrApp());
            removeConnectionListener(contextOrApp());
            removeServiceConnectionListener();
            currentNode = null;
            permissionGranted = false;
        }
    }

    private void ensureServiceConnectionListener() {
        if (serviceApi == null || serviceConnectionListenerRegistered) return;
        try {
            serviceApi.registerServiceConnectionListener(serviceConnectionListener);
            serviceConnectionListenerRegistered = true;
            log("已注册 Mi Fitness 服务连接监听");
        } catch (Throwable error) {
            setFailure("注册 Mi Fitness 服务连接监听失败", error);
        }
    }

    private void removeServiceConnectionListener() {
        if (serviceApi == null || !serviceConnectionListenerRegistered) return;
        try {
            serviceApi.unregisterServiceConnectionListener(serviceConnectionListener);
            log("已注销 Mi Fitness 服务连接监听");
        } catch (Throwable error) {
            setFailure("注销 Mi Fitness 服务连接监听失败", error);
        } finally {
            serviceConnectionListenerRegistered = false;
            serviceConnectedOnce = false;
        }
    }

    private void invalidateRemoteListeners(String reason) {
        boolean hadMessageListener = messageListener != null || listenerNodeId != null;
        boolean hadConnectionListener = connectionListener != null || connectionListenerNodeId != null;
        messageListener = null;
        listenerNodeId = null;
        connectionListener = null;
        connectionListenerNodeId = null;
        log("远端监听已失效，等待重新注册: reason=" + reason
                + ", message=" + hadMessageListener
                + ", connection=" + hadConnectionListener);
    }

    private Context contextOrApp() {
        return appContext;
    }

    private String currentNodeId() {
        Node node = currentNode;
        return node == null ? null : node.id;
    }

    private boolean awaitVoidTask(Task<Void> task, String timeoutMessage) {
        if (task == null) {
            log(timeoutMessage + ": Task=null");
            return false;
        }
        lastFailure = null;
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean succeeded = new AtomicBoolean(false);
        task.addOnSuccessListener(new OnSuccessListener<Void>() {
            @Override
                public void onSuccess(Void ignored) {
                    succeeded.set(true);
                    lastFailure = null;
                    log(timeoutMessage + "成功");
                latch.countDown();
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(Exception error) {
                log(timeoutMessage + "失败: " + errorMessage(error));
                setFailure(timeoutMessage.replace("超时", "失败"), error);
                latch.countDown();
            }
        });
        boolean completed = await(latch, timeoutMessage);
        return completed && succeeded.get();
    }

    private boolean await(CountDownLatch latch, String timeoutMessage) {
        try {
            if (latch.await(CALL_TIMEOUT_MS, TimeUnit.MILLISECONDS)) return true;
            lastError = timeoutMessage;
            return false;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            lastError = timeoutMessage.replace("超时", "被中断");
            return false;
        }
    }

    private void setFailure(String action, Throwable error) {
        lastFailure = error;
        String detail = error == null ? null : error.getMessage();
        String detailLower = detail == null ? "" : detail.toLowerCase(java.util.Locale.ROOT);
        if (detailLower.contains("app not installed")
                || detailLower.contains("app_uninstalled")
                || detailLower.contains("not installed")) {
            // XMS/Mi Fitness 按包名查找手表端快应用失败，明确引导用户安装 rpk。
            lastError = "手表端未安装澎湃记快应用（rpk），请先安装后再授权";
        } else {
            lastError = detail == null || detail.isBlank()
                    ? action
                    : action + ": " + detail;
        }
        Log.w(TAG, lastError, error);
    }

    private static boolean isAlreadyRegisteredMessage(String message) {
        if (message == null) return false;
        String normalized = message.toLowerCase(java.util.Locale.ROOT);
        return normalized.contains("you have registered")
                && !normalized.contains("not registered");
    }

    private static boolean isBroadcastInProgressMessage(String message) {
        if (message == null) return false;
        String normalized = message.toLowerCase(java.util.Locale.ROOT);
        return normalized.contains("beginbroadcast")
                || normalized.contains("already in a broadcast");
    }

    private static boolean isBroadcastInProgressCause(Throwable error) {
        Throwable current = error;
        int depth = 0;
        while (current != null && depth++ < 8) {
            if (isBroadcastInProgressMessage(current.getMessage())
                    || isBroadcastInProgressMessage(current.toString())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static String errorMessage(Throwable error) {
        if (error == null) return "null";
        String message = error.getMessage();
        return error.getClass().getSimpleName()
                + (message == null || message.isBlank() ? "" : ": " + message);
    }

    private static String describeBytes(byte[] bytes) {
        if (bytes == null) return "bytes=null";
        String preview = new String(bytes, StandardCharsets.UTF_8)
                .replace('\n', ' ')
                .replace('\r', ' ');
        if (preview.length() > 240) preview = preview.substring(0, 240) + "...";
        StringBuilder hex = new StringBuilder();
        int hexLength = Math.min(bytes.length, 48);
        for (int i = 0; i < hexLength; i++) {
            if (i > 0) hex.append(' ');
            hex.append(String.format(java.util.Locale.ROOT, "%02X", bytes[i] & 0xFF));
        }
        if (bytes.length > hexLength) hex.append(" ...");
        return "bytes=" + bytes.length + ", utf8=" + preview + ", hex=" + hex;
    }

    private static String permissionArray(Permission[] values) {
        if (values == null) return "null";
        StringBuilder result = new StringBuilder("[");
        for (int i = 0; i < values.length; i++) {
            if (i > 0) result.append(',');
            Permission value = values[i];
            result.append(value == null ? "null" : value.getName());
        }
        return result.append(']').toString();
    }

    private static void log(String message) {
        Log.d(TAG, message);
    }
}
