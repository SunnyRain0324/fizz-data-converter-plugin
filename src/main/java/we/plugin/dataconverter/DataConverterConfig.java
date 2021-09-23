package we.plugin.dataconverter;

/**
 * @author jixiang.wang
 * @version 1.0
 * @date 2021/9/15 9:56
 */
public class DataConverterConfig {
    /**
     * 参数转换格式
     */
    private String convertType;
    /**
     * 转换规则
     */
    private String convertRule;
    /**
     * 请求头数据转换
     */
    private String requestHeaderTemplate;
    /**
     * 返回头信息转换
     */
    private String responseHeaderTemplate;
    /**
     * 格式化模板
     */
    private String responseTemplate;

    public String getConvertType() {
        return convertType;
    }

    public void setConvertType(String convertType) {
        this.convertType = convertType;
    }

    public String getConvertRule() {
        return convertRule;
    }

    public void setConvertRule(String convertRule) {
        this.convertRule = convertRule;
    }

    public String getRequestHeaderTemplate() {
        return requestHeaderTemplate;
    }

    public void setRequestHeaderTemplate(String requestHeaderTemplate) {
        this.requestHeaderTemplate = requestHeaderTemplate;
    }

    public String getResponseHeaderTemplate() {
        return responseHeaderTemplate;
    }

    public void setResponseHeaderTemplate(String responseHeaderTemplate) {
        this.responseHeaderTemplate = responseHeaderTemplate;
    }

    public String getResponseTemplate() {
        return responseTemplate;
    }

    public void setResponseTemplate(String responseTemplate) {
        this.responseTemplate = responseTemplate;
    }
}
