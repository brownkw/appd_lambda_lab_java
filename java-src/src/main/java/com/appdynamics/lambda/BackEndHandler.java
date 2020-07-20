package com.appdynamics.lambda;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import com.amazonaws.util.IOUtils;
import com.appdynamics.lambda.dal.CommerceOrder;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

// TODO: Add in AppDynamics imports
import com.appdynamics.serverless.tracers.aws.api.AppDynamics;
import com.appdynamics.serverless.tracers.aws.api.Tracer;
import com.appdynamics.serverless.tracers.aws.api.Transaction;
import com.appdynamics.serverless.tracers.dependencies.com.google.gson.Gson;
import com.appdynamics.serverless.tracers.dependencies.com.google.gson.reflect.TypeToken;
import com.appdynamics.serverless.tracers.aws.api.ExitCall;

public class BackEndHandler implements RequestStreamHandler {

    private static final Logger LOG = LogManager.getLogger(FrontEndHandler.class);
    private static final Map<String, Object> CONTROLLER_INFO = SecretsManager.getSecret();

    // TODO: Add variables for the tracer and transaction
    Tracer tracer = null;
    Transaction txn = null;

    @Override
    public void handleRequest(InputStream input, OutputStream output, Context context) throws IOException {

        String input_str = IOUtils.toString(input);
        Gson gson = new Gson();
        Type t = new TypeToken<HashMap<String, String>>() {
        }.getType();
        HashMap<String, String> input_body = gson.fromJson(input_str, t);

        // TODO: Add in code to build tracer and start transaction.
        String correlationHeader = "";

        AppDynamics.Config.Builder configBuilder = new AppDynamics.Config.Builder();
        configBuilder.accountName(BackEndHandler.CONTROLLER_INFO.get("aws-sandbox-controller-key").toString())
                .controllerAccessKey(BackEndHandler.CONTROLLER_INFO.get("aws-sandbox-controller-account").toString())
                .lambdaContext(context);

        tracer = AppDynamics.getTracer(configBuilder.build());

        if (input_body.containsKey(Tracer.APPDYNAMICS_TRANSACTION_CORRELATION_HEADER_KEY)) {
            correlationHeader = input_body.get(Tracer.APPDYNAMICS_TRANSACTION_CORRELATION_HEADER_KEY);
        }

        txn = tracer.createTransaction(correlationHeader);
        txn.start();

        String output_str = "";

        // TODO: Add code for exit call to DynamoDB
        ExitCall db_exit_call = null;
        if (!txn.equals(null)) {
            HashMap<String, String> db_props = new HashMap<>();
            db_props.put("VENDOR", System.getenv("ORDERS_TABLE_NAME") + " DynamoDB");
            db_exit_call = txn.createExitCall("AMAZON_WEB_SERVICES", db_props);
            db_exit_call.start();
        }

        try {
            CommerceOrder order_obj = new CommerceOrder();
            List<CommerceOrder> orders = order_obj.recentOrders();
            output_str = new ObjectMapper().writeValueAsString(orders);
            
        } catch (IOException e) {
            // TODO: Add code to report error for exit call to DynamoDB.
            if (!db_exit_call.equals(null)) {
                db_exit_call.reportError(e);
            }

            Map<String, Object> error_map = new HashMap<String, Object>();
            error_map.put("error_msg", e.getMessage());
            output_str = new ObjectMapper().writeValueAsString(error_map);
            
        }

        // TODO: Add code to end exit call to DynamoDB.
        if (!db_exit_call.equals(null)) {
            db_exit_call.stop();
        }

        // TODO: Add code to end transaction
        if (!txn.equals(null)) {
            txn.stop();
        }

        output.write(output_str.getBytes(Charset.forName("UTF-8")));

    }

}