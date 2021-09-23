package com.we.plugin.dataconverter;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import we.plugin.dataconverter.DataConverterPluginFilter;
import we.plugin.dataconverter.Main;

import java.util.HashMap;
import java.util.Map;

/**
 * @author jixiang.wang
 * @version 1.0
 * @description: TODO
 * @date 2021/9/23 14:49
 */
@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest(classes = Main.class)
public class DataConverterPluginTest {

    @Autowired
    private DataConverterPluginFilter pluginFilter;

    @Test
    public void getJsonValueByKeyTest() {
        Map<String, Object> map = new HashMap<>();
        map.put("message", "this message");
        map.put("code", "this code");
        map.put("result", "this result");
        String code = pluginFilter.getJsonValueByKey("code", map);
        System.out.println("this getJsonValueByKeyTest value:" + code);
        String result = pluginFilter.getJsonValueByKey("${result}", map);
        System.out.println("this getJsonValueByKeyTest value:" + result);
        String nullIS = pluginFilter.getJsonValueByKey("", map);
        System.out.println("this getJsonValueByKeyTest value:" + nullIS);
        assert true;
    }

    @Test
    public void getByFormTest() {
        String s = "aa=11&bb=22&cc=33";
        Map<String, Object> byForm = pluginFilter.getByForm(s);
        for (Map.Entry<String, Object> entry : byForm.entrySet()) {
            System.out.println("this getByFormTest key:" + entry.getKey() + "   value:" + entry.getValue());
        }
        Map<String, Object> byForm1 = pluginFilter.getByForm("");
        for (Map.Entry<String, Object> entry : byForm1.entrySet()) {
            System.out.println("this getByFormTest key:" + entry.getKey() + "   value:" + entry.getValue());
        }
        assert true;
    }

    @Test
    public void jsonMapTest() {
        String s = "{\"resultS\":\"${result}\",\"messageS\":\"${message}\",\"codeS\":\"@{code}\"}";
        Map<String, String> headers = new HashMap<>();
        headers.put("message", "this headers message");
        headers.put("code", "this headers code");
        headers.put("result", "this headers result");
        Map<String, Object> params = new HashMap<>();
        params.put("message", "this params message");
        params.put("code", "this params code");
        params.put("result", "this params result");
        String s1 = pluginFilter.jsonMap(headers, s, params);
        System.out.println("this jsonMapTest  value:" + s1);
        assert true;
    }

    @Test
    public void getByJsonTest() {
        String s = "{\"resultS\":\"${result}\",\"messageS\":\"${message}\",\"codeS\":\"@{code}\"}";
        Map<String, Object> byJson = pluginFilter.getByJson(s);
        for (Map.Entry<String, Object> entry : byJson.entrySet()) {
            System.out.println("this getByJsonTest key:" + entry.getKey() + "   value:" + entry.getValue());
        }
        assert true;
    }

    @Test
    public void formMapTest() {
        String s = "message=${message}&code=@{code}";
        Map<String, String> headers = new HashMap<>();
        headers.put("message", "this headers message");
        headers.put("code", "this headers code");
        headers.put("result", "this headers result");
        Map<String, Object> params = new HashMap<>();
        params.put("message", "this params message");
        params.put("code", "this params code");
        params.put("result", "this params result");
        String s1 = pluginFilter.jsonMap(headers, s, params);
        System.out.println("this formMapTest  value:" + s1);
        assert true;
    }

}
