package com.enodeframework.queue.command;

import com.enodeframework.commanding.CommandResult;
import com.enodeframework.commanding.CommandReturnType;
import com.enodeframework.commanding.CommandStatus;
import com.enodeframework.commanding.ICommand;
import com.enodeframework.common.io.AsyncTaskResult;
import com.enodeframework.common.io.AsyncTaskStatus;
import com.enodeframework.common.remoting.RemotingServer;
import com.enodeframework.common.remoting.netty.NettyRemotingServer;
import com.enodeframework.common.remoting.netty.NettyRequestProcessor;
import com.enodeframework.common.remoting.netty.NettyServerConfig;
import com.enodeframework.common.remoting.protocol.RemotingCommand;
import com.enodeframework.common.scheduling.Worker;
import com.enodeframework.common.serializing.IJsonSerializer;
import com.enodeframework.queue.domainevent.DomainEventHandledMessage;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.SocketAddress;
import java.nio.charset.Charset;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.LinkedBlockingQueue;

public class CommandResultProcessor implements NettyRequestProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CommandResultProcessor.class);

    private static final Charset CHARSETUTF8 = Charset.forName("UTF-8");

    public SocketAddress bindingAddress;

    private RemotingServer remotingServer;

    private ConcurrentMap<String, CommandTaskCompletionSource> commandTaskDict;

    private BlockingQueue<CommandResult> commandExecutedMessageLocalQueue;

    private BlockingQueue<DomainEventHandledMessage> domainEventHandledMessageLocalQueue;

    private Worker commandExecutedMessageWorker;

    private Worker domainEventHandledMessageWorker;

    @Autowired
    private IJsonSerializer jsonSerializer;

    private boolean started;

    public CommandResultProcessor(int listenPort) {
        NettyServerConfig nettyServerConfig = new NettyServerConfig();
        nettyServerConfig.setListenPort(listenPort);
        nettyServerConfig.setServerChannelMaxIdleTimeSeconds(3600);
        remotingServer = new NettyRemotingServer(nettyServerConfig);
        remotingServer.registerProcessor(CommandReturnType.CommandExecuted.getValue(), this);
        remotingServer.registerProcessor(CommandReturnType.EventHandled.getValue(), this);
        commandTaskDict = new ConcurrentHashMap<>();
        commandExecutedMessageLocalQueue = new LinkedBlockingQueue<>();
        domainEventHandledMessageLocalQueue = new LinkedBlockingQueue<>();
        commandExecutedMessageWorker = new Worker("ProcessExecutedCommandMessage", () -> processExecutedCommandMessage(commandExecutedMessageLocalQueue.take()));
        domainEventHandledMessageWorker = new Worker("ProcessDomainEventHandledMessage", () -> processDomainEventHandledMessage(domainEventHandledMessageLocalQueue.take()));
    }

    public void registerProcessingCommand(ICommand command, com.enodeframework.commanding.CommandReturnType commandReturnType, CompletableFuture<AsyncTaskResult<CommandResult>> taskCompletionSource) {
        if (commandTaskDict.containsKey(command.id())) {
            throw new RuntimeException(String.format("Duplicate processing command registration, type:%s, id:%s", command.getClass().getName(), command.id()));
        }
        commandTaskDict.put(command.id(), new CommandTaskCompletionSource(commandReturnType, taskCompletionSource));
    }

    public void processFailedSendingCommand(ICommand command) {
        CommandTaskCompletionSource commandTaskCompletionSource = commandTaskDict.remove(command.id());

        if (commandTaskCompletionSource != null) {
            CommandResult commandResult = new CommandResult(CommandStatus.Failed, command.id(), command.getAggregateRootId(), "Failed to send the command.", String.class.getName());
            commandTaskCompletionSource.getTaskCompletionSource().complete(new AsyncTaskResult<>(AsyncTaskStatus.Success, commandResult));
        }
    }

    public CommandResultProcessor start() {
        if (started) {
            return this;
        }
        remotingServer.start();
        bindingAddress = remotingServer.bindAddress();
        commandExecutedMessageWorker.start();
        domainEventHandledMessageWorker.start();
        started = true;
        logger.info("Command result processor started, bindingAddress: {}", remotingServer.bindAddress());
        return this;
    }

    public CommandResultProcessor shutdown() {
        remotingServer.shutdown();
        commandExecutedMessageWorker.stop();
        domainEventHandledMessageWorker.stop();
        return this;
    }

    public SocketAddress getBindingAddress() {
        return bindingAddress;
    }

    @Override
    public RemotingCommand processRequest(ChannelHandlerContext ctx, RemotingCommand request) throws Exception {
        if (request.getCode() == CommandReturnType.CommandExecuted.getValue()) {
            String body = new String(request.getBody(), CHARSETUTF8);
            CommandResult result = jsonSerializer.deserialize(body, CommandResult.class);
            commandExecutedMessageLocalQueue.add(result);
        } else if (request.getCode() == CommandReturnType.EventHandled.getValue()) {
            String body = new String(request.getBody(), CHARSETUTF8);
            DomainEventHandledMessage message = jsonSerializer.deserialize(body, DomainEventHandledMessage.class);
            domainEventHandledMessageLocalQueue.add(message);
        } else {
            logger.error("Invalid remoting request: {}", request);
        }
        return null;
    }

    @Override
    public boolean rejectRequest() {
        return false;
    }

    private void processExecutedCommandMessage(CommandResult commandResult) {
        CommandTaskCompletionSource commandTaskCompletionSource = commandTaskDict.get(commandResult.getCommandId());

        if (commandTaskCompletionSource != null) {
            if (commandTaskCompletionSource.getCommandReturnType().equals(com.enodeframework.commanding.CommandReturnType.CommandExecuted)) {
                commandTaskDict.remove(commandResult.getCommandId());

                if (commandTaskCompletionSource.getTaskCompletionSource().complete(new AsyncTaskResult<>(AsyncTaskStatus.Success, commandResult))) {
                    if (logger.isDebugEnabled()) {
                        logger.debug("Command result return, {}", commandResult);
                    }
                }
            } else if (commandTaskCompletionSource.getCommandReturnType().equals(com.enodeframework.commanding.CommandReturnType.EventHandled)) {
                if (commandResult.getStatus().equals(CommandStatus.Failed) || commandResult.getStatus().equals(CommandStatus.NothingChanged)) {
                    commandTaskDict.remove(commandResult.getCommandId());
                    if (commandTaskCompletionSource.getTaskCompletionSource().complete(new AsyncTaskResult<>(AsyncTaskStatus.Success, commandResult))) {
                        if (logger.isDebugEnabled()) {
                            logger.debug("Command result return, {}", commandResult);
                        }
                    }
                }
            }
        }
    }

    private void processDomainEventHandledMessage(DomainEventHandledMessage message) {
        CommandTaskCompletionSource commandTaskCompletionSource = commandTaskDict.remove(message.getCommandId());
        if (commandTaskCompletionSource != null) {
            CommandResult commandResult = new CommandResult(CommandStatus.Success, message.getCommandId(), message.getAggregateRootId(), message.getCommandResult(), message.getCommandResult() != null ? String.class.getName() : null);

            if (commandTaskCompletionSource.getTaskCompletionSource().complete(new AsyncTaskResult<>(AsyncTaskStatus.Success, commandResult))) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Command result return, {}", commandResult);
                }
            }
        }
    }

    class CommandTaskCompletionSource {
        private com.enodeframework.commanding.CommandReturnType commandReturnType;
        private CompletableFuture<AsyncTaskResult<CommandResult>> taskCompletionSource;

        public CommandTaskCompletionSource(com.enodeframework.commanding.CommandReturnType commandReturnType, CompletableFuture<AsyncTaskResult<CommandResult>> taskCompletionSource) {
            this.commandReturnType = commandReturnType;
            this.taskCompletionSource = taskCompletionSource;
        }

        public com.enodeframework.commanding.CommandReturnType getCommandReturnType() {
            return commandReturnType;
        }

        public void setCommandReturnType(com.enodeframework.commanding.CommandReturnType commandReturnType) {
            this.commandReturnType = commandReturnType;
        }

        public CompletableFuture<AsyncTaskResult<CommandResult>> getTaskCompletionSource() {
            return taskCompletionSource;
        }

        public void setTaskCompletionSource(CompletableFuture<AsyncTaskResult<CommandResult>> taskCompletionSource) {
            this.taskCompletionSource = taskCompletionSource;
        }
    }
}
