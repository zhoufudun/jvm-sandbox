package com.alibaba.jvm.sandbox.core;

import com.alibaba.jvm.sandbox.api.Information;
import com.alibaba.jvm.sandbox.core.util.FeatureCodec;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.*;

/**
 * 内核启动配置
 * Created by luanjia@taobao.com on 16/10/2.
 */
public class CoreConfigure {

    private static final String KEY_NAMESPACE = "namespace";
    private static final String DEFAULT_VAL_NAMESPACE = "default";

    private static final String KEY_SANDBOX_HOME = "sandbox_home";
    private static final String KEY_LAUNCH_MODE = "mode";
    private static final String KEY_SERVER_IP = "server.ip";
    private static final String KEY_SERVER_PORT = "server.port";
    private static final String KEY_SERVER_CHARSET = "server.charset";

    private static final String KEY_SYSTEM_MODULE_LIB_PATH = "system_module";
    private static final String KEY_USER_MODULE_LIB_PATH = "user_module";
    private static final String KEY_PROVIDER_LIB_PATH = "provider";
    private static final String KEY_CFG_LIB_PATH = "cfg";
    private static final String VAL_LAUNCH_MODE_AGENT = "agent";
    private static final String VAL_LAUNCH_MODE_ATTACH = "attach";

    private static final String KEY_UNSAFE_ENABLE = "unsafe.enable";
    private static final String KEY_NATIVE_SUPPORTED = "native.supported";

    // 受保护key数组，在保护key范围之内，以用户传递的配置为准，系统配置不允许覆盖
    private static final String[] PROTECT_KEY_ARRAY = {KEY_NAMESPACE, KEY_SANDBOX_HOME, KEY_LAUNCH_MODE, KEY_SERVER_IP, KEY_SERVER_PORT, KEY_SERVER_CHARSET};

    // 用户配置和系统默认配置都可以，需要进行合并的key，例如user_module
    private static final String[] MULTI_KEY_ARRAY = {KEY_USER_MODULE_LIB_PATH};

    private static final FeatureCodec codec = new FeatureCodec(';', '=');

    private final Map<String, String> featureMap = new LinkedHashMap<>();

    private CoreConfigure(final String featureString, // ;cfg=/root/jvm-sandbox/sandbox/bin/../cfg;system_module=/root/jvm-sandbox/sandbox/bin/../module;mode=attach;sandbox_home=/root/jvm-sandbox/sandbox/bin/..;user_module=/root/jvm-sandbox/sandbox/sandbox-module;provider=/root/jvm-sandbox/sandbox/bin/../provider;namespace=default;server.ip=0.0.0.0;server.port=0;
                          final String propertiesFilePath) { // /root/jvm-sandbox/sandbox/bin/../cfg/sandbox.properties
        final Map<String, String> featureMap = toFeatureMap(featureString); // ;cfg=/root/jvm-sandbox/sandbox/bin/../cfg;system_module=/root/jvm-sandbox/sandbox/bin/../module;mode=attach;sandbox_home=/root/jvm-sandbox/sandbox/bin/..;user_module=/root/jvm-sandbox/sandbox/sandbox-module;provider=/root/jvm-sandbox/sandbox/bin/../provider;namespace=default;server.ip=0.0.0.0;server.port=0;
        final Map<String, String> propertiesMap = toPropertiesMap(propertiesFilePath); // {unsafe.enable=true, user_module=~/.sandbox-module;, server.charset=UTF-8}
        this.featureMap.putAll(merge(featureMap, propertiesMap)); // 合并后： {mode=attach, sandbox_home=/root/jvm-sandbox/sandbox/bin/.., user_module=/root/jvm-sandbox/sandbox/sandbox-module;~/.sandbox-module;, cfg=/root/jvm-sandbox/sandbox/bin/../cfg, provider=/root/jvm-sandbox/sandbox/bin/../provider, namespace=default, server.ip=0.0.0.0, server.port=0, system_module=/root/jvm-sandbox/sandbox/bin/../module, unsafe.enable=true, server.charset=UTF-8}
    }

    private Map<String, String> toFeatureMap(String featureString) {
        return codec.toMap(featureString);
    }

    private Map<String, String> toPropertiesMap(String propertiesFilePath) {
        final Map<String, String> propertiesMap = new LinkedHashMap<>();

        if(null == propertiesFilePath) {
            return propertiesMap;
        }

        final File propertiesFile = new File(propertiesFilePath);
        if (!propertiesFile.exists()
                || !propertiesFile.canRead()) {
            return propertiesMap;
        }


        // 从指定配置文件路径中获取配置信息
        final Properties properties = new Properties();
        InputStream is = null;
        try {
            is = FileUtils.openInputStream(propertiesFile);
            properties.load(is);
        } catch (Throwable cause) {
            // cause.printStackTrace(System.err);
        } finally {
            IOUtils.closeQuietly(is);
        }

        // 转换为Map
        for (String key : properties.stringPropertyNames()) {
            propertiesMap.put(key, properties.getProperty(key));
        }

        return propertiesMap;
    }

    /**
     *
     * @param featureMap : {mode=attach, sandbox_home=/root/jvm-sandbox/sandbox/bin/.., user_module=/root/jvm-sandbox/sandbox/sandbox-module, cfg=/root/jvm-sandbox/sandbox/bin/../cfg, provider=/root/jvm-sandbox/sandbox/bin/../provider, namespace=default, server.ip=0.0.0.0, server.port=0, system_module=/root/jvm-sandbox/sandbox/bin/../module}
     * @param propertiesMap: {unsafe.enable=true, user_module=~/.sandbox-module;, server.charset=UTF-8}
     * @return
     */
    private Map<String, String> merge(Map<String, String> featureMap, Map<String, String> propertiesMap) {

        // 以featureMap配置为准
        final Map<String, String> mergeMap = new LinkedHashMap<>(featureMap);

        // 合并propertiesMap
        for (final Map.Entry<String, String> propertiesEntry : propertiesMap.entrySet()) {

            // 如果是多值KEY，且featureMap中也存在，则进行合并
            if (ArrayUtils.contains(MULTI_KEY_ARRAY, propertiesEntry.getKey())
                    && mergeMap.containsKey(propertiesEntry.getKey())) {
                mergeMap.put(
                        propertiesEntry.getKey(),
                        mergeMap.get(propertiesEntry.getKey()) + ";" + propertiesEntry.getValue()
                );
            }

            // 如果是受保护KEY，只有在featureMap中为空值时才能合并入
            else if(ArrayUtils.contains(PROTECT_KEY_ARRAY, propertiesEntry.getKey())) {
                mergeMap.computeIfAbsent(propertiesEntry.getKey(), k -> propertiesEntry.getValue());
            }



            // 其他情况一律以propertiesMap为准
            else {
                mergeMap.put(propertiesEntry.getKey(), propertiesEntry.getValue());
            }

        }

        return mergeMap;

    }

    private static volatile CoreConfigure instance;

    public static CoreConfigure toConfigure(final String featureString, final String propertiesFilePath) {
        return instance = new CoreConfigure(featureString, propertiesFilePath);
    }

    public static CoreConfigure getInstance() {
        return instance;
    }

    /**
     * 获取容器的命名空间
     *
     * @return 容器的命名空间
     */
    public String getNamespace() {
        final String namespace = featureMap.get(KEY_NAMESPACE);
        return StringUtils.isNotBlank(namespace)
                ? namespace
                : DEFAULT_VAL_NAMESPACE;
    }

    /**
     * 获取系统模块加载路径
     *
     * @return 模块加载路径
     */
    public String getSystemModuleLibPath() {
        return featureMap.get(KEY_SYSTEM_MODULE_LIB_PATH);
    }


    /**
     * 获取用户模块加载路径
     *
     * @return 用户模块加载路径
     */
    public String getUserModuleLibPath() {
        return featureMap.get(KEY_USER_MODULE_LIB_PATH);
    }

    /**
     * 获取用户模块加载路径(集合)
     *
     * @return 用户模块加载路径(集合)
     */
    public String[] getUserModuleLibPaths() {
        return replaceWithSysPropUserHome(codec.toCollection(featureMap.get(KEY_USER_MODULE_LIB_PATH)).toArray(new String[]{}));
    }

    private static String[] replaceWithSysPropUserHome(final String[] pathArray) {
        if (ArrayUtils.isEmpty(pathArray)) {
            return pathArray;
        }
        final String SYS_PROP_USER_HOME = System.getProperty("user.home");
        for (int index = 0; index < pathArray.length; index++) {
            if (StringUtils.startsWith(pathArray[index], "~")) {
                pathArray[index] = StringUtils.replaceOnce(pathArray[index], "~", SYS_PROP_USER_HOME);
            }
        }
        return pathArray;
    }

    /**
     * 获取用户模块加载文件/目录(集合)
     *
     * @return 用户模块加载文件/目录(集合)
     */
    public synchronized File[] getUserModuleLibFiles() {

        final Collection<File> foundModuleJarFiles = new LinkedHashSet<>();
        for (final String path : getUserModuleLibPaths()) {
            final File fileOfPath = new File(path);
            if (fileOfPath.isDirectory()) {
                foundModuleJarFiles.addAll(FileUtils.listFiles(new File(path), new String[]{"jar"}, false));
            } else {
                if (StringUtils.endsWithIgnoreCase(fileOfPath.getPath(), ".jar")) {
                    foundModuleJarFiles.add(fileOfPath);
                }
            }
        }

        return GET_USER_MODULE_LIB_FILES_CACHE = foundModuleJarFiles.toArray(new File[]{});
    }

    // 用户模块加载文件/目录缓存集合
    private volatile File[] GET_USER_MODULE_LIB_FILES_CACHE = null;

    /**
     * 从缓存中获取用户模块加载文件/目录
     *
     * @return 用户模块加载文件/目录
     */
    public File[] getUserModuleLibFilesWithCache() {
        if (null != GET_USER_MODULE_LIB_FILES_CACHE) {
            return GET_USER_MODULE_LIB_FILES_CACHE;
        } else {
            return getUserModuleLibFiles();
        }
    }


    /**
     * 获取配置文件加载路径
     *
     * @return 配置文件加载路径
     */
    public String getCfgLibPath() {
        return featureMap.get(KEY_CFG_LIB_PATH);
    }

    @Override
    public String toString() {
        return codec.toString(featureMap);
    }

    /**
     * 是否以Agent模式启动
     *
     * @return true/false
     */
    private boolean isLaunchByAgentMode() {
        return StringUtils.equals(featureMap.get(KEY_LAUNCH_MODE), VAL_LAUNCH_MODE_AGENT);
    }

    /**
     * 是否以Attach模式启动
     *
     * @return true/false
     */
    private boolean isLaunchByAttachMode() {
        return StringUtils.equals(featureMap.get(KEY_LAUNCH_MODE), VAL_LAUNCH_MODE_ATTACH);
    }

    /**
     * 获取沙箱的启动模式
     * 默认按照ATTACH模式启动
     *
     * @return 沙箱的启动模式
     */
    public Information.Mode getLaunchMode() {
        if (isLaunchByAgentMode()) {
            return Information.Mode.AGENT;
        }
        else if (isLaunchByAttachMode()) {
            return Information.Mode.ATTACH;
        }
        return Information.Mode.ATTACH;
    }

    /**
     * 是否启用Unsafe功能
     *
     * @return unsafe.enable
     */
    public boolean isEnableUnsafe() {
        return BooleanUtils.toBoolean(featureMap.get(KEY_UNSAFE_ENABLE));
    }

    /**
     * 获取沙箱安装目录
     *
     * @return 沙箱安装目录
     */
    public String getJvmSandboxHome() {
        return featureMap.get(KEY_SANDBOX_HOME);
    }

    /**
     * 获取服务器绑定IP
     *
     * @return 服务器绑定IP
     */
    public String getServerIp() {
        return StringUtils.isNotBlank(featureMap.get(KEY_SERVER_IP))
                ? featureMap.get(KEY_SERVER_IP)
                : "127.0.0.1";
    }

    /**
     * 获取服务器端口
     *
     * @return 服务器端口
     */
    public int getServerPort() {
        return NumberUtils.toInt(featureMap.get(KEY_SERVER_PORT), 0);
    }

    /**
     * 获取沙箱内部服务提供库目录
     *
     * @return 沙箱内部服务提供库目录
     */
    public String getProviderLibPath() {
        return featureMap.get(KEY_PROVIDER_LIB_PATH);
    }

    /**
     * 获取服务器编码
     *
     * @return 服务器编码
     */
    public Charset getServerCharset() {
        try {
            return Charset.forName(featureMap.get(KEY_SERVER_CHARSET));
        } catch (Exception cause) {
            return Charset.defaultCharset();
        }
    }


    /**
     * 设置是否支持观察native方法，
     * 这个值不期望后续被改变，所以设置为default的访问类型
     * @param isNativeSupported 是否支持观察native方法
     */
    void setNativeSupported(boolean isNativeSupported) {
        featureMap.put(KEY_NATIVE_SUPPORTED, BooleanUtils.toString(
                isNativeSupported,
                "true",
                "false"
        ));
    }

    /**
     * 是否支持观察native方法
     * @return TRUE | FALSE
     */
    public boolean isNativeSupported() {
        return BooleanUtils.toBoolean(featureMap.get(KEY_NATIVE_SUPPORTED));
    }

}
