package com.example.handler;

import com.example.dto.domain.MessageSeqId;
import com.example.dto.websocket.inbound.*;
import com.example.dto.websocket.outbound.FetchMessageRequest;
import com.example.service.MessageService;
import com.example.service.TerminalService;
import com.example.service.UserService;
import com.example.util.JsonUtil;

public class InboundMessageHandler {
    private final int LIMIT_MESSAGE_COUNT = 10;


    private final TerminalService terminalService;
    private final UserService userService;
    private final MessageService messageService;

    public InboundMessageHandler(TerminalService terminalService, UserService userService, MessageService messageService) {
        this.terminalService = terminalService;
        this.userService = userService;
        this.messageService = messageService;
    }

    public void handle(String payload) {
        JsonUtil.fromJson(payload, BaseMessage.class)
                .ifPresent(message -> {
                    if (message instanceof MessageNotification messageNotification) {
                        messageService.receiveMessage(messageNotification);
                    } else if (message instanceof WriteMessageAck writeMessageAck) {
                        messageService.receiveMessage(writeMessageAck);
                    } else if (message instanceof FetchMessageResponse fetchMessageResponse) {
                        messageService.receiveMessage(fetchMessageResponse);
                    } else if (message instanceof FetchUserInvitecodeResponse fetchUserInvitecodeResponse) {
                        fetchUserInviteCode(fetchUserInvitecodeResponse);
                    } else if (message instanceof InviteResponse inviteResponse) {
                        invite(inviteResponse);
                    } else if (message instanceof InviteNotification inviteNotification) {
                        askInvite(inviteNotification);
                    }else if (message instanceof AcceptResponse acceptResponse) {
                        accept(acceptResponse);
                    } else if (message instanceof AcceptNotification acceptNotification) {
                        acceptNotification(acceptNotification);
                    } else if (message instanceof RejectResponse rejectResponse) {
                        reject(rejectResponse);
                    } else if (message instanceof DisconnectResponse disconnectResponse) {
                        disconnect(disconnectResponse);
                    } else if (message instanceof FetchConnectionsResponse fetchConnectionsResponse) {
                        fetchConnections(fetchConnectionsResponse);
                    } else if (message instanceof FetchChannelsResponse fetchChannelsResponse) {
                        fetchChannels(fetchChannelsResponse);
                    } else if (message instanceof FetchChannelInviteCodeResponse fetchChannelInviteCodeResponse) {
                        fetchChannelInviteCode(fetchChannelInviteCodeResponse);
                    } else if (message instanceof CreateResponse createResponse) {
                        create(createResponse);
                    } else if (message instanceof JoinResponse joinResponse) {
                        join(joinResponse);
                    } else if (message instanceof JoinNotification joinNotification) {
                        joinNotification(joinNotification);
                    } else if (message instanceof EnterResponse enterResponse) {
                        enter(enterResponse);
                    } else if (message instanceof LeaveResponse leaveResponse) {
                        leave(leaveResponse);
                    } else if (message instanceof QuitResponse quitResponse) {
                        quit(quitResponse);
                    } else if (message instanceof ErrorResponse errorResponse) {
                        error(errorResponse);
                    }
                });
    }

    private void fetchUserInviteCode(FetchUserInvitecodeResponse fetchUserInvitecodeResponse) {
        terminalService.printSystemMessage("My InviteCode: %s".formatted(fetchUserInvitecodeResponse.getInviteCode()));
    }

    private void invite(InviteResponse inviteResponse) {
        terminalService.printSystemMessage("Invite %s result: %s".formatted(inviteResponse.getInviteCode(), inviteResponse.getStatus()));
    }

    private void askInvite(InviteNotification inviteNotification) {
        terminalService.printSystemMessage("Do you accept %s's connection request?".formatted(inviteNotification.getUsername()));
    }

    private void accept(AcceptResponse acceptResponse) {
        terminalService.printSystemMessage("Connected %s".formatted(acceptResponse.getUsername()));
    }

    private void acceptNotification(AcceptNotification acceptNotification) {
        terminalService.printSystemMessage("Connected %s".formatted(acceptNotification.getUsername()));
    }

    private void reject(RejectResponse rejectResponse) {
        terminalService.printSystemMessage("Reject %s result: %s".formatted(rejectResponse.getUsername(), rejectResponse.getStatus()));
    }

    private void disconnect(DisconnectResponse disconnectResponse) {
        terminalService.printSystemMessage("Disconnected %s result : %S".formatted(disconnectResponse.getUsername(), disconnectResponse.getStatus()));
    }

    private void fetchConnections(FetchConnectionsResponse fetchConnectionsResponse) {
        fetchConnectionsResponse.getConnections().forEach(connection -> terminalService.printSystemMessage("%s : %s".formatted(connection.username(), connection.status())));
    }

    private void fetchChannels(FetchChannelsResponse fetchChannelsResponse) {
        fetchChannelsResponse.getChannels().forEach(channel -> {
            terminalService.printSystemMessage("%s : %s".formatted(channel.channelId(), channel.title(), channel.headCount()));
        });
    }

    private void fetchChannelInviteCode(FetchChannelInviteCodeResponse fetchChannelInviteCodeResponse) {
        terminalService.printSystemMessage("%s InviteCode: %s".formatted(fetchChannelInviteCodeResponse.getChannelId(), fetchChannelInviteCodeResponse.getInviteCode()));
    }

    private void create(CreateResponse createResponse) {
        terminalService.printSystemMessage("Created channel %s : %s".formatted(createResponse.getChannelId(), createResponse.getTitle()));
    }

    private void join(JoinResponse joinResponse) {
        terminalService.printSystemMessage("Joined channel %s : %s".formatted(joinResponse.getChannelId(), joinResponse.getTitle()));
    }

    private void joinNotification(JoinNotification joinNotification) {
        terminalService.printSystemMessage("Joined channel %s : %s".formatted(joinNotification.getChannelId(), joinNotification.getTitle()));
    }

    private void enter(EnterResponse enterResponse) {
        userService.moveToChannel(enterResponse.getChannelId());
        if (!enterResponse.getLastReadMessageSeqId().equals(enterResponse.getLastChannelMessageSeqId())) {
            MessageSeqId startMessageSeqId = new MessageSeqId(Math.max(enterResponse.getLastChannelMessageSeqId().id() - LIMIT_MESSAGE_COUNT, enterResponse.getLastReadMessageSeqId().id() + 1));
            messageService.sendMessage(new FetchMessageRequest(enterResponse.getChannelId(), startMessageSeqId, enterResponse.getLastChannelMessageSeqId()));
        }

        terminalService.printSystemMessage("Enter channel %s : %s".formatted(enterResponse.getChannelId(), enterResponse.getTitle()));
    }

    private void leave(LeaveResponse leaveResponse) {
        terminalService.printSystemMessage("Leave channel %s".formatted(userService.getChannelId()));
        userService.moveToLobby();
    }

    private void quit(QuitResponse quitResponse) {
        terminalService.printSystemMessage("Quit channel %s".formatted(quitResponse.getChannelId()));
    }

    private void error(ErrorResponse errorResponse) {
        terminalService.printSystemMessage("Error %s : %s".formatted(errorResponse.getMessageType(), errorResponse.getMessage()));
    }
}
