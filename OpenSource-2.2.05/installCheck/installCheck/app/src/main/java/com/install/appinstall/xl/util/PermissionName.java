package com.install.appinstall.xl.util;

// 应用声明权限列表-中文字符映射合集

public class PermissionName {
    // 未收录权限的名称前缀常量
    private static final String UNKNOWN_PREFIX = "[未知权限]";
    // 缓存清理配置
    private static final java.util.Map<
            String, String> sCache = java.util.Collections.synchronizedMap(
            new java.util.LinkedHashMap<String, String>(640, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(java.util.Map.Entry<String, String> eldest) {
                    return size() > 640;
                }
            }
    );

    // 未命中任何语义规则且未收录的文本
    public static String getFriendlyName(String perm) {
        if (perm == null) return "未知权限";
        String cached = sCache.get(perm);
        if (cached != null) return cached;
        String name = computeFriendlyName(perm);
        sCache.put(perm, name);
        return name;
    }

    private static String computeFriendlyName(String perm) {
        // ======== 统一处理常见自定义权限后缀 ========
        // 动态广播接收器
        if (perm.endsWith(".DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")) {
            return "动态广播接收器权限";
        }
        // 推送提供者
        if (perm.endsWith(".permission.PUSH_PROVIDER")) {
            return "推送提供者权限";
        }
        // 处理推送消息
        if (perm.endsWith(".permission.PROCESS_PUSH_MSG")) {
            return "处理推送消息权限";
        }
        // 小程序接收器
        if (perm.endsWith(".permission.FIN_APPLET_RECEIVER")) {
            return "小程序接收器权限";
        }
        // 核心守护广播(无S)
        if (perm.endsWith(".LIBCOREDAEMON_BROADCAST_PERMISSION")) {
            return "核心守护广播权限";
        }
        // 核心守护广播(有S)
        if (perm.endsWith(".LIBCOREDAEMON_BROADCAST_PERMISSIONS")) {
            return "核心守护广播权限";
        }

        switch (perm) {
            // ======== 网络相关 ========
            case "android.permission.INTERNET":
                return "完全的网络访问权限";
            case "android.permission.ACCESS_WIFI_STATE":
                return "查看WLAN连接";
            case "android.permission.ACCESS_NETWORK_STATE":
                return "查看网络连接";
            case "android.permission.CHANGE_WIFI_STATE":
                return "连接WLAN网络和断开连接";
            case "android.permission.CHANGE_NETWORK_STATE":
                return "更改网络连接性";
            case "android.permission.NEARBY_WIFI_DEVICES":
                return "与附近的WLAN设备互动";
            case "android.permission.CHANGE_WIFI_MULTICAST_STATE":
                return "修改WiFi多播状态";
            case "android.permission.OVERRIDE_WIFI_CONFIG":
                return "覆盖修改系统WiFi配置";
            case "android.permission.ACCESS_DOWNLOAD_MANAGER":
                return "访问下载管理器";

            // ======== 存储相关 ========
            case "android.permission.READ_EXTERNAL_STORAGE":
                return "读取共享存储空间中的内容（照片/视频/音频）";
            case "android.permission.WRITE_EXTERNAL_STORAGE":
                return "修改或删除共享存储空间中的内容";
            case "android.permission.MANAGE_EXTERNAL_STORAGE":
                return "所有文件访问权限";
            case "android.permission.MANAGE_MEDIA":
                return "所有文件访问权限";
            case "android.permission.ACCESS_ALL_EXTERNAL_STORAGE":
                return "全权管理媒体文件";
            case "android.permission.WRITE_MEDIA_STORAGE":
                return "写入媒体存储底层权限";
            case "android.permission.WRITE_APP_SPECIFIC_EXTERNAL_STORAGE":
                return "写入应用专属外部存储";
            case "android.permission.READ_APP_SPECIFIC_EXTERNAL_STORAGE":
                return "读取应用专属外部存储";
            case "android.permissio.WRITE_EXTERNAL_STORAGE":
                return "写入外部储存";
            case "android.permission.ACCESS_ALL_DOWNLOADS":
                return "访问所有下载目录";
            case "android.permission.ACCESS_CACHE_FILESYSTEM":
                return "访问系统缓存文件系统";
            case "MediaStore.Images.Media.INTERNAL_CONTENT_URI":
                return "Android系统相册内容内部提供";
            case "MediaStore.Images.Media.EXTERNAL_CONTENT_URI":
                return "Android系统相册内容外部提供";
            case "android.permission.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION":
                return "请求所有文件访问权限（引导至设置页面）";
            case "android.permission.ACCESS_DOWNLOAD_MANAGER_ADVANCED":
                return "高级下载管理器访问权限（系统级）";
            case "android.permission.DOWNLOAD_CACHE_NON_PURGEABLE":
                return "下载缓存不可清除权限（系统级）";
            case "android.permission.SEND_DOWNLOAD_COMPLETED_INTENTS":
                return "发送下载完成广播权限（系统级）";

            // ======== 位置相关 ========
            case "android.permission.ACCESS_COARSE_LOCATION":
                return "粗略位置（网络定位）";
            case "android.permission.ACCESS_FINE_LOCATION":
                return "精确位置（GPS）";
            case "android.permission.ACCESS_BACKGROUND_LOCATION":
                return "后台位置信息";
            case "android.permission.ACCESS_LOCATION_EXTRA_COMMANDS":
                return "获取额外的位置信息提供程序命令";
            case "android.permission.CONTROL_LOCATION_UPDATES":
                return "控制定位更新开关";
            case "android.permission.LOCATION_HARDWARE":
                return "底层硬件定位控制";
            case "android.permission.INSTALL_LOCATION_PROVIDER":
                return "安装定位服务提供者";
            case "android.permission.ACCESS_ASSISTED_GPS":
                return "访问辅助GPS";
            case "android.permission.ACCESS_GPS":
                return "访问GPS（旧）";
            case "android.permission.ACCESS_COARSE_UPDATES":
                return "粗略位置更新";

            // ======== 传感器/身体活动 =======
            case "android.permission.BODY_SENSORS":
                return "设备动作与方向（传感器）";
            case "android.permission.ACTIVITY_RECOGNITION":
                return "识别身体活动";
            case "android.permission.HIGH_SAMPLING_RATE_SENSORS":
                return "传感器高采样率采集";
            case "android.permission.SENSOR_PRIVACY":
                return "传感器隐私管控权限";
            case "android.permission.USE_SENSOR_MANAGER_SERVICE":
                return "使用底层传感器管理服务";
            case "android.permission.BODY_SENSORS_BACKGROUND":
                return "后台身体传感器";

            // ======== 相机/麦克风/录屏 ======
            case "android.permission.CAMERA":
                return "拍摄照片和视频";
            case "android.permission.RECORD_AUDIO":
                return "录音";
            case "android.permission.RECORD_VIDEO":
                return "底层录制视频权限";
            case "android.permission.CAPTURE_VIDEO_OUTPUT":
                return "捕获系统视频输出（录屏底层）";
            case "android.permission.CAPTURE_SECURE_VIDEO_OUTPUT":
                return "捕获安全视频输出（系统）";
            case "android.permission.CAPTURE_AUDIO_OUTPUT":
                return "捕获系统内部音频输出";
            case "android.permission.DETECT_SCREEN_CAPTURE":
                return "检测屏幕截取/录制";

            // ======== 电话/设备信息 =========
            case "android.permission.READ_PHONE_STATE":
                return "读取手机状态和身份";
            case "android.permission.READ_PRIVILEGED_PHONE_STATE":
                return "读取特权手机状态";
            case "android.permission.READ_PRECISE_PHONE_STATE":
                return "读取精确电话状态";
            case "android.permission.READ_PHONE_NUMBERS":
                return "读取本机手机号码";
            case "android.permission.READ_BASIC_PHONE_STATE":
                return "读取基本手机状态";
            case "android.permission.ANSWER_PHONE_CALLS":
                return "接听电话";
            case "android.permission.CALL_PHONE":
                return "直接拨打电话";
            case "android.permission.PROCESS_OUTGOING_CALLS":
                return "处理拨出电话";
            case "android.permission.ADD_VOICEMAIL":
                return "添加语音邮件";
            case "android.permission.USE_SIP":
                return "使用SIP通话";
            case "android.permission.ACCEPT_HANDOVER":
                return "接受通话移交";
            case "android.permission.MANAGE_CALLS":
                return "管理通话挂断拦截";
            case "android.permission.MODIFY_PHONE_STATE":
                return "修改电话状态飞行模式";
            case "android.permission.CALL_PRIVILEGED":
                return "特权拨打紧急号码";
            case "android.permission.USE_CARRIER_APP_MANAGER":
                return "使用运营商应用管理服务";
            case "android.permission.USE_CARRIER_MESSAGING_CLIENT":
                return "运营商短信客户端权限";
            case "android.permission.USE_CARRIER_SIP":
                return "运营商SIP通话特权";

            // =========== 通话记录/联系人/账号/日历 ======
            case "android.permission.READ_CALL_LOG":
                return "读取通话记录";
            case "android.permission.WRITE_CALL_LOG":
                return "写入通话记录";
            case "android.permission.READ_CONTACTS":
                return "读取联系人";
            case "android.permission.WRITE_CONTACTS":
                return "修改联系人";
            case "android.permission.GET_ACCOUNTS":
                return "获取系统账号列表";
            case "android.permission.AUTHENTICATE_ACCOUNTS":
                return "身份验证帐户";
            case "android.permission.MANAGE_ACCOUNTS":
                return "管理系统账号增删";
            case "android.permission.USE_CREDENTIALS":
                return "使用系统账号凭证";
            case "android.permission.ACCOUNT_MANAGER":
                return "账户管理特权";
            case "android.permission.READ_CALENDAR":
                return "读取日历";
            case "android.permission.WRITE_CALENDAR":
                return "修改日历";
            case "android.permission.READ_PROFILE":
                return "读取用户个人资料";
            case "android.permission.WRITE_PROFILE":
                return "写入用户个人资料";
            case "android.permission.READ_USER_DICTIONARY":
                return "读取用户词典";
            case "android.permission.WRITE_USER_DICTIONARY":
                return "写入用户词典";

            // ========== 短信/彩信/WAP ========
            case "android.permission.SEND_SMS":
                return "发送短信";
            case "android.permission.READ_SMS":
                return "读取短信";
            case "android.permission.RECEIVE_SMS":
                return "接收短信";
            case "android.permission.RECEIVE_MMS":
                return "接收彩信消息";
            case "android.permission.RECEIVE_WAP_PUSH":
                return "接收WAP推送";
            case "android.permission.BROADCAST_SMS":
                return "广播短信接收事件";
            case "android.permission.BROADCAST_WAP_PUSH":
                return "广播WAP推送事件";
            case "android.permission.WRITE_SMS":
                return "写入短信";
            case "android.provider.Telephony.SMS_RECEIVED":
                return "接收短信（旧广播）";

            // ======== 蓝牙相关 ========
            case "android.permission.BLUETOOTH":
                return "蓝牙设备连接";
            case "android.permission.BLUETOOTH_ADMIN":
                return "管理蓝牙扫描配对";
            case "android.permission.BLUETOOTH_SCAN":
                return "扫描附近的蓝牙设备";
            case "android.permission.BLUETOOTH_CONNECT":
                return "连接附近的蓝牙设备";
            case "android.permission.BLUETOOTH_ADVERTISE":
                return "蓝牙广播";
            case "android.permission.BLUETOOTH_PRIVILEGED":
                return "蓝牙特权底层控制";
            case "android.permission.RECEIVE_BLUETOOTH_BROADCAST":
                return "接收蓝牙系统广播";
            case "android.permission.ACCESS_BLUETOOTH_SHARE":
                return "蓝牙接收分享文件";

            // ======== 通知/状态栏 ========
            case "android.permission.POST_NOTIFICATIONS":
                return "发送通知";
            case "android.permission.POST_PROMOTED_NOTIFICATIONS":
                return "推送推广类通知";
            case "android.permission.ACCESS_NOTIFICATION_POLICY":
                return "勿扰模式访问";
            case "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE":
                return "通知监听服务";
            case "android.permission.BIND_NOTIFICATION_SERVICE":
                return "绑定系统通知服务";
            case "android.permission.SUBSCRIBE_TO_KEYGUARD":
                return "订阅锁屏状态监听";
            case "android.permission.READ_NEARBY_STREAMING_POLICY":
                return "读取附近流媒体策略";
            case "android.permission.EXPAND_STATUS_BAR":
                return "展开/收起状态栏";
            case "android.permission.STATUS_BAR":
                return "控制系统状态栏";

            // ======== 悬浮窗/系统窗口 ========
            case "android.permission.SYSTEM_ALERT_WINDOW":
                return "悬浮窗/后台弹出界面";
            case "android.permission.SYSTEM_OVERLAY_WINDOW":
                return "系统级悬浮窗";
            case "android:picture_in_picture":
                return "画中画权限";
            case "android.permission.ALWAYS_ON_TOP":
                return "窗口始终置顶显示";
            case "android.permission.HIDE_OVERLAY_WINDOWS":
                return "隐藏第三方悬浮窗口";
            case "android.permission.ALLOW_BACKGROUND_ACTIVITY":
                return "允许后台弹窗活动";
            case "android.permission.USE_FULL_SCREEN_INTENT":
                return "使用全屏弹窗通知";
            case "android.permission.REMOVE_DRAMATIC_OVERLAY":
                return "移除系统覆盖层";

            // ======== 系统设置修改 ========
            case "android.permission.WRITE_SETTINGS":
                return "修改系统设置";
            case "android.permission.WRITE_SECURE_SETTINGS":
                return "修改系统安全级设置";
            case "android.permission.MODIFY_AUDIO_SETTINGS":
                return "修改系统音频音量设置";
            case "android.permission.CHANGE_CONFIGURATION":
                return "修改系统语言布局配置";
            case "android.permission.CONFIGURE_DISPLAY":
                return "配置屏幕亮度显示参数";
            case "android.permission.CHANGE_BACKGROUND_DATA_SETTINGS":
                return "修改后台数据设置";
            case "android.permission.CHANGE_DEVICE_CONFIGURATION":
                return "修改设备底层配置";
            case "android.permission.CHANGE_SECURITY_SETTINGS":
                return "修改系统安全设置";
            case "android.permission.MODIFY_BOOT_ANIMATION":
                return "修改开机动画";
            case "android.permission.MODIFY_DAY_NIGHT_MODE":
                return "修改深色浅色模式";
            case "android.permission.MODIFY_SETTINGS":
                return "修改全局系统设置";
            case "android.permission.OVERRIDE_RINGTONE_SETTINGS":
                return "覆盖系统铃声设置";
            case "android.permission.OVERRIDE_SOUND_SETTINGS":
                return "覆盖系统声音设置";
            case "android.permission.NFC_SETTINGS":
                return "修改NFC系统设置";
            case "android.permission.MANAGE_SOUND_SETTINGS":
                return "管理系统声音设置";
            case "android.permission.SET_TIME":
                return "手动设置系统时间";
            case "android.permission.SET_TIME_ZONE":
                return "修改系统时区";
            case "android.permission.SET_SCREEN_BRIGHTNESS":
                return "直接设置屏幕亮度";
            case "android.permission.SET_SCREEN_OFF_TIMEOUT":
                return "修改屏幕自动休眠时间";
            case "android.permission.USE_FACERECOGNITION":
                return "使用人脸识别（旧 API）";
            case "android.permission.ACCESS_LOCAL_NETWORK":
                return "访问本地网络（Android 16）";
            case "android.permission.UPDATE_APPWIDGET":
                return "更新桌面小组件";
            case "android.permission.UPDATE_APP_BADGE":
                return "更新应用角标";
            case "android.permission.ACCELEROMETER":
                return "读取加速度计数据";
            case "android.permission.GYROSCOPE":
                return "读取陀螺仪数据";
            case "android.permission.SYS":
                return "系统内核读取权限";
            case "android.permission.DETECT_SCREEN_RECORDING":
                return "检测屏幕录制";
            case "android.permission.REQUEST_OBSERVE_COMPANION_DEVICE_PRESENCE":
                return "观测配套设备存在状态";
            case "android.pminermission.WAKE_LOCK":
                return "防止手机休眠";

            // ======== 剪贴板 ========
            case "android.permission.READ_CLIPBOARD":
                return "读取剪贴板内容";
            case "android.permission.WRITE_CLIPBOARD":
                return "写入剪贴板";
            case "android.permission.READ_CLIPBOARD_IN_BACKGROUND":
                return "后台读取剪贴板";
            case "android.permission.READ_CLIPS":
                return "读取剪贴板（旧版）";
            case "android.permission.WRITE_CLIPS":
                return "写入剪贴板（旧版）";
            case "android.permission.GET_CLIPS":
                return "获取剪贴板内容";

            // ======== 快捷方式/桌面角标 =========
            case "android.permission.INSTALL_SHORTCUT":
                return "创建桌面快捷方式";
            case "com.android.launcher.permission.INSTALL_SHORTCUT":
                return "创建桌面快捷方式（旧版）";
            case "android.permission.UNINSTALL_SHORTCUT":
                return "卸载桌面快捷方式";
            case "com.android.launcher.permission.UNINSTALL_SHORTCUT":
                return "卸载桌面快捷方式（旧版）";
            case "android.permission.READ_APP_BADGE":
                return "读取应用角标";
            case "android.permission.WRITE_APP_BADGE":
                return "写入应用角标数据";
            case "com.huawei.android.launcher.permission.CHANGE_BADGE":
                return "修改华为桌面角标";
            case "com.hihonor.android.launcher.permission.CHANGE_BADGE":
                return "修改荣耀桌面角标";
            case "com.vivo.notification.permission.BADGE_ICON":
                return "修改vivo桌面角标";
            case "com.vivo.abe.permission.launcher.notification.num":
                return "修改vivo桌面角标数字";
            case "com.sec.android.provider.badge.permission.READ":
                return "读取三星桌面角标";
            case "com.sec.android.provider.badge.permission.WRITE":
                return "写入三星桌面角标";
            case "com.sonymobile.home.permission.PROVIDER_INSERT_BADGE":
                return "插入索尼桌面角标";
            case "com.sonyericsson.home.permission.BROADCAST_BADGE":
                return "广播索尼角标";
            case "me.everything.badger.permission.BADGE_COUNT_READ":
                return "读取角标计数";
            case "me.everything.badger.permission.BADGE_COUNT_WRITE":
                return "写入角标计数";
            case "com.majeur.launcher.permission.UPDATE_BADGE":
                return "更新角标";
            case "com.anddoes.launcher.permission.UPDATE_COUNT":
                return "更新角标计数";

            // ======== 应用列表/使用统计 =========
            case "android.permission.QUERY_ALL_PACKAGES":
                return "读取应用列表";
            case "android.permission.PACKAGE_USAGE_STATS":
                return "使用情况访问权限";
            case "android.permission.GET_INSTALLED_APPS":
                return "获取已安装应用列表";
            case "com.android.permission.GET_INSTALLED_APPS":
                return "获取已安装应用列表（自定义）";
            case "android.permission.GET_PACKAGE_SIZE":
                return "获取应用安装包大小";
            case "android.permission.READ_DEFAULT_APPLICATIONS":
                return "读取系统默认应用列表";
            case "android.permission.SET_PREFERRED_APPLICATIONS":
                return "修改默认应用关联";
            case "android.permission.READ_DEVICE_CONFIG":
                return "读取设备底层配置文件";
            case "android.permission.READ_RUNTIME_PROFILES":
                return "读取运行时用户配置";
            case "android.permission.CHANGE_APP_IDLE_STATE":
                return "修改应用空闲状态权限（系统级）";
            case "android.permission.ACCESS_EPHEMERAL_APPS":
                return "访问瞬时应用列表权限（系统级）";

            // ======== 安装/卸载/应用管控 =========
            case "android.permission.REQUEST_INSTALL_PACKAGES":
                return "请求安装文件包";
            case "android.permission.REQUEST_DELETE_PACKAGES":
                return "请求删除应用包";
            case "android.permission.INSTALL_PACKAGES":
                return "安装应用包";
            case "android.permission.DELETE_PACKAGES":
                return "删除应用包";
            case "android.permission.CLEAR_APP_CACHE":
                return "清除所有应用缓存";
            case "android.permission.FORCE_STOP_PACKAGES":
                return "强制停止第三方应用";
            case "android.permission.CLEAR_APP_USER_DATA":
                return "清除应用用户数据";
            case "android.permission.READ_INSTALL_SESSIONS":
                return "读取安装会话";

            // ======== 前台服务 ===========
            case "android.permission.FOREGROUND_SERVICE":
                return "运行前台服务";
            case "android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK":
                return "前台服务媒体播放";
            case "android.permission.FOREGROUND_SERVICE_DATA_SYNC":
                return "前台服务数据同步";
            case "android.permission.FOREGROUND_SERVICE_LOCATION":
                return "前台服务位置";
            case "android.permission.FOREGROUND_SERVICE_CAMERA":
                return "前台服务使用相机";
            case "android.permission.FOREGROUND_SERVICE_MICROPHONE":
                return "前台服务使用麦克风";
            case "android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE":
                return "前台服务连接设备";
            case "android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION":
                return "前台服务屏幕录制/投射";
            case "android.permission.FOREGROUND_SERVICE_SPECIAL_USE":
                return "前台服务特殊用途";
            case "android.permission.FOREGROUND_SERVICE_HEALTH":
                return "前台服务健康数据";
            case "android.permission.FOREGROUND_SERVICE_PHONE_CALL":
                return "前台服务电话";
            case "android.permission.FOREGROUND_SERVICE_SYSTEM_EXEMPTED":
                return "前台服务系统豁免";
            case "android.permission.FOREGROUND_SERVICE_SHORT_SERVICE":
                return "前台服务短期任务";

            // ======== 任务/进程管理 =========
            case "android.permission.REORDER_TASKS":
                return "对正在运行的应用重新排序";
            case "android.permission.GET_TASKS":
                return "获取当前运行的任务";
            case "android.permission.GET_DETAILED_TASKS":
                return "获取详细后台任务信息";
            case "android.permission.GET_TOP_ACTIVITY_INFO":
                return "获取顶层应用活动信息";
            case "android.permission.REMOVE_TASKS":
                return "移除后台应用任务";
            case "android.permission.KILL_BACKGROUND_PROCESSES":
                return "杀死后台进程";
            case "android.permission.SET_PROCESS_LIMIT":
                return "设置后台进程限制数量";
            case "android.permission.PERSISTENT_ACTIVITY":
                return "应用常驻前台活动";
            case "android.permission.SET_ALWAYS_FINISH":
                return "设置页面立即销毁";
            case "android.permission.STOP_APP_SWITCHES":
                return "禁止应用切换任务";
            case "android.permission.RESTART_PACKAGES":
                return "关闭其他应用进程";

            // ======== 系统服务绑定 =========
            case "android.permission.BIND_VPN_SERVICE":
                return "绑定VPN服务";
            case "android.permission.BIND_ACCESSIBILITY_SERVICE":
                return "绑定无障碍辅助服务";
            case "android.permission.BIND_DEVICE_ADMIN":
                return "绑定设备管理员组件";
            case "android.permission.BIND_DEVICE_ADMIN_SERVICE":
                return "绑定设备管理服务";
            case "android.permission.BIND_INPUT_METHOD":
                return "绑定输入法服务";
            case "android.permission.BIND_WALLPAPER":
                return "绑定动态壁纸服务";
            case "android.permission.BIND_WALLPAPER_SERVICE":
                return "绑定壁纸管理服务";
            case "android.permission.BIND_PRINT_SERVICE":
                return "绑定打印服务";
            case "android.permission.BIND_AUTOFILL_SERVICE":
                return "绑定自动填充服务";
            case "android.permission.BIND_APPWIDGET":
                return "绑定桌面小组件服务";
            case "android.permission.BIND_BACKUP_AGENT":
                return "绑定备份代理服务";
            case "android.permission.BIND_CARRIER_MESSAGING_SERVICE":
                return "绑定运营商短信服务";
            case "android.permission.BIND_CARRIER_SERVICES":
                return "绑定运营商系统服务";
            case "android.permission.BIND_CHOOSER_TARGET_SERVICE":
                return "绑定应用选择器服务";
            case "android.permission.BIND_CONDITION_PROVIDER_SERVICE":
                return "绑定条件判断提供服务";
            case "android.permission.BIND_CONTROLS":
                return "绑定系统控件服务";
            case "android.permission.BIND_DREAM_SERVICE":
                return "绑定屏保服务";
            case "android.permission.BIND_EPHEMERAL_SERVICE":
                return "绑定临时应用服务";
            case "android.permission.BIND_GAME_SERVICE":
                return "绑定游戏服务";
            case "android.permission.BIND_KEYGUARD_SERVICE":
                return "绑定锁屏服务";
            case "android.permission.BIND_LAUNCHER_SERVICE":
                return "绑定桌面启动器服务";
            case "android.permission.BIND_MIDI_SERVICE":
                return "绑定MIDI音乐服务";
            case "android.permission.BIND_OVERLAY_SERVICE":
                return "绑定系统覆盖层服务";
            case "android.permission.BIND_REMOTEVIEWS":
                return "绑定远程视图服务";
            case "android.permission.BIND_ROUTE_PROVIDER":
                return "绑定路由提供服务";
            case "android.permission.BIND_SCREEN_DECORATION":
                return "绑定屏幕装饰服务";
            case "android.permission.BIND_TELECOM_CONNECTION_SERVICE":
                return "绑定电信通话连接服务";
            case "android.permission.BIND_TEXT_SERVICE":
                return "绑定文本处理服务";
            case "android.permission.BIND_VOICE_INTERACTION":
                return "绑定语音交互服务";
            case "android.permission.BIND_VR_LISTENER_SERVICE":
                return "绑定VR监听服务";
            case "android.permission.BIND_DIRECTORY_SEARCH":
                return "绑定目录搜索";

            // ======== 媒体文件（Android 13+） ========
            case "android.permission.READ_MEDIA_AUDIO":
                return "读取音频文件";
            case "android.permission.READ_MEDIA_VIDEO":
                return "读取视频文件";
            case "android.permission.READ_MEDIA_IMAGES":
                return "读取图片文件";
            case "android.permission.READ_MEDIA_VISUAL_USER_SELECTED":
                return "读取用户选择的媒体文件";
            case "android.permission.ACCESS_MEDIA_LOCATION":
                return "访问媒体文件中的位置信息";
            case "android.permission.MEDIA_CONTENT_CONTROL":
                return "控制媒体内容播放（系统）";

            // ======== 基础硬件/系统能力 =========
            case "android.permission.FLASHLIGHT":
                return "控制手电筒";
            case "android.permission.VIBRATE":
                return "振动";
            case "android.permission.VIBRATE_HAPTIC":
                return "高级触觉振动反馈";
            case "android.permission.WAKE_LOCK":
                return "防止手机休眠";
            case "android.permission.TRANSMIT_IR":
                return "使用红外发射器";
            case "android.permission.SET_ALARM":
                return "设置系统闹钟提醒";
            case "android.permission.SCHEDULE_EXACT_ALARM":
                return "设置精准定时闹钟任务";
            case "android.permission.USE_EXACT_ALARM":
                return "使用高精度闹钟定时";
            case "android.permission.SCHEDULE_IGNORE_ALARMS":
                return "设置忽略系统闹钟";
            case "com.android.alarm.permission.SET_ALARM":
                return "设置闹钟";
            case "android.permission.ALARM_LOCK":
                return "闹钟锁定";
            case "android.permission.SET_WALLPAPER":
                return "设置壁纸";
            case "android.permission.SET_WALLPAPER_HINTS":
                return "设置壁纸适配参数";
            case "android.permission.ACCESS_WALLPAPER":
                return "访问系统壁纸";
            case "android.permission.ACCESS_WALLPAPER_SETTINGS":
                return "访问壁纸设置";
            case "android.permission.READ_WALLPAPER_INTERNAL":
                return "读取系统内置壁纸";
            case "android.permission.MOUNT_UNMOUNT_FILESYSTEMS":
                return "挂载/卸载文件系统";
            case "android.permission.RECEIVE_BOOT_COMPLETED":
                return "开机自启动";
            case "android.permission.RECEIVE_BOOT_UPGRADE":
                return "系统升级完成开机广播";
            case "android.permission.BROADCAST_STICKY":
                return "发送持久系统广播";
            case "android.permission.BROADCAST_PACKAGE_ADDED":
                return "监听应用安装广播";
            case "android.permission.BROADCAST_PACKAGE_REMOVED":
                return "监听应用卸载广播";
            case "android.permission.BROADCAST_PACKAGE_CHANGED":
                return "监听应用变更广播";
            case "android.permission.BROADCAST_PACKAGE_REPLACED":
                return "监听应用覆盖安装广播";
            case "android.permission.RECEIVE_USER_PRESENT":
                return "监听用户解锁屏幕事件";
            case "android.permission.RECEIVE_EMERGENCY_BROADCAST":
                return "接收紧急广播消息";
            case "android.permission.RECEIVE_MEDIA_MESSAGES":
                return "接收媒体消息广播";
            case "android.permission.RECEIVE_SAFETY_BROADCAST":
                return "接收系统安全广播";
            case "android.permission.RECEIVE_LOST_DEVICE_BROADCAST":
                return "接收设备丢失广播";
            case "android.permission.DISABLE_KEYGUARD":
                return "临时禁用屏幕锁屏";
            case "android.permission.SET_KEYGUARD":
                return "自定义设置锁屏界面";
            case "android.permission.CONTROL_KEYGUARD":
                return "控制锁屏按键防护";
            case "android.permission.MANAGE_KEYGUARD":
                return "管理锁屏权限（系统）";
            case "android.permission.READ_DREAM_STATE":
                return "读取屏保休眠状态";
            case "android.permission.WRITE_DREAM_STATE":
                return "控制屏保休眠状态";
            case "android.permission.RESET_PREFERENCES":
                return "重置应用偏好设置";
            case "android.permission.INTERACT_ACROSS_USERS":
                return "跨多用户交互访问";
            case "android.permission.INTERACT_ACROSS_USERS_FULL":
                return "完全跨用户权限访问";
            case "android.permission.CHANGE_USER":
                return "跨资料配置文件交互";
            case "android.permission.INTERACT_ACROSS_PROFILES":
                return "切换系统多用户";
            case "android.permission.SHUTDOWN":
                return "关机关闭设备";
            case "android.permission.REBOOT":
                return "重启设备系统";
            case "android.permission.CHANGE_COMPONENT_ENABLED_STATE":
                return "启用禁用应用组件";
            case "android.permission.DUMP":
                return "获取系统内核调试信息";
            case "android.permission.READ_LOGS":
                return "读取系统日志";
            case "android.permission.RUN_IN_BACKGROUND":
                return "允许后台常驻运行";
            case "android.permission.GLOBAL_SEARCH":
                return "全局搜索权限（系统）";
            case "android.permission.READ_SEARCH_INDEXABLES":
                return "读取全局搜索索引数据";
            case "android.permission.FACTORY_TEST":
                return "工厂测试模式权限";
            case "android.permission.READ_FRAME_BUFFER":
                return "读取屏幕帧缓冲区（截屏底层）";
            case "android.permission.READ_INPUT_STATE":
                return "读取按键输入状态（系统）";
            case "android.permission.READ_NETWORK_USAGE_HISTORY":
                return "读取网络流量使用历史";
            case "android.permission.WRITE_NETWORK_USAGE_HISTORY":
                return "写入网络流量使用历史（系统）";
            case "android.permission.READ_OEM_UNLOCK_STATE":
                return "读取OEM解锁状态";
            case "android.permission.SET_ACTIVITY_WATCHER":
                return "监控应用页面跳转（系统）";
            case "android.permission.SET_ANIMATION_SCALE":
                return "修改窗口动画缩放比例";
            case "android.permission.SET_DEBUG_APP":
                return "设置应用为可调试状态（系统）";
            case "android.permission.MANAGE_NETWORK_POLICY":
                return "管理网络策略管控";
            case "android.permission.MANAGE_PROFILE_AND_DEVICE_OWNERS":
                return "管理设备所有者与工作资料";
            case "android.permission.WRITE_OWNER_DATA":
                return "修改设备所有者数据";
            case "android.permission.READ_OWNER_DATA":
                return "读取设备所有者数据";
            case "android.permission.WRITE_GSERVICES":
                return "写入Google服务配置（系统）";
            case "android.permission.DEVICE_POLICY_MANAGER":
                return "设备策略管理员权限";
            case "android.permission.USE_DEVICE_POLICY":
                return "使用设备策略管理（系统）";
            case "android.permission.MANAGE_FINGERPRINT":
                return "管理指纹录入与删除";
            case "android.permission.MANAGE_GAME_SERVICE":
                return "管理游戏服务后台";
            case "android.permission.MANAGE_USB":
                return "管理USB设备挂载与权限";
            case "android.permission.MANAGE_INPUT_METHODS":
                return "管理输入法列表（系统）";
            case "android.permission.REQUEST_COMPANION_RUN_IN_BACKGROUND":
                return "请求配套应用后台运行";
            case "android.permission.REQUEST_COMPANION_USE_DATA_IN_BACKGROUND":
                return "请求配套应用后台耗流量";
            case "android.permission.REQUEST_PASSWORD_RESET":
                return "请求重置设备密码";
            case "android.permission.CONTROL_WIFI_DISPLAY":
                return "控制WiFi投屏显示";
            case "android.permission.AUDIO_FOCUS_CHANGE":
                return "监听音频焦点变化";
            case "android.permission.ACCESS_DRM":
                return "访问DRM数字版权保护";
            case "android.permission.ACCESS_OTA_UPDATES":
                return "访问OTA系统更新";
            case "android.permission.BACKUP":
                return "系统备份与恢复权限";
            case "android.permission.DEVICE_POWER":
                return "设备电源控制";
            case "android.permission.HARDWARE_TEST":
                return "硬件测试权限";
            case "android.permission.ACCESS_MTK_MMHW":
                return "访问联发科硬件驱动";
            case "android.hardware.usb.host":
                return "USB主机权限";
            case "android.permission.BATTERY_STATS":
                return "电池统计信息";
            case "android.permission.SUSTAINED_PERFORMANCE_MODE":
                return "持续性能模式";
            case "android.permission.READ_PHONE_SINTERNETWIFI_STATE":
                return "读取手机/WiFi状态";

            // ======== 同步设置 ===========
            case "android.permission.READ_SYNC_SETTINGS":
                return "读取同步设置";
            case "android.permission.READ_SYNC_STATS":
                return "读取同步统计";
            case "android.permission.WRITE_SYNC_SETTINGS":
                return "写入同步设置";

            // ======== 生物识别 ===========
            case "android.permission.USE_FINGERPRINT":
                return "指纹识别";
            case "android.permission.USE_BIOMETRIC":
                return "生物识别";
            case "org.fidoalliance.uaf.permissions.FIDO_CLIENT":
                return "安全认证";
            case "com.fingerprints.service.ACCESS_FINGERPRINT_MANAGER":
                return "指纹硬件服务";
            case "com.sec.feature.fingerprint_manager_service":
                return "三星指纹管理服务";

            // ======== NFC 相关 ===========
            case "android.permission.NFC":
                return "NFC近场通信";
            case "android.permission.NFC_PREFERRED_PAYMENT_INFO":
                return "获取NFC默认支付信息";
            case "android.permission.NFC_TRANSACTION_EVENT":
                return "监听NFC交易事件";
            case "org.simalliance.openmobileapi.SMARTCARD":
                return "eSE/SIM卡安全元件";

            // ======== 电池优化 ===========
            case "android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS":
                return "请求忽略电池优化";

            // ======== 电视/车机/平板专属 ========
            case "android.permission.ACCESS_TV_INPUT":
                return "访问电视信号输入源";
            case "android.permission.CHANGE_TV_INPUT":
                return "切换电视信号输入源";
            case "android.permission.CAR_CONTROL_SERVICE":
                return "车机控制服务权限";
            case "android.permission.CAR_INFORMATION":
                return "读取车机车辆信息";
            case "android.permission.TUNER_RESOURCE_ACCESS":
                return "电视调谐器资源访问";

            // ======== 第三方广告SDK ========
            case "info.muge.appshare.openadsdk.permission.TT_PANGOLIN":
                return "穿山甲广告SDK权限";
            case "com.snda.wifilocating.openadsdk.permission.TT_PANGOLIN":
                return "穿山甲广告SDK权限";
            case "com.google.android.gms.permission.AD_ID":
                return "Google广告ID访问权限";
            case "freemme.permission.msa":
                return "MSA移动安全联盟广告标识";
            case "com.google.android.gms.permission.AD_ID_NOTIFICATION":
                return "广告ID通知";
            case "android.permission.ACCESS_ADSERVICES_TOPICS":
                return "访问广告主题信息";
            case "android.permission.ACCESS_ADSERVICES_AD_ID":
                return "访问广告ID";
            case "android.permission.ACCESS_ADSERVICES_ATTRIBUTION":
                return "访问广告归因信息";
            case "com.google.android.finsky.permission.BIND_GET_INSTALL_REFERRER_SERVICE":
                return "绑定Google Play安装引荐服务";

            // ======== 第三方推送SDK ========
            case "info.muge.appshare.permission.JPUSH_MESSAGE":
                return "极光推送消息权限";
            case "com.snda.wifilocating.permission.JPUSH_MESSAGE":
                return "极光推送消息权限";
            case "info.muge.appshare.permission.MIPUSH_RECEIVE":
                return "小米推送接收权限";
            case "com.snda.wifilocating.permission.MIPUSH_RECEIVE":
                return "小米推送接收权限";
            case "com.meizu.flyme.permission.PUSH":
                return "魅族推送权限";
            case "com.coloros.mcs.permission.RECIEVE_MCS_MESSAGE":
                return "OPPO推送接收权限";
            case "com.heytap.mcs.permission.RECIEVE_MCS_MESSAGE":
                return "欢太推送接收权限";
            case "com.hihonor.push.permission.READ_PUSH_NOTIFICATION_INFO":
                return "读取荣耀推送通知信息";
            case "com.google.android.c2dm.permission.RECEIVE":
                return "Google云消息接收";

            // ======== 厂商启动器设置 ========
            case "android.permission.READ_SETTINGS":
                return "读取系统启动器设置";
            case "com.android.launcher.permission.WRITE_SETTINGS":
                return "修改启动器设置";
            case "com.android.launcher.permission.WIRTE_SETTINGS ":
                return "修改启动器设置";
            case "com.oppo.launcher.permission.READ_SETTINGS":
                return "读取OPPO启动器设置";
            case "com.oppo.launcher.permission.WRITE_SETTINGS":
                return "修改OPPO启动器设置";
            case "com.htc.launcher.permission.READ_SETTINGS":
                return "读取HTC启动器设置";
            case "com.htc.launcher.permission.UPDATE_SHORTCUT":
                return "更新HTC快捷方式";
            case "com.android.launcher.permission.READ_SETTINGS":
                return "读取启动器设置";
            case "com.android.launcher2.permission.READ_SETTINGS":
                return "读取启动器2设置";
            case "com.android.launcher3.permission.READ_SETTINGS":
                return "读取启动器3设置";
            case "com.bbk.launcher2.permission.READ_SETTINGS":
                return "读取BBK启动器设置";
            case "com.bbk.launcher2.permission.WRITE_SETTINGS":
                return "修改BBK启动器设置";
            case "com.huawei.launcher2.permission.READ_SETTINGS":
                return "读取华为启动器2设置";
            case "com.huawei.launcher3.permission.READ_SETTINGS":
                return "读取华为启动器3设置";
            case "com.huawei.android.launcher.permission.READ_SETTINGS":
                return "读取华为启动器设置";
            case "com.huawei.android.launcher.permission.WRITE_SETTINGS":
                return "修改华为启动器设置";
            case "com.huawei.launcher.permission.READ_SETTINGS":
                return "读取华为启动器设置";
            case "com.miui.home.permission.READ_SETTINGS":
                return "读取MIUI桌面设置";
            case "com.miui.home.permission.WRITE_SETTINGS":
                return "修改MIUI桌面设置";
            case "net.oneplus.launcher.permission.READ_SETTINGS":
                return "读取一加启动器设置";
            case "net.oneplus.launcher.permission.WRITE_SETTINGS":
                return "修改一加启动器设置";
            case "cn.nubia.launcher.permission.READ_SETTINGS":
                return "读取努比亚启动器设置";
            case "cn.nubia.launcher.permission.WRITE_SETTINGS":
                return "修改努比亚启动器设置";
            case "cn.nubia.launcher2.permission.READ_SETTINGS":
                return "读取努比亚启动器2设置";
            case "cn.nubia.launcher2.permission.WRITE_SETTINGS":
                return "修改努比亚启动器2设置";
            case "com.lenovo.launcher.permission.READ_SETTINGS":
                return "读取联想启动器设置";
            case "com.lge.launcher.permission.READ_SETTINGS":
                return "读取LG启动器设置";
            case "com.tencent.qqlauncher.permission.READ_SETTINGS":
                return "读取QQ启动器设置";
            case "com.google.android.apps.nexuslauncher.permission.READ_SETTINGS":
                return "读取Google Nexus启动器设置";
            case "com.google.android.launcher.permission.READ_SETTINGS":
                return "读取Google启动器设置";
            case "org.adw.launcher.permission.READ_SETTINGS":
                return "读取ADW启动器设置";
            case "org.adw.launcher_donut.permission.READ_SETTINGS":
                return "读取ADW启动器(Donut版)设置";
            case "org.adwfreak.launcher.permission.READ_SETTINGS":
                return "读取ADWfreak启动器设置";
            case "com.teslacoilsw.launcher.permission.READ_SETTINGS":
                return "读取Nova启动器设置";
            case "com.anddoes.launcher.permission.READ_SETTINGS":
                return "读取Anddoes启动器设置";
            case "com.apusapps.launcher.permission.READ_SETTINGS":
                return "读取Apus启动器设置";
            case "com.mx.launcher.permission.READ_SETTINGS":
                return "读取MX启动器设置";
            case "com.tsf.shell.permission.READ_SETTINGS":
                return "读取TSF启动器设置";
            case "com.fede.launcher.permission.READ_SETTINGS":
                return "读取Fede启动器设置";
            case "com.ebproductions.android.launcher.permission.READ_SETTINGS":
                return "读取EB Productions启动器设置";
            case "com.android.mylauncher.permission.READ_SETTINGS":
                return "读取我的启动器设置";
            case "com.qihoo360.launcher.permission.READ_SETTINGS":
                return "读取360启动器设置(旧)";
            case "net.qihoo.launcher.permission.READ_SETTINGS":
                return "读取360启动器设置";
            case "com.sec.android.app.twlauncher.settings.READ_SETTINGS":
                return "读取三星TouchWiz启动器设置";
            case "com.s.launcher.permission.READ_SETTINGS":
                return "读取S启动器设置";
            case "com.actionlauncher.playstore.permission.READ_SETTINGS":
                return "读取Action启动器设置";
            case "com.tencent.qqlauncher.permission.WRITE_SETTINGS":
                return "修改QQ启动器设置";
            case "com.meizu.flyme.launcher.permission.READ_SETTINGS":
                return "读取魅族Flyme启动器设置";
            case "com.meizu.flyme.launcher.permission.WRITE_SETTINGS":
                return "修改魅族Flyme启动器设置";
            case "net.qihoo.launcher.permission.WRITE_SETTINGS":
                return "修改360启动器设置";
            case "com.qihoo360.launcher.permission.WRITE_SETTINGS":
                return "修改360启动器设置(旧)";
            case "com.qihoo360.home.permission.READ_SETTINGS":
                return "读取360桌面设置";
            case "com.qihoo360.home.permission.WRITE_SETTINGS":
                return "修改360桌面设置";
            case "com.qihoo360.screenlock.permission.READ_LOCAL_THEME":
                return "读取360锁屏本地主题";
            case "com.qihoo360.screenlock.permission.WRITE_LOCAL_THEME":
                return "修改360锁屏本地主题";
            case "com.lge.launcher.permission.WRITE_SETTINGS":
                return "修改LG启动器设置";
            case "com.lenovo.launcher.permission.WRITE_SETTINGS":
                return "修改联想启动器设置";
            case "com.lenovo.launcherhdmarket.permission.READ_SETTINGS":
                return "读取联想启动器市场设置";
            case "com.lenovo.launcherhdmarket.permission.WRITE_SETTINGS":
                return "修改联想启动器市场设置";
            case "com.anddoes.launcher.permission.WRITE_SETTINGS":
                return "修改Anddoes启动器设置";
            case "com.teslacoilsw.launcher.permission.WRITE_SETTINGS":
                return "修改Nova启动器设置";
            case "com.fede.launcher.permission.WRITE_SETTINGS":
                return "修改Fede启动器设置";
            case "com.ebproductions.android.launcher.permission.WRITE_SETTINGS":
                return "修改EB Productions启动器设置";
            case "com.android.mylauncher.permission.WRITE_SETTINGS":
                return "修改我的启动器设置";
            case "com.android.launcher2.permission.WRITE_SETTINGS":
                return "修改启动器2设置";
            case "com.android.launcher3.permission.WRITE_SETTINGS":
                return "修改启动器3设置";
            case "com.android.launcher23.permission.READ_SETTINGS":
                return "读取启动器23设置";
            case "com.android.launcher23.permission.WRITE_SETTINGS":
                return "修改启动器23设置";
            case "com.htc.launcher.permission.WRITE_SETTINGS":
                return "修改HTC启动器设置";
            case "com.sec.android.app.twlauncher.WRITE_SETTINGS":
                return "修改三星TouchWiz启动器设置";
            case "com.sec.android.app.twlauncher.settings.WRITE_SETTINGS":
                return "修改三星TouchWiz启动器设置(旧)";
            case "com.sec.android.app.launcher.permission.READ_SETTINGS":
                return "读取三星启动器设置";
            case "com.sec.android.app.launcher.permission.WRITE_SETTINGS":
                return "修改三星启动器设置";
            case "com.google.android.launcher.permission.WRITE_SETTINGS":
                return "修改Google启动器设置";
            case "org.adw.launcher.permission.WRITE_SETTINGS":
                return "修改ADW启动器设置";
            case "org.adw.launcher_donut.permission.WRITE_SETTINGS":
                return "修改ADW启动器(Donut版)设置";
            case "org.adwfreak.launcher.permission.WRITE_SETTINGS":
                return "修改ADWfreak启动器设置";
            case "com.s.launcher.permission.WRITE_SETTINGS":
                return "修改S启动器设置";
            case "com.tul.aviate.permission.READ_SETTINGS":
                return "读取TUL Aviate启动器设置";
            case "com.tul.aviate.permission.WRITE_SETTINGS":
                return "修改TUL Aviate启动器设置";
            case "com.dlto.atom.launcher.permission.READ_SETTINGS":
                return "读取Atom启动器设置";
            case "com.dlto.atom.launcher.permission.WRITE_SETTINGS":
                return "修改Atom启动器设置";
            case "com.android.sprdlauncher1.permission.READ_SETTINGS":
                return "读取展讯启动器1设置";
            case "com.android.sprdlauncher1.permission.WRITE_SETTINGS":
                return "修改展讯启动器1设置";
            case "com.android.sprdlauncher2.permission.READ_SETTINGS":
                return "读取展讯启动器2设置";
            case "com.android.sprdlauncher2.permission.WRITE_SETTINGS":
                return "修改展讯启动器2设置";
            case "com.modaco.android.launchergb.permission.READ_SETTINGS":
                return "读取Modaco GB启动器设置";
            case "com.modaco.android.launchergb.permission.WRITE_SETTINGS":
                return "修改Modaco GB启动器设置";
            case "com.nd.android.launcher.permission.READ_SETTINGS":
                return "读取ND启动器设置";
            case "com.nd.android.launcher.permission.WRITE_SETTINGS":
                return "修改ND启动器设置";
            case "com.lo.launcher.permission.READ_SETTINGS":
                return "读取LO启动器设置";
            case "com.lo.launcher.permission.WRITE_SETTINGS":
                return "修改LO启动器设置";
            case "com.hola.launcher.permission.READ_SETTINGS":
                return "读取Hola启动器设置";
            case "com.hola.launcher.permission.WRITE_SETTINGS":
                return "修改Hola启动器设置";
            case "com.nicelauncher.lolauncher.permission.READ_SETTINGS":
                return "读取Nice Launcher设置";
            case "com.nicelauncher.lolauncher.permission.WRITE_SETTINGS":
                return "修改Nice Launcher设置";
            case "com.zte.lqsoft.launcher.permission.READ_SETTINGS":
                return "读取中兴LQ启动器设置";
            case "com.zte.lqsoft.launcher.permission.WRITE_SETTINGS":
                return "修改中兴LQ启动器设置";
            case "com.konka.launcher2.permission.READ_SETTINGS":
                return "读取康佳启动器2设置";
            case "com.konka.launcher2.permission.WRITE_SETTINGS":
                return "修改康佳启动器2设置";
            case "com.gau.go.launcherex.permission.READ_SETTINGS":
                return "读取GO启动器EX设置";
            case "com.gau.go.launcherex.permission.WRITE_SETTINGS":
                return "修改GO启动器EX设置";
            case "com.iLoong.permission.READ_SETTINGS":
                return "读取iLoong启动器设置";
            case "com.iLoong.permission.WRITE_SETTINGS":
                return "修改iLoong启动器设置";
            case "com.zte.mobile.ZteLauncher3D.permission.READ_SETTINGS":
                return "读取中兴3D桌面设置";
            case "com.zte.mobile.ZteLauncher3D.permission.WRITE_SETTINGS":
                return "修改中兴3D桌面设置";
            case "com.ztemt.launcher.permission.READ_SETTINGS":
                return "读取中兴天机启动器设置";
            case "com.ztemt.launcher.permission.WRITE_SETTINGS":
                return "修改中兴天机启动器设置";
            case "com.zte.mifavor.launcher.permission.READ_SETTINGS":
                return "读取中兴MiFavor启动器设置";
            case "com.zte.mifavor.launcher.permission.WRITE_SETTINGS":
                return "修改中兴MiFavor启动器设置";
            case "com.microsoft.launcher.permission.READ_SETTINGS":
                return "读取微软桌面设置";
            case "com.microsoft.launcher.permission.WRITE_SETTINGS":
                return "修改微软桌面设置";
            case "com.jui.launcher3.permission.READ_SETTINGS":
                return "读取JUI启动器3设置";
            case "com.jui.launcher3.permission.WRITE_SETTINGS":
                return "修改JUI启动器3设置";
            case "com.baoruan.launcher2.permission.READ_SETTINGS":
                return "读取暴风启动器2设置";
            case "com.baoruan.launcher2.permission.WRITE_SETTINGS":
                return "修改暴风启动器2设置";
            case "com.Dean.launcher.permission.READ_SETTINGS":
                return "读取Dean启动器设置";
            case "com.Dean.launcher.permission.WRITE_SETTINGS":
                return "修改Dean启动器设置";
            case "com.fineos.launcher.permission.READ_SETTINGS":
                return "读取FINEOS启动器设置";
            case "com.fineos.launcher.permission.WRITE_SETTINGS":
                return "修改FINEOS启动器设置";
            case "com.huaqin.launcherEx.permission.READ_SETTINGS":
                return "读取华勤启动器EX设置";
            case "com.huaqin.launcherEx.permission.WRITE_SETTINGS":
                return "修改华勤启动器EX设置";
            case "com.ibingo.launcher.permission.READ_SETTINGS":
                return "读取iBingo启动器设置";
            case "com.ibingo.launcher.permission.WRITE_SETTINGS":
                return "修改iBingo启动器设置";
            case "com.l.launcher.permission.READ_SETTINGS":
                return "读取L启动器设置";
            case "com.l.launcher.permission.WRITE_SETTINGS":
                return "修改L启动器设置";
            case "com.lollipop.launcher.permission.READ_SETTINGS":
                return "读取Lollipop启动器设置";
            case "com.lollipop.launcher.permission.WRITE_SETTINGS":
                return "修改Lollipop启动器设置";
            case "com.slim.slimlauncher.permission.READ_SETTINGS":
                return "读取Slim启动器设置";
            case "com.slim.slimlauncher.permission.WRITE_SETTINGS":
                return "修改Slim启动器设置";
            case "com.epic.launcher.tw.permission.READ_SETTINGS":
                return "读取Epic启动器设置";
            case "com.epic.launcher.tw.permission.WRITE_SETTINGS":
                return "修改Epic启动器设置";
            case "com.mx.launcher.permission.WRITE_SETTINGS":
                return "修改MX启动器设置";
            case "com.tsf.shell.permission.WRITE_SETTINGS":
                return "修改TSF启动器设置";
            case "com.alphalp.launcher.permission.READ_SETTINGS":
                return "读取Alpha启动器设置";
            case "com.alphalp.launcher.permission.WRITE_SETTINGS":
                return "修改Alpha启动器设置";
            case "app.cobo.launcher.permission.READ_SETTINGS":
                return "读取Cobo启动器设置";
            case "app.cobo.launcher.permission.WRITE_SETTINGS":
                return "修改Cobo启动器设置";
            case "com.mycheering.launcher.permission.READ_SETTINGS":
                return "读取MyCheering启动器设置";
            case "com.mycheering.launcher.permission.WRITE_SETTINGS":
                return "修改MyCheering启动器设置";
            case "com.mgyun.permission.READ_SETTINGS":
                return "读取Mgyun启动器设置";
            case "com.mgyun.permission.WRITE_SETTINGS":
                return "修改Mgyun启动器设置";
            case "com.zui.launcher.permission.READ_SETTINGS":
                return "读取ZUI启动器设置";
            case "com.zui.launcher.permission.WRITE_SETTINGS":
                return "修改ZUI启动器设置";
            case "com.asus.launcher3.permission.READ_SETTINGS":
                return "读取华硕启动器3设置";
            case "com.asus.launcher3.permission.WRITE_SETTINGS":
                return "修改华硕启动器3设置";
            case "com.aliyun.homeshell.permission.READ_SETTINGS":
                return "读取阿里YunOS桌面设置";
            case "com.aliyun.homeshell.permission.WRITE_SETTINGS":
                return "修改阿里YunOS桌面设置";
            case "telecom.mdesk.permission.READ_SETTINGS":
                return "读取MDESK启动器设置";
            case "telecom.mdesk.permission.WRITE_SETTINGS":
                return "修改MDESK启动器设置";
            case "com.android.tpwlauncher2.permission.READ_SETTINGS":
                return "读取TPW启动器2设置";
            case "com.android.tpwlauncher2.permission.WRITE_SETTINGS":
                return "修改TPW启动器2设置";
            case "com.android.lewalauncher.permission.READ_SETTINGS":
                return "读取Lewa启动器设置";
            case "com.android.lewalauncher.permission.WRITE_SETTINGS":
                return "修改Lewa启动器设置";
            case "com.huawei.launcher2.permission.WRITE_SETTINGS":
                return "修改华为启动器2设置";
            case "com.huawei.launcher3.permission.WRITE_SETTINGS":
                return "修改华为启动器3设置";
            case "com.huawei.launcher.permission.WRITE_SETTINGS":
                return "修改华为启动器设置";
            case "com.smartisanos.launcher.data.ExportDataProvider":
                return "锤子桌面数据导出提供者";
            case "com.smartisanos.launcher.WRITE_DATABASE":
                return "写入锤子桌面数据库";
            case "com.smartisanos.launcher.READ_DATABASE":
                return "读取锤子桌面数据库";
            case "dianxin.permission.ACCESS_LAUNCHER_DATA":
                return "访问点心桌面数据";
            case "com.samsung.android.launcher.permission.READ_SETTINGS":
                return "读取三星启动器设置";
            case "com.samsung.android.launcher.permission.WRITE_SETTINGS":
                return "修改三星启动器设置";
            case "com.sec.android.app.twlauncher.READ_SETTINGS":
                return "读取三星TouchWiz启动器设置";
            case "com.google.android.apps.nexuslauncher.permission.WRITE_SETTINGS":
                return "修改Google Nexus启动器设置";

            // ======== 健康数据 ===========
            case "android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND":
                return "后台读取健康数据";
            case "android.permission.health.READ_HEART_RATE":
                return "读取心率数据";

            // ======== 其他系统/厂商特有权限 ==========
            case "android.permission.DOWNLOAD_WITHOUT_NOTIFICATION":
                return "无通知下载";
            case "android.permission.SUBSCRIBED_FEEDS_READ":
                return "读取订阅源";
            case "android.permission.SUBSCRIBED_FEEDS_WRITE":
                return "写入订阅源";
            case "android.permission.READ_CELL_BROADCASTS":
                return "读取小区广播";
            case "android.permission.CHANGE_WIMAX_STATE":
                return "修改Wimax状态";
            case "android.permission.ACCESS_WIMAX_STATE":
                return "访问Wimax状态";
            case "android.permission.UPDATE_APP_OPS_STATS":
                return "更新应用操作统计";
            case "android.permission.WRITE_SOCIAL_STREAM":
                return "写入社交动态";
            case "android.permission.READ_SOCIAL_STREAM":
                return "读取社交动态";
            case "android.permission.GET_INTENT_SENDER_INTENT":
                return "获取Intent发送者";
            case "android.permission.SEND_RESPOND_VIA_MESSAGE":
                return "通过消息回复";
            case "com.android.browser.permission.READ_HISTORY_BOOKMARKS":
                return "读取浏览器书签历史";
            case "com.android.browser.permission.WRITE_HISTORY_BOOKMARKS":
                return "写入浏览器书签历史";
            case "com.android.voicemail.permission.ADD_VOICEMAIL":
                return "添加语音邮件";
            case "com.android.voicemail.permission.READ_WRITE_ALL_VOICEMAIL":
                return "读写所有语音邮件";
            case "com.sec.android.permission.LAUNCH_PERSONAL_PAGE_SERVICE":
                return "启动个人页面服务";
            case "com.sec.android.permission.VOIP_INTERFACE":
                return "VoIP接口";
            case "com.sec.android.settings.permission.SOFT_RESET":
                return "软重启";
            case "com.sec.android.app.voicenote.Controller":
                return "语音笔记控制器";
            case "com.samsung.svoice.sync.READ_DATABASE":
                return "读取三星语音数据库";
            case "com.samsung.svoice.sync.WRITE_DATABASE":
                return "写入三星语音数据库";
            case "com.samsung.svoice.sync.ACCESS_SERVICE":
                return "访问三星语音服务";
            case "com.samsung.android.providers.context.permission.READ_RECORD_AUDIO":
                return "读取录音上下文";
            case "com.samsung.android.providers.context.permission.WRITE_RECORD_AUDIO":
                return "写入录音上下文";
            case "com.samsung.android.providers.context.permission.WRITE_USE_APP_FEATURE_SURVEY":
                return "写入应用功能调查";
            case "com.samsung.android.scloud.backup.lib.read":
                return "三星云备份读取";
            case "com.samsung.android.scloud.backup.lib.write":
                return "三星云备份写入";
            case "com.huawei.authentication.HW_ACCESS_AUTH_SERVICE":
                return "华为认证服务";
            case "com.google.android.providers.talk.permission.READ_ONLY":
                return "读取Talk只读";
            case "com.google.android.providers.talk.permission.WRITE_ONLY":
                return "写入Talk只写";
            case "com.google.android.providers.gsf.permission.READ_GSERVICES":
                return "读取Google服务框架";
            case "sec.android.permission.READ_MSG_PREF":
                return "读取消息偏好";
            case "com.asus.msa.SupplementaryDID.ACCESS":
                return "华硕补充设备标识访问";
            case "freemme.permission.msa.SECURITY_ACCESS":
                return "传音补充设备标识访问";
            case "moe.shizuku.manager.permission.API_V23":
                return "Shizuku API v23 权限";
            case "com.rosan.dhizuku.permission.API":
                return "Dhizuku API权限";
            case "com.android.vending.BILLING":
                return "Google支付计费";
            case "com.android.vending.CHECK_LICENSE":
                return "Google许可检查";
            case "com.google.android.gms.permission.ACTIVITY_RECOGNITION":
                return "Google活动识别";
            case "com.ss.android.auto.push.permission.MESSAGE":
                return "推送消息";
            case "com.ss.android.auto.permission.antisurvival":
                return "反保活检测";
            case "com.ss.android.auto.permission.MINIAPP_PROCESS_COMMUNICATION":
                return "小程序进程通信";
            case "com.ss.android.auto.minigame.PROCESS_COMMUNICATION":
                return "小游戏进程通信";
            case "com.ss.android.auto.permission.cjpay.multi.process":
                return "CJ支付多进程";
            case "com.ss.android.auto.permission.SELF_WRITE_DATABASE":
                return "自写数据库";
            case "com.ss.android.account.share.provider.tt.TT_READ_PROVIDER":
                return "账号分享读取";
            case "com.ss.android.account.share.provider.tt.TT_WRITE_PROVIDER":
                return "账号分享写入";
            case "com.ss.android.auto.permission.MIPUSH_RECEIVE":
                return "推送接收";
            case "com.ss.android.auto.permission.C2D_MESSAGE":
                return "云消息推送";

            // ======== Android 系统核心权限 ========
            case "android.permission.ACCESS_BLOBS_ACROSS_USERS":
                return "跨用户访问数据Blob";
            case "android.permission.ACCESS_BIOMETRIC_SENSOR_STRENGTHS":
                return "查询生物识别传感器安全等级";
            case "android.permission.ACCESS_INSTANT_APPS":
                return "查询并安装即时应用";
            case "android.permission.ACCESS_INTERACTIVE_AUTH_SERVICE":
                return "调用交互式身份验证服务";
            case "android.permission.ACCESS_UNINSTALLED_PACKAGES":
                return "查询已卸载应用信息";
            case "android.permission.ACTIVITY_EMBEDDING":
                return "在活动中嵌入其他活动";
            case "android.permission.ALLOW_SLIPPERY_TOUCHES":
                return "接收不精确触摸事件";
            case "android.permission.BIND_INLINE_SUGGESTIONS_UI_SERVICE":
                return "绑定内嵌建议UI服务";
            case "android.permission.BIND_PICTURE_IN_PICTURE_UI_SERVICE":
                return "绑定画中画UI服务";
            case "android.permission.CONTENT_CHANGE_OBSERVER":
                return "观察系统内容变化";
            case "android.permission.FORCE_EMPTY_MENU":
                return "强制清空选项菜单";
            case "android.permission.INTERCEPT_REMOTE_VIEWS":
                return "拦截远程视图加载";
            case "android.permission.INTERNAL_SYSTEM_WINDOW":
                return "创建系统内部窗口";
            case "android.permission.INVALIDATE_APP_RESTRICTION":
                return "清除应用限制标志";
            case "android.permission.LOOP_RADIO":
                return "控制循环广播";
            case "android.permission.MANAGE_DEVICE_ID_ATTESTATION":
                return "管理设备ID认证";
            case "android.permission.MANAGE_ONE_TIME_SYSTEM_SERVICES":
                return "管理一次性系统服务";
            case "android.permission.MANAGE_PHONE_NUMBERS":
                return "管理电话号码列表";
            case "android.permission.OVERRIDE_DO_NOT_ASK_CREDENTIALS":
                return "覆盖不再询问凭证标志";
            case "android.permission.PACKAGE_ROLLBACK_AGENT":
                return "管理应用包回滚";
            case "android.permission.PERFORM_ACTIVITY_EMBEDDING":
                return "执行活动嵌入操作";
            case "android.permission.PERFORM_ACTIVITY_POPUP":
                return "将嵌入活动弹出到新窗口";
            case "android.permission.PERFORM_AUTHENTICATION":
                return "执行身份验证操作";
            case "android.permission.PERFORM_ROLLBACK":
                return "执行应用包版本回滚";
            case "android.permission.POPULATE_WALLPAPER_COLORS":
                return "获取壁纸主色用于系统UI";
            case "android.permission.RECOVERY_UPDATE":
                return "在恢复模式下更新系统";
            case "android.permission.RETRIEVE_WINDOW_CONTENT":
                return "检索系统窗口内容";
            case "android.permission.SAFE_BOOT_OVERRIDE":
                return "覆盖安全启动标志";
            case "android.permission.SET_INPUT_CALIBRATION":
                return "设置输入设备校准参数";
            case "android.permission.SET_MEDIA_KEY_LISTENER":
                return "注册全局媒体按钮监听";
            case "android.permission.SKIP_CALL_SCREEN_UI":
                return "跳过拨号界面";
            case "android.permission.SUSPEND_APPS":
                return "挂起其他非活动应用";
            case "android.permission.USE_SYSTEM_OVERLAY_IMPLEMENTATION":
                return "使用系统默认悬浮窗实现";
            case "android.permission.WRITE_MEDIA_IMAGES":
                return "直接写入媒体存储中的图片";
            case "android.permission.MANAGE_OWN_CALLS":
                return "管理自己的通话（VoIP/自管理通话）";
            case "android.permission.START_VIEW_PERMISSION_USAGE":
                return "启动权限使用记录查看器（隐私看板）";
            case "jp.co.sony.threesixtyra.system.permission.OBTAIN_HRTF":
                return "获取 HRTF 空间音频参数";
            case "android.permission.FOREGROUND_SERVICE_FILE_TRANSFER":
                return "前台服务：文件传输";
            case "android.permission.FOREGROUND_SERVICE_MEDIA_PROCESSING":
                return "前台服务：媒体处理";
            case "android.permission.FOREGROUND_SERVICE_REMOTE_MESSAGING":
                return "前台服务：远程消息";

            // ======== 厂商及第三方 SDK 权限（华为/vivo/OPPO/魅族） ========
            case "com.huawei.permission.ACCESS_HW_KEYSTORE":
                return "访问华为硬件密钥库";
            case "com.huawei.permission.ACCESS_IBL_ACCOUNT":
                return "访问华为ID账号";
            case "com.huawei.permission.ACCESS_IMPRESS":
                return "访问华为IMPRESS打印服务";
            case "com.huawei.permission.ACCESS_INTELLIGENT_SECURITY":
                return "访问华为智能安全服务";
            case "com.huawei.permission.ACCESS_NLP":
                return "访问华为自然语言处理";
            case "com.huawei.permission.ACCESS_PUSH_SERVICE":
                return "华为推送服务";
            case "com.huawei.permission.ACCESS_WIFI_ANTENNA":
                return "访问华为WiFi天线状态";
            case "com.huawei.permission.CAMERA_ANTI_SHAKE":
                return "开启华为相机光学防抖";
            case "com.huawei.permission.DISABLE_SYSTEM_KEYEVENTS":
                return "禁用华为系统按键事件";
            case "com.huawei.permission.ENABLE_FINGERPRINT_LOCK":
                return "管理华为指纹锁屏";
            case "com.huawei.permission.READ_CELL_BROADCAST":
                return "读取华为小区广播";
            case "com.huawei.permission.READ_DEVICE_UDID":
                return "读取华为设备UDID";
            case "com.huawei.permission.USE_CRYPTO_ALGORITHM":
                return "调用华为硬件加密算法";
            case "com.huawei.permission.WRITE_BLUETOOTH_DB":
                return "写入华为蓝牙配对数据库";
            case "com.vivo.permission.ACCOUNT_ACCESS":
                return "访问vivo账号";
            case "com.vivo.permission.BACKUP_DATA":
                return "vivo系统数据备份";
            case "com.vivo.permission.CHECK_CRASH_REPORT":
                return "检查vivo崩溃报告";
            case "com.vivo.permission.DELETE_APP_PACKAGE":
                return "删除vivo应用包";
            case "com.vivo.permission.INSTALL_APP_PACKAGE":
                return "安装vivo应用包";
            case "com.vivo.permission.READ_SYSTEM_CONFIG":
                return "读取vivo系统配置";
            case "com.vivo.permission.RESTART_SYSTEM_SERVER":
                return "重启vivo系统服务";
            case "com.vivo.permission.SET_APP_PERMISSION":
                return "管理vivo应用权限";
            case "com.vivo.permission.WRITE_SYSTEM_CONFIG":
                return "修改vivo系统配置";
            case "com.oppo.permission.ACCESS_FINGERPRINT_DATA":
                return "访问OPPO指纹数据";
            case "com.oppo.permission.ACCESS_SYSTEM_UI":
                return "访问OPPO系统UI状态";
            case "com.oppo.permission.CHANGE_BLUETOOTH_STATE":
                return "修改OPPO蓝牙状态";
            case "com.oppo.permission.CHANGE_WIFI_STATE_CONFIG":
                return "修改OPPO WiFi配置";
            case "com.oppo.permission.LAUNCH_HOME_ACTIVITY":
                return "启动OPPO桌面主页";
            case "com.oppo.permission.READ_SYSTEM_SETTINGS":
                return "读取OPPO系统设置";
            case "com.oppo.permission.WRITE_SYSTEM_SETTINGS":
                return "修改OPPO系统设置";
            case "oplus.permission.settings.LAUNCH_FOR_EXPORT":
                return "启动OPPO系统设置";
            case "com.oplus.ocs.permission.third":
                return "访问OPPO账号数据";
            case "com.meizu.permission.ACCESS_FACE_DATA":
                return "访问魅族面部数据";
            case "com.meizu.permission.ACCESS_THIRD_PARTY_APP":
                return "管理魅族第三方应用安装";
            case "com.meizu.permission.USE_EDU_ACCOUNT":
                return "访问魅族教育账户";
            case "com.bybutter.key.WRITE_DATABASE":
                return "存储设备密钥";
            case "com.huawei.hicar.HICAR_PERMISSION":
                return "华为车载互联";
            case "com.huawei.permission.external_app_settings.USE_COMPONENT":
                return "华为外部应用设置组件";
            case "com.meizu.c2dm.permission.RECEIVE":
                return "魅族推送服务(C2DM)";
            case "com.meizu.flyme.push.permission.RECEIVE":
                return "魅族云端消息";
            case "ohos.permission.ACCESS_SEARCH_SERVICE":
                return "鸿蒙融合搜索服务";

            // === 华为 / 荣耀 服务权限 小米 / Redmi OPPO / 一加 / realme /vivo===
            case "com.huawei.permission.tee.USE_OTRP":
                return "使用华为TEE OTRP";
            case "com.huawei.permission.USE_TSM_AGENT":
                return "使用华为TSM代理";
            case "com.huawei.wallet.permission.QUERY_TRAFFIC_CARD_INFO":
                return "查询华为交通卡信息";
            case "com.huawei.appmarket.service.commondata.permission.GET_COMMON_DATA":
                return "获取华为应用市场公共数据";
            case "com.huawei.health.permission.BASE_STEP_SERVICE":
                return "读取华为健康基础步数";
            case "com.huawei.easygo.permission.READ_PERMISSION":
                return "读取华为EasyGo权限";
            case "com.hihonor.permission.ACCESS_HW_KEYSTORE":
                return "访问荣耀硬件密钥库";
            case "com.hihonor.health.permission.BASE_STEP_SERVICE":
                return "读取荣耀健康基础步数";
            case "com.hihonor.onetouchshare.permission.BIND_ONETOUCHSHARE_SERVICE":
                return "绑定荣耀一碰传服务";
            case "com.hihonor.security.permission.ACCESS_THREAT_DETECTION":
                return "访问荣耀威胁检测";
            case "com.hihonor.brain.permission.KIT_SERVICE_ACCESS":
                return "访问荣耀大脑服务";
            case "com.huawei.meetime.CAAS_SHARE_SERVICE":
                return "华为畅连跨设备分享服务";
            case "miui.permission.READ_STEPS":
                return "读取MIUI步数";
            case "com.miui.personalassistant.permission.WIDGET_READ_SETTINGS":
                return "读取MIUI智能助理小组件设置";
            case "com.miui.personalassistant.permission.WIDGET_WRITE_SETTINGS":
                return "修改MIUI智能助理小组件设置";
            case "com.miui.mihome2.permission.READ_SETTINGS":
                return "读取MIUI桌面2设置";
            case "com.miui.mihome2.permission.WRITE_SETTINGS":
                return "修改MIUI桌面2设置";
            case "com.miui.easygo.permission.READ_PERMISSION":
                return "读取MIUI EasyGo权限";
            case "com.xiaomi.security.permission.ACCESS_XSOF":
                return "访问小米安全服务";
            case "com.xiaomi.market.sdk.UPDATE":
                return "小米应用商店更新";
            case "com.oplus.permission.safe.AI_APP":
                return "OPPO AI 应用安全权限";
            case "com.oppo.permission.safe.AI_APP":
                return "OPPO AI 应用安全权限(旧)";
            case "com.oplus.interconnect.permission.SERVICE":
                return "OPPO 互联服务";
            case "com.oplus.pantaconnect.permission.CONNECTIVITY":
                return "OPPO Pantaconnect 连接";
            case "com.oplus.metis.factdata.permission.DATABASE":
                return "OPPO Metis 数据数据库";
            case "oppo.permission.OPPO_COMPONENT_SAFE":
                return "OPPO 组件安全";
            case "com.oplus.notification.permission.BADGE_DIGITAL":
                return "OPPO 桌面角标数字";
            case "com.vivo.healthservice.permission.DATA_ACCESS":
                return "访问vivo健康服务数据";
            case "com.vivo.assistant.permission.sport.broadcast":
                return "vivo 助手运动广播";
            case "com.vivo.assistant.permission.access.provider":
                return "访问vivo助手提供者";
            case "com.vivo.assistant.StepProvider":
                return "vivo助手步数提供者";
            case "com.vivo.identifier.permission.OAID_STATE":
                return "读取 vivo OAID 状态";
            case "com.vivo.identifier.permission.OAID":
                return "读取 vivo OAID 值";
            case "com.vivo.identifier.permission.OAID_STATE_DIALOG":
                return "vivo OAID 状态弹窗控制";
            case "com.nemu.oaid.permission.read":
                return "Nemu OAID 读取";
            case "com.nemu.oaid.permission.write":
                return "Nemu OAID 写入";
            case "com.asus.permission.READ_SDID_PROVIDER":
                return "华硕读取补充设备标识SDID";
            case "com.miui.systemAdSolution.adSwitch.PROVIDER":
                return "小米广告开关Provider访问";
            case "com.miui.systemAdSolution.permission.MSA_CACHE":
                return "小米MSA缓存访问";

            // ======== 阿里 YunOS / 阿里云 /支付宝 /淘宝 ========
            case "com.yunos.permission.STORAGE_SERVICE":
                return "阿里YunOS存储服务";
            case "com.yunos.permission.TYID_MGR_SERVICE":
                return "阿里YunOS TYID管理服务";
            case "com.yunos.permission.TYID_SERVICE":
                return "阿里YunOS TYID服务";
            case "com.aliyun.permission.TYID_SERVICE":
                return "阿里云TYID服务";
            case "com.aliyun.permission.TYID_MGR_SERVICE":
                return "阿里云TYID管理服务";
            case "com.aliyun.permission.STORAGE_SERVICE":
                return "阿里云存储服务";
            case "com.alipay.permission.ALIPAY_USE_CREDENTIALS":
                return "使用支付宝凭证";
            case "com.alipay.permission.ALIPAY_UPDATE_CREDENTIALS":
                return "更新支付宝凭证";
            case "com.alipay.mobile.wallet.agent.BIND_IPC_SERVICE":
                return "绑定支付宝钱包IPC服务";
            case "com.alipay.mobile.rome.voicebroadcast.helper.BROADCAST":
                return "支付宝语音播报广播";
            case "com.taobao.permission.USE_CREDENTIALS":
                return "使用淘宝凭证";
            case "com.taobao.permission.UPDATE_CREDENTIALS":
                return "更新淘宝凭证";
            case "com.eg.android.AlipayGphone.permission.FUSION_STATE_CHANGE":
                return "支付宝融合状态变更";
            case "com.eg.android.AlipayGphone.permission.MIPUSH_RECEIVE":
                return "支付宝小米推送接收";
            case "com.eg.android.AlipayGphone.permission.NFC_PRE_LAUNCH":
                return "支付宝NFC预启动";
            case "com.eg.android.AlipayGphone.permission.C2D_MESSAGE":
                return "支付宝云消息(C2D)";
            case "com.eg.android.AlipayGphone.push.permission.MESSAGE":
                return "支付宝推送消息";
            case "com.soter.permission.ACCESS_SOTER_KEYSTORE":
                return "Soter 密钥库访问";
            case "nxp.permission.ACCESS_WALLET_SERVICE":
                return "NXP 钱包服务访问";
            case "cn.org.ifaa.permission.USE_IFAA_MANAGER":
                return "IFAA 生物识别管理";
            case "hyper.permission.POWER_MODE_LEVEL":
                return "Hyper 性能模式等级";
            case "slardaros.intent.permission":
                return "Slardaros 意图权限";
            case "android.permission.BAIDU_LOCATION_SERVICE":
                return "百度定位服务";

            // ======== 系统服务与管理权限 ========
            case "android.permission.ACCESS_KEYGUARD_SECURE_STORAGE":
                return "访问锁屏安全存储";
            case "android.permission.BIND_DEVICE_OWNER":
                return "绑定设备管理器";
            case "android.permission.BIND_PACKAGE_VERIFIER":
                return "绑定应用包验证服务";
            case "android.permission.BIND_QUICK_SETTINGS_TILE":
                return "创建快捷设置磁贴";
            case "android.permission.CREATE_USERS":
                return "创建和管理多用户";
            case "android.permission.MANAGE_APPOPS":
                return "管理应用操作权限";
            case "android.permission.MANAGE_NOTIFICATIONS":
                return "管理所有应用通知策略";
            case "android.permission.PEERS_MAC_ADDRESS":
                return "读取对等设备MAC地址";
            case "android.permission.SUSPEND_PACKAGES":
                return "挂起其他应用";
            case "android.permission.START_ACTIVITIES_FROM_BACKGROUND":
                return "从后台启动Activity";

            // ======== 安全与生物识别权限 ========
            case "android.permission.ACCESS_FINGERPRINT_MANAGER":
                return "访问指纹管理器服务";
            case "android.permission.ACCESS_KEYSTORE":
                return "访问系统密钥库";
            case "android.permission.MANAGE_BIOMETRIC":
                return "管理生物识别功能";
            case "android.permission.USE_FACE":
                return "使用人脸识别";
            case "android.permission.USE_IRIS":
                return "使用虹膜识别";

            // ======== 硬件与底层系统权限 ========
            case "android.permission.ACCESS_UWB":
                return "访问超宽带(UWB)硬件";
            case "android.permission.CONTROL_ALWAYS_ON_DISPLAY":
                return "控制息屏显示";
            case "android.permission.SYSTEM_CAMERA":
                return "打开系统相机服务";
            case "android.permission.WRITE_APN_SETTINGS":
                return "写入APN设置";

            // ======== 账户、联系人及企业权限 ========
            case "android.permission.ACCESS_DEVICE_OWNER":
                return "访问设备管理器功能";
            case "android.permission.GET_DEVICE_ACCOUNTS":
                return "获取设备账户列表";
            case "android.permission.INSTALL_DEVICE_ADMIN":
                return "安装设备管理器";
            case "android.permission.SET_DEVICE_OWNER":
                return "设置设备所有者";

            // ======== 测试与调试权限 ========
            case "android.permission.ACCESS_MOCK_LOCATION":
                return "创建模拟位置提供程序";
            case "android.permission.INSTRUMENTATION":
                return "启动测试Instrumentation";
            case "android.permission.MANAGE_USERS":
                return "管理多用户";
            case "mediatek.permission.ACCESS_APU_SYS":
                return "联发科 APU 系统访问";
            case "permission.bind.HiMediaEngines":
                return "绑定华为媒体引擎";
            case "android.permission.RUN_INSTRUMENTATION":
                return "运行自动化测试 Instrumentation";

            default:
                int lastDot = perm.lastIndexOf('.');
                String shortName = lastDot == -1 ? perm : perm.substring(lastDot + 1);
                String readable = shortName.replaceAll("([a-z])([A-Z])", "$1 $2").toLowerCase();
                return UNKNOWN_PREFIX + ": " + readable;
        }
    }

    // ============================================================
    //  权限分组（语义归类）
    // ============================================================
    // 检测权限（复用 HookInit.DETECTION_PERMISSIONS 常量，分组判定专用）
    public static final String[] DETECTION_PERMISSIONS = {
            "android.permission.QUERY_ALL_PACKAGES",
            "android.permission.GET_PACKAGE_SIZE",
            "com.android.permission.GET_INSTALLED_APPS",
            "android.permission.PACKAGE_USAGE_STATS",
            "android.permission.PACKAGE_VERIFICATION_AGENT",
            "android.permission.READ_DEFAULT_APPLICATIONS",
            "android.permission.SET_PREFERRED_APPLICATIONS",
    };
    public static final String GROUP_CAMERA = "相机";
    public static final String GROUP_LOCATION = "定位";
    public static final String GROUP_CONTACTS = "通讯录";
    public static final String GROUP_ACCOUNTS = "账户";
    public static final String GROUP_SMS = "短信";
    public static final String GROUP_CALL_LOG = "通话记录";
    public static final String GROUP_PHONE = "电话";
    public static final String GROUP_STORAGE = "存储/媒体";
    public static final String GROUP_MICROPHONE = "麦克风";
    public static final String GROUP_SENSOR = "传感器";
    public static final String GROUP_NOTIFICATION = "通知";
    public static final String GROUP_NETWORK = "网络";
    public static final String GROUP_NEARBY = "附近/蓝牙";
    public static final String GROUP_BIOMETRIC = "生物识别";
    public static final String GROUP_BATTERY = "电池";
    public static final String GROUP_CLOCK = "时钟";
    public static final String GROUP_WINDOW = "窗口";
    public static final String GROUP_APP_LIST = "应用列表";
    public static final String GROUP_SETTINGS = "设置";
    public static final String GROUP_CLIPBOARD = "剪贴板";
    public static final String GROUP_NFC = "NFC";
    public static final String GROUP_PKG_MGR = "安装/卸载管控";
    public static final String GROUP_BIND = "服务绑定";
    public static final String GROUP_FOREGROUND = "前台服务";
    public static final String GROUP_PROCESS = "进程管理";
    public static final String GROUP_USERS = "多用户/策略";
    public static final String GROUP_KEYGUARD = "锁屏/屏保";
    public static final String GROUP_BOOT = "开机自启";
    public static final String GROUP_HEALTH = "健康";
    public static final String GROUP_AD_SDK = "广告SDK";
    public static final String GROUP_PUSH_SDK = "推送SDK";
    public static final String GROUP_VENDOR_HOME = "厂商桌面";
    public static final String GROUP_OTHER = "其他权限";
    public static final String GROUP_CUSTOM = "自定义权限";
    public static final String GROUP_UNKNOWN = "未知权限";

    public static final String[] GROUP_DISPLAY_ORDER = {
            GROUP_CAMERA, GROUP_LOCATION, GROUP_CONTACTS,
            GROUP_ACCOUNTS, GROUP_SMS, GROUP_CALL_LOG, GROUP_PHONE,
            GROUP_STORAGE, GROUP_MICROPHONE, GROUP_SENSOR, GROUP_NOTIFICATION,
            GROUP_NETWORK, GROUP_NEARBY, GROUP_CLIPBOARD, GROUP_NFC,
            GROUP_BIOMETRIC, GROUP_BATTERY, GROUP_CLOCK, GROUP_WINDOW,
            GROUP_APP_LIST, GROUP_SETTINGS, GROUP_PKG_MGR, GROUP_BIND,
            GROUP_FOREGROUND, GROUP_PROCESS, GROUP_USERS, GROUP_KEYGUARD,
            GROUP_BOOT, GROUP_HEALTH, GROUP_AD_SDK, GROUP_PUSH_SDK,
            GROUP_VENDOR_HOME, GROUP_OTHER, GROUP_CUSTOM, GROUP_UNKNOWN
    };

    // 组名 → 排序权重（越靠前越小；缺省落到未知组权重之后）
    private static final java.util.Map<String, Integer> sGroupOrder;

    static {
        java.util.HashMap<String, Integer> m = new java.util.HashMap<String, Integer>();
        for (int i = 0; i < GROUP_DISPLAY_ORDER.length; i++) m.put(GROUP_DISPLAY_ORDER[i], i);
        sGroupOrder = java.util.Collections.unmodifiableMap(m);
    }

    /** 组名排序权重；未知组名给 Integer.MAX_VALUE（永远排最后） */
    public static int groupOrder(String groupName) {
        if (groupName == null) return Integer.MAX_VALUE;
        Integer w = sGroupOrder.get(groupName);
        return w != null ? w : Integer.MAX_VALUE;
    }

    /** 把一个权限字符串归到语义组名。 规则为「首个命中即返回」，覆盖系统权限 + 自定义/第三方权限字符串。 */
    public static String groupOf(String perm) {
        if (perm == null) return GROUP_OTHER;
        boolean system = perm.startsWith("android.permission.");
        if (!system) {
            // 自定义权限（第三方SDK/厂商）：未收录语义映射的归「未知权限」
            if (getFriendlyName(perm).startsWith(UNKNOWN_PREFIX)) return GROUP_UNKNOWN;
            return GROUP_CUSTOM;
        }
        if (perm.contains("CAMERA")) return GROUP_CAMERA;
        if (perm.contains("LOCATION")) return GROUP_LOCATION;
        if (perm.contains("CONTACT")) return GROUP_CONTACTS;
        if (perm.contains("ACCOUNT")) return GROUP_ACCOUNTS;
        if (perm.contains("SMS") || perm.contains("MMS") || perm.contains("WAP_PUSH"))
            return GROUP_SMS;
        if (perm.contains("CALL_LOG")) return GROUP_CALL_LOG;
        if (perm.contains("PHONE")) return GROUP_PHONE;
        if (perm.contains("STORAGE") || perm.contains("MEDIA")) return GROUP_STORAGE;
        if (perm.contains("RECORD_AUDIO") || perm.contains("MICROPHONE")) return GROUP_MICROPHONE;
        if (perm.contains("SENSOR") || perm.contains("ACTIVITY_RECOGNITION")) return GROUP_SENSOR;
        if (perm.contains("NOTIFICATION")) return GROUP_NOTIFICATION;
        // 网络组：状态查询类（INTERNET 不含 WIFI 且不含 NEARBY，放在 WIFI 规则之前）
        if (perm.contains("INTERNET") || perm.contains("NETWORK")) return GROUP_NETWORK;
        if (perm.contains("BLUETOOTH") || perm.contains("WIFI") || perm.contains("NEARBY"))
            return GROUP_NEARBY;
        if (perm.contains("BIOMETRIC") || perm.contains("FINGERPRINT")) return GROUP_BIOMETRIC;
        if (perm.contains("BATTERY")) return GROUP_BATTERY;
        if (perm.contains("ALARM")) return GROUP_CLOCK;
        if (perm.contains("WINDOW")) return GROUP_WINDOW;
        if (perm.contains("QUERY_ALL_PACKAGES") || perm.contains("PACKAGE_USAGE") ||
                perm.contains("GET_PACKAGE_SIZE") || perm.contains("GET_INSTALLED_APPS"))
            return GROUP_APP_LIST;
        if (perm.contains("SETTINGS")) return GROUP_SETTINGS;
        // ======== 细分子组 ========
        if (perm.contains("CLIPBOARD") || perm.contains("CLIPS")) return GROUP_CLIPBOARD;
        if (perm.contains("NFC") || perm.contains("SMARTCARD")) return GROUP_NFC;
        if (perm.contains("INSTALL_PACKAGES") || perm.contains("DELETE_PACKAGES") || perm.contains("REQUEST_INSTALL") ||
                perm.contains("REQUEST_DELETE") || perm.contains("CLEAR_APP") || perm.contains("FORCE_STOP") ||
                perm.contains("READ_INSTALL_SESSIONS")) return GROUP_PKG_MGR;
        if (perm.startsWith("android.permission.BIND_")) return GROUP_BIND;
        if (perm.contains("FOREGROUND_SERVICE")) return GROUP_FOREGROUND;
        if (perm.contains("TASKS") || perm.contains("KILL_BACKGROUND") || perm.contains("SET_PROCESS_LIMIT") ||
                perm.contains("PERSISTENT_ACTIVITY") || perm.contains("STOP_APP_SWITCHES") || perm.contains("RESTART_PACKAGES"))
            return GROUP_PROCESS;
        if (perm.contains("USERS") || perm.contains("PROFILES") || perm.contains("OWNER") || perm.contains("DEVICE_POLICY"))
            return GROUP_USERS;
        if (perm.contains("KEYGUARD") || perm.contains("DREAM")) return GROUP_KEYGUARD;
        if (perm.contains("BOOT_COMPLETED") || perm.contains("BOOT_UPGRADE") || perm.contains("RECEIVE_USER_PRESENT"))
            return GROUP_BOOT;
        if (perm.contains("HEALTH") || perm.contains("FITNESS")) return GROUP_HEALTH;
        // 第三方广告SDK（含自定义权限字符串）
        if (perm.contains("TT_PANGOLIN") || perm.contains("ADSERVICES") || perm.contains("AD_ID") || perm.contains("FREEMEME"))
            return GROUP_AD_SDK;
        // 第三方推送SDK（含自定义权限字符串）
        if (perm.contains("JPUSH") || perm.contains("MIPUSH") || perm.contains("MCS_MESSAGE") || perm.contains("C2DM") ||
                perm.contains("PUSH_PROVIDER")) return GROUP_PUSH_SDK;
        // 厂商启动器/桌面设置（自定义权限字符串）
        if (perm.contains("LAUNCHER") || perm.contains("HOME") || perm.contains("MIUI") || perm.contains("HUAWEI") ||
                perm.contains("VIVO") || perm.contains("OPPO") || perm.contains("MEIZU") || perm.contains("HONOR") ||
                perm.contains("BBK") || perm.contains("QIHO") || perm.contains("LGE") || perm.contains("HTC") ||
                perm.contains("TTS") || perm.contains("SEC_")) return GROUP_VENDOR_HOME;
        // 未命中任何语义规则且未收录 → 未知权限
        if (getFriendlyName(perm).startsWith(UNKNOWN_PREFIX)) return GROUP_UNKNOWN;
        return GROUP_OTHER;
    }
}
