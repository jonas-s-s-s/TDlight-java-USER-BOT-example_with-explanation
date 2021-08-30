package TDlight_classes;

import Static_config.Tdlight_config;
import it.tdlight.common.ResultHandler;
import it.tdlight.jni.TdApi;
import it.tdlight.jni.TdApi.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/*
*
*  This class authenticates TDlight with the constants you specified in the config files.
*  You don't have to edit anything here, except for the TDlib parameters, if you want to.
*
* */
public class Authorization_Update {

    private static AuthorizationState authorizationState = null;
    private static AuthorizationRequestHandler authorizationRequestHandler = new AuthorizationRequestHandler();

    public static volatile boolean haveAuthorization = false;
    public static final Lock authorizationLock = new ReentrantLock();
    public static final Condition gotAuthorization = authorizationLock.newCondition();
    private static final Logger LOGGER = LoggerFactory.getLogger(Authorization_Update.class);

    public static void onAuthorizationStateUpdated(AuthorizationState authorizationState) {
        if (authorizationState != null) {
            Authorization_Update.authorizationState = authorizationState;
        }
        switch (Authorization_Update.authorizationState.getConstructor()) {
            case AuthorizationStateWaitTdlibParameters.CONSTRUCTOR -> {

                TdlibParameters parameters = new TdlibParameters();
                /* The TDlib parameters can be viewed here:
                https://core.telegram.org/tdlib/docs/classtd_1_1td__api_1_1tdlib_parameters.html

                For example, you can disable these databases to reduce memory usage (if you don't need them):
                parameters.useMessageDatabase = false;
                parameters.useChatInfoDatabase = false;
                parameters.useFileDatabase = false;*/
                parameters.databaseDirectory = Tdlight_config.DATABASE_DIRECTORY;
                parameters.useSecretChats = true;
                parameters.apiId = Tdlight_config.APP_ID;
                parameters.apiHash = Tdlight_config.API_HASH;
                parameters.systemLanguageCode = Tdlight_config.SYSTEM_LANGUAGE_CODE;
                parameters.deviceModel = Tdlight_config.DEVICE_MODEL;
                parameters.applicationVersion = Tdlight_config.APP_VERSION;
                parameters.enableStorageOptimizer = true;
                Client.client.send(new SetTdlibParameters(parameters), authorizationRequestHandler);
            }
            case AuthorizationStateWaitEncryptionKey.CONSTRUCTOR -> Client.client.send(new CheckDatabaseEncryptionKey(), authorizationRequestHandler);
            case AuthorizationStateWaitPhoneNumber.CONSTRUCTOR -> {
                System.out.println("Enter your phone number:");
                String phoneNumber = getInput();
                Client.client.send(new TdApi.SetAuthenticationPhoneNumber(phoneNumber, null),
                        authorizationRequestHandler);
            }

            case AuthorizationStateWaitCode.CONSTRUCTOR -> {
                System.out.println("Enter Authencation code:");
                String code = getInput();
                Client.client.send(new TdApi.CheckAuthenticationCode(code), authorizationRequestHandler);
            }

            case TdApi.AuthorizationStateWaitPassword.CONSTRUCTOR -> {
                System.out.println("Enter your password:");
                String password = getInput();
                Client.client.send(new TdApi.CheckAuthenticationPassword(password),
                        authorizationRequestHandler);
            }

            case AuthorizationStateReady.CONSTRUCTOR -> {
                haveAuthorization = true;
                authorizationLock.lock();
                try {
                    gotAuthorization.signal();
                } finally {
                    authorizationLock.unlock();
                }
            }
        }
    }

    private static class AuthorizationRequestHandler implements ResultHandler {

        @Override
        public void onResult(TdApi.Object object) {
            switch (object.getConstructor()) {
                case TdApi.Error.CONSTRUCTOR:
                    LOGGER.error("Receive an error " + object.toString());
                    onAuthorizationStateUpdated(null);
                    break;
                case TdApi.Ok.CONSTRUCTOR:
                    break;
                default:
                    LOGGER.error("Receive wrong response from TDLib ", object);
            }
        }
    }

    public static String getInput() {
        String str = "";
        BufferedReader read = new BufferedReader(new InputStreamReader(System.in));
        try {
            str = read.readLine();
        } catch (IOException e) {
            LOGGER.error("Failed to read",e);
        }
        return str;
    }
}
