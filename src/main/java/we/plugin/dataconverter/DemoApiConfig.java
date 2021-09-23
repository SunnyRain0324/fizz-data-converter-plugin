package we.plugin.dataconverter;

import com.alibaba.fastjson.JSON;
import org.springframework.context.annotation.Configuration;
import we.config.ManualApiConfig;
import we.plugin.PluginConfig;
import we.plugin.auth.ApiConfig;
import we.plugin.requestbody.RequestBodyPlugin;

import java.util.*;

/**
 * 定义 DemoApiConfig 继承 ManualApiConfig，并注解为 Configuration，然后实现 setApiConfigs 方法，在方法中添加路由配置；
 * 本类仅为方便开发和测试，正式环境应该通过管理后台配置路由
 */
@Configuration
public class DemoApiConfig extends ManualApiConfig {

    @Override
    public List<ApiConfig> setApiConfigs() {

        List<ApiConfig> apiConfigs = new ArrayList<>();

        ApiConfig ac = new ApiConfig(); // 一个路由配置
        ac.id = 1000; // 路由 id，建议从 1000 开始
        ac.service = "xservice"; // 前端服务名
        ac.path = "/ypath"; // 前端路径
        ac.type = ApiConfig.Type.REVERSE_PROXY; // 路由类型，此处为反向代理
        ac.httpHostPorts = Collections.singletonList("http://172.25.102.133:8082/cli"); // 被代理接口的地址
        ac.backendPath = "/app/frame/v1/flist"; // 被代理接口的路径
        ac.pluginConfigs = new ArrayList<>();

        // 如果你的插件需要访问请求体，则首先要把 RequestBodyPlugin.REQUEST_BODY_PLUGIN 加到 ac.pluginConfigs 中，就像下面这样
        PluginConfig pc1 = new PluginConfig();
        pc1.plugin = RequestBodyPlugin.REQUEST_BODY_PLUGIN;

        ac.pluginConfigs.add(pc1);

        PluginConfig pc2 = new PluginConfig();
        pc2.plugin = DataConverterPluginFilter.DATA_CONVERTER_PLUGIN_FILTER; // 应用 id 为 demoPlugin 的插件
        Map<String, Object> objectObjectHashMap = new HashMap<>();
        objectObjectHashMap.put("convertType", "FORM_TO_JSON");
        objectObjectHashMap.put("convertRule", "{\"mid\":\"${timestamp}\",\"menu\":\"${menu}\"}");

        //objectObjectHashMap.put("convertType", "JSON_TO_FORM");
        //objectObjectHashMap.put("convertRule", "mid=${a}&menu=@{mid}");
        objectObjectHashMap.put("responseTemplate", "{\"mid\":${result}}");
        objectObjectHashMap.put("requestHeaderTemplate", "{\"code\": \"${mid}\",\"mes\": \"${menu}\",\"result\":\"001\"}");
        objectObjectHashMap.put("responseHeaderTemplate", "{\"code\": 0,\"mes\": \"msgCode\",\"result\": \"${msgCode}\"}");
        pc2.setConfig(JSON.toJSONString(objectObjectHashMap));
        ac.pluginConfigs.add(pc2);

        apiConfigs.add(ac);

        log.info("set api configs end");
        return apiConfigs; // 返回路由配置
    }
}
