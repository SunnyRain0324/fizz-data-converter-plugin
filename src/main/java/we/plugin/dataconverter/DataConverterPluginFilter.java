package we.plugin.dataconverter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import we.config.SystemConfig;
import we.filter.AggregateFilterProperties;
import we.fizz.ConfigLoader;
import we.plugin.FizzPluginFilter;
import we.plugin.FizzPluginFilterChain;
import we.spring.http.server.reactive.ext.FizzServerHttpRequestDecorator;
import we.util.NettyDataBufferUtils;

import javax.annotation.Resource;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author jixiang.wang
 * @version 1.0
 * @date 2021/9/14 11:23
 */
@Component(DataConverterPluginFilter.DATA_CONVERTER_PLUGIN_FILTER)
public class DataConverterPluginFilter implements FizzPluginFilter {

    private static final Logger log = LoggerFactory.getLogger(DataConverterPluginFilter.class);

    public static final String DATA_CONVERTER_PLUGIN_FILTER = "dataConverterPlugin";
    @Resource
    private ConfigLoader configLoader;
    @Resource
    private AggregateFilterProperties aggregateFilterProperties;

    @Resource
    private SystemConfig systemConfig;

    /**
     * Content-Type Header
     */
    public static final String HEADER_CONTENT_TYPE = "Content-Type";
    /**
     * JSON Content-Type
     */
    public static final String CONTENT_TYPE_JSON = "application/json; charset=UTF-8";
    /**
     * FORM Content-Type
     */
    public static final String CONTENT_TYPE_FORM = "application/x-www-form-urlencoded; charset=UTF-8";
    public static final String HEADER_EXPRESS = "@\\{(.*?)}";
    public static final String PARAM_EXPRESS = "\\$\\{(.*?)}";
    public static final String FORM_TO_JSON = "FORM_TO_JSON";
    public static final String JSON_TO_FORM = "JSON_TO_FORM";
    public static final String CONVERT_TYPE = "convertType";
    public static final String CONVERT_RULE = "convertRule";
    public static final String RESPONSE_TEPLATE = "responseTemplate";
    public static final String REQUEST_HEADER_TEMPLATE = "requestHeaderTemplate";
    public static final String RESPONSE_HEADER_TEMPLATE = "responseHeaderTemplate";


    @Override
    public Mono<Void> filter(ServerWebExchange exchange, Map<String, Object> config) {
        FizzServerHttpRequestDecorator request = (FizzServerHttpRequestDecorator) exchange.getRequest();
        String convertType = (String) config.get(CONVERT_TYPE);
        String convertRule = (String) config.get(CONVERT_RULE);
        String requestHeaderTemplate = (String) config.get(REQUEST_HEADER_TEMPLATE);
        String responseTemplate = (String) config.get(RESPONSE_TEPLATE);
        String responseHeaderTemplate = (String) config.get(RESPONSE_HEADER_TEMPLATE);
        if (FORM_TO_JSON.equals(convertType)) {
            return request.getBody().defaultIfEmpty(NettyDataBufferUtils.EMPTY_DATA_BUFFER)
                    .single()
                    .flatMap(boby -> {
                        //  form转换为json
                        String string = URLDecoder.decode(boby.toString(StandardCharsets.UTF_8));
                        Map<String, Object> params = getByForm(string);
                        String jsonParams = jsonMap(request.getHeaders().toSingleValueMap(), convertRule, params);
                        request.setBody(jsonParams);
                        request.getHeaders().set(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON);
                        jsonRequestHeaderTemplate(jsonParams, request, requestHeaderTemplate);
                        return onResponseContent(exchange, responseTemplate, responseHeaderTemplate);
                    });
        } else if (JSON_TO_FORM.equals(convertType)) {
            return request.getBody().defaultIfEmpty(NettyDataBufferUtils.EMPTY_DATA_BUFFER)
                    .single()
                    .flatMap(boby -> {
                        //  json转换为form
                        String string = URLDecoder.decode(boby.toString(StandardCharsets.UTF_8));
                        Map<String, Object> params = getByJson(string);
                        String formParams = formMap(request.getHeaders().toSingleValueMap(), convertRule, params);
                        request.setBody(formParams);
                        request.getHeaders().set(HEADER_CONTENT_TYPE, CONTENT_TYPE_FORM);
                        formRequestHeaderTemplate(formParams, request, requestHeaderTemplate);
                        return onResponseContent(exchange, responseTemplate, responseHeaderTemplate);
                    });
        }
        return FizzPluginFilterChain.next(exchange);
    }

    public Mono<Void> onResponseContent(ServerWebExchange exchange, String responseTemplate, String responseHeaderTemplate) {
        ServerHttpResponse response = exchange.getResponse();
        ServerHttpResponseDecorator serverHttpResponseDecorator = new ServerHttpResponseDecorator(response) {
            @Override
            public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
                Mono<DataBuffer> nettyDataBufferMono = NettyDataBufferUtils.join(body).flatMap(dataBuffer -> {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    String s = new String(bytes, StandardCharsets.UTF_8);
                    String newResponse = responseTemplate;
                    if (!StringUtils.isEmpty(responseTemplate)) {
                        String originalResponse = s;
                        if (StringUtils.isEmpty(originalResponse)) {
                            newResponse = newResponse.replaceFirst(PARAM_EXPRESS, "");
                        } else {
                            originalResponse = originalResponse.replaceAll("\\\\", "/@#/");
                            newResponse = newResponse.replaceFirst(PARAM_EXPRESS, originalResponse);
                            newResponse = newResponse.replaceAll("/@#/", "\\\\");
                        }
                    } else {
                        newResponse = s;
                    }
                    if (!StringUtils.isEmpty(responseHeaderTemplate)) {
                        Map<String, Object> jsonMap = getByJson(s);
                        Map<String, Object> templateMap = getByJson(responseHeaderTemplate);
                        for (Map.Entry<String, Object> entry : templateMap.entrySet()) {
                            String value = getJsonValueByKey(entry.getValue().toString(), jsonMap);
                            if (value != null && !"".equals(value.trim())) {
                                super.getHeaders().set(entry.getKey(), value);
                                log.info("set response header, key: {}, value: {}", entry.getKey(), value);
                            } else {
                                super.getHeaders().set(entry.getKey(), entry.getValue().toString());
                                log.info("set response header, key: {}, value: {}", entry.getKey(), entry.getValue().toString());
                            }
                        }
                    }
                    return Mono.just(NettyDataBufferUtils.from(newResponse));
                });
                return super.writeWith(nettyDataBufferMono);
            }
        };
        ServerWebExchange build = exchange.mutate().response(serverHttpResponseDecorator).build();
        return FizzPluginFilterChain.next(build);
    }


    /**
     * json Request请求头模板处理
     */
    public void jsonRequestHeaderTemplate(String json, FizzServerHttpRequestDecorator request, String requestHeaderTemplate) {

        if (requestHeaderTemplate != null && !"".equals(requestHeaderTemplate.trim())) {
            Map<String, Object> jsonMap = getByJson(json);
            Map<String, Object> templateMap = getByJson(requestHeaderTemplate);
            for (Map.Entry<String, Object> entry : templateMap.entrySet()) {
                String value = getJsonValueByKey(entry.getValue().toString(), jsonMap);
                if (value != null && !"".equals(value.trim())) {
                    request.getHeaders().add(entry.getKey(), value);
                    log.info("set request header, key: {}, value: {}", entry.getKey(), value);
                } else {
                    request.getHeaders().add(entry.getKey(), value);
                    log.info("set request header, key: {}, value: {}", entry.getKey(), entry.getValue().toString());
                }
            }
        }

    }

    /**
     * form Request请求头模板处理
     */
    public void formRequestHeaderTemplate(String formParams, FizzServerHttpRequestDecorator request, String requestHeaderTemplate) {
        if (requestHeaderTemplate != null && !"".equals(requestHeaderTemplate.trim())) {
            Map<String, Object> formMap = getByForm(formParams);
            Map<String, Object> templateMap = getByJson(requestHeaderTemplate);
            for (Map.Entry<String, Object> entry : templateMap.entrySet()) {
                String key = entry.getValue().toString();
                Matcher matcher = Pattern.compile(PARAM_EXPRESS).matcher(key);
                if (matcher.find()) {
                    key = matcher.group(1);
                }
                if (formMap.get(key) != null) {
                    request.getHeaders().add(entry.getKey(), String.valueOf(formMap.get(key)));
                    log.info("set request header, key: {}, value: {}", entry.getKey(), entry.getValue().toString());
                } else {
                    request.getHeaders().add(entry.getKey(), key);
                    log.info("set request header, key: {}, value: {}", entry.getKey(), entry.getValue().toString());
                }
            }
        }
    }


    public String getJsonValueByKey(String jsonKeys, Map<String, Object> jsonMap) {
        Matcher matcher = Pattern.compile(PARAM_EXPRESS).matcher(jsonKeys);
        if (matcher.find()) {
            jsonKeys = matcher.group(1);
        }

        String[] paramKeys = jsonKeys.split("\\.");
        Map<String, Object> jsonMapTemp = jsonMap;
        for (int i = 0; i < paramKeys.length; i++) {
            if (i == paramKeys.length - 1) {
                if (jsonMapTemp.get(paramKeys[i]) != null) {
                    return jsonMapTemp.get(paramKeys[i]).toString();
                }
            } else {
                if (jsonMapTemp.get(paramKeys[i]) == null) {
                    break;
                }
                jsonMapTemp = (Map<String, Object>) jsonMapTemp.get(paramKeys[i]);
            }
        }
        return null;
    }

    /**
     * 将url参数转换成map
     *
     * @param param aa=11&bb=22&cc=33
     * @return
     */
    public static Map<String, Object> getByForm(String param) {
        param = param.trim();
        Map<String, Object> map = new HashMap<String, Object>(0);
        if (StringUtils.isEmpty(param)) {
            return map;
        }
        String[] params = param.split("&");
        for (String s : params) {
            String[] p = s.split("=");
            if (p.length == 2) {
                map.put(p[0], p[1]);
            }
        }
        return map;
    }


    /**
     * form转换为json
     *
     * @param headers     请求头
     * @param convertRule 转换规则
     * @param params      请求参数
     * @return 转换后json
     */
    public String jsonMap(Map<String, String> headers, String convertRule, Map<String, Object> params) {

        if (StringUtils.isEmpty(convertRule)) {
            return "{}";
        }

        //  @{}替换为请求头的值
        Matcher headerMatch = Pattern.compile(HEADER_EXPRESS).matcher(convertRule);
        while (headerMatch.find()) {
            String key = headerMatch.group(1);
            if (headers.get(key) == null) {
                convertRule = convertRule.replaceFirst(HEADER_EXPRESS, "");
            } else {
                convertRule = convertRule.replaceFirst(HEADER_EXPRESS, headers.get(key));
            }
        }

        //  ${}替换为请求参数的值
        Matcher paramMatch = Pattern.compile(PARAM_EXPRESS).matcher(convertRule);
        while (paramMatch.find()) {
            String key = paramMatch.group(1);
            if (params.get(key) == null) {
                convertRule = convertRule.replaceFirst(PARAM_EXPRESS, "");
            } else {
                convertRule = convertRule.replaceFirst(PARAM_EXPRESS, String.valueOf(params.get(key)));
            }
        }

        return convertRule;
    }

    /**
     * 将json提交的参数转换成map
     *
     * @param jsonString
     * @return
     */
    public static Map<String, Object> getByJson(String jsonString) {
        Map<String, Object> map = new HashMap<>(0);
        if (StringUtils.isEmpty(jsonString)) {
            return map;
        }

        try {
            ObjectMapper mapper = new ObjectMapper();
            map = mapper.readValue(jsonString, Map.class);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return map;
    }

    /**
     * json转换为form
     *
     * @param headers     请求头
     * @param convertRule 转换规则
     * @param params      请求参数
     * @return 转换后form
     */
    public String formMap(Map<String, String> headers, String convertRule, Map<String, Object> params) {
        if (StringUtils.isEmpty(convertRule)) {
            return "";
        }

        //  @{}替换为请求头的值
        Matcher headerMatch = Pattern.compile(HEADER_EXPRESS).matcher(convertRule);
        while (headerMatch.find()) {
            String key = headerMatch.group(1);
            if (headers.get(key) == null) {
                convertRule = convertRule.replaceFirst(HEADER_EXPRESS, "");
            } else {
                convertRule = convertRule.replaceFirst(HEADER_EXPRESS, headers.get(key));
            }
        }

        //  ${}替换为请求参数的值
        Matcher paramMatch = Pattern.compile(PARAM_EXPRESS).matcher(convertRule);
        while (paramMatch.find()) {
            String key = paramMatch.group(1);
            String[] paramKeys = key.split("\\.");
            Map<String, Object> paramsTemp = params;
            for (int i = 0; i < paramKeys.length; i++) {
                if (i == paramKeys.length - 1) {
                    if (paramsTemp.get(paramKeys[i]) == null) {
                        convertRule = convertRule.replaceFirst(PARAM_EXPRESS, "");
                    } else {
                        convertRule = convertRule.replaceFirst(PARAM_EXPRESS, String.valueOf(paramsTemp.get(paramKeys[i])));
                    }
                } else {
                    if (paramsTemp.get(paramKeys[i]) == null) {
                        break;
                    }
                    paramsTemp = (Map<String, Object>) paramsTemp.get(paramKeys[i]);
                }
            }
        }

        return convertRule;
    }


}
