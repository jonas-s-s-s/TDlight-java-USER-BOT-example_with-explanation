import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import it.tdlight.common.Init;
import it.tdlight.common.utils.CantLoadLibrary;
import it.tdlight.jni.TdApi;
import it.tdlight.tdlight.ClientManager;
import TDlight_classes.*;

import java.io.IOError;
import java.io.IOException;

public class program {
    private static final Logger LOGGER = LoggerFactory.getLogger(program.class);

    public static void main(String[] args) {
        initialiseTDlight();

        /*This will keep the program running until you shut it down manually.
          You won't need this if you're putting in on another thread.
         */
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }

    private static void initialiseTDlight() {
        try {
            //Starting TDlight initialisation
            Init.start();
            Client.client = ClientManager.create();

            //These methods will handle updates, and errors. You can set them to non-static methods too, by using this::method_name
            Client.client.initialize(program::onUpdate, program::onUpdateError, program::onError);

            //Meaning of the "LogVerbosity" values: https://core.telegram.org/tdlib/docs/classtd_1_1td__api_1_1set_log_verbosity_level.html
            Client.client.execute(new TdApi.SetLogVerbosityLevel(0));

            /*
            These options allow you to configure how much memory will TDlight use, etc.
            They are listed at: https://github.com/tdlight-team/tdlight#custom-options

            Some examples:
            Client.client.execute(new TdApi.SetOption("delete_chat_reference_after_seconds", new TdApi.OptionValueInteger(200)));
            Client.client.execute(new TdApi.SetOption("delete_user_reference_after_seconds", new TdApi.OptionValueInteger(200)));
            Client.client.execute(new TdApi.SetOption("delete_file_reference_after_seconds", new TdApi.OptionValueInteger(200)));
            Client.client.execute(new TdApi.SetOption("disable_minithumbnails", new TdApi.OptionValueBoolean(true)));
            */

            //No need to change anything here.
            if (Client.client.execute(new TdApi.SetLogStream(
                    new TdApi.LogStreamFile("tdlib.log", 1 << 27, false))) instanceof TdApi.Error) {
                throw new IOError(new IOException("Write access to the current directory is required"));
            }
            Authorization_Update.authorizationLock.lock();
            try {
                while (!Authorization_Update.haveAuthorization) {
                    Authorization_Update.gotAuthorization.await();
                }
            } catch (InterruptedException e) {
                LOGGER.error("Authorization Interrupted", e);
            } finally {
                Authorization_Update.authorizationLock.unlock();
            }
        } catch (CantLoadLibrary e1) {
            LOGGER.error("Failed to load Tdlight library", e1);
        }

    }

    private static void onUpdate(TdApi.Object object) {
        //Cast to update object, so it can be accessed later
        TdApi.Update update = (TdApi.Update) object;
        //Print out the entire update object as string
        LOGGER.info("Received update:\n" + update);

        switch (update.getConstructor()) {
            //Authorization
            case TdApi.UpdateAuthorizationState.CONSTRUCTOR -> {
                Authorization_Update.onAuthorizationStateUpdated(((TdApi.UpdateAuthorizationState) object).authorizationState);

                //TDlight is connected.
                if (((TdApi.UpdateAuthorizationState) object).authorizationState.getConstructor() == TdApi.AuthorizationStateReady.CONSTRUCTOR)
                    LOGGER.info("TDlight has been connected.");
            }

            //For detecting new messages:
            case TdApi.UpdateNewMessage.CONSTRUCTOR -> {

                TdApi.UpdateNewMessage newMessage = ((TdApi.UpdateNewMessage) object);
                TdApi.Message message = newMessage.message;
                TdApi.MessageSenderUser sender = (TdApi.MessageSenderUser) message.sender;

                LOGGER.info("NEW MESSAGE:" + "\nUser ID: " + sender.userId + "\nMessage content: " + message.content);
            }

            //For detecting deleted messages:
            /*case TdApi.UpdateDeleteMessages.CONSTRUCTOR -> {
                TdApi.UpdateDeleteMessages messageDeleted = ((TdApi.UpdateDeleteMessages) object);
            }*/

            /*Add more cases for the events/updates you want to catch
              Find more events & updates at:
                 https://tdlight-team.github.io/tdlight-docs/it/tdlight/jni/TdApi.ChatEventAction.html
                 https://tdlight-team.github.io/tdlight-docs/it/tdlight/jni/TdApi.Update.html
             */
        }

    }

    private static void onUpdateError(Throwable exception) {
        LOGGER.error("Received an error from updates:");
        exception.printStackTrace();
    }

    private static void onError(Throwable exception) {
        LOGGER.error("Received an error:");
        exception.printStackTrace();
    }

    /*This will try to cleanup some unused memory.
    * You first have to disable the databases in order to use this, more info at: https://github.com/tdlight-team/tdlight#tdlight
    * */
    private static void optimizeMemory() {
        Client.client.execute(new TdApi.SetNetworkType(new TdApi.NetworkTypeNone()));
        TdApi.OptimizeMemory optimizeMemory = new TdApi.OptimizeMemory();
        optimizeMemory.full = true;
        Client.client.execute(optimizeMemory);
        Client.client.execute(new TdApi.SetNetworkType(new TdApi.NetworkTypeOther()));
    }
}



