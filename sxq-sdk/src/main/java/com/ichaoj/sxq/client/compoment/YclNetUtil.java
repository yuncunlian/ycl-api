package com.ichaoj.sxq.client.compoment;

import com.ichaoj.sxq.client.SxqClient;
import com.ichaoj.sxq.client.beans.OcsvRequst;
import com.ichaoj.sxq.client.enums.SxqServiceEnum;
import com.yiji.openapi.tool.codec.Hex;
import com.yiji.openapi.tool.fastjson.JSONObject;
import com.yiji.openapi.tool.util.DigestUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.net.ssl.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

public class YclNetUtil {
	private static final Log logger = LogFactory.getLog(YclNetUtil.class);
    public static final String     DEFAULT_CHARSET = "UTF-8";
    private static final String     METHOD_POST     = "POST";
    private static final String     METHOD_GET      = "GET";

    private static SSLContext       ctx             = null;

    private static HostnameVerifier verifier;

    private static SSLSocketFactory socketFactory   = null;

    private static class DefaultTrustManager implements X509TrustManager {
        @Override
		public X509Certificate[] getAcceptedIssuers() {
            return null;
        }

        @Override
		public void checkClientTrusted(X509Certificate[] chain,
                                       String authType) throws CertificateException {
        }

        @Override
		public void checkServerTrusted(X509Certificate[] chain,
                                       String authType) throws CertificateException {
        }
    }

    static {

        try {
            ctx = SSLContext.getInstance("TLS");
            ctx.init(new KeyManager[0], new TrustManager[] { new DefaultTrustManager() },
                    new SecureRandom());

            ctx.getClientSessionContext().setSessionTimeout(15);
            ctx.getClientSessionContext().setSessionCacheSize(1000);

            socketFactory = ctx.getSocketFactory();
        } catch (Exception e) {

        }

        verifier = new HostnameVerifier() {
            @Override
			public boolean verify(String hostname, SSLSession session) {
                return true;//默认认证不通过，进行证书校验。
            }
        };

    }

    private YclNetUtil() {
    }

    /**
     * 执行HTTP POST请求。
     *
     * @param url 请求地址
     * @param params 请求参数
     * @return 响应字符串
     * @throws IOException
     */
    public static String doPost(String url, Map<String, String> params, int connectTimeout, int readTimeout, HashMap<String, String> headerMap) throws IOException {
        return doPost(url, params, DEFAULT_CHARSET, connectTimeout, readTimeout, headerMap);
    }

    /**
     * 执行HTTP POST请求。
     *
     * @param url 请求地址
     * @param params 请求参数
     * @param charset 字符集，如UTF-8, GBK, GB2312
     * @return 响应字符串
     * @throws IOException
     */
    public static String doPost(String url, Map<String, String> params, String charset, int connectTimeout, int readTimeout, HashMap<String, String> headerMap) throws IOException {
        String ctype = "application/x-www-form-urlencoded;charset=" + charset;
        String query = buildQuery(params, charset);
        byte[] content = {};
        if (query != null) {
            content = query.getBytes(charset);
        }
        return doPost(url, ctype, content, connectTimeout, readTimeout, headerMap);
    }

    /**
     * 执行HTTP POST请求。
     *
     * @param url 请求地址
     * @param ctype 请求类型
     * @param content 请求字节数组
     * @return 响应字符串
     * @throws IOException
     */
    public static String doPost(String url, String ctype, byte[] content, int connectTimeout, int readTimeout, HashMap<String, String> headerMap) throws IOException {
        HttpURLConnection conn = null;
        OutputStream out = null;
        String rsp;
        try {
            try {
                conn = getConnection(new URL(url), METHOD_POST, ctype, headerMap);
                conn.setConnectTimeout(connectTimeout);
                conn.setReadTimeout(readTimeout);
            } catch (IOException e) {
                Map<String, String> map = getParamsFromUrl(url);
                logger.error("服务["+map.get("service")+"]请求失败:"+ e.getMessage());
                throw e;
            }
            try {
                out = conn.getOutputStream();
                out.write(content);
                rsp = getResponseAsString(conn);
            } catch (IOException e) {
                Map<String, String> map = getParamsFromUrl(url);
                logger.error("服务["+map.get("service")+"]请求失败:"+ e.getMessage());
                throw e;
            }
        } finally {
            if (out != null) {
                out.close();
            }
            if (conn != null) {
                conn.disconnect();

            }
        }

        return rsp;
    }

    private static byte[] getTextEntry(String fieldName, String fieldValue, String charset) throws IOException {
        StringBuilder entry = new StringBuilder();
        entry.append("Content-Disposition:form-data;name=\"");
        entry.append(fieldName);
        entry.append("\"\r\nContent-Type:text/plain\r\n\r\n");
        entry.append(fieldValue);
        return entry.toString().getBytes(charset);
    }

    private static byte[] getFileEntry(String fieldName, String fileName, String mimeType,
                                       String charset) throws IOException {
        StringBuilder entry = new StringBuilder();
        entry.append("Content-Disposition:form-data;name=\"");
        entry.append(fieldName);
        entry.append("\";filename=\"");
        entry.append(fileName);
        entry.append("\"\r\nContent-Type:");
        entry.append(mimeType);
        entry.append("\r\n\r\n");
        return entry.toString().getBytes(charset);
    }

    /**
     * 执行HTTP GET请求。
     *
     * @param url 请求地址
     * @param params 请求参数
     * @return 响应字符串
     * @throws IOException
     */
    public static String doGet(String url, Map<String, String> params, HashMap<String, String> headerMap) throws IOException {
        return doGet(url, params, DEFAULT_CHARSET, headerMap);
    }

    /**
     * 执行HTTP GET请求。
     *
     * @param url 请求地址
     * @param params 请求参数
     * @param charset 字符集，如UTF-8, GBK, GB2312
     * @return 响应字符串
     * @throws IOException
     */
    public static String doGet(String url, Map<String, String> params, String charset, HashMap<String, String> headerMap) throws IOException {
        HttpURLConnection conn = null;
        String rsp;

        try {
            String ctype = "application/x-www-form-urlencoded;charset=" + charset;
            String query = buildQuery(params, charset);
            try {
                conn = getConnection(buildGetUrl(url, query), METHOD_GET, ctype, headerMap);
            } catch (IOException e) {
                Map<String, String> map = getParamsFromUrl(url);
                logger.error("服务["+map.get("service")+"]请求失败+"+ e.getMessage());
                throw e;
            }

            try {
                rsp = getResponseAsString(conn);
            } catch (IOException e) {
                Map<String, String> map = getParamsFromUrl(url);
                logger.error("服务["+map.get("service")+"]请求失败:"+ e.getMessage());
                throw e;
            }

        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }

        return rsp;
    }

    public static ResultInfo ocsv(OcsvRequst oq){
        ResultInfo re;
        /*添加效验参数*/
        ocsvDigest(oq);
        String s = JSONObject.toJSONString(oq);
        logger.info("请求参数：" + s);
        try {
            HashMap<String, String> headerMap = new HashMap<>();
            headerMap.put("x-sxq-open-accesstoken", oq.getAppKey());
            headerMap.put("x-sxq-open-accesssecret", oq.getAppSecret());
            String s1 = YclNetUtil.doPost(oq.getEnv() + SxqServiceEnum.OCSV.getCode(), "application/x-www-form-urlencoded;charset=" + DEFAULT_CHARSET, s.getBytes(DEFAULT_CHARSET), 60000, 60000, headerMap);
            re = JSONObject.parseObject(s1, ResultInfo.class);
            logger.info("返回参数：" + s1);
        } catch (IOException e) {
            re = ResultInfo.error("出现错误，错误信息：" + e.getMessage() + "，错误原因：" + e.getCause() + "，位置：" + Arrays.toString(e.getStackTrace()));
        }
        return re;
    }

    private static HttpURLConnection getConnection(URL url, String method, String ctype, HashMap<String, String> headerMap) throws IOException {
        HttpURLConnection conn;
        if ("https".equals(url.getProtocol())) {
            HttpsURLConnection connHttps = (HttpsURLConnection) url.openConnection();
            connHttps.setSSLSocketFactory(socketFactory);
            connHttps.setHostnameVerifier(verifier);
            conn = connHttps;
        } else {
            conn = (HttpURLConnection) url.openConnection();
        }

        conn.setRequestMethod(method);
        conn.setDoInput(true);
        conn.setDoOutput(true);
        conn.setRequestProperty("Accept", "text/xml,text/javascript,text/html,application/json");
        conn.setRequestProperty("User-Agent", "aop-sdk-java");
        conn.setRequestProperty("Content-Type", ctype);
        if (headerMap != null){
            for (String key : headerMap.keySet()){
                conn.setRequestProperty(key, headerMap.get(key));
            }
        }
        return conn;
    }

    private static URL buildGetUrl(String strUrl, String query) throws IOException {
        URL url = new URL(strUrl);
        if (StringUtils.isEmpty(query)) {
            return url;
        }

        if (StringUtils.isEmpty(url.getQuery())) {
            if (strUrl.endsWith("?")) {
                strUrl = strUrl + query;
            } else {
                strUrl = strUrl + "?" + query;
            }
        } else {
            if (strUrl.endsWith("&")) {
                strUrl = strUrl + query;
            } else {
                strUrl = strUrl + "&" + query;
            }
        }

        return new URL(strUrl);
    }

    public static String buildQuery(Map<String, String> params, String charset) throws IOException {
        if (params == null || params.isEmpty()) {
            return null;
        }

        StringBuilder query = new StringBuilder();
        Set<Entry<String, String>> entries = params.entrySet();
        boolean hasParam = false;

        for (Entry<String, String> entry : entries) {
            String name = entry.getKey();
            String value = entry.getValue();
            // 忽略参数名或参数值为空的参数
            if (StringUtils.isNotEmpty(name)&&StringUtils.isNotEmpty(value)) {
                if (hasParam) {
                    query.append("&");
                } else {
                    hasParam = true;
                }

                query.append(name).append("=").append(URLEncoder.encode(value, charset));
            }
        }

        return query.toString();
    }

    protected static String  getResponseAsString(HttpURLConnection conn) throws IOException {
        String charset = getResponseCharset(conn.getContentType());
        InputStream es = conn.getErrorStream();
        if (es == null) {
            return getStreamAsString(conn.getInputStream(), charset);
        } else {
            String msg = getStreamAsString(es, charset);
            throw new IOException(StringUtils.isEmpty(msg) ? conn.getResponseCode() + ":" + conn.getResponseMessage() : msg);
        }
    }

    private static byte[] getStreamAsByteArray(InputStream stream) throws IOException{

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

        byte [] bytes = new byte[1024];
        int index;
        try {
            while ( (index = stream.read(bytes)) != -1){
                byteArrayOutputStream.write(bytes,0,index);
            }
            return byteArrayOutputStream.toByteArray();
        } finally {
            byteArrayOutputStream.close();
            stream.close();
        }

    }
    private static String getStreamAsString(InputStream stream, String charset) throws IOException {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream, charset));
            StringWriter writer = new StringWriter();

            char[] chars = new char[256];
            int count;
            while ((count = reader.read(chars)) > 0) {
                writer.write(chars, 0, count);
            }

            return writer.toString();
        } finally {
            if (stream != null) {
                stream.close();
            }
        }
    }

    private static String getResponseCharset(String ctype) {
        String charset = DEFAULT_CHARSET;

        if (!StringUtils.isEmpty(ctype)) {
            String[] params = ctype.split(";");
            for (String param : params) {
                param = param.trim();
                if (param.startsWith("charset")) {
                    String[] pair = param.split("=", 2);
                    if (pair.length == 2) {
                        if (!StringUtils.isEmpty(pair[1])) {
                            charset = pair[1].trim();
                        }
                    }
                    break;
                }
            }
        }

        return charset;
    }

    /**
     * 使用默认的UTF-8字符集反编码请求参数值。
     *
     * @param value 参数值
     * @return 反编码后的参数值
     */
    public static String decode(String value) {
        return decode(value, DEFAULT_CHARSET);
    }

    /**
     * 使用默认的UTF-8字符集编码请求参数值。
     *
     * @param value 参数值
     * @return 编码后的参数值
     */
    public static String encode(String value) {
        return encode(value, DEFAULT_CHARSET);
    }

    /**
     * 使用指定的字符集反编码请求参数值。
     *
     * @param value 参数值
     * @param charset 字符集
     * @return 反编码后的参数值
     */
    private static String decode(String value, String charset) {
        String result = null;
        if (!StringUtils.isEmpty(value)) {
            try {
                result = URLDecoder.decode(value, charset);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return result;
    }

    /**
     * 使用指定的字符集编码请求参数值。
     *
     * @param value 参数值
     * @param charset 字符集
     * @return 编码后的参数值
     */
    private static String encode(String value, String charset) {
        String result = null;
        if (!StringUtils.isEmpty(value)) {
            try {
                result = URLEncoder.encode(value, charset);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return result;
    }

    private static Map<String, String> getParamsFromUrl(String url) {
        Map<String, String> map = null;
        if (url != null && url.indexOf('?') != -1) {
            map = splitUrlQuery(url.substring(url.indexOf('?') + 1));
        }
        if (map == null) {
            map = new HashMap<String, String>();
        }
        return map;
    }

    /**
     * 从URL中提取所有的参数。
     *
     * @param query URL地址
     * @return 参数映射
     */
    public static Map<String, String> splitUrlQuery(String query) {
        Map<String, String> result = new HashMap<>();

        String[] pairs = query.split("&");
        if (pairs != null && pairs.length > 0) {
            for (String pair : pairs) {
                String[] param = pair.split("=", 2);
                if (param.length == 2) {
                    result.put(param[0], param[1]);
                }
            }
        }

        return result;
    }

//    public String buildForm(String baseUrl, RequestParametersHolder requestHolder) {
//        return null;
//    }

    public static String buildForm(String baseUrl, Map<String, String> parameters) {
        StringBuffer sb = new StringBuffer();
        sb.append("<form name=\"punchout_form\" method=\"post\" action=\"");
        sb.append(baseUrl);
        sb.append("\">\n");
        sb.append(buildHiddenFields(parameters));

        sb.append("<input type=\"submit\" value=\"立即支付\" style=\"display:none\" >\n");
        sb.append("</form>\n");
        sb.append("<script>document.forms[0].submit();</script>");
        return sb.toString();
    }

    private static String buildHiddenFields(Map<String, String> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return "";
        }
        StringBuffer sb = new StringBuffer();
        Set<String> keys = parameters.keySet();
        for (String key : keys) {
            String value = parameters.get(key);
            // 除去参数中的空值
            if (key == null || value == null) {
                continue;
            }
            sb.append(buildHiddenField(key, value));
        }
        return sb.toString();
    }

    private static String buildHiddenField(String key, String value) {
        StringBuffer sb = new StringBuffer();
        sb.append("<input type=\"hidden\" name=\"");
        sb.append(key);

        sb.append("\" value=\"");
        //转义双引号
        String a = value.replace("\"", "&quot;");
        sb.append(a).append("\">\n");
        return sb.toString();
    }

    private static void ocsvDigest(OcsvRequst oq){
        try {
            String str = JSONObject.toJSONString(oq);
            logger.debug("待签名字符串[" + str+"]");
            MessageDigest md = MessageDigest.getInstance(DigestUtil.DigestALGEnum.MD5.getName());
            md.update(str.getBytes(DEFAULT_CHARSET));
            oq.setSign(new String(Hex.encodeHex(md.digest())));
        } catch (Exception e) {
            throw new RuntimeException("签名失败", e);
        }
    }
}
