package org.rakam.kume;

import com.google.common.cache.Cache;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.rakam.kume.transport.Packet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentMap;

/**
 * Created by buremba <Burak Emre Kabakcı> on 15/11/14 15:45.
 */
public class ClientChannelAdapter extends ChannelInboundHandlerAdapter {
    final static Logger LOGGER = LoggerFactory.getLogger(ClientChannelAdapter.class);

    final ConcurrentMap<Integer, CompletableFuture<Result>> messageHandlers;

    public ClientChannelAdapter(Cache<Integer, CompletableFuture<Result>> messageHandlers) {
        this.messageHandlers = messageHandlers.asMap();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        LOGGER.debug("client {} got message {}", ctx.channel().localAddress(), msg);
        Packet read = (Packet) msg;
        CompletableFuture<Result> ifPresent = messageHandlers.remove(read.packetNum);
        LOGGER.debug("got response for {}: {}", read.packetNum, read.data);
        if (ifPresent != null)
            ifPresent.complete(new Result(read.getData()));
        else
            LOGGER.warn("unhandled packet {}", msg);
    }
}
