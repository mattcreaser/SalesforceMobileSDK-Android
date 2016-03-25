/*
 * Copyright (c) 2015, salesforce.com, inc.
 * All rights reserved.
 * Redistribution and use of this software in source and binary forms, with or
 * without modification, are permitted provided that the following conditions
 * are met:
 * - Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 * - Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * - Neither the name of salesforce.com, inc. nor the names of its contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission of salesforce.com, inc.
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package com.salesforce.androidsdk.reactnative.bridge;

import android.support.annotation.NonNull;
import android.util.Log;

import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.salesforce.androidsdk.accounts.UserAccount;
import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.rest.RestClient;
import com.salesforce.androidsdk.rest.RestRequest;
import com.salesforce.androidsdk.rest.RestResponse;

import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.util.Map;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;

public class SalesforceNetReactBridge extends ReactContextBaseJavaModule {

    public static final String METHOD_KEY = "method";
    public static final String END_POINT_KEY = "endPoint";
    public static final String PATH_KEY = "path";
    public static final String QUERY_PARAMS_KEY = "queryParams";
    public static final String HEADER_PARAMS_KEY = "headerParams";
    public static final String FILE_PARAMS_KEY = "fileParams";
    public static final String FILE_MIME_TYPE_KEY = "fileMimeType";
    public static final String FILE_URL_KEY = "fileUrl";
    public static final String FILE_NAME_KEY = "fileName";

    private RestClient restClient;


    public SalesforceNetReactBridge(ReactApplicationContext reactContext) {
        super(reactContext);
    }

    @Override
    public String getName() {
        return "SalesforceNetReactBridge";
    }

    @ReactMethod
    public void sendRequest(ReadableMap args,
                            final Callback successCallback, final Callback errorCallback) {

        try {
            // Prepare request
            RestRequest request = prepareRestRequest(args);

            // Sending request
            RestClient restClient = getRestClient();
            restClient.sendAsync(request, new RestClient.AsyncRequestCallback() {
                @Override
                public void onSuccess(RestRequest request, RestResponse response) {
                    try {
                        String responseAsString = response.asString();
                        // XXX Sending the string over and letting javascript do a JSON.parse(result)
                        //     It would be better to using NativeMap/NativeArray
                        //     Although the absence of a common super class would force us to
                        //     introduce two sendRequest methods:
                        //     - one that expects map back from the server
                        //     - one that expects array back from the server
                        successCallback.invoke(responseAsString);
                    } catch (IOException e) {
                        Log.e("NetReactBridge", "sendRequest", e);
                        onError(e);
                    }
                }

                @Override
                public void onError(Exception exception) {
                    errorCallback.invoke(exception.getMessage());
                }
            });
        }
        catch (Exception exception) {
            errorCallback.invoke(exception.getMessage());
        }
    }

    @NonNull
    private RestRequest prepareRestRequest(ReadableMap args) throws UnsupportedEncodingException, URISyntaxException {
        // Parse args
        RestRequest.RestMethod method = RestRequest.RestMethod.valueOf(args.getString(METHOD_KEY));
        String endPoint = args.getString(END_POINT_KEY);
        String path = args.getString(PATH_KEY);
        ReadableMap queryParams = args.getMap(QUERY_PARAMS_KEY);
        ReadableMap headerParams = args.getMap(HEADER_PARAMS_KEY);
        ReadableMap fileParams = args.getMap(FILE_PARAMS_KEY);

        // Preparing request
        Map<String, String> additionalHeaders = ReactBridgeHelper.toJavaStringStringMap(headerParams);
        Map<String, String> queryParamsMap = ReactBridgeHelper.toJavaStringStringMap(queryParams);
        Map<String, Map<String, String>> fileParamsMap = ReactBridgeHelper.toJavaStringMapMap(fileParams);

        String urlParams = "";
        RequestBody requestBody = null;
        if (method == RestRequest.RestMethod.DELETE || method == RestRequest.RestMethod.GET || method == RestRequest.RestMethod.HEAD) {
            urlParams = buildQueryString(queryParamsMap);
        } else {
            requestBody = buildRequestBody(queryParamsMap, fileParamsMap);
        }

        String separator = urlParams.isEmpty()
                ? ""
                : path.contains("?")
                    ? (path.endsWith("&") ? "" : "&")
                    : "?";

        return new RestRequest(method, endPoint + path + separator + urlParams, requestBody, additionalHeaders);
    }

    private RestClient getRestClient() {
        if (restClient == null) {
            UserAccount account = SalesforceSDKManager.getInstance().getUserAccountManager().getCurrentUser();
            if (account == null) {
                restClient = SalesforceSDKManager.getInstance().getClientManager().peekUnauthenticatedRestClient();
            } else {
                restClient = SalesforceSDKManager.getInstance().getClientManager().peekRestClient(account);
            }
        }
        return restClient;
    }

    private static String buildQueryString(Map<String, String> params) throws UnsupportedEncodingException {
        StringBuilder sb = new StringBuilder();
        for(Map.Entry<String, String> entry : params.entrySet()) {
            sb.append(entry.getKey()).append("=").append(URLEncoder.encode(entry.getValue(), RestRequest.UTF_8)).append("&");
        }
        return sb.toString();
    }

    private static RequestBody buildRequestBody(Map<String, String> params, Map<String, Map<String, String>> fileParams) throws URISyntaxException {
        if (fileParams.isEmpty()) {
            return RequestBody.create(RestRequest.MEDIA_TYPE_JSON, new JSONObject(params).toString());
        }
        else {
            MultipartBody.Builder builder = new MultipartBody.Builder().setType(MultipartBody.FORM);
            for (Map.Entry<String, String> entry : params.entrySet()) {
                builder.addFormDataPart(entry.getKey(), entry.getValue());
            }

            // File params expected to be of the form:
            // {<fileParamNameInPost>: {fileMimeType:<someMimeType>, fileUrl:<fileUrl>, fileName:<fileNameForPost>}}
            for(Map.Entry<String, Map<String, String>> fileParamEntry : fileParams.entrySet()) {
                Map<String, String> fileParam = fileParamEntry.getValue();
                String fileParamName = fileParamEntry.getKey();
                String mimeType = fileParam.get(FILE_MIME_TYPE_KEY);
                String name = fileParam.get(FILE_NAME_KEY);
                URI url = new URI(fileParam.get(FILE_URL_KEY));
                File file = new File(url);
                MediaType mediaType = MediaType.parse(mimeType);
                builder.addFormDataPart(fileParamName, name, RequestBody.create(mediaType, file));
            }

            return builder.build();
        }
    }
}
