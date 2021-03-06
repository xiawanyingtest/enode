package com.enodeframework.queue;

import com.enodeframework.common.remoting.RemotingClient;
import com.enodeframework.common.remoting.netty.NettyClientConfig;
import com.enodeframework.common.remoting.netty.NettyRemotingClient;
import com.enodeframework.common.remoting.protocol.RemotingCommand;
import com.enodeframework.common.serializing.IJsonSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.Charset;
import java.util.concurrent.CompletableFuture;

public class SendReplyService {

    private static final Logger logger = LoggerFactory.getLogger(SendReplyService.class);

    private static final Charset CHARSETUTF8 = Charset.forName("UTF-8");

    private RemotingClient remotingClient;

    @Autowired
    private IJsonSerializer jsonSerializer;

    private boolean started;

    private boolean stoped;

    public SendReplyService() {
        NettyClientConfig nettyClientConfig = new NettyClientConfig();
        nettyClientConfig.setClientChannelMaxIdleTimeSeconds(3600);
        remotingClient = new NettyRemotingClient(nettyClientConfig);
    }

    public void start() {
        if (!started) {
            remotingClient.start();
            started = true;
        }
    }

    public void stop() {
        if (!stoped) {
            remotingClient.shutdown();
            stoped = true;
        }
    }

    public CompletableFuture<Void> sendReply(short replyType, Object replyData, String replyAddress) {
        return CompletableFuture.runAsync(() -> {
            SendReplyContext context = new SendReplyContext(replyType, replyData, replyAddress);
            try {
                String message = jsonSerializer.serialize(context.replyData);
                byte[] body = message.getBytes(CHARSETUTF8);
                RemotingCommand request = RemotingCommand.createRequestCommand(context.getReplyType(), null);
                request.setBody(body);
                remotingClient.invokeOneway(replyAddress, request, 1000 * 5);
            } catch (Exception ex) {
                logger.error("Send command reply has exception, replyAddress: {}", context.getReplyAddress(), ex);
            }
        });
    }

    class SendReplyContext {
        private short replyType;
        private Object replyData;
        private String replyAddress;

        public SendReplyContext(short replyType, Object replyData, String replyAddress) {
            this.replyType = replyType;
            this.replyData = replyData;
            this.replyAddress = replyAddress;
        }

        public short getReplyType() {
            return replyType;
        }

        public Object getReplyData() {
            return replyData;
        }

        public String getReplyAddress() {
            return replyAddress;
        }
    }
}
